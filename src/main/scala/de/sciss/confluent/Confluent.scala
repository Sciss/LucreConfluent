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

import util.MurmurHash
import de.sciss.lucre.event.ReactionMap
import de.sciss.lucre.{DataOutput, DataInput}
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike}
import de.sciss.collection.txn.Ancestor
import collection.immutable.{IntMap, LongMap}
import concurrent.stm.{TxnLocal, TxnExecutor, InTxn, Txn => ScalaTxn}
import TemporalObjects.logConfig
import de.sciss.lucre.stm.impl.BerkeleyDB
import java.io.File
import de.sciss.lucre.stm.{IdentifierMap, Cursor, Disposable, Var => STMVar, Serializer, Durable, PersistentStoreFactory, PersistentStore, Writer, TxnSerializer}

object Confluent {
   private type S = Confluent

   def apply( storeFactory: PersistentStoreFactory[ PersistentStore ]) : Confluent = new System( storeFactory )

   def tmp() : Confluent = {
      val dir = File.createTempFile( "confluent_", "db" )
      dir.delete()
//      dir.mkdir()
      new System( BerkeleyDB.factory( dir ))
   }

   final class ID private[Confluent]( val id: Int, val path: Path ) extends KSys.ID[ S#Tx, Path ] {
//      final def shortString : String = access.mkString( "<", ",", ">" )

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
         that.isInstanceOf[ KSys.ID[ _, _ ]] && {
            val b = that.asInstanceOf[ KSys.ID[ _, _ ]]
            id == b.id && path == b.path
         }

      def write( out: DataOutput ) {
         out.writeInt( id )
         path.write( out )
      }

      override def toString = "<"  + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: S#Tx ) {}
   }

   private object IDOrdering extends Ordering[ S#ID ] {
      def compare( a: S#ID, b: S#ID ) : Int = {
         val aid = a.id
         val bid = b.id
         if( aid < bid ) -1 else if( aid > bid ) 1 else {
            val ahash   = a.path.sum
            val bhash   = b.path.sum
            if( ahash < bhash ) -1 else if( ahash > bhash ) 1 else 0
         }
      }
   }

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

      /* private[confluent] */ def dropAndReplaceHead( dropLen: Int, newHead: Long ) : Path = {
         val (_, _, right) = tree.split1( _._1 > dropLen )
         wrap( newHead +: right )
      }

      private[Confluent] def addTerm( term: Long )( implicit tx: S#Tx ) : Path = {
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

      def take( n: Int ) : PathLike = wrap( tree.split( _._1 > n )._1 ) // XXX future optimization in finger tree

      protected def wrap( _tree: FingerTree[ (Int, Long), Long ]) : Path = new Path( _tree )

      def mkString( prefix: String, sep: String, suffix: String ) : String =
         tree.iterator.map( _.toInt ).mkString( prefix, sep, suffix )
   }

   sealed trait IndexTree extends Writer with Disposable[ Durable#Tx ] {
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

   /**
    * Instances of `CacheEntry` are stored for each variable write in a transaction. They
    * are flushed at the commit to the persistent store. There are two sub types, a
    * transactional and a non-transactional one. A non-transactional cache entry can deserialize
    * the value without transactional context, e.g. this is true for all primitive types.
    * A transactional entry is backed by a `TxnSerializer`. To be saved in the store which uses
    * a sub system (`Durable`), serialization is a two-step process, using an intermediate
    * binary representation.
    */
   private sealed trait CacheEntry {
      def id: S#ID
      def flush( outTerm: Long, store: PersistentMap[ S, Int ])( implicit tx: S#Tx ) : Unit
      def value: Any
   }
   private final class NonTxnCacheEntry[ A ]( val id: S#ID, val value: A )( implicit serializer: Serializer[ A ])
   extends CacheEntry {
      override def toString = "NonTxnCacheEntry(" + id + ", " + value + ")"

      def flush( outTerm: Long, store: PersistentMap[ S, Int ])( implicit tx: S#Tx ) {
         val pathOut = id.path.addTerm( outTerm )
         logConfig( "txn flush write " + value + " for " + pathOut.mkString( "<" + id.id + " @ ", ",", ">" ))
         store.put( id.id, pathOut, value )
      }
   }
   private final class TxnCacheEntry[ A ]( val id: S#ID, val value: A )
                                         ( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ])
   extends CacheEntry {
      override def toString = "NonTxnCacheEntry(" + id + ", " + value + ")"

      def flush( outTerm: Long, store: PersistentMap[ S, Int ])( implicit tx: S#Tx ) {
         val pathOut = id.path.addTerm( outTerm )
         logConfig( "txn flush write " + value + " for " + pathOut.mkString( "<" + id.id + " @ ", ",", ">" ))
         val out     = new DataOutput()
         serializer.write( value, out )
         val arr     = out.toByteArray
         store.put( id.id, pathOut, arr )( tx, ByteArraySerializer )
      }
   }

   private val emptyLongMapVal      = LongMap.empty[ Any ]
   private def emptyLongMap[ T ]    = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]
   private val emptyIntMapVal       = IntMap.empty[ Any ]
   private def emptyIntMap[ T ]     = emptyIntMapVal.asInstanceOf[ IntMap[ T ]]

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
      private val cache = TxnLocal( emptyIntMap[ LongMap[ CacheEntry ]])
//private val writes = TxnLocal( IIdxSeq.empty[ CacheEntry ])
      private val markDirty = TxnLocal( init = {
//         logConfig( Console.CYAN + "txn dirty" + Console.RESET )
         logConfig( "....... txn dirty ......." )
         ScalaTxn.beforeCommit( _ => flush() )( peer )
         ()
      })
      private val meld  = TxnLocal( emptyMeldInfo )
//      @volatile var inFlush = false

      protected def flushNewTree( level: Int ) : Long
      protected def flushOldTree() : Long

      private def flush() {
         val meldInfo      = meld.get( peer )
         val newTree       = meldInfo.requiresNewTree
//         logConfig( Console.RED + "txn flush - term = " + outTerm.toInt + Console.RESET )
         val persistent    = system.varMap
         val outTerm = if( newTree ) {
            flushNewTree( meldInfo.outputLevel )
         } else {
            flushOldTree()
         }
         logConfig( "::::::: txn flush - " + (if( newTree ) "meld " else "") + "term = " + outTerm.toInt + " :::::::" )
         system.position_=( inputAccess.addTerm( outTerm )( this ))( this ) // XXX TODO last path would depend on value written to inputAccess?
         cache.get( peer ).foreach { tup1 =>
            val map  = tup1._2
            map.foreach { tup2 =>
               val e    = tup2._2
               e.flush( outTerm, persistent )( this )
            }
         }
//         writes.get( peer ).foreach { e =>
//            e.flush( outTerm, persistent )( this )
//         }
      }

      private[Confluent] implicit def durable: Durable#Tx

//      final private[Confluent] implicit lazy val durable: Durable#Tx = {
//         logConfig( "txn durable" )
//         system.durable.wrap( peer )
//      }

      final def newID() : S#ID = {
         val res = new ID( system.newIDValue()( this ), Path.empty )
         logConfig( "txn newID " + res )
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

      final private[Confluent] def getNonTxn[ A ]( id: S#ID )( implicit ser: Serializer[ A ]) : A = {
         logConfig( "txn get " + id )
         val id1  = id.id
         val path = id.path
         cache.get( peer ).get( id1 ).flatMap( _.get( path.sum ).map( _.value )).asInstanceOf[ Option[ A ]].orElse(
            system.varMap.get[ A ]( id1, path )( this, ser )
         ).getOrElse(
            sys.error( "No value for " + id )
         )
      }

      final private[Confluent] def getTxn[ A ]( id: S#ID )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : A = {
         logConfig( "txn get' " + id )
         val id1  = id.id
         val path = id.path
         cache.get( peer ).get( id1 ).flatMap( _.get( path.sum ).map { e =>
            e.value.asInstanceOf[ A ]
         }).orElse({
            system.varMap.getWithSuffix[ Array[ Byte ]]( id1, path )( this, ByteArraySerializer ).map { tup =>
               val access  = tup._1
               val arr     = tup._2
               val in      = new DataInput( arr )
               ser.read( in, access )( this )
            }
         }).getOrElse( sys.error( "No value for " + id ))
      }

      final private[Confluent] def putTxn[ A ]( id: S#ID, value: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
//         logConfig( "txn put " + id )
         val e = new TxnCacheEntry( id, value )
         cache.transform( mapMap => {
            val id1     = id.id
            val path    = id.path
            val mapOld  = mapMap.getOrElse( id1, emptyLongMap[ CacheEntry ])
            val mapNew  = mapOld + (path.sum -> e)
            mapMap + ((id1, mapNew))
         })( peer )

//writes.transform( _ :+ e )( peer )

         markDirty()( peer )
      }

      final private[Confluent] def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ A ]) {
//         logConfig( "txn put " + id )
         val e = new NonTxnCacheEntry( id, value )
         cache.transform( mapMap => {
            val id1     = id.id
            val path    = id.path
            val mapOld  = mapMap.getOrElse( id1, emptyLongMap[ CacheEntry ])
            val mapNew  = mapOld + (path.sum -> e)
            mapMap + ((id1, mapNew))
         })( peer )

//writes.transform( _ :+ e )( peer )

         markDirty()( peer )
      }

      final private[Confluent] def dispose( id: S#ID ) {
         cache.transform( _ - id.id )( peer )
      }

//      def indexTree( version: Int ) : Ancestor.Tree[ S, Int ] = system.indexTree( version )( this )

      final private[Confluent] def readIndexTree( term: Long ) : IndexTree = {
         val st = system.store
         st.get { out =>
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         } { in =>
            val tree    = Ancestor.readTree[ Durable, Long ]( in, () )( durable, TxnSerializer.Long, _.toInt )
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

      final def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                                 ( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ] = {
         val term = index.term
         val tree = readIndexTree( term )
         val map  = Ancestor.readMap[ Durable, Long, A ]( in, (), tree.tree )
         new IndexMapImpl[ A ]( index, map )
      }

      final private[Confluent] def newIndexTree( term: Long, level: Int ) : IndexTree = {
         logConfig( "txn new tree " + term.toInt )
         val tree = Ancestor.newTree[ Durable, Long ]( term )( durable, TxnSerializer.Long, _.toInt )
         val res  = new IndexTreeImpl( tree, level )
         system.store.put( out => {
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         })( res.write )
         writeTreeVertex( res, tree.root )
         res
      }

      final def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )
                                ( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ] = {
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

      @inline private def alloc( pid: S#ID ) : S#ID = new ID( system.newIDValue()( this ), pid.path )

      final def newVar[ A ]( pid: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( alloc( pid ))
         logConfig( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newBooleanVar( pid: S#ID, init: Boolean ) : S#Var[ Boolean ] = {
         val id   = alloc( pid )
         val res  = new BooleanVar( id )
         logConfig( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newIntVar( pid: S#ID, init: Int ) : S#Var[ Int ] = {
         val id   = alloc( pid )
         val res  = new IntVar( id )
         logConfig( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newLongVar( pid: S#ID, init: Long ) : S#Var[ Long ] = {
         val id   = alloc( pid )
         val res  = new LongVar( id )
         logConfig( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = new Array[ S#Var[ A ]]( size )

      final def newIDMap[ A ]( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#Tx, S#ID, A ] = {
         val id   = system.newIDValue()( this )
         val map  = PersistentMap.newLongMap[ Confluent ]( system.store )
         new IDMapImpl[ A ]( id, map )
      }

      private def readSource( in: DataInput, pid: S#ID ) : S#ID = {
         val id = in.readInt()
         new ID( id, pid.path )
      }

      final def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A = {
         val schizo = new ID( id.id, parent.path )
         getTxn[ A ]( schizo )
      }

      final def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )
                               ( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) {
//         val out = new DataOutput()
//         writer.write( value, out )
////         val bytes = out.toByteArray
////         system.storage += id.id -> (system.storage.getOrElse( id.id,
////            Map.empty[ S#Acc, Array[ Byte ]]) + (parent.path -> bytes))
//         sys.error( "TODO" )
         val schizo = new ID( id.id, parent.path )
         putTxn[ A ]( schizo, value )
      }

      final def readVal[ A ]( id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A =
         getTxn[ A ]( id )

      final def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) {
         putTxn[ A ]( id, value )
      }

      final private[Confluent] def makeVar[ A ]( id: S#ID )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : BasicVar[ A ] = {
         ser match {
            case plain: Serializer[ _ ] =>
               new VarImpl[ A ]( id, plain.asInstanceOf[ Serializer[ A ]])
            case _ =>
               new VarTxImpl[ A ]( id, ser )
         }
      }

      final def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( readSource( in, pid ))
         logConfig( "txn read " + res )
         res
      }

      final def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] = {
         val res = new BooleanVar( readSource( in, pid ))
         logConfig( "txn read " + res )
         res
      }

      final def readIntVar( pid: S#ID, in: DataInput ) : S#Var[ Int ] = {
         val res = new IntVar( readSource( in, pid ))
         logConfig( "txn read " + res )
         res
      }

      final def readLongVar( pid: S#ID, in: DataInput ) : S#Var[ Long ] = {
         val res = new LongVar( readSource( in, pid ))
         logConfig( "txn read " + res )
         res
      }

      final def readID( in: DataInput, acc: S#Acc ) : S#ID = {
         val res = new ID( in.readInt(), Path.readAndAppend( in, acc )( this ))
         logConfig( "txn readID " + res )
         res
      }

      final def access[ A ]( source: S#Var[ A ]) : A = {
         sys.error( "TODO" )  // source.access( system.path( this ))( this )
      }
   }

   private final class TxnImpl( val system: S, private[Confluent] val durable: Durable#Tx,
                                val peer: InTxn, val inputAccess: Path )
   extends Txn {

      override def toString = "Txn" + inputAccess

      protected def flushOldTree() : Long = {
         val childTerm  = system.newVersionID( this )
         val (index, parentTerm) = inputAccess.splitIndex
         val tree       = readIndexTree( index.term )
         val parent     = readTreeVertex( tree.tree, index, parentTerm )._1
         val child      = tree.tree.insertChild( parent, childTerm )( durable )
         writeTreeVertex( tree, child )
         childTerm
      }
      protected def flushNewTree( level: Int ) : Long = {
         val term = system.newVersionID( this )
         newIndexTree( term, level )
         term
      }
   }

   private final class RootTxn( val system: S, val peer: InTxn ) extends Txn {
      val inputAccess = Path.root
      override def toString = "RootTxn"

      private[Confluent] implicit lazy val durable: Durable#Tx = {
         logConfig( "txn durable" )
         system.durable.wrap( peer )
      }

      protected def flushOldTree() : Long = inputAccess.term
      protected def flushNewTree( level: Int ) : Long = sys.error( "Cannot meld in the root version" )
   }

   private sealed trait BasicVar[ A ] extends STMVar[ S#Tx, A ] {
      protected def id: S#ID

//      @elidable(CONFIG) protected final def assertExists()(implicit tx: Txn) {
//         require(tx.system.exists(id), "trying to write disposed ref " + id)
//      }

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
//         id.write( out )
      }

      final def dispose()( implicit tx: S#Tx ) {
         tx.dispose( id )
         id.dispose()
      }

      def setInit( v: A )( implicit tx: S#Tx ) : Unit
      final def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}
   }

   private final class VarImpl[ A ]( protected val id: S#ID, protected val ser: Serializer[ A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         logConfig( this.toString + " set " + v )
         tx.putNonTxn( id, v )( ser )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfig( this.toString + " get" )
         tx.getNonTxn[ A ]( id )( ser )
      }

      def setInit( v: A )( implicit tx: S#Tx ) {
         logConfig( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( ser )
      }

      override def toString = "Var(" + id + ")"
   }

   private object ByteArraySerializer extends Serializer[ Array[ Byte ]] {
      def write( v: Array[ Byte ], out: DataOutput ) {
         out.writeInt( v.length )
         out.write( v )
      }

      def read( in: DataInput ) : Array[ Byte ] = {
         val sz   = in.readInt()
         val v    = new Array[ Byte ]( sz )
         in.read( v )
         v
      }
   }

   private final class IDMapImpl[ A ]( mapID: Int, map: PersistentMap[ Confluent, Long ])
                                     ( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ])
   extends IdentifierMap[ S#Tx, S#ID, A ] {
      private val nid = mapID.toLong << 32

      def get( id: S#ID )( implicit tx: S#Tx ) : Option[ A ] = {
         val key = nid | (id.id.toLong & 0xFFFFFFFFL)
         map.getWithSuffix( key, id.path )( tx, ByteArraySerializer ).map { tup =>
            val access  = tup._1
            val arr     = tup._2
            val in      = new DataInput( arr )
            serializer.read( in, access )
         }
      }

      def getOrElse( id: S#ID, default: => A )( implicit tx: S#Tx ) : A = {
         get( id ).getOrElse( default )
      }

      def put( id: S#ID, value: A )( implicit tx: S#Tx ) {
         val key  = nid | (id.id.toLong & 0xFFFFFFFFL)
         val out  = new DataOutput()
         serializer.write( value, out )
         val arr  = out.toByteArray
         // XXX TODO -- id.path might be empty or change its term at the end of the commit?
         map.put( key, id.path, arr )( tx, ByteArraySerializer )
      }
      def contains( id: S#ID )( implicit tx: S#Tx ) : Boolean = {
         get( id ).isDefined  // XXX TODO more efficient implementation
      }
      def remove( id: S#ID )( implicit tx: S#Tx ) {
println( "WARNING: IDMap.remove : not yet implemented" )
      }

      override def toString = "IdentifierMap<" + mapID + ">"
   }

   private sealed trait VarTxLike[ A ] extends BasicVar[ A ] {
      protected def id: S#ID
      protected implicit def ser: TxnSerializer[ S#Tx, S#Acc, A ]

      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         logConfig( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfig( this.toString + " get" )
         tx.getTxn( id )
      }

      def setInit( v: A )( implicit tx: S#Tx ) {
         logConfig( this.toString + " ini " + v )
         tx.putTxn( id, v )
      }

      override def toString = "Var(" + id + ")"
   }

   private final class VarTxImpl[ A ]( protected val id: S#ID, protected val ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends VarTxLike[ A ]

   private final class RootVar[ A ]( id1: Int, implicit val ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends KEntry[ S, A ] {
      def setInit( v: A )( implicit tx: S#Tx ) {
         set( v ) // XXX could add require( tx.inAccess == Path.root )
      }

      override def toString = "Root"

      private def id( implicit tx: S#Tx ) : S#ID = new ID( id1, tx.inputAccess )

      def meld( from: S#Acc )( implicit tx: S#Tx ) : A = {
         logConfig( this.toString + " meld " + from )
         val idm  = new ID( id1, from )
         tx.addInputVersion( from )
         tx.getTxn( idm )
      }

      def set( v: A )( implicit tx: S#Tx ) {
         logConfig( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         logConfig( this.toString + " get" )
         tx.getTxn( id )
      }

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      def write( out: DataOutput ) { sys.error( "Unsupported Operation -- access.write" )}

      def dispose()( implicit tx: S#Tx ) {}
   }

   private final class BooleanVar( protected val id: S#ID )
   extends BasicVar[ Boolean ] with Serializer[ Boolean ] {
      def get( implicit tx: S#Tx ): Boolean = {
         logConfig( this.toString + " get" )
         tx.getNonTxn[ Boolean ]( id )( this )
      }

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         logConfig( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
//         assertExists()
         logConfig( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Boolean](" + id + ")"

      // ---- TxnSerializer ----
      def write( v: Boolean, out: DataOutput ) { out.writeBoolean( v )}
      def read( in: DataInput ) : Boolean = in.readBoolean()
   }

   private final class IntVar( protected val id: S#ID )
   extends BasicVar[ Int ] with Serializer[ Int ] {
      def get( implicit tx: S#Tx ) : Int = {
         logConfig( this.toString + " get" )
         tx.getNonTxn[ Int ]( id )( this )
      }

      def setInit( v: Int )( implicit tx: S#Tx ) {
         logConfig( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         logConfig( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Int](" + id + ")"

      // ---- TxnSerializer ----
      def write( v: Int, out: DataOutput ) { out.writeInt( v )}
      def read( in: DataInput ) : Int = in.readInt()
   }

//   private final class CachedIntVar( protected val id: Int, peer: ScalaRef[ Int ])
//   extends Var[ Int ] with BasicSource {
//      def get( implicit tx: S#Tx ) : Int = peer.get( tx.peer )
//
//      def setInit( v: Int )( implicit tx: S#Tx ) { set( v )}
//
//      def set( v: Int )( implicit tx: S#Tx ) {
//         peer.set( v )( tx.peer )
////         tx.system.write( id )( _.writeInt( v ))
//      }
//
//      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}
//
//      override def toString = "Var[Int](" + id + ")"
//   }

   private final class LongVar( protected val id: S#ID )
   extends BasicVar[ Long ] with Serializer[ Long ] {
      def get( implicit tx: S#Tx ) : Long = {
         logConfig( this.toString + " get" )
         tx.getNonTxn[ Long ]( id )( this )
      }

      def setInit( v: Long )( implicit tx: S#Tx ) {
         logConfig( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Long )( implicit tx: S#Tx ) {
//         assertExists()
         logConfig( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Long](" + id + ")"

      // ---- TxnSerializer ----
      def write( v: Long, out: DataOutput ) { out.writeLong( v )}
      def read( in: DataInput ) : Long = in.readLong()
   }

   private object GlobalState {
      private val SER_VERSION = 0

      implicit object PathSerializer extends TxnSerializer[ Durable#Tx, Durable#Acc, Path ] {
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

      implicit object Serializer extends TxnSerializer[ Durable#Tx, Durable#Acc, GlobalState ] {
         def write( v: GlobalState, out: DataOutput ) {
            import v._
            out.writeUnsignedByte( SER_VERSION )
            idCnt.write( out )
            reactCnt.write( out )
            versionLinear.write( out )
            versionRandom.write( out )
            lastAccess.write( out )
         }

         def read( in: DataInput, acc: Durable#Acc )( implicit tx: Durable#Tx ) : GlobalState = {
            val serVer        = in.readUnsignedByte()
            require( serVer == SER_VERSION, "Incompatible serialized version. Found " + serVer + " but require " + SER_VERSION )
            val idCnt         = tx.readCachedIntVar( in )
            val reactCnt      = tx.readCachedIntVar( in )
            val versionLinear = tx.readCachedIntVar( in )
            val versionRandom = tx.readCachedLongVar( in )
            val lastAccess    = tx.readCachedVar[ Path ]( in )
            GlobalState( idCnt, reactCnt, versionLinear, versionRandom, lastAccess )
         }
      }
   }
   private final case class GlobalState(
      idCnt: Durable#Var[ Int ], reactCnt: Durable#Var[ Int ],
      versionLinear: Durable#Var[ Int ], versionRandom: Durable#Var[ Long ],
      lastAccess: Durable#Var[ Path ]
   )

   private final class System( storeFactory: PersistentStoreFactory[ PersistentStore ])
   extends Confluent {
      val manifest                        = Predef.manifest[ Confluent ]
      def idOrdering : Ordering[ S#ID ]   = IDOrdering

      private[confluent] val store        = storeFactory.open( "data" )
      private[confluent] val durable      = Durable( store ) : Durable
      private[confluent] val varMap   = PersistentMap.newIntMap[ S ]( store )

      private val global = durable.step { implicit tx =>
         val root = durable.root { implicit tx =>
            val idCnt         = tx.newCachedIntVar( 0 )
            val reactCnt      = tx.newCachedIntVar( 0 )
            val versionLinear = tx.newCachedIntVar( 0 )
            val versionRandom = tx.newCachedLongVar( 0L )
            val lastAccess    = tx.newCachedVar[ Path ]( Path.root )( GlobalState.PathSerializer )
            GlobalState( idCnt, reactCnt, versionLinear, versionRandom, lastAccess )
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

      private[confluent] def newVersionID( implicit tx: S#Tx ) : Long = {
         implicit val dtx = tx.durable
         val lin  = global.versionLinear.get + 1
         global.versionLinear.set( lin )
         (versionRandom.nextInt().toLong << 32) | (lin.toLong & 0xFFFFFFFFL)
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
            logConfig( "::::::: atomic - input access = " + last + " :::::::" )
            fun( new TxnImpl( this, dtx, itx, last ))
         }
      }

      // XXX TODO
      /* private[Confluent] */ def position_=( p: Path )( implicit tx: S#Tx ) {
         global.lastAccess.set( p )( tx.durable )
      }

      def position( implicit tx: S#Tx ) : Path = global.lastAccess.get( tx.durable )

      def root[ A ]( init: S#Tx => A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         require( ScalaTxn.findCurrent.isEmpty, "root must be called outside of a transaction" )
         logConfig( "::::::: root :::::::" )
         TxnExecutor.defaultAtomic { itx =>
            implicit val tx = new RootTxn( this, itx )
            val rootVar    = new RootVar[ A ]( 0, serializer )
            val rootPath   = tx.inputAccess
            if( varMap.get[ Array[ Byte ]]( 0, rootPath )( tx, ByteArraySerializer ).isEmpty ) {
               rootVar.setInit( init( tx ))
               tx.newIndexTree( rootPath.term, 0 )
            }
            rootVar
         }
      }

      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = sys.error( "TODO" )

      def close() { store.close()}

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )
   }
}
sealed trait Confluent extends KSys[ Confluent ] with Cursor[ Confluent ] {
   final type ID                    = Confluent.ID
   final type Tx                    = Confluent.Txn
   final type Acc                   = Confluent.Path
   final type Var[ @specialized A ] = STMVar[ Tx, A ]
   final type Entry[ A ]            = KEntry[ Confluent, A ]

   private[confluent] def store : PersistentStore
   private[confluent] def durable : Durable
   private[confluent] def varMap : PersistentMap[ Confluent, Int ]
   private[confluent] def newIDValue()( implicit tx: Tx ) : Int
   private[confluent] def newVersionID( implicit tx: Tx ) : Long
   private[confluent] def reactionMap : ReactionMap[ Confluent ]
}