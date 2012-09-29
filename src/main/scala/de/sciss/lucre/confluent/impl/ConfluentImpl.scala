/*
 *  ConfluentImpl.scala
 *  (LucreConfluent)
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

package de.sciss.lucre
package confluent
package impl

import stm.{InMemory, DataStore, DataStoreFactory, Durable, Serializer, IdentifierMap, ImmutableSerializer}
import concurrent.stm.{InTxn, TxnExecutor, TxnLocal, Txn => ScalaTxn}
import collection.immutable.{IndexedSeq => IIdxSeq, LongMap, IntMap}
import de.sciss.fingertree
import fingertree.{FingerTreeLike, FingerTree}
import data.Ancestor
import util.MurmurHash

object ConfluentImpl {
   def apply( storeFactory: DataStoreFactory[ DataStore ]) : Confluent = {
      // tricky: before `durable` was a `val` in `System`, this caused
      // a NPE with `Mixin` initialising `global`.
      // (http://stackoverflow.com/questions/12647326/avoiding-npe-in-trait-initialization-without-using-lazy-vals)
      val durable = Durable( storeFactory )
      new System( storeFactory, durable )
   }

   // --------------------------------------------
   // ---------------- BEGIN Path ----------------
   // --------------------------------------------

   private object PathMeasure extends fingertree.Measure[ Long, (Int, Long) ] {
      override def toString = "PathMeasure"
      val zero = (0, 0L)
      def apply( c: Long ) = (1, c >> 32)
      def |+|( a: (Int, Long), b: (Int, Long) ) = ((a._1 + b._1), (a._2 + b._2))
      def |+|( a: (Int, Long), b: (Int, Long), c: (Int, Long) ) = ((a._1 + b._1 + c._1), (a._2 + b._2 + c._2))
   }

   private object Path {
      implicit def serializer[ S <: Sys[ S ], D <: stm.DurableLike[ D ]] : stm.Serializer[ D#Tx, D#Acc, S#Acc ] =
         new Ser[ S, D ]

      private final class Ser[ S <: Sys[ S ], D <: stm.DurableLike[ D ]] extends stm.Serializer[ D#Tx, D#Acc, S#Acc ] {
         def write( v: S#Acc, out: DataOutput ) {
            v.write( out )
         }

         def read( in: DataInput, acc: D#Acc )( implicit tx: D#Tx ) : S#Acc = {
            implicit val m = PathMeasure
            val sz         = in.readInt()
            var tree       = FingerTree.empty( m )
            var i = 0; while( i < sz ) {
               tree :+= in.readLong()
            i += 1 }
            new Path[ S ]( tree )
         }
      }

      def test_empty[ S <: Sys[ S ]] : S#Acc = empty

      private val anyEmpty = new Path[ Confluent ]( FingerTree.empty( PathMeasure ))

      def empty[ S <: Sys[ S ]]: S#Acc = anyEmpty.asInstanceOf[ Path[ S ]]

      /* private[Confluent] */ def root[ S <: Sys[ S ]]: S#Acc = new Path[ S ]( FingerTree( 1L << 32, 1L << 32 )( PathMeasure ))
//      private[Confluent] def apply( tree: Long, term: Long ) = new Path( FingerTree( tree, term )( PathMeasure ))

      def read[ S <: Sys[ S ]]( in: DataInput ) : S#Acc  = {
         implicit val m = PathMeasure
         val sz         = in.readInt()
         var tree       = FingerTree.empty( m )
         var i = 0; while( i < sz ) {
            tree      :+= in.readLong()
         i += 1 }
         new Path[ S ]( tree )
      }

      def readAndAppend[ S <: Sys[ S ]]( in: DataInput, acc: S#Acc )( implicit tx: S#Tx ) : S#Acc = {
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
         new Path[ S ]( tree )
      }
   }

   /**
    * The finger tree has elements of type `Long` where the upper 32 bits are the randomized version,
    * and the lower 32 bits are the incremental version. The measure is taking the index and running sum
    * of the tree.
    */
   private final class Path[ S <: Sys[ S ]]( val tree: FingerTree[ (Int, Long), Long ])
   extends Sys.Acc[ S ] with FingerTreeLike[ (Int, Long), Long, Path[ S ]] {
      implicit protected def m: fingertree.Measure[ Long, (Int, Long) ] = PathMeasure

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

//      def test_:+( elem: Long ) : Path = :+( elem )

      def :+( last: Long ) : S#Acc = wrap( tree :+ last )

      def +:( head: Long ) : S#Acc = wrap( head +: tree )

      def apply( idx: Int ) : Long = tree.find1( _._1 > idx )

      // XXX TODO testin one two
      def partial : S#Acc = {
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

      def maxPrefixLength( that: Long ) : Int = {
         // XXX TODO more efficient
         val sz = size
         var i = 0; while( i < sz ) {
            if( apply( i ) == that ) return i + 1
         i += 2 }
         0
      }

      def maxPrefixLength( that: S#Acc ) : Int = {
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

      def dropAndReplaceHead( dropLen: Int, newHead: Long ) : S#Acc = {
         val (_, _, right) = tree.split1( _._1 > dropLen )
         wrap( newHead +: right )
      }

      def addTerm( term: Long )( implicit tx: S#Tx ) : S#Acc = {
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

      def seminal : S#Acc = {
         val (_init, term) = splitIndex
         wrap( FingerTree( _init.term, term ))
      }

      // XXX TODO should have an efficient method in finger tree
      def indexTerm : Long = {
         tree.init.last
//         val idx = size - 2; tree.find1( _._1 > idx ) ??
      }

      def indexSum : Long = sum - (last >> 32)

      // XXX TODO should have an efficient method in finger tree
      def :-|( suffix: Long ) : S#Acc = wrap( tree.init :+ suffix )

      // XXX TODO should have an efficient method in finger tree
      def drop( n: Int ) : S#Acc = {
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
      def splitIndex : (S#Acc, Long) = (init, last)

      def splitAtIndex( idx: Int ) : (S#Acc, Long) = {
         val tup = tree.split1( _._1 > idx )
         (wrap( tup._1 ), tup._2)
      }

      def splitAtSum( hash: Long ) : (S#Acc, Long) = {
         val tup = tree.split1( _._2 > hash )
         (wrap( tup._1 ), tup._2)
      }

      def indexOfSum( hash: Long ): Int = {
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

      def index : S#Acc = wrap( tree.init )
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

      def wrap( _tree: FingerTree[ (Int, Long), Long ]) : Path[ S ] /* S#Acc */ = new Path( _tree )

      def mkString( prefix: String, sep: String, suffix: String ) : String =
         tree.iterator.map( _.toInt ).mkString( prefix, sep, suffix )
   }

   // --------------------------------------------
   // ----------------- END Path -----------------
   // --------------------------------------------

   private final class IndexTreeImpl[ D <: stm.DurableLike[ D ]]( val tree: Ancestor.Tree[ D, Long ], val level: Int )
   extends Sys.IndexTree[ D ] {
      override def hashCode : Int = term.toInt
      def term: Long = tree.root.version

      override def equals( that: Any ) : Boolean = {
         that.isInstanceOf[ Sys.IndexTree[ _ ]] && (term == that.asInstanceOf[ Sys.IndexTree[ _ ]].term)
      }

      def write( out: DataOutput ) {
         tree.write( out )
         out.writeInt( level )
      }

      def dispose()( implicit tx: D#Tx ) {
         tree.dispose()
      }

      override def toString = "IndexTree<v=" + term.toInt + ", l=" + level + ">"
   }

   // ----------------------------------------------
   // ------------- BEGIN transactions -------------
   // ----------------------------------------------

   trait TxnMixin[ S <: Sys[ S ]]
   extends Sys.Txn[ S ] /* with DurableCacheMapImpl[ S, Int ] */ {
      _: S#Tx =>

      // ---- abstract ----

      protected def flushCaches( meld: MeldInfo[ S ], caches: IIdxSeq[ Cache[ S#Tx ]]) : Unit

      // ---- init ----

//      private val meld  = TxnLocal( MeldInfo.empty[ S ])
      private var meld = MeldInfo.empty[ S ]

      private val dirtyMaps : TxnLocal[ IIdxSeq[ Cache[ S#Tx ]]] = TxnLocal( initialValue =
         { implicit itx =>
            log( "....... txn dirty ......." )
            ScalaTxn.beforeCommit { implicit itx => flushCaches( meld, dirtyMaps() )}
            IIdxSeq.empty
         })

//      private val markDirtyFlag = TxnLocal( false )
      private var markDirtyFlag = false

//      def isDirty = markDirtyFlag.get( peer )

      final private def markDirty() {
         if( !markDirtyFlag ) {
            markDirtyFlag = true
            addDirtyCache( fullCache )
            addDirtyCache( partialCache )
         }
      }

      final def forceWrite() { markDirty() }

      final def addDirtyCache( map: Cache[ S#Tx ]) {
         dirtyMaps.transform( _ :+ map )( peer )
      }

      final protected def fullCache    = system.fullMap
      final protected def partialCache = system.partialMap

      final def newID() : S#ID = {
         val res = new ConfluentID[ S ]( system.newIDValue()( this ), Path.empty[ S ])
         log( "txn newID " + res )
         res
      }

      final def newPartialID() : S#ID = {
         val res = new PartialID[ S ]( system.newIDValue()( this ), Path.empty[ S ])
         log( "txn newPartialID " + res )
         res
      }

      final def readPath( in: DataInput ) : S#Acc = Path.read[ S ]( in )

      final def readTreeVertexLevel( term: Long ) : Int = {
         system.store.get( out => {
            out.writeUnsignedByte( 0 )
            out.writeInt( term.toInt )
         })( in => {
            in.readInt()   // tree index!
            in.readInt()
         })( this ).getOrElse( sys.error( "Trying to access inexisting vertex " + term.toInt ))
      }

      final def addInputVersion( path: S#Acc ) {
         val sem1 = path.seminal
         val sem2 = inputAccess.seminal
         if( sem1 == sem2 ) return
         if( sem1 != sem2 ) {
            val m = meld
            // note: before we were reading the index tree; but since only the level
            // is needed, we can read the vertex instead which also stores the
            // the level.
//               val tree1 = readIndexTree( sem1.head )
            val tree1Level = readTreeVertexLevel( sem1.head )
            val m1 = if( m.isEmpty ) {
//                     val tree2 = readIndexTree( sem2.head )
               val tree2Level = readTreeVertexLevel( sem2.head )
               m.add( tree2Level, sem2 )
            } else m
            meld = m1.add( tree1Level, sem1 )
         }
      }

      final def getNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A = {
         log( "txn get " + id )
         fullCache.getCacheNonTxn[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

      final def getTxn[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
         log( "txn get' " + id )
         fullCache.getCacheTxn[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

      final def putTxn[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) {
//         logConfig( "txn put " + id )
         fullCache.putCacheTxn[ A ]( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) {
//         logConfig( "txn put " + id )
         fullCache.putCacheNonTxn[ A ]( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final def putPartial[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) {
         partialCache.putPartial( id.id, id.path, value )( this, ser )
         markDirty()
      }

      final def getPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
//         logPartial( "txn get " + id )
         partialCache.getPartial[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
      }

//      final private[Confluent] def getFreshPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A = {
////         logPartial( "txn getFresh " + id )
//         partialCache.getFreshPartial[ A ]( id.id, id.path )( this, ser ).getOrElse( sys.error( "No value for " + id ))
//      }

      final def isFresh( id: S#ID ) : Boolean = {
         val id1  = id.id
         val path = id.path
         // either the value was written during this transaction (implies freshness)...
         fullCache.cacheContains( id1, path )( this ) || {
            // ...or we have currently an ongoing meld which will produce a new
            // index tree---in that case (it wasn't in the cache!) the value is definitely not fresh...
            if( meld.requiresNewTree ) false else {
               // ...or otherwise freshness means the most recent write index corresponds
               // to the input access index
               // store....
               fullCache.store.isFresh( id1, path )( this )
            }
         }
      }

      final def removeFromCache( id: S#ID ) {
         fullCache.removeCacheOnly( id.id )( this )
      }

//      final def readVal[ A ]( id: S#ID )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : A = getTxn[ A ]( id )
//
//      final def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) {
//         putTxn[ A ]( id, value )
//      }

      @inline private def alloc(        pid: S#ID ) : S#ID = new ConfluentID( system.newIDValue()( this ), pid.path )
      @inline private def allocPartial( pid: S#ID ) : S#ID = new PartialID(   system.newIDValue()( this ), pid.path )

      final def newVar[ A ]( pid: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( alloc( pid ))
         log( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newLocalVar[ A ]( init: S#Tx => A ) : stm.LocalVar[ S#Tx, A ] = new stm.impl.LocalVarImpl[ S, A ]( init )

      final def newPartialVar[ A ]( pid: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = new PartialVarTxImpl[ S, A ]( allocPartial( pid ))
         log( "txn newPartialVar " + res )
         res.setInit( init )( this )
         res
      }

      final def newBooleanVar( pid: S#ID, init: Boolean ) : S#Var[ Boolean ] = {
         val id   = alloc( pid )
         val res  = new BooleanVar( id )
         log( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newIntVar( pid: S#ID, init: Int ) : S#Var[ Int ] = {
         val id   = alloc( pid )
         val res  = new IntVar( id )
         log( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newLongVar( pid: S#ID, init: Long ) : S#Var[ Long ] = {
         val id   = alloc( pid )
         val res  = new LongVar( id )
         log( "txn newVar " + res ) // + " - init = " + init
         res.setInit( init )( this )
         res
      }

      final def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = new Array[ S#Var[ A ]]( size )

      final def newInMemoryIDMap[ A ] : IdentifierMap[ S#ID, S#Tx, A ] = {
         val map = InMemoryConfluentMap.newIntMap[ S ]
         new InMemoryIDMapImpl[ S, A ]( map )
      }

      final def newDurableIDMap[ A ]( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         mkDurableIDMap( system.newIDValue()( this ))
      }

      private def mkDurableIDMap[ A ]( id: Int )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         val map  = DurablePersistentMap.newConfluentLongMap[ S ]( system.store, system.indexMap )
         val idi  = new ConfluentID( id, Path.empty[ S ])
         val res  = new DurableIDMapImpl[ S, A ]( idi, map )
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

      final def makeVar[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] /* BasicVar[ S, A ] */ = {
         ser match {
            case plain: ImmutableSerializer[ _ ] =>
               new VarImpl[ S, A ]( id, plain.asInstanceOf[ ImmutableSerializer[ A ]])
            case _ =>
               new VarTxImpl[ S, A ]( id )
         }
      }

      final def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( readSource( in, pid ))
         log( "txn read " + res )
         res
      }

      final def readPartialVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = new PartialVarTxImpl[ S, A ]( readPartialSource( in, pid ))
         log( "txn read " + res )
         res
      }

      final def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] = {
         val res = new BooleanVar( readSource( in, pid ))
         log( "txn read " + res )
         res
      }

      final def readIntVar( pid: S#ID, in: DataInput ) : S#Var[ Int ] = {
         val res = new IntVar( readSource( in, pid ))
         log( "txn read " + res )
         res
      }

      final def readLongVar( pid: S#ID, in: DataInput ) : S#Var[ Long ] = {
         val res = new LongVar( readSource( in, pid ))
         log( "txn read " + res )
         res
      }

      final def readID( in: DataInput, acc: S#Acc ) : S#ID = {
         val res = new ConfluentID( in.readInt(), Path.readAndAppend[ S ]( in, acc )( this ))
         log( "txn readID " + res )
         res
      }

      final def readPartialID( in: DataInput, acc: S#Acc ) : S#ID = {
//         readID( in, acc )
         val res = new PartialID( in.readInt(), Path.readAndAppend( in, acc )( this ))
         log( "txn readPartialID " + res )
         res
      }

      final def readDurableIDMap[ A ]( in: DataInput )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] = {
         val id = in.readInt()
         durableIDMaps.get( peer ).get( id ) match {
            case Some( existing ) => existing.asInstanceOf[ DurableIDMapImpl[ S, A ]]
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

      override def toString = "confluent.Sys#Tx" + inputAccess // + system.path.mkString( "<", ",", ">" )
   }

   trait RegularTxnMixin[ S <: Sys[ S ], D <: stm.DurableLike[ D ]] extends TxnMixin[ S ] {
      _: S#Tx =>

      final protected def flushCaches( meldInfo: MeldInfo[ S ], caches: IIdxSeq[ Cache[ S#Tx ]]) {
         system.flushRegular( meldInfo, caches )( this )
      }

      override def toString = "Confluent#Tx" + inputAccess
   }

   trait RootTxnMixin[ S <: Sys[ S ], D <: stm.DurableLike[ D ]]
   extends TxnMixin[ S ] {
      _: S#Tx =>

      final val inputAccess = Path.root[ S ]

      final protected def flushCaches( meldInfo: MeldInfo[ S ], caches: IIdxSeq[ Cache[ S#Tx ]]) {
         system.flushRoot( meldInfo, caches )( this )
      }

      override def toString = "Confluent.RootTxn"
   }

   private sealed trait TxnImpl extends /* TxnMixin[ Confluent, Durable ] with */ Confluent.Txn {
      final lazy val inMemory: InMemory#Tx = system.inMemory.wrap( peer )
   }

   private final class RegularTxn( val system: Confluent, val durable: Durable#Tx,
                                   val inputAccess: Confluent#Acc )
   extends RegularTxnMixin[ Confluent, Durable ] with TxnImpl {
      lazy val peer = durable.peer
   }
   
   private final class RootTxn( val system: Confluent, val peer: InTxn )
   extends RootTxnMixin[ Confluent, Durable ] with TxnImpl {
      lazy val durable: Durable#Tx = {
         log( "txn durable" )
         system.durable.wrap( peer )
      }
   }
   
   // ----------------------------------------------
   // -------------- END transactions --------------
   // ----------------------------------------------

   // -----------------------------------------------
   // -------------- BEGIN identifiers --------------
   // -----------------------------------------------

   private final class ConfluentID[ S <: Sys[ S ]]( val id: Int, val path: S#Acc ) extends Sys.ID[ S ] {
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
         that.isInstanceOf[ Sys.ID[ _ ]] && {
            val b = that.asInstanceOf[ Sys.ID[ _ ]]
            id == b.id && path == b.path
         }

      def write( out: DataOutput ) {
         out.writeInt( id )
         path.write( out )
      }

      override def toString = "<" + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: S#Tx ) {}
   }

   private final class PartialID[ S <: Sys[ S ]]( val id: Int, val path: S#Acc ) extends Sys.ID[ S ] {
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
         that.isInstanceOf[ PartialID[ _ ]] && {
            val b = that.asInstanceOf[ PartialID[ _ ]]
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

   // -----------------------------------------------
   // --------------- END identifiers ---------------
   // -----------------------------------------------

   // ---------------------------------------------
   // -------------- BEGIN variables --------------
   // ---------------------------------------------

   private sealed trait BasicVar[ S <: Sys[ S ], A ] extends Sys.Var[ S, A ] {
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

   private final class VarImpl[ S <: Sys[ S ], A ]( protected val id: S#ID, protected val ser: ImmutableSerializer[ A ])
   extends BasicVar[ S, A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         log( this.toString + " set " + v )
         tx.putNonTxn( id, v )( ser )
      }

      def get( implicit tx: S#Tx ) : A = {
         log( this.toString + " get" )
         tx.getNonTxn[ A ]( id )( ser )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def setInit( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( ser )
      }

//      // XXX TODO - this will fail if id.path does not start in v0
//      private[Confluent] def asEntry : S#Entry[ A ] = new RootVar[ A ]( id.id, toString, ser )

      override def toString = "Var(" + id + ")"
   }

   private final class PartialVarTxImpl[ S <: Sys[ S ], A ]( protected val id: S#ID )
                                                           ( implicit ser: Serializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ S, A ] {
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

   private final class VarTxImpl[ S <: Sys[ S ], A ]( protected val id: S#ID )
                                                    ( implicit ser: Serializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ S, A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         log( this.toString + " get" )
         tx.getTxn( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def setInit( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putTxn( id, v )
      }

      override def toString = "Var(" + id + ")"
   }

   private final class RootVar[ S <: Sys[ S ], A ]( id1: Int, name: String )
                                                  ( implicit val ser: Serializer[ S#Tx, S#Acc, A ])
   extends Sys.Entry[ S, A ] {
      def setInit( v: A )( implicit tx: S#Tx ) {
         set( v ) // XXX could add require( tx.inAccess == Path.root )
      }

      override def toString = name // "Root"

      private def id( implicit tx: S#Tx ) : S#ID = new ConfluentID[ S ]( id1, tx.inputAccess )

      def meld( from: S#Acc )( implicit tx: S#Tx ) : A = {
         log( this.toString + " meld " + from )
         val idm  = new ConfluentID[ S ]( id1, from )
         tx.addInputVersion( from )
         tx.getTxn( idm )
      }

      def set( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " set " + v )
         tx.putTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : A = {
         log( this.toString + " get" )
         tx.getTxn( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      def isFresh( implicit tx: S#Tx ) : Boolean = tx.isFresh( id )

      def write( out: DataOutput ) { sys.error( "Unsupported Operation -- access.write" )}

      def dispose()( implicit tx: S#Tx ) {}
   }

   private final class BooleanVar[ S <: Sys[ S ]]( protected val id: S#ID )
   extends BasicVar[ S, Boolean ] with ImmutableSerializer[ Boolean ] {
      def get( implicit tx: S#Tx ): Boolean = {
         log( this.toString + " get" )
         tx.getNonTxn[ Boolean ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Boolean = get

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
//         assertExists()
         log( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Boolean](" + id + ")"

      // ---- Serializer ----
      def write( v: Boolean, out: DataOutput ) { out.writeBoolean( v )}
      def read( in: DataInput ) : Boolean = in.readBoolean()
   }

   private final class IntVar[ S <: Sys[ S ]]( protected val id: S#ID )
   extends BasicVar[ S, Int ] with ImmutableSerializer[ Int ] {
      def get( implicit tx: S#Tx ) : Int = {
         log( this.toString + " get" )
         tx.getNonTxn[ Int ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Int = get

      def setInit( v: Int )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         log( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Int](" + id + ")"

      // ---- Serializer ----
      def write( v: Int, out: DataOutput ) { out.writeInt( v )}
      def read( in: DataInput ) : Int = in.readInt()
   }

   private final class LongVar[ S <: Sys[ S ]]( protected val id: S#ID )
   extends BasicVar[ S, Long ] with ImmutableSerializer[ Long ] {
      def get( implicit tx: S#Tx ) : Long = {
         log( this.toString + " get" )
         tx.getNonTxn[ Long ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Long = get

      def setInit( v: Long )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Long )( implicit tx: S#Tx ) {
//         assertExists()
         log( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Long](" + id + ")"

      // ---- Serializer ----
      def write( v: Long, out: DataOutput ) { out.writeLong( v )}
      def read( in: DataInput ) : Long = in.readLong()
   }

   // ---------------------------------------------
   // --------------- END variables ---------------
   // ---------------------------------------------

   // ----------------------------------------------
   // ----------------- BEGIN maps -----------------
   // ----------------------------------------------

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
   private val durableIDMaps = TxnLocal( IntMap.empty[ DurableIDMapImpl[ _, _ ]])

   private final class InMemoryIDMapImpl[ S <: Sys[ S ], A ]( val store: InMemoryConfluentMap[ S, Int ])
   extends IdentifierMap[ S#ID, S#Tx, A ] with InMemoryCacheMapImpl[ S, Int ] {
      private val markDirtyFlag = TxnLocal( false )

      def id: S#ID = new ConfluentID( 0, Path.empty[ S ])

      private def markDirty()( implicit tx: S#Tx ) {
         if( !markDirtyFlag.swap( true )( tx.peer )) tx.addDirtyCache( this )
      }

      protected def emptyCache : Map[ Int, Any ] = CacheMapImpl.emptyIntMapVal

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

   private final class DurableIDMapImpl[ S <: Sys[ S ], A ]( val id: S#ID,
                                                             val store: DurablePersistentMap[ S, Long ])
                                           ( implicit serializer: Serializer[ S#Tx, S#Acc, A ])
   extends IdentifierMap[ S#ID, S#Tx, A ] with DurableCacheMapImpl[ S, Long ] {
      private val nid = id.id.toLong << 32

      private val markDirtyFlag = TxnLocal( false )

      private def markDirty()( implicit tx: S#Tx ) {
         if( !markDirtyFlag.swap( true )( tx.peer )) tx.addDirtyCache( this )
      }

      protected def emptyCache : Map[ Long, Any ] = CacheMapImpl.emptyLongMapVal

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

   // ----------------------------------------------
   // ------------------ END maps ------------------
   // ----------------------------------------------

   private object GlobalState {
      private val SER_VERSION = 0

      implicit def serializer[ S <: Sys[ S ], D <: stm.DurableLike[ D ]] : stm.Serializer[ D#Tx, D#Acc, GlobalState[ S, D ]] =
         new Ser[ S, D ]

      private final class Ser[ S <: Sys[ S ], D <: stm.DurableLike[ D ]] extends stm.Serializer[ D#Tx, D#Acc, GlobalState[ S, D ]] {
         def write( v: GlobalState[ S, D ], out: DataOutput ) {
            import v._
            out.writeUnsignedByte( SER_VERSION )
            idCnt.write( out )
//            reactCnt.write( out )
            versionLinear.write( out )
            versionRandom.write( out )
            lastAccess.write( out )
            partialTree.write( out )
         }

         def read( in: DataInput, acc: D#Acc )( implicit tx: D#Tx ) : GlobalState[ S, D ] = {
            val serVer        = in.readUnsignedByte()
            require( serVer == SER_VERSION, "Incompatible serialized version. Found " + serVer + " but require " + SER_VERSION )
            val idCnt         = tx.readCachedIntVar( in )
//            val reactCnt      = tx.readCachedIntVar( in )
            val versionLinear = tx.readCachedIntVar( in )
            val versionRandom = tx.readCachedLongVar( in )
            val lastAccess: D#Var[ S#Acc ] = tx.readCachedVar[ S#Acc ]( in )( Path.serializer[ S, D ])
            val partialTree   = Ancestor.readTree[ D, Long ]( in, acc )( tx, ImmutableSerializer.Long, _.toInt )
            GlobalState[ S, D ]( idCnt, /* reactCnt, */ versionLinear, versionRandom, lastAccess, partialTree )
         }
      }
   }
   private final case class GlobalState[ S <: Sys[ S ], D <: stm.DurableLike[ D ]](
      idCnt: D#Var[ Int ], /* reactCnt: D#Var[ Int ], */
      versionLinear: D#Var[ Int ], versionRandom: D#Var[ Long ],
      lastAccess: D#Var[ S#Acc ],
      partialTree: Ancestor.Tree[ D, Long ]
   )

   // ---------------------------------------------
   // --------------- BEGIN systems ---------------
   // ---------------------------------------------

   private final class System( protected val storeFactory: DataStoreFactory[ DataStore ], val durable: Durable )
   extends Mixin[ Confluent ]
//   with Sys.IndexTreeHandler[ Durable, Confluent#Acc ]
   with Confluent {
//      val durable : D                     = Durable( store )
      def inMemory : I                    = durable.inMemory
      def durableTx(  tx: S#Tx ) : D#Tx   = tx.durable
      def inMemoryTx( tx: S#Tx ) : I#Tx   = tx.inMemory

      protected def wrapRegular( dtx: D#Tx, inputAccess: S#Acc ) : S#Tx = new RegularTxn( this, dtx, inputAccess )
      protected def wrapRoot( peer: InTxn ) : S#Tx = new RootTxn( this, peer )
   }

   trait Mixin[ S <: Sys[ S ] /*, D1 <: stm.DurableLike[ D1 ]*/]
   extends Sys[ S ]
   with Sys.IndexMapHandler[ S ]
   with Sys.PartialMapHandler[ S ]
   /* with Sys.IndexTreeHandler[ S#D, S#Acc ] */ {
      system: S /* with Sys.IndexTreeHandler[ D1, S#Acc ] */ =>

      // ---- abstract methods ----

      protected def storeFactory: DataStoreFactory[ DataStore ]
      protected def wrapRegular( dtx: D#Tx, inputAccess: S#Acc ) : S#Tx
      protected def wrapRoot( peer: InTxn ) : S#Tx

      // ---- init ----

      final val store         = storeFactory.open( "data" )
      private val varMap      = DurablePersistentMap.newConfluentIntMap[ S ]( store, this )
      final val fullMap       = DurableCacheMapImpl.newIntCache( varMap )
      final val partialMap    = PartialCacheMapImpl.newIntCache( DurablePersistentMap.newPartialMap[ S ]( store, this ))

      private val global: GlobalState[ S, D ] = durable.step { implicit tx =>
         val root = durable.root { implicit tx =>
            val idCnt         = tx.newCachedIntVar( 0 )
//            val reactCnt      = tx.newCachedIntVar( 0 )
            val versionLinear = tx.newCachedIntVar( 0 )
            val versionRandom = tx.newCachedLongVar( TxnRandom.initialScramble( 0L )) // scramble !!!
            val lastAccess: D#Var[ S#Acc ] = tx.newCachedVar[ S#Acc ]( Path.root[ S ])( Path.serializer[ S, D ])
            val partialTree   = Ancestor.newTree[ D, Long ]( 1L << 32 )( tx, Serializer.Long, _.toInt )
            GlobalState[ S, D ]( idCnt, /* reactCnt, */ versionLinear, versionRandom, lastAccess, partialTree )
         }
         root.get
      }

      private val versionRandom  = TxnRandom.wrap( global.versionRandom )

      override def toString = "Confluent"

      final def indexMap : Sys.IndexMapHandler[ S ] = this

      final def partialTree : Ancestor.Tree[ D, Long ] = global.partialTree

      final def newVersionID( implicit tx: S#Tx ) : Long = {
         implicit val dtx = durableTx( tx ) // tx.durable
         val lin  = global.versionLinear.get + 1
         global.versionLinear.set( lin )
         var rnd = 0
         do {
            rnd = versionRandom.nextInt()
         } while( rnd == 0 )
         (rnd.toLong << 32) | (lin.toLong & 0xFFFFFFFFL)
      }

      final def newIDValue()( implicit tx: S#Tx ) : Int = {
         implicit val dtx = durableTx( tx ) // tx.durable
         val res = global.idCnt.get + 1
//         logConfig( "new   <" + id + ">" )
         global.idCnt.set( res )
         res
      }

      final def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic { implicit itx =>
            implicit val dtx = durable.wrap( itx )
            val last = global.lastAccess.get
            log( "::::::: atomic - input access = " + last + " :::::::" )
            fun( wrapRegular( dtx, last ))
         }
      }

      final def position_=( p: S#Acc )( implicit tx: S#Tx ) {
         implicit val dtx = durableTx( tx )
         global.lastAccess.set( p ) // ( tx.durable )
      }

      final def position( implicit tx: S#Tx ) : S#Acc = {
         implicit val dtx = durableTx( tx )
         global.lastAccess.get // ( tx.durable )
      }

      final def root[ A ]( init: S#Tx => A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         require( ScalaTxn.findCurrent.isEmpty, "root must be called outside of a transaction" )
         log( "::::::: root :::::::" )
         TxnExecutor.defaultAtomic { itx =>
            implicit val tx   = wrapRoot( itx )
            val rootVar    = new RootVar[ S, A ]( 0, "Root" ) // serializer
            val rootPath   = tx.inputAccess
            if( varMap.get[ Array[ Byte ]]( 0, rootPath )( tx, ByteArraySerializer ).isEmpty ) {
               implicit val dtx = durableTx( tx )  // created on demand (now)
               rootVar.setInit( init( tx ))
               newIndexTree( rootPath.term, 0 )
               writePartialTreeVertex( partialTree.root )
            }
            rootVar
         }
      }

      final def flushRoot( meldInfo: MeldInfo[ S ], caches: IIdxSeq[ Cache[ S#Tx ]])( implicit tx: S#Tx ) {
         require( !meldInfo.requiresNewTree, "Cannot meld in the root version" )
         flush( tx.inputAccess.term, caches )
      }

      final def flushRegular( meldInfo: MeldInfo[ S ], caches: IIdxSeq[ Cache[ S#Tx ]])( implicit tx: S#Tx ) {
         val newTree = meldInfo.requiresNewTree
         val outTerm = if( newTree ) {
            flushNewTree( meldInfo.outputLevel )
         } else {
            flushOldTree()
         }
         log( "::::::: txn flush - " + (if( newTree ) "meld " else "") + "term = " + outTerm.toInt + " :::::::" )
         flush( outTerm, caches )
      }

      private def flush( outTerm: Long, caches: IIdxSeq[ Cache[ S#Tx ]])( implicit tx: S#Tx ) {
         position_=( tx.inputAccess.addTerm( outTerm ))   // XXX TODO last path would depend on value written to inputAccess?
         caches.foreach( _.flushCache( outTerm ))
      }

      private def flushOldTree()( implicit tx: S#Tx ) : Long = {
         implicit val dtx = durableTx( tx )
         val childTerm  = newVersionID( tx )
         val (index, parentTerm) = tx.inputAccess.splitIndex
         val tree       = readIndexTree( index.term )
         val parent     = readTreeVertex( tree.tree, index, parentTerm )._1
         val child      = tree.tree.insertChild( parent, childTerm )
         writeTreeVertex( tree, child )

         // ---- partial ----
         val pParent    = readPartialTreeVertex( index, parentTerm )
         val pChild     = partialTree.insertChild( pParent, childTerm )
         writePartialTreeVertex( pChild )

         childTerm
      }

      private def flushNewTree( level: Int )( implicit tx: S#Tx ) : Long = {
         implicit val dtx = durableTx( tx )
         val term = newVersionID( tx )
         newIndexTree( term, level )

         // ---- partial ----
         val (index, parentTerm) = tx.inputAccess.splitIndex
         val pParent    = readPartialTreeVertex( index, parentTerm )
         val pChild     = partialTree.insertChild( pParent, term ) // ( durable )
         writePartialTreeVertex( pChild )

         term
      }

      // do not make this final
      def close() { store.close()}

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )

      // ---- index tree handler ----

      private final class IndexMapImpl[ A ](
         protected val index: S#Acc,
         protected val map: Ancestor.Map[ D, Long, A ])
      extends IndexMap[ S, A ] {
         override def toString = index.mkString( "IndexMap(<", ",", ">, " + map + ")" )

         def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A) = {
   //         val v = tx.system.readTreeVertex( map.full, index, term )._1
            implicit val dtx = durableTx( tx )
            val v = readTreeVertex( map.full, index, term )._1
            val (v2, value) = map.nearest( v )
            (v2.version, value)
         }

         def add( term: Long, value: A )( implicit tx: S#Tx ) {
            implicit val dtx = durableTx( tx )
            val v = readTreeVertex( map.full, index, term )._1
            map.add( (v, value) )
         }

         def write( out: DataOutput ) {
            map.write( out )
         }
      }

      private def writeTreeVertex( tree: Sys.IndexTree[ D ], v: Ancestor.Vertex[ D, Long ])( implicit tx: D#Tx ) {
         system.store.put { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( v.version.toInt )
         } { out =>
            out.writeInt( tree.term.toInt )
            out.writeInt( tree.level )
            tree.tree.vertexSerializer.write( v, out )
         }
      }

      private def newIndexTree( term: Long, level: Int )( implicit tx: D#Tx ) : Sys.IndexTree[ D ] = {
         log( "txn new tree " + term.toInt )
         val tree = Ancestor.newTree[ D, Long ]( term )( tx, Serializer.Long, _.toInt )
         val res  = new IndexTreeImpl( tree, level )
         system.store.put( out => {
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         })( res.write )
         writeTreeVertex( res, tree.root )
         res
      }

      private def readIndexTree( term: Long )( implicit tx: D#Tx ) : Sys.IndexTree[ D ] = {
         val st = store
         st.get { out =>
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         } { in =>
            val tree    = Ancestor.readTree[ D, Long ]( in, () )( tx, Serializer.Long, _.toInt ) // tx.durable
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

      final def readTreeVertex( tree: Ancestor.Tree[ D, Long ], index: S#Acc, term: Long )
                        ( implicit tx: D#Tx ) : (Ancestor.Vertex[ D, Long ], Int) = {
//         val root = tree.root
//         if( root.version == term ) return root // XXX TODO we might also save the root version separately and remove this conditional
         store.get { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( term.toInt )
         } { in =>
            in.readInt()   // tree index!
            val access  = index :+ term
            val level   = in.readInt()
            val v       = tree.vertexSerializer.read( in, access )   // durableTx( tx )) // tx.durable )
            (v, level)
         } getOrElse sys.error( "Trying to access inexisting vertex " + term.toInt )
      }

      final def writePartialTreeVertex( v: Ancestor.Vertex[ D, Long ])( implicit tx: S#Tx ) {
         store.put { out =>
            out.writeUnsignedByte( 3 )
            out.writeInt( v.version.toInt )
         } { out =>
            partialTree.vertexSerializer.write( v, out )
         }
      }

      // ---- index map handler ----

      final def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )
                          ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         implicit val dtx = durableTx( tx )
         val tree       = readIndexTree( index.term )
         val full       = tree.tree
         val rootVertex = if( rootTerm == tree.term ) {
            full.root
         } else {
            readTreeVertex( full, index, rootTerm )._1
         }
         val map  = Ancestor.newMap[ D, Long, A ]( full, rootVertex, rootValue )
         new IndexMapImpl[ /* S, D, */ A ]( index, map )
      }

      final def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                           ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         implicit val dtx = durableTx( tx )
         val term = index.term
         val tree = readIndexTree( term )
         val map  = Ancestor.readMap[ D, Long, A ]( in, (), tree.tree )
         new IndexMapImpl[ A ]( index, map )
      }

      // ---- partial map handler ----

      private final class PartialMapImpl[ A ]( protected val index: S#Acc,
                                               protected val map: Ancestor.Map[ D, Long, A ])
      extends IndexMap[ S, A ] {
         override def toString = index.mkString( "PartialMap(<", ",", ">, " + map + ")" )

         def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A) = {
            implicit val dtx = durableTx( tx )
            val v = readPartialTreeVertex( index, term )
            val (v2, value) = map.nearest( v )
            (v2.version, value)
         }

         def add( term: Long, value: A )( implicit tx: S#Tx ) {
            implicit val dtx = durableTx( tx )
            val v = readPartialTreeVertex( index, term )
            map.add( (v, value) )
         }

         def write( out: DataOutput ) {
            map.write( out )
         }
      }

      private def readPartialTreeVertex( index: S#Acc, term: Long )( implicit tx: D#Tx ) : Ancestor.Vertex[ D, Long ] = {
         store.get { out =>
            out.writeUnsignedByte( 3 )
            out.writeInt( term.toInt )
         } { in =>
            val access  = index :+ term
            partialTree.vertexSerializer.read( in, access )
         } getOrElse sys.error( "Trying to access inexisting vertex " + term.toInt )
      }

      final def getIndexTreeTerm( term: Long )( implicit tx: S#Tx ) : Long = {
         implicit val dtx = durableTx( tx )
         readIndexTree( term ).term
      }

      final def newPartialMap[ A ]( access: S#Acc, rootTerm: Long, rootValue: A )
                            ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         implicit val dtx = durableTx( tx )
         val map     = Ancestor.newMap[ D, Long, A ]( partialTree, partialTree.root, rootValue )
         val index   = access._take( 1 )   // XXX correct ?
         new PartialMapImpl[ A ]( index, map )
      }

      final def readPartialMap[ A ]( access: S#Acc, in: DataInput )
                             ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ] = {
         implicit val dtx = durableTx( tx )
         val map     = Ancestor.readMap[ D, Long, A ]( in, (), partialTree )
         val index   = access._take( 1 )   // XXX correct ?
         new PartialMapImpl[ A ]( index, map )
      }
   }

   // ---------------------------------------------
   // ---------------- END systems ----------------
   // ---------------------------------------------
}
