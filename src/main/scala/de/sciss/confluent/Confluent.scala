/*
 *  Confluent.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
 *
 *	 This software is free software; you can redistribute it and/or
 *	 modify it under the terms of the GNU General Public License
 *	 as published by the Free Software Foundation; either
 *	 version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	 This software is distributed in the hope that it will be useful,
 *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	 General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 */

package de.sciss.confluent

import impl.{PartialCacheMapImpl, InMemoryCacheMapImpl, DurableCacheMapImpl, CacheMapImpl}
import util.MurmurHash
import de.sciss.lucre.{event => evt, Writable, data, stm, DataInput, DataOutput}
import evt.ReactionMap
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike}
import data.Ancestor
import concurrent.stm.{TxnLocal, TxnExecutor, InTxn, Txn => ScalaTxn}
import TemporalObjects.{logConfluent, logPartial}
import stm.impl.BerkeleyDB
import java.io.File
import stm.{Var => STMVar, ImmutableSerializer, IdentifierMap, Cursor, Disposable, Durable, DataStoreFactory, DataStore, Serializer}
import collection.immutable.{IndexedSeq => IIdxSeq, IntMap, LongMap}

object Confluent {
   private type S = Confluent

   def apply( storeFactory: DataStoreFactory[ DataStore ]) : Confluent = new System( storeFactory )

   def tmp() : Confluent = {
      val dir = File.createTempFile( "confluent_", "db" )
      dir.delete()
//      dir.mkdir()
      new System( BerkeleyDB.factory( dir ))
   }

   sealed trait ID extends KSys.ID[ S#Tx, Path ]

   private final class ConfluentID( val id: Int, val path: Path ) extends ID {
      override def hashCode = {
         import MurmurHash._
         var h = startHash( 2 )
         val c = startMagicA
         val k = startMagicB
         h     = extendHash( h, id, c, k )
         h     = extendHash( h, path.##, nextMagicA( c ), nextMagicB( k ))
         finalizeHash( h )
      }

      override def equals( that: Any ) : Boolean =
         that.isInstanceOf[ ConfluentID ] && {
            val b = that.asInstanceOf[ ConfluentID ]
            id == b.id && path == b.path
         }

      def write( out: DataOutput ) {
         out.writeInt( id )
         path.write( out )
      }

      override def toString = "<" + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: S#Tx ) {}
   }

   private final class PartialID( val id: Int, val path: Path ) extends ID {
      override def hashCode = {
         import MurmurHash._
         var h = startHash( 2 )
         var a = startMagicA
         var b = startMagicB
         h     = extendHash( h, id, a, b )

         if( path.nonEmpty ) {
            a     = nextMagicA( a )
            b     = nextMagicB( b )
            h     = extendHash( h, (path.head >> 32).toInt, a, b )

            a     = nextMagicA( a )
            b     = nextMagicB( b )
            h     = extendHash( h, (path.last >> 32).toInt, a, b )
         }
         finalizeHash( h )
      }

      override def equals( that: Any ) : Boolean =
         that.isInstanceOf[ PartialID ] && {
            val b = that.asInstanceOf[ PartialID ]
            val bp = b.path
            if( path.isEmpty ) {
               id == b.id && bp.isEmpty
            } else {
               id == b.id && bp.nonEmpty && path.head == bp.head && path.last == bp.last
            }
         }

      def write( out: DataOutput ) {
         out.writeInt( id )
         path.write( out )
      }

      override def toString = "<" + id + " @ " + {
         if( path.isEmpty ) ">" else {
            val head = path.head
            val tail = path.tail
            val (mid, last) = tail.splitIndex
            mid.mkString( head.toInt.toString + "(,", ",", ")," + last.toInt + ">" )
         }
      }

      def dispose()( implicit tx: S#Tx ) {}
   }

//   private object IDOrdering extends Ordering[ S#ID ] {
//      def compare( a: S#ID, b: S#ID ) : Int = {
//         val aid = a.id
//         val bid = b.id
//         if( aid < bid ) -1 else if( aid > bid ) 1 else {
//            val ahash   = a.path.sum
//            val bhash   = b.path.sum
//            if( ahash < bhash ) -1 else if( ahash > bhash ) 1 else 0
//         }
//      }
//   }

   private object PathMeasure extends Measure[ Long, (Int, Long) ] {
      override def toString = "PathMeasure"
      val zero = (0, 0L)
      def apply( c: Long ) = (1, c >> 32)
      def |+|( a: (Int, Long), b: (Int, Long) ) = ((a._1 + b._1), (a._2 + b._2))
      def |+|( a: (Int, Long), b: (Int, Long), c: (Int, Long) ) = ((a._1 + b._1 + c._1), (a._2 + b._2 + c._2))
   }

   object Path {
      def test_empty : Path = empty
      private[Confluent] val empty      = new Path( FingerTree.empty( PathMeasure ))
      /* private[Confluent] */ def root = new Path( FingerTree( 1L << 32, 1L << 32 )( PathMeasure ))
//      private[Confluent] def apply( tree: Long, term: Long ) = new Path( FingerTree( tree, term )( PathMeasure ))

      def read( in: DataInput ) : S#Acc  = {
         implicit val m = PathMeasure
         val sz         = in.readInt()
         var tree       = FingerTree.empty( m )
         var i = 0; while( i < sz ) {
            tree      :+= in.readLong()
         i += 1 }
         new Path( tree )
      }

      def readAndAppend( in: DataInput, acc: S#Acc )( implicit tx: S#Tx ) : S#Acc = {
         implicit val m = PathMeasure
         val sz         = in.readInt()
//         val accTree    = acc.tree
         var tree       = FingerTree.empty( m )
         val accIter    = acc.tree.iterator
         if( accIter.isEmpty ) {
            var i = 0; while( i < sz ) {
               tree      :+= in.readLong()
            i += 1 }
         } else {
            val writeTerm  = accIter.next()
            val readTerm   = accIter.next()
            if( sz == 0 ) {
               // entity was created in the terminal version
   //            tree = readTerm +: readTerm +: tree   // XXX TODO should have FingerTree.two
               tree = writeTerm +: readTerm +: tree   // XXX TODO should have FingerTree.two
   //logConfig( "readAndAppend for " + acc + " finds empty path, yields " + tree )
               // XXX is this correct? i would think that still
               // we need to compare tree levels? -- if writeTerm level != readTerm level,
               // wouldn't we instead need <write, write, read, read> ?
               // --> NO, they would always have the same level, because
               // the write path begins with the read path (they share the same initial version)
            } else {
               val szm        = sz - 1
               var i = 0; while( i < szm ) {
                  tree      :+= in.readLong()
               i += 1 }
               val lastTerm   = in.readLong()
               val oldLevel   = tx.readTreeVertexLevel( lastTerm )
               val newLevel   = tx.readTreeVertexLevel( writeTerm )

               if( oldLevel != newLevel ) {
                  tree      :+= lastTerm
                  tree      :+= writeTerm
               }
               tree         :+= readTerm
            }
            accIter.foreach( tree :+= _ )
         }
         new Path( tree )
      }
   }

   /**
    * The finger tree has elements of type `Long` where the upper 32 bits are the randomized version,
    * and the lower 32 bits are the incremental version. The measure is taking the index and running sum
    * of the tree.
    */
   final class Path private[Confluent]( protected val tree: FingerTree[ (Int, Long), Long ])
   extends KSys.Acc[ S ] with FingerTreeLike[ (Int, Long), Long, Path ] {
      implicit protected def m: Measure[ Long, (Int, Long) ] = PathMeasure

      override def toString = mkString( "Path(", ", ", ")" )

      override def hashCode = {
         import MurmurHash._
         val m = sum
         var h = startHash( 2 )
         val c = startMagicA
         val k = startMagicB
         h     = extendHash( h, (m >> 32).toInt, c, k )
         h     = extendHash( h, m.toInt, nextMagicA( c ), nextMagicB( k ))
         finalizeHash( h )
      }

      override def equals( that: Any ) : Boolean =
         that.isInstanceOf[ PathLike ] && (that.asInstanceOf[ PathLike ].sum == sum)

      def test_:+( elem: Long ) : Path = :+( elem )

      private[confluent] def :+( last: Long ) : Path = wrap( tree :+ last )

      private[confluent] def +:( head: Long ) : Path = wrap( head +: tree )

      private[confluent] def apply( idx: Int ) : Long = tree.find1( _._1 > idx )

      // XXX TODO testin one two
      private[confluent] def partial : Path = {
         val sz   = size
         if( sz == 0 ) return this

         var res = FingerTree.empty( PathMeasure )
//         var idx = 0; while( idx < sz ) {
//            res :+= tree.find1( _._1 > idx )
//            idx += 2
//         }
//         if( sz % 2 == 0 ) res :+= tree.last

//         require( sz % 2 == 0 )
         if( sz % 2 != 0 ) {
println( "?? partial from index " + this )
         }
         res :+= head
         res :+= last
         wrap( res )
      }

      private[confluent] def maxPrefixLength( that: Long ) : Int = {
         // XXX TODO more efficient
         val sz = size
         var i = 0; while( i < sz ) {
            if( apply( i ) == that ) return i + 1
         i += 2 }
         0
      }

      private[confluent] def maxPrefixLength( that: S#Acc ) : Int = {
         val ta   = tree
         val tb   = that.tree
         val sz   = math.min( size, that.size )
         var same = true
         // XXX TODO more efficient (binary search according to sum)
         var i = 0; while( i < sz && same ) {
            same = ta.find1( _._1 > i ) == tb.find1( _._1 > i )
         i += 1 }
//         wrap( ta.split1( _._1 > i )._1 )
         if( same ) i else i - 1
      }

      /* private[confluent] */ def dropAndReplaceHead( dropLen: Int, newHead: Long ) : Path = {
         val (_, _, right) = tree.split1( _._1 > dropLen )
         wrap( newHead +: right )
      }

      private[confluent] def addTerm( term: Long )( implicit tx: S#Tx ) : Path = {
         val t = if( tree.isEmpty ) {
            term +: term +: FingerTree.empty( PathMeasure )  // have FingerTree.two at some point
         } else {
            val oldLevel   = tx.readTreeVertexLevel( this.term )
            val newLevel   = tx.readTreeVertexLevel( term )
            if( oldLevel == newLevel ) {
               tree.init :+ term // XXX TODO Have :-| in the finger tree at some point
            } else {
               tree :+ term :+ term
            }
         }
         wrap( t )
      }

      private[Confluent] def seminal : Path = {
         val (_init, term) = splitIndex
         wrap( FingerTree( _init.term, term ))
      }

      // XXX TODO should have an efficient method in finger tree
      private[Confluent] def indexTerm : Long = {
         tree.init.last
//         val idx = size - 2; tree.find1( _._1 > idx ) ???
      }

      private[confluent] def indexSum : Long = sum - (last >> 32)

      // XXX TODO should have an efficient method in finger tree
      private[confluent] def :-|( suffix: Long ) : Path = wrap( tree.init :+ suffix )

      // XXX TODO should have an efficient method in finger tree
      /* private[confluent] */ def drop( n: Int ) : Path = {
//         var res = tree
//         var i = 0; while( i < n ) {
//            res = res.tail
//         i += 1 }
         if( n <= 0 ) return this
         if( n >= size ) return Path.empty
         val nm = n - 1
         if( nm == 0 ) return wrap( tree.tail )
         val (_ ,_, right) = tree.split1( _._1 > nm )
         wrap( right )
      }

      // XXX TODO should have an efficient method in finger tree
      private[confluent] def splitIndex : (Path, Long) = (init, last)

      private[confluent] def splitAtIndex( idx: Int ) : (Path, Long) = {
         val tup = tree.split1( _._1 > idx )
         (wrap( tup._1 ), tup._2)
      }

      private[confluent] def splitAtSum( hash: Long ) : (Path, Long) = {
         val tup = tree.split1( _._2 > hash )
         (wrap( tup._1 ), tup._2)
      }

      private[confluent] def indexOfSum( hash: Long ): Int = {
         // XXX TODO very inefficient
         var idx = 0; val sz = size; while( idx < sz ) {
            if( sumUntil( idx ) >= hash ) return idx
            idx += 1
         }
         idx
      }

      def write( out: DataOutput ) {
         out.writeInt( size )
         tree.iterator.foreach( out.writeLong )
      }

      def index : Path = wrap( tree.init )
      def term : Long = tree.last

      def size : Int = tree.measure._1
      def sum : Long = tree.measure._2

      // XXX TODO -- need an applyMeasure method in finger tree
      def sumUntil( n: Int ) : Long = {
//         if( n <= 0 ) return 0L
//         if( n >= size ) return sum
//         tree.find1( _._1 >= n ).
         tree.split( _._1 > n )._1.measure._2
      }

      def take( n: Int ) : PathLike = _take( n )

      def _take( n: Int ) : S#Acc = wrap( tree.split( _._1 > n )._1 ) // XXX future optimization in finger tree

      protected def wrap( _tree: FingerTree[ (Int, Long), Long ]) : Path = new Path( _tree )

      def mkString( prefix: String, sep: String, suffix: String ) : String =
         tree.iterator.map( _.toInt ).mkString( prefix, sep, suffix )
   }

   sealed trait IndexTree extends Writable with Disposable[ Durable#Tx ] {
      def tree: Ancestor.Tree[ Durable, Long ]
      def level: Int
      def term: Long
   }

   private final class IndexTreeImpl( val tree: Ancestor.Tree[ Durable, Long ], val level: Int )
   extends IndexTree {
      override def hashCode : Int = term.toInt
      def term: Long = tree.root.version

      override def equals( that: Any ) : Boolean = {
         that.isInstanceOf[ IndexTree ] && (term == that.asInstanceOf[ IndexTree ].term)
      }

      def write( out: DataOutput ) {
         tree.write( out )
         out.writeInt( level )
      }

      def dispose()( implicit tx: Durable#Tx ) {
         tree.dispose()
      }

      override def toString = "IndexTree<v=" + term.toInt + ", l=" + level + ">"
   }

   private final class IndexMapImpl[ A ]( protected val index: S#Acc,
                                          protected val map: Ancestor.Map[ Durable, Long, A ])
   extends IndexMap[ S, A ] {
      override def toString = index.mkString( "IndexMap(<", ",", ">, " + map + ")" )

      def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A) = {
         val v = tx.readTreeVertex( map.full, index, term )._1
         val (v2, value) = map.nearest( v )( tx.durable )
         (v2.version, value)
      }

      def add( term: Long, value: A )( implicit tx: S#Tx ) {
         val v = tx.readTreeVertex( map.full, index, term )._1
         map.add( (v, value) )( tx.durable )
      }

      def write( out: DataOutput ) {
         map.write( out )
      }
   }

   private final class PartialMapImpl[ A ]( protected val index: S#Acc,
                                            protected val map: Ancestor.Map[ Durable, Long, A ])
   extends IndexMap[ S, A ] {
      override def toString = index.mkString( "PartialMap(<", ",", ">, " + map + ")" )

      def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A) = {
         val v = tx.readPartialTreeVertex( index, term )
         val (v2, value) = map.nearest( v )( tx.durable )
         (v2.version, value)
      }

      def add( term: Long, value: A )( implicit tx: S#Tx ) {
         val v = tx.readPartialTreeVertex( index, term )
         map.add( (v, value) )( tx.durable )
      }

      def write( out: DataOutput ) {
         map.write( out )
      }
   }

   private final case class MeldInfo( highestLevel: Int, highestTrees: Set[ S#Acc ]) {
      def requiresNewTree : Boolean = highestTrees.size > 1
      def outputLevel : Int = if( requiresNewTree ) highestLevel + 1 else highestLevel

      /**
       * An input tree is relevant if its level is higher than the currently observed
       * highest level, or if it has the same level but was not recorded in the set
       * of highest trees.
       */
      def isRelevant( level: Int, seminal: S#Acc ) : Boolean = {
         level > highestLevel || (level == highestLevel && !highestTrees.contains( seminal ))
      }

      def add( level: Int, seminal: S#Acc ) : MeldInfo = {
         if( isRelevant( level, seminal )) MeldInfo( level, highestTrees + seminal ) else this
      }

      def isEmpty : Boolean = highestLevel < 0
   }
   private val emptyMeldInfo = MeldInfo( -1, Set.empty )

   sealed trait Txn extends KSys.Txn[ S ] {
      private[Confluent] implicit def durable: Durable#Tx

      private[Confluent] def readTreeVertex( tree: Ancestor.Tree[ Durable, Long ], index: S#Acc,
                                             term: Long ) : (Ancestor.Vertex[ Durable, Long ], Int)
      private[Confluent] def readPartialTreeVertex( index: S#Acc, term: Long ) : Ancestor.Vertex[ Durable, Long ]
      private[Confluent] def writeTreeVertex( tree: IndexTree, v: Ancestor.Vertex[ Durable, Long ]) : Unit
      private[Confluent] def readTreeVertexLevel( term: Long ) : Int
      private[Confluent] def readIndexTree( term: Long ) : IndexTree
      private[Confluent] def newIndexTree( term: Long, level: Int ) : IndexTree

      private[Confluent] def addInputVersion( path: S#Acc ) : Unit

      private[Confluent] def putTxn[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : Unit
      private[Confluent] def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) : Unit
      private[Confluent] def getTxn[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A
      private[Confluent] def getNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A
      private[Confluent] def isFresh( id: S#ID ) : Boolean

      private[Confluent] def putPartial[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : Unit
      private[Confluent] def getPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A
//      private[Confluent] def getFreshPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A

      private[Confluent] def removeFromCache( id: S#ID ) : Unit
      private[Confluent] def addDirtyMap( map: CacheMapImpl[ Confluent, _, _ ]) : Unit

      private[Confluent] def makeVar[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : BasicVar[ A ]
   }

   private sealed trait TxnImpl extends Txn with DurableCacheMapImpl[ Confluent, Int ] {
      private val dirtyMaps : TxnLocal[ IIdxSeq[ CacheMapImpl[ Confluent, _, _ ]]] = TxnLocal( initialValue =
         { implicit itx =>
            logConfluent( "....... txn dirty ......." )
            ScalaTxn.beforeCommit { implicit itx => flushMaps( dirtyMaps() )}
            IIdxSeq.empty
         })

      private val markDirtyFlag = TxnLocal( false )

      final private def markDirty() {
         if( !markDirtyFlag.swap( true )( peer )) {
            addDirtyMap( this )
            addDirtyMap( partialCache )
         }
      }

      def forceWrite() { markDirty() }

      final private[Confluent] def addDirtyMap( map: CacheMapImpl[ Confluent, _, _ ]) {
         dirtyMaps.transform( _ :+ map )( peer )
      }

      final protected def emptyCache : Map[ Int, _ ] = CacheMapImpl.emptyIntMapVal

      private val meld  = TxnLocal( emptyMeldInfo )

      protected def flushNewTree( level: Int ) : Long
      protected def flushOldTree() : Long

      final protected def partialCache: PartialCacheMapImpl[ Confluent, Int ] = system.partialMap

      final protected def store = system.varMap

      protected def partialTree: Ancestor.Tree[ Durable, Long ] = system.partialTree

      private def flushMaps( maps: IIdxSeq[ CacheMapImpl[ Confluent, _, _ ]]) {
         val meldInfo      = meld.get( peer )
         val newTree       = meldInfo.requiresNewTree
//         logConfig( Console.RED + "txn flush - term = " + outTerm.toInt + Console.RESET )
         val outTerm = if( newTree ) {
            flushNewTree( meldInfo.outputLevel )
         } else {
            flushOldTree()
         }
         logConfluent( "::::::: txn flush - " + (if( newTree ) "meld " else "") + "term = " + outTerm.toInt + " :::::::" )
         system.position_=( inputAccess.addTerm( outTerm )( this ))( this ) // XXX TODO last path would depend on value written to inputAccess?
         maps.foreach( _.flushCache( outTerm )( this ))
      }

      final def newID() : S#ID = {
         val res = new ConfluentID( system.newIDValue()( this ), Path.empty )
         logConfluent( "txn newID " + res )
         res
      }

      final def newPartialID() : S#ID = {
         val res = new PartialID( system.newIDValue()( this ), Path.empty )
         logConfluent( "txn newPartialID " + res )
         res
      }

      // XXX TODO eventually should use caching
      final private[Confluent] def readTreeVertex( tree: Ancestor.Tree[ Durable, Long ], index: S#Acc,
                                                  term: Long ) : (Ancestor.Vertex[ Durable, Long ], Int) = {
//         val root = tree.root
//         if( root.version == term ) return root // XXX TODO we might also save the root version separately and remove this conditional
         system.store.get { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( term.toInt )
         } { in =>
            in.readInt()   // tree index!
            val access  = index :+ term
            val level   = in.readInt()
            val v       = tree.vertexSerializer.read( in, access )( durable )
            (v, level)
         } getOrElse sys.error( "Trying to access inexisting vertex " + term.toInt )
      }

      final private[Confluent] def readPartialTreeVertex( index: S#Acc,
                                                          term: Long ) : Ancestor.Vertex[ Durable, Long ] = {
//         val root = tree.root
//         if( root.version == term ) return root // XXX TODO we might also save the root version separately and remove this conditional
         system.store.get { out =>
            out.writeUnsignedByte( 3 )
            out.writeInt( term.toInt )
         } { in =>
            val access  = index :+ term
            partialTree.vertexSerializer.read( in, access )( durable )
         } getOrElse sys.error( "Trying to access inexisting vertex " + term.toInt )
      }

      final private[Confluent] def writeTreeVertex( tree: IndexTree, v: Ancestor.Vertex[ Durable, Long ]) {
         system.store.put { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( v.version.toInt )
         } { out =>
            out.writeInt( tree.term.toInt )
            out.writeInt( tree.level )
            tree.tree.vertexSerializer.write( v, out )
         }
      }

      final private[Confluent] def writePartialTreeVertex( v: Ancestor.Vertex[ Durable, Long ]) {
         system.store.put { out =>
            out.writeUnsignedByte( 3 )
            out.writeInt( v.version.toInt )
         } { out =>
            partialTree.vertexSerializer.write( v, out )
         }
      }

      final private[confluent] def readPath( in: DataInput ) : S#Acc = Path.read( in )

      final private[Confluent] def readTreeVertexLevel( term: Long ) : Int = {
         system.store.get { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( term.toInt )
         } { in =>
            in.readInt()
         } getOrElse sys.error( "Trying to access inexisting vertex " + term.toInt )
      }

      override def toString = "KSys#Tx" // + system.path.mkString( "<", ",", ">" )

      final def reactionMap : ReactionMap[ S ] = system.reactionMap

      final private[Confluent] def addInputVersion( path: S#Acc ) {
         val sem1 = path.seminal
         val sem2 = inputAccess.seminal
         if( sem1 == sem2 ) return
         meld.transform( m => {
            if( sem1 == sem2 ) m else {
               val tree1 = readIndexTree( sem1.head )
               val m1 = if( m.isEmpty ) {
                  val tree2 = readIndexTree( sem2.head )
                  m.add( tree2.level, sem2 )
               } else m
               m1.add( tree1.level, sem1 )
            }
         })( peer )
      }

      final private[Confluent] def getNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A = {
         logConfluent( "txn get " + id )
         getCacheNonTxn[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

      final private[Confluent] def getTxn[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
         logConfluent( "txn get' " + id )
         getCacheTxn[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

      final private[Confluent] def putTxn[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) {
//         logConfig( "txn put " + id )
         putCacheTxn[ A ]( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final private[Confluent] def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) {
//         logConfig( "txn put " + id )
         putCacheNonTxn[ A ]( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final private[Confluent] def putPartial[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) {
         partialCache.putPartial( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final private[Confluent] def getPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
//         logPartial( "txn get " + id )
         partialCache.getPartial[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

//      final private[Confluent] def getFreshPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
////         logPartial( "txn getFresh " + id )
//         partialCache.getFreshPartial[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
//      }

      final private[Confluent] def isFresh( id: S#ID ) : Boolean = {
         val id1  = id.id
         val path = id.path
         // either the value was written during this transaction (implies freshness)...
         cacheContains( id1, path )( this ) || {
            // ...or we have currently an ongoing meld which will produce a new
            // index tree---in that case (it wasn't in the cache!) the value is definitely not fresh...
            if( meld.get( peer ).requiresNewTree ) false else {
               // ...or otherwise freshness means the most recent write index corresponds
               // to the input access index
               // store....
               store.isFresh( id1, path )( this )
            }
         }
      }

      final private[Confluent] def removeFromCache( id: S#ID ) {
         removeCacheOnly( id.id )( this )
      }

//      final def readVal[ A ]( id: S#ID )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : A = getTxn[ A ]( id )
//
//      final def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) {
//         putTxn[ A ]( id, value )
//      }

      final private[confluent] def getIndexTreeTerm( term: Long ) : Long = readIndexTree( term ).term

      final private[Confluent] def readIndexTree( term: Long ) : IndexTree = {
         val st = system.store
         st.get { out =>
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         } { in =>
            val tree    = Ancestor.readTree[ Durable, Long ]( in, () )( durable, Serializer.Long, _.toInt )
            val level   = in.readInt()
            new IndexTreeImpl( tree, level )
         } getOrElse { // sys.error( "Trying to access inexisting tree " + term.toInt )

            // `term` does not form a tree index. it may be a tree vertex, though. thus,
            // in this conditional step, we try to (partially) read `term` as vertex, thereby retrieving
            // the underlying tree index, and then retrying with that index (`term2`).
            st.get { out =>
               out.writeUnsignedByte( 0 )
               out.writeInt( term.toInt )
            } { in =>
               val term2 = in.readInt()   // tree index!
               require( term2 != term, "Trying to access inexisting tree " + term.toInt )
               readIndexTree( term2 )
            } getOrElse sys.error( "Trying to access inexisting tree " + term.toInt )
         }
      }

      final private[Confluent] def newIndexTree( term: Long, level: Int ) : IndexTree = {
         logConfluent( "txn new tree " + term.toInt )
         val tree = Ancestor.newTree[ Durable, Long ]( term )( durable, Serializer.Long, _.toInt )
         val res  = new IndexTreeImpl( tree, level )
         system.store.put( out => {
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         })( res.write )
         writeTreeVertex( res, tree.root )
         res
      }

      final private[confluent] def readPartialMap[ A ]( access: S#Acc, in: DataInput )
                                                      ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         val map     = Ancestor.readMap[ Durable, Long, A ]( in, (), partialTree )
         val index   = access._take( 1 )   // XXX correct ?
         new PartialMapImpl[ A ]( index, map )
      }

      final private[confluent] def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                                                   ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         val term = index.term
         val tree = readIndexTree( term )
         val map  = Ancestor.readMap[ Durable, Long, A ]( in, (), tree.tree )
         new IndexMapImpl[ A ]( index, map )
      }

      final private[confluent] def newPartialMap[ A ]( access: S#Acc, rootTerm: Long, rootValue: A )
                                                     ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         val map     = Ancestor.newMap[ Durable, Long, A ]( partialTree, partialTree.root, rootValue )
         val index   = access._take( 1 )   // XXX correct ?
         new PartialMapImpl[ A ]( index, map )
      }

      final private[confluent] def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )
                                                   ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         val tree       = readIndexTree( index.term )
         val full       = tree.tree
         val rootVertex = if( rootTerm == tree.term ) {
            full.root
         } else {
            readTreeVertex( full, index, rootTerm )._1
         }
         val map  = Ancestor.newMap[ Durable, Long, A ]( full, rootVertex, rootValue )
         new IndexMapImpl[ A ]( index, map )
      }

      @inline private def alloc(        pid: S#ID ) : S#ID = new ConfluentID( system.newIDValue()( this ), pid.path )
      @inline private def allocPartial( pid: S#ID ) : S#ID = new PartialID(   system.newIDValue()( this ), pid.path )

      final def newVar[ A ]( pid: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( alloc( pid ))
         logConfluent( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newPartialVar[ A ]( pid: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = new PartialVarTxImpl[ A ]( allocPartial( pid ))
         logConfluent( "txn newPartialVar " + res )
         res.setInit( init )( this )
         res
      }

      final def newBooleanVar( pid: S#ID, init: Boolean ) : S#Var[ Boolean ] = {
         val id   = alloc( pid )
         val res  = new BooleanVar( id )
         logConfluent( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newIntVar( pid: S#ID, init: Int ) : S#Var[ Int ] = {
         val id   = alloc( pid )
         val res  = new IntVar( id )
         logConfluent( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newLongVar( pid: S#ID, init: Long ) : S#Var[ Long ] = {
         val id   = alloc( pid )
         val res  = new LongVar( id )
         logConfluent( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = new Array[ S#Var[ A ]]( size )

      final def newInMemoryIDMap[ A ] : IdentifierMap[ S#ID, S#Tx, A ] = {
         val map = InMemoryConfluentMap.newIntMap[ Confluent ]
         new InMemoryIDMapImpl[ A ]( map )
      }

      final def newDurableIDMap[ A ]( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         mkDurableIDMap( system.newIDValue()( this ))
      }

      private def mkDurableIDMap[ A ]( id: Int )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         val map  = DurablePersistentMap.newConfluentLongMap[ Confluent ]( system.store )
         val idi  = new ConfluentID( id, Path.empty )
         val res  = new DurableIDMapImpl[ A ]( idi, map )
         durableIDMaps.transform( _ + (id -> res) )( peer )
         res
      }

      private def readSource( in: DataInput, pid: S#ID ) : S#ID = {
         val id = in.readInt()
         new ConfluentID( id, pid.path )
      }

      private def readPartialSource( in: DataInput, pid: S#ID ) : S#ID = {
         val id = in.readInt()
         new PartialID( id, pid.path )
      }

      final private[Confluent] def makeVar[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : BasicVar[ A ] = {
         ser match {
            case plain: ImmutableSerializer[ _ ] =>
               new VarImpl[ A ]( id, plain.asInstanceOf[ ImmutableSerializer[ A ]])
            case _ =>
               new VarTxImpl[ A ]( id )
         }
      }

      final def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( readSource( in, pid ))
         logConfluent( "txn read " + res )
         res
      }

      final def readPartialVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = new PartialVarTxImpl[ A ]( readPartialSource( in, pid ))
         logConfluent( "txn read " + res )
         res
      }

      final def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] = {
         val res = new BooleanVar( readSource( in, pid ))
         logConfluent( "txn read " + res )
         res
      }

      final def readIntVar( pid: S#ID, in: DataInput ) : S#Var[ Int ] = {
         val res = new IntVar( readSource( in, pid ))
         logConfluent( "txn read " + res )
         res
      }

      final def readLongVar( pid: S#ID, in: DataInput ) : S#Var[ Long ] = {
         val res = new LongVar( readSource( in, pid ))
         logConfluent( "txn read " + res )
         res
      }

      final def readID( in: DataInput, acc: S#Acc ) : S#ID = {
         val res = new ConfluentID( in.readInt(), Path.readAndAppend( in, acc )( this ))
         logConfluent( "txn readID " + res )
         res
      }

      final def readPartialID( in: DataInput, acc: S#Acc ) : S#ID = {
//         readID( in, acc )
         val res = new PartialID( in.readInt(), Path.readAndAppend( in, acc )( this ))
         logConfluent( "txn readPartialID " + res )
         res
      }

      final def readDurableIDMap[ A ]( in: DataInput )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         val id = in.readInt()
         durableIDMaps.get( peer ).get( id ) match {
            case Some( existing ) => existing.asInstanceOf[ DurableIDMapImpl[ A ]]
            case None => mkDurableIDMap( id )
         }
      }

      // there may be a more efficient implementation, but for now let's just calculate
      // all the prefixes and retrieve them the normal way.
      final def refresh[ A ]( writePath: S#Acc, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : A = {
         val readPath = inputAccess
         if( readPath == writePath ) return value

         val out     = new DataOutput()
         serializer.write( value, out )
         val in      = new DataInput( out )

         val (writeIndex, writeTerm) = writePath.splitIndex
         var entries = LongMap.empty[ Long ]
         Hashing.foreachPrefix( writeIndex, entries.contains ) {
            case (_hash, _preSum) => entries += (_hash, _preSum)
         }
         entries += (writeIndex.sum, 0L)  // full cookie

         var (maxIndex, maxTerm) = readPath.splitIndex
         while( true ) {
            val preLen = Hashing.maxPrefixLength( maxIndex, entries.contains )
            val index = if( preLen == maxIndex.size ) {
               // maximum prefix lies in last tree
               maxIndex
            } else {
               // prefix lies in other tree
               maxIndex._take( preLen )
            }
            val preSum = index.sum
            val hash = entries( preSum )
            if( hash == 0L ) {   // full entry
               val suffix  = writeTerm +: readPath.drop( preLen )
               return serializer.read( in, suffix )( this )
            } else {   // partial hash
               val (fullIndex, fullTerm) = maxIndex.splitAtSum( hash )
               maxIndex = fullIndex
               maxTerm  = fullTerm
            }
         }
         sys.error( "Never here" )
      }
   }

   private final class RegularTxn( val system: S, private[Confluent] val durable: Durable#Tx,
                                   val peer: InTxn, val inputAccess: Path )
   extends TxnImpl {

      override def toString = "Txn" + inputAccess

      protected def flushOldTree() : Long = {
         val childTerm  = system.newVersionID( this )
         val (index, parentTerm) = inputAccess.splitIndex
         val tree       = readIndexTree( index.term )
         val parent     = readTreeVertex( tree.tree, index, parentTerm )._1
         val child      = tree.tree.insertChild( parent, childTerm )( durable )
         writeTreeVertex( tree, child )

         // ---- partial ----
         val pParent    = readPartialTreeVertex( index, parentTerm )
         val pChild     = partialTree.insertChild( pParent, childTerm )( durable )
         writePartialTreeVertex( pChild )

         childTerm
      }

      protected def flushNewTree( level: Int ) : Long = {
         val term = system.newVersionID( this )
         newIndexTree( term, level )

         // ---- partial ----
         val (index, parentTerm) = inputAccess.splitIndex
         val pParent    = readPartialTreeVertex( index, parentTerm )
         val pChild     = partialTree.insertChild( pParent, term )( durable )
         writePartialTreeVertex( pChild )

         term
      }
   }

   private final class RootTxn( val system: S, val peer: InTxn ) extends TxnImpl {
      val inputAccess = Path.root
      override def toString = "RootTxn"

      private[Confluent] implicit lazy val durable: Durable#Tx = {
         logConfluent( "txn durable" )
         system.durable.wrap( peer )
      }

      protected def flushOldTree() : Long = inputAccess.term
      protected def flushNewTree( level: Int ) : Long = sys.error( "Cannot meld in the root version" )
   }

   sealed trait Var[ @specialized A ] extends STMVar[ S#Tx, A ] {
//      private[Confluent] def asEntry: S#Entry[ A ]
   }

   private sealed trait BasicVar[ A ] extends Var[ A ] {
      protected def id: S#ID

//      @elidable(CONFIG) protected final def assertExists()(implicit tx: Txn) {
//         require(tx.system.exists(id), "trying to write disposed ref " + id)
//      }

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
//         id.write( out )
      }

      final def dispose()( implicit tx: S#Tx ) {
         tx.removeFromCache( id )
         id.dispose()
      }

      def setInit( v: A )( implicit tx: S#Tx ) : Unit
      final def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      final def isFresh( implicit tx: S#Tx ) : Boolean  = tx.isFresh( id )
   }

   private final class VarImpl[ A ]( protected val id: S#ID, protected val ser: ImmutableSerializer[ A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         logConfluent( this.toString + " set " + v )
         tx.putNonTxn( id, v )( ser )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfluent( this.toString + " get" )
         tx.getNonTxn[ A ]( id )( ser )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def setInit( v: A )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( ser )
      }

//      // XXX TODO - this will fail if id.path does not start in v0
//      private[Confluent] def asEntry : S#Entry[ A ] = new RootVar[ A ]( id.id, toString, ser )

      override def toString = "Var(" + id + ")"
   }

   /*
    * Because durable maps are persisted, they may be deserialized multiple times per transaction.
    * This could potentially cause a problem: imagine two instances A1 and A2. A1 is read, a `put`
    * is performed, making A1 call `markDirty`. Next, A2 is read, again a `put` performed, and A2
    * calls `markDirty`. Next, another `put` on A1 is performed. In the final flush, because A2
    * was marked after A1, it's cached value will override A2's, even though it is older.
    *
    * To avoid that, durable maps are maintained by their id's in a transaction local map. That way,
    * only one instance per id is available in a single transaction.
    */
   private val durableIDMaps = TxnLocal( IntMap.empty[ DurableIDMapImpl[ _ ]])

   private final class InMemoryIDMapImpl[ A ]( protected val store: InMemoryConfluentMap[ Confluent, Int ])
   extends IdentifierMap[ S#ID, S#Tx, A ] with InMemoryCacheMapImpl[ Confluent, Int ] {
      private val markDirtyFlag = TxnLocal( false )

      def id: S#ID = new ConfluentID( 0, Path.empty )

      private def markDirty()( implicit tx: S#Tx ) {
         if( !markDirtyFlag.swap( true )( tx.peer )) tx.addDirtyMap( this )
      }

      protected def emptyCache : Map[ Int, _ ] = CacheMapImpl.emptyIntMapVal

      def get( id: S#ID )( implicit tx: S#Tx ) : Option[ A ] = {
         getCache[ A ]( id.id, id.path )
      }

      def getOrElse( id: S#ID, default: => A )( implicit tx: S#Tx ) : A = {
         get( id ).getOrElse( default )
      }

      def put( id: S#ID, value: A )( implicit tx: S#Tx ) {
         putCache[ A ]( id.id, id.path, value )
         markDirty()
      }

      def contains( id: S#ID )( implicit tx: S#Tx ) : Boolean = {
         get( id ).isDefined  // XXX TODO more efficient implementation
      }

      def remove( id: S#ID )( implicit tx: S#Tx ) {
println( "WARNING: IDMap.remove : not yet implemented" )
         markDirty()
      }

      def write( out: DataOutput ) {}
      def dispose()( implicit tx: S#Tx ) {}

      override def toString = "IdentifierMap<" + hashCode().toHexString + ">"
   }

   private final class DurableIDMapImpl[ A ]( val id: S#ID, protected val store: DurablePersistentMap[ Confluent, Long ])
                                           ( implicit serializer: Serializer[ S#Tx, S#Acc, A ])
   extends IdentifierMap[ S#ID, S#Tx, A ] with DurableCacheMapImpl[ Confluent, Long ] {
      private val nid = id.id.toLong << 32

      private val markDirtyFlag = TxnLocal( false )

      private def markDirty()( implicit tx: S#Tx ) {
         if( !markDirtyFlag.swap( true )( tx.peer )) tx.addDirtyMap( this )
      }

      protected def emptyCache : Map[ Long, _ ] = CacheMapImpl.emptyLongMapVal

      def get( id: S#ID )( implicit tx: S#Tx ) : Option[ A ] = {
         val key = nid | (id.id.toLong & 0xFFFFFFFFL)
         getCacheTxn[ A ]( key, id.path )
      }

      def getOrElse( id: S#ID, default: => A )( implicit tx: S#Tx ) : A = {
         get( id ).getOrElse( default )
      }

      def put( id: S#ID, value: A )( implicit tx: S#Tx ) {
         val key = nid | (id.id.toLong & 0xFFFFFFFFL)
         putCacheTxn[ A ]( key, id.path, value )
         markDirty()
      }

      def contains( id: S#ID )( implicit tx: S#Tx ) : Boolean = {
         get( id ).isDefined  // XXX TODO more efficient implementation
      }

      def remove( id: S#ID )( implicit tx: S#Tx ) {
println( "WARNING: IDMap.remove : not yet implemented" )
         markDirty()
      }

      def write( out: DataOutput ) {
         out.writeInt( id.id )
      }

      def dispose()( implicit tx: S#Tx ) {}

      override def toString = "IdentifierMap<" + id.id + ">"
   }

//   private sealed trait VarTxLike[ A ] extends BasicVar[ A ] {
//      protected def id: S#ID
//      protected implicit def ser: Serializer[ S#Tx, S#Acc, A ]
//
//      def set( v: A )( implicit tx: S#Tx ) {
////         assertExists()
//         logConfig( this.toString + " set " + v )
//         tx.putTxn( id, v )
//      }
//
//      def get( implicit tx: S#Tx ) : A = {
//         logConfig( this.toString + " get" )
//         tx.getTxn( id )
//      }
//
//      def setInit( v: A )( implicit tx: S#Tx ) {
//         logConfig( this.toString + " ini " + v )
//         tx.putTxn( id, v )
//      }
//
//      override def toString = "Var(" + id + ")"
//   }

   private final class PartialVarTxImpl[ A ]( protected val id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         logPartial( this.toString + " set " + v )
         tx.putPartial( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         logPartial( this.toString + " get" )
         tx.getPartial( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = {
//         logPartial( this.toString + " getFresh" )
////         tx.getFreshPartial( id )
//         tx.getPartial( id )
//      }

      def setInit( v: A )( implicit tx: S#Tx ) {
         logPartial( this.toString + " ini " + v )
         tx.putPartial( id, v )
      }

      override def toString = "PartialVar(" + id + ")"
   }

   private final class VarTxImpl[ A ]( protected val id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfluent( this.toString + " get" )
         tx.getTxn( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def setInit( v: A )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " ini " + v )
         tx.putTxn( id, v )
      }

      override def toString = "Var(" + id + ")"
   }

   private final class RootVar[ A ]( id1: Int, name: String, implicit val ser: Serializer[ S#Tx, S#Acc, A ])
   extends KEntry[ S, A ] {
      def setInit( v: A )( implicit tx: S#Tx ) {
         set( v ) // XXX could add require( tx.inAccess == Path.root )
      }

      override def toString = name // "Root"

      private def id( implicit tx: S#Tx ) : S#ID = new ConfluentID( id1, tx.inputAccess )

      def meld( from: S#Acc )( implicit tx: S#Tx ) : A = {
         logConfluent( this.toString + " meld " + from )
         val idm  = new ConfluentID( id1, from )
         tx.addInputVersion( from )
         tx.getTxn( idm )
      }

      def set( v: A )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfluent( this.toString + " get" )
         tx.getTxn( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      def isFresh( implicit tx: S#Tx ) : Boolean = tx.isFresh( id )

      def write( out: DataOutput ) { sys.error( "Unsupported Operation -- access.write" )}

      def dispose()( implicit tx: S#Tx ) {}
   }

   private final class BooleanVar( protected val id: S#ID )
   extends BasicVar[ Boolean ] with ImmutableSerializer[ Boolean ] {
      def get( implicit tx: S#Tx ): Boolean = {
         logConfluent( this.toString + " get" )
         tx.getNonTxn[ Boolean ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Boolean = get

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
//         assertExists()
         logConfluent( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Boolean](" + id + ")"

      // ---- Serializer ----
      def write( v: Boolean, out: DataOutput ) { out.writeBoolean( v )}
      def read( in: DataInput ) : Boolean = in.readBoolean()
   }

   private final class IntVar( protected val id: S#ID )
   extends BasicVar[ Int ] with ImmutableSerializer[ Int ] {
      def get( implicit tx: S#Tx ) : Int = {
         logConfluent( this.toString + " get" )
         tx.getNonTxn[ Int ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Int = get

      def setInit( v: Int )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         logConfluent( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Int](" + id + ")"

      // ---- Serializer ----
      def write( v: Int, out: DataOutput ) { out.writeInt( v )}
      def read( in: DataInput ) : Int = in.readInt()
   }

   private final class LongVar( protected val id: S#ID )
   extends BasicVar[ Long ] with ImmutableSerializer[ Long ] {
      def get( implicit tx: S#Tx ) : Long = {
         logConfluent( this.toString + " get" )
         tx.getNonTxn[ Long ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Long = get

      def setInit( v: Long )( implicit tx: S#Tx ) {
         logConfluent( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Long )( implicit tx: S#Tx ) {
//         assertExists()
         logConfluent( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Long](" + id + ")"

      // ---- Serializer ----
      def write( v: Long, out: DataOutput ) { out.writeLong( v )}
      def read( in: DataInput ) : Long = in.readLong()
   }

   private object GlobalState {
      private val SER_VERSION = 0

      implicit object PathSerializer extends Serializer[ Durable#Tx, Durable#Acc, Path ] {
         def write( v: Path, out: DataOutput ) {
            v.write( out )
         }

         def read( in: DataInput, acc: Durable#Acc )( implicit tx: Durable#Tx ) : Path = {
            implicit val m = PathMeasure
            val sz         = in.readInt()
            var tree       = FingerTree.empty( m )
            var i = 0; while( i < sz ) {
               tree :+= in.readLong()
            i += 1 }
            new Path( tree )
         }
      }

      implicit object Serializer extends Serializer[ Durable#Tx, Durable#Acc, GlobalState ] {
         def write( v: GlobalState, out: DataOutput ) {
            import v._
            out.writeUnsignedByte( SER_VERSION )
            idCnt.write( out )
            reactCnt.write( out )
            versionLinear.write( out )
            versionRandom.write( out )
            lastAccess.write( out )
            partialTree.write( out )
         }

         def read( in: DataInput, acc: Durable#Acc )( implicit tx: Durable#Tx ) : GlobalState = {
            val serVer        = in.readUnsignedByte()
            require( serVer == SER_VERSION, "Incompatible serialized version. Found " + serVer + " but require " + SER_VERSION )
            val idCnt         = tx.readCachedIntVar( in )
            val reactCnt      = tx.readCachedIntVar( in )
            val versionLinear = tx.readCachedIntVar( in )
            val versionRandom = tx.readCachedLongVar( in )
            val lastAccess    = tx.readCachedVar[ Path ]( in )
            val partialTree   = Ancestor.readTree[ Durable, Long ]( in, acc )( tx, ImmutableSerializer.Long, _.toInt )
            GlobalState( idCnt, reactCnt, versionLinear, versionRandom, lastAccess, partialTree )
         }
      }
   }
   private final case class GlobalState(
      idCnt: Durable#Var[ Int ], reactCnt: Durable#Var[ Int ],
      versionLinear: Durable#Var[ Int ], versionRandom: Durable#Var[ Long ],
      lastAccess: Durable#Var[ Path ],
      partialTree: Ancestor.Tree[ Durable, Long ]
   )

   private final class System( storeFactory: DataStoreFactory[ DataStore ])
   extends Confluent {
//      val manifest                        = Predef.manifest[ Confluent ]
//      def idOrdering : Ordering[ S#ID ]   = IDOrdering

      private[confluent] val store        = storeFactory.open( "data" )
      private[confluent] val durable      = Durable( store ) : Durable
      private[confluent] val varMap       = DurablePersistentMap.newConfluentIntMap[ S ]( store )
      private[confluent] val partialMap : PartialCacheMapImpl[ Confluent, Int ] =
         PartialCacheMapImpl.newIntCache( DurablePersistentMap.newPartialMap( store ))

//      private val refreshMap = InMemoryConfluentMap.newIntMap[ Confluent ]

      private val global = durable.step { implicit tx =>
         val root = durable.root { implicit tx =>
            val idCnt         = tx.newCachedIntVar( 0 )
            val reactCnt      = tx.newCachedIntVar( 0 )
            val versionLinear = tx.newCachedIntVar( 0 )
            val versionRandom = tx.newCachedLongVar( TxnRandom.initialScramble( 0L )) // scramble !!!
            val lastAccess    = tx.newCachedVar[ Path ]( Path.root )( GlobalState.PathSerializer )
            val partialTree   = Ancestor.newTree[ Durable, Long ]( 1L << 32 )( tx, Serializer.Long, _.toInt )
            GlobalState( idCnt, reactCnt, versionLinear, versionRandom, lastAccess, partialTree )
         }
         root.get
      }

//      private val inMem : InMemory = InMemory()
      private val versionRandom  = TxnRandom.wrap( global.versionRandom )
//      private val versionLinear  = ScalaRef( 0 )
//      private val lastAccess     = ScalaRef( Path.root ) // XXX TODO dirty dirty

//      private val idCntVar : ScalaRef[ Int ] = ScalaRef {
//         inMem.step { implicit tx =>
//            store.get[ Int ]( _.writeInt( 0 ))( _.readInt() ).getOrElse( 1 ) // 0 is the idCnt var itself !
//         }
//      }

      private[confluent] /* lazy */ val reactionMap : ReactionMap[ S ] =
         ReactionMap[ S, Durable ]( global.reactCnt )( _.durable )

      override def toString = "Confluent"

      private[confluent] def partialTree : Ancestor.Tree[ Durable, Long ] = global.partialTree

      private[confluent] def newVersionID( implicit tx: S#Tx ) : Long = {
         implicit val dtx = tx.durable
         val lin  = global.versionLinear.get + 1
         global.versionLinear.set( lin )
         var rnd = 0
         do {
            rnd = versionRandom.nextInt()
         } while( rnd == 0 )
         (rnd.toLong << 32) | (lin.toLong & 0xFFFFFFFFL)
      }

      private[confluent] def newIDValue()( implicit tx: S#Tx ) : Int = {
         implicit val dtx = tx.durable
         val res = global.idCnt.get + 1
//         logConfig( "new   <" + id + ">" )
         global.idCnt.set( res )
         res
      }

      def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic { implicit itx =>
            implicit val dtx = durable.wrap( itx )
            val last = global.lastAccess.get
            logConfluent( "::::::: atomic - input access = " + last + " :::::::" )
            fun( new RegularTxn( this, dtx, itx, last ))
         }
      }

      // XXX TODO
      /* private[Confluent] */ def position_=( p: Path )( implicit tx: S#Tx ) {
         global.lastAccess.set( p )( tx.durable )
      }

      def position( implicit tx: S#Tx ) : Path = global.lastAccess.get( tx.durable )

      def root[ A ]( init: S#Tx => A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         require( ScalaTxn.findCurrent.isEmpty, "root must be called outside of a transaction" )
         logConfluent( "::::::: root :::::::" )
         TxnExecutor.defaultAtomic { itx =>
            implicit val tx = new RootTxn( this, itx )
            val rootVar    = new RootVar[ A ]( 0, "Root", serializer )
            val rootPath   = tx.inputAccess
            if( varMap.get[ Array[ Byte ]]( 0, rootPath )( tx, ByteArraySerializer ).isEmpty ) {
               rootVar.setInit( init( tx ))
               tx.newIndexTree( rootPath.term, 0 )
               tx.writePartialTreeVertex( partialTree.root )
            }
            rootVar
         }
      }

      def close() { store.close()}

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )
   }
}
sealed trait Confluent extends KSys[ Confluent ] with Cursor[ Confluent ] {
   final type ID                    = Confluent.ID
   final type Tx                    = Confluent.Txn
   final type Acc                   = Confluent.Path
   final type Var[ @specialized A ] = Confluent.Var[ A ] // STMVar[ Tx, A ]
   final type Entry[ A ]            = KEntry[ Confluent, A ]

   private[confluent] def store : DataStore
   private[confluent] def durable : Durable
   private[confluent] def varMap : DurablePersistentMap[ Confluent, Int ]
   private[confluent] def partialMap : PartialCacheMapImpl[ Confluent, Int ]
   private[confluent] def partialTree: Ancestor.Tree[ Durable, Long ]
   private[confluent] def newIDValue()( implicit tx: Tx ) : Int
   private[confluent] def newVersionID( implicit tx: Tx ) : Long
   private[confluent] def reactionMap : ReactionMap[ Confluent ]

   private[confluent] def position_=( newPos: Acc )( implicit tx: Tx ) : Unit
}