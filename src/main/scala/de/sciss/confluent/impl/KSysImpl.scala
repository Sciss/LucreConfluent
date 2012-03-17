/*
 *  KSysImpl.scala
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
package impl

import util.MurmurHash
import de.sciss.lucre.event.ReactionMap
import de.sciss.lucre.{DataOutput, DataInput}
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import de.sciss.collection.txn.Ancestor
import de.sciss.lucre.stm.{Serializer, Durable, PersistentStoreFactory, InMemory, PersistentStore, TxnWriter, Writer, TxnReader, TxnSerializer}

object KSysImpl {
   private type S = System

   def apply( storeFactory: PersistentStoreFactory[ PersistentStore ]) : System = new System( storeFactory )

//   private object IDImpl {
//      def readAndAppend( id: Int, postfix: S#Acc, in: DataInput ) : S#ID = {
//         val path = Path.readAndAppend( in, postfix )
//         new IDImpl( id, path )
//      }
//   }

   final class IDImpl private[KSysImpl]( val id: Int, val path: Path ) extends KSys.ID[ S#Tx, Path ] {
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

//   private object PathMeasure extends Measure[ Int, (Int, Long) ]

   private object PathMeasure extends Measure[ Long, (Int, Long) ] {
      override def toString = "PathMeasure"
      val zero = (0, 0L)
      def apply( c: Long ) = (1, c >> 32)
      def |+|( a: (Int, Long), b: (Int, Long) ) = ((a._1 + b._1), (a._2 + b._2))
      def |+|( a: (Int, Long), b: (Int, Long), c: (Int, Long) ) = ((a._1 + b._1 + c._1), (a._2 + b._2 + c._2))
   }

   object Path {
      def test_empty : Path = empty
      private[KSysImpl] def empty = new Path( FingerTree.empty( PathMeasure ))

      def read( in: DataInput ) : S#Acc = new Path( readTree( in ))

      def readAndAppend( in: DataInput, post: S#Acc ) : S#Acc = {
         var tree = readTree( in )
         // XXX TODO make this more efficient
         implicit val m = PathMeasure
         post.tree.iterator.foreach( tree :+= _ )
         new Path( tree )
      }

      private def readTree( in: DataInput ) : FingerTree[ (Int, Long), Long ] = {
         val sz = in.readInt()
         // XXX TODO make this more efficient
         implicit val m = PathMeasure
         var tree = FingerTree.empty( m )
         var i = 0; while( i < sz ) {
            tree :+= in.readLong()
         i += 1 }
         tree
      }
   }

   /**
    * The finger tree has elements of type `Long` where the upper 32 bits are the randomized version,
    * and the lower 32 bits are the incremental version. The measure is taking the index and running sum
    * of the tree.
    */
   final class Path private[KSysImpl]( protected val tree: FingerTree[ (Int, Long), Long ])
   extends KSys.Acc[ S ] with FingerTreeLike[ (Int, Long), Long, Path ] {
      implicit protected def m: Measure[ Long, (Int, Long) ] = PathMeasure

//      def foreach( fun: Int => Unit ) {
//         // XXX TODO efficient implementation
//         tree.iterator.foreach( fun )
//      }

      override def toString = mkString( "Path(", ", ", ")" )

      def test_:+( elem: Long ) : Path = :+( elem )

      private[confluent] def :+( suffix: Long ) : Path = wrap( tree :+ suffix )

      // XXX TODO should have an efficient method in finger tree
      private[confluent] def :-|( suffix: Long ) : Path = wrap( tree.init :+ suffix )

      // XXX TODO should have an efficient method in finger tree
      private[confluent] def splitIndex : (Path, Long) = (init, last)

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

   private final class IndexMapImpl[ A ]( protected val index: S#Acc,
                                         protected val map: Ancestor.Map[ Durable, Long, A ])
   extends IndexMap[ S, A ] {
      def nearest( term: Long )( implicit tx: S#Tx ) : A = {
         val v = termToVertex( term )
         map.nearest( v )( tx.durable )._2
      }

      def add( term: Long, value: A )( implicit tx: S#Tx ) {
         val v = termToVertex( term )
         map.add( (v, value) )( tx.durable )
      }

      // XXX TODO eventually should use caching
      private def termToVertex( term: Long )( implicit tx: S#Tx ) : Ancestor.Vertex[ Durable, Long ] = {
         val vs   = map.full.vertexSerializer
         tx.system.store.get { out =>
            out.writeUnsignedByte( 0 )
            out.writeInt( term.toInt )
         } { in =>
            val access = index :+ term
            vs.read( in, access )( tx.durable )
         } getOrElse sys.error( "Trying to access inexisting vertex " + term )
      }
   }

   final class TxnImpl private[KSysImpl]( val system: System, val peer: InTxn )
   extends KSys.Txn[ S ] {

      private[KSysImpl] implicit lazy val durable: Durable#Tx = system.durable.wrap( peer )

      def newID() : S#ID = system.newID()( this )

      override def toString = "KSys#Tx" // + system.path.mkString( "<", ",", ">" )

      def reactionMap : ReactionMap[ S ] = system.reactionMap

//      def indexTree( version: Int ) : Ancestor.Tree[ S, Int ] = system.indexTree( version )( this )

      private def readIndexTree( term: Long ) : Ancestor.Tree[ Durable, Long ] = {
         system.store.get { out =>
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         } { in =>
            Ancestor.readTree[ Durable, Long ]( in, () )( durable, TxnSerializer.Long, _.toInt )
         } getOrElse sys.error( "Trying to access inexisting tree " + term )
      }

      def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                           ( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ] = {
         val term = index.term
         val full = readIndexTree( term )
         val map  = Ancestor.readMap[ Durable, Long, A ]( in, (), full )
         new IndexMapImpl[ A ]( index, map )
      }

      private[KSysImpl] def newIndexTree( term: Long ) : Ancestor.Tree[ Durable, Long ] = {
         val tree = Ancestor.newTree[ Durable, Long ]( term )( durable, TxnSerializer.Long, _.toInt )
         system.store.put({ out =>
            out.writeUnsignedByte( 1 )
            out.writeInt( term.toInt )
         })( tree.write )
         tree
      }

      def newIndexMap[ A ]( index: S#Acc, value: A )( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ] = {
         val tree = readIndexTree( index.term )
         val map  = Ancestor.newMap[ Durable, Long, A ]( tree, value )
         new IndexMapImpl[ A ]( index, map )
      }

      private def alloc( pid: S#ID ) : S#ID = new IDImpl( system.newIDValue()( this ), pid.path )

      def newVar[ A ]( pid: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val res = makeVar[ A ]( alloc( pid ))
         res.setInit( init )( this )
         res
      }

      def newBooleanVar( pid: S#ID, init: Boolean ) : S#Var[ Boolean ] = {
         val id   = alloc( pid )
         val res  = new BooleanVar( id )
         res.setInit( init )( this )
         res
      }

      def newIntVar( pid: S#ID, init: Int ) : S#Var[ Int ] = {
         val id   = alloc( pid )
         val res  = new IntVar( id )
         res.setInit( init )( this )
         res
      }

      def newLongVar( pid: S#ID, init: Long ) : S#Var[ Long ] = {
         val id   = alloc( pid )
         val res  = new LongVar( id )
         res.setInit( init )( this )
         res
      }

      def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = new Array[ S#Var[ A ]]( size )

      private def readSource( in: DataInput, pid: S#ID ) : S#ID = {
         val id = in.readInt()
         new IDImpl( id, pid.path )
      }

      def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
//         val (in, acc) = system.access( id.id, parent.path )( this )
//         reader.read( in, acc )( this )
         sys.error( "TODO" )
      }

      def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )( implicit writer: TxnWriter[ A ]) {
         val out = new DataOutput()
         writer.write( value, out )
         val bytes = out.toByteArray
//         system.storage += id.id -> (system.storage.getOrElse( id.id,
//            Map.empty[ S#Acc, Array[ Byte ]]) + (parent.path -> bytes))
         sys.error( "TODO" )
      }

      def readVal[ A ]( id: S#ID )( implicit reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
//         val (in, acc) = system.access( id.id, id.path )( this )
//         reader.read( in, acc )( this )
         sys.error( "TODO" )
      }

      def writeVal( id: S#ID, value: Writer ) {
         val out = new DataOutput()
         value.write( out )
         val bytes = out.toByteArray
//         system.storage += id.id -> (system.storage.getOrElse( id.id,
//            Map.empty[ S#Acc, Array[ Byte ]]) + (id.path -> bytes))
         sys.error( "TODO" )
      }

      private[KSysImpl] def makeVar[ A ]( id: S#ID )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : BasicVar[ A ] = {
         ser match {
            case plain: Serializer[ _ ] =>
               new VarImpl[ A ]( id, plain.asInstanceOf[ Serializer[ A ]])
            case _ =>
               new VarTxImpl[ A ]( id, ser )
         }
      }

      def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] =
         makeVar[ A ]( readSource( in, pid ))

      def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] =
         new BooleanVar( readSource( in, pid ))

      def readIntVar( pid: S#ID, in: DataInput ) : S#Var[ Int ] =
         new IntVar( readSource( in, pid ))

      def readLongVar( pid: S#ID, in: DataInput ) : S#Var[ Long ] =
         new LongVar( readSource( in, pid ))

      def readID( in: DataInput, acc: S#Acc ) : S#ID = {
         new IDImpl( in.readInt(), Path.readAndAppend( in, acc ))
      }

      def access[ A ]( source: S#Var[ A ]) : A = {
         sys.error( "TODO" )  // source.access( system.path( this ))( this )
      }
   }

   private sealed trait BasicSource {
      protected def id: S#ID

      final def write( out: DataOutput ) {
         id.write( out )
      }

      /* final */
      def dispose()( implicit tx: S#Tx ) {
         id.dispose()
      }

//      @elidable(CONFIG) protected final def assertExists()(implicit tx: Txn) {
//         require(tx.system.exists(id), "trying to write disposed ref " + id)
//      }
   }

   private sealed trait BasicVar[ A ] extends Var[ A ] with BasicSource {
      def setInit( v: A )( implicit tx: S#Tx ) : Unit
   }

   private final class VarImpl[ A ]( protected val id: S#ID, protected val ser: Serializer[ A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.put( id, v )( tx, ser )
      }

      def get( implicit tx: S#Tx ) : A = tx.system.get[ A ]( id )( tx, ser )

      def setInit( v: A )( implicit tx: S#Tx ) { tx.system.put( id, v )( tx, ser )}

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

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

   private final class VarTxImpl[ A ]( protected val id: S#ID, protected val ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         val out  = new DataOutput()
         ser.write( v, out )
         val arr  = out.toByteArray
         tx.system.put( id, arr )( tx, ByteArraySerializer )
      }

      def get( implicit tx: S#Tx ) : A = {
         val arr     = tx.system.get( id )( tx, ByteArraySerializer )
         val in      = new DataInput( arr )
         val access  = id.path   // XXX ???
         ser.read( in, access )
      }

      def setInit( v: A )( implicit tx: S#Tx ) {
         val out  = new DataOutput()
         ser.write( v, out )
         val arr  = out.toByteArray
         tx.system.put( id, arr )( tx, ByteArraySerializer )
      }

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

   private final class BooleanVar( protected val id: S#ID )
   extends Var[ Boolean ] with BasicSource with Serializer[ Boolean ] {
      def get( implicit tx: S#Tx ): Boolean = {
         tx.system.get[ Boolean ]( id )( tx, this )
      }

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         tx.system.put( id, v )( tx, this )
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.put( id, v )( tx, this )
      }

      def transform( f: Boolean => Boolean )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Boolean](" + id + ")"

      // ---- TxnSerializer ----
      def write( v: Boolean, out: DataOutput ) { out.writeBoolean( v )}
      def read( in: DataInput ) : Boolean = in.readBoolean()
   }

   private final class IntVar( protected val id: S#ID )
   extends Var[ Int ] with BasicSource with Serializer[ Int ] {
      def get( implicit tx: S#Tx ) : Int = {
         tx.system.get[ Int ]( id )( tx, this )
      }

      def setInit( v: Int )( implicit tx: S#Tx ) {
         tx.system.put( id, v )( tx, this )
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.put( id, v )( tx, this )
      }

      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}

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
   extends Var[ Long ] with BasicSource with Serializer[ Long ] {
      def get( implicit tx: S#Tx ) : Long = {
         tx.system.get[ Long ]( id )( tx, this )
      }

      def setInit( v: Long )( implicit tx: S#Tx ) {
         tx.system.put( id,v  )( tx, this )
      }

      def set( v: Long )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.put( id, v )( tx, this )
      }

      def transform( f: Long => Long )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"

      // ---- TxnSerializer ----
      def write( v: Long, out: DataOutput ) { out.writeLong( v )}
      def read( in: DataInput ) : Long = in.readLong()
   }

   sealed trait Var[ @specialized A ] extends KSys.Var[ S, A ]

   final class System private[KSysImpl]( storeFactory: PersistentStoreFactory[ PersistentStore ])
   extends KSys[ System ] {
      type ID                    = KSysImpl.IDImpl
      type Tx                    = KSysImpl.TxnImpl
      type Acc                   = KSysImpl.Path
      type Var[ @specialized A ] = KSysImpl.Var[ A ]

      val manifest               = Predef.manifest[ System ]
      private[KSysImpl] val store  = storeFactory.open( "data" )
//      private val kStore         = storeFactory.open( "confluent" )
      private[KSysImpl] val durable = Durable( store ) : Durable
      private val persistent        = ConfluentPersistentMap[ S, Any ]( store )
      private val map               = ConfluentCacheMap[ S, Any ]( persistent )

      // XXX TODO should be persistent, e.g. use CachedIntVar again
      private val idCntVar : ScalaRef[ Int ] = ScalaRef {
         atomic { implicit tx =>
            store.get[ Int ]( _.writeInt( 0 ))( _.readInt() ).getOrElse( 1 ) // 0 is the idCnt var itself !
         }
      }

//      private val rootVar : S#Var[ Root ] = atomic { implicit tx =>
//         var res = tx.makeVar
//      }

      private val inMem    = InMemory()

      private[KSysImpl] lazy val reactionMap : ReactionMap[ S ] =
         ReactionMap[ S, InMemory ]( inMem.atomic { implicit tx =>
            tx.newIntVar( tx.newID(), 0 )
         })( ctx => inMem.wrap( ctx.peer ))

      private[KSysImpl] def newID()( implicit tx: S#Tx ) : ID = {
         new IDImpl( newIDValue(), Path.empty )
      }

      private[KSysImpl] def newIDValue()( implicit tx: S#Tx ) : Int = {
         implicit val itx = tx.peer
         val res = idCntVar.get + 1
//         logConfig( "new   <" + id + ">" )
         idCntVar.set( res )
         // ... and persist ...
         store.put( _.writeInt( 0 ))( _.writeInt( res ))
         res
      }

      private[KSysImpl] def access[ A ]( id: Int, acc: S#Acc )
                                       ( implicit tx: S#Tx, reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
         sys.error( "TODO" )
//
//
//         var best: Array[Byte]   = null
//         var bestLen = 0
//         val map = storage.getOrElse( id, Map.empty )
//         map.foreach {
//            case (path, arr) =>
//               val len = path.zip( acc ).segmentLength({ case (a, b) => a == b }, 0 )
//               if( len > bestLen && len == path.size ) {
//                  best     = arr
//                  bestLen  = len
//               }
//         }
//         require( best != null, "No value for path " + acc )
//         val in = new DataInput( best )
//         (in, acc.drop( bestLen ))
      }

      def atomic[ A ]( fun: S#Tx => A ): A =
         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx )))

//      def t[ A ]( fun: S#Tx => S#Var[ Root ] => A ) : A = atomic[ A ]( fun( _ )( rootVar ))

      //      def atomicAccess[ A ]( fun: (S#Tx, S#Acc) => A ) : A =
      //         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx ), () ))

      //      def atomicAccess[ A, B ]( source: S#Var[ A ])( fun: (S#Tx, A) => B ) : B = atomic { tx =>
      //         fun( tx, source.get( tx ))
      //      }

      def root[ A ]( init: => A )( implicit tx: S#Tx, serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] =
         sys.error( "TODO" )

      def close() { store.close()}

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )

      private[KSysImpl] def put[ @specialized A ]( id: S#ID, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
//         logConfig( "write <" + id + ">" )
         map.put[ A ]( id.id, id.path, value )
      }

//      def remove( id: S#ID )( implicit tx: S#Tx ) {
//         sys.error( "TODO" )
////         logConfig( "remov <" + id + ">" )
////         kStore.remove( _.writeInt( id ))
//      }

      private[KSysImpl] def get[ @specialized A ]( id: S#ID )( implicit tx: S#Tx,
                                                               ser: Serializer[ A ]) : A = {
         map.get[ A ]( id.id, id.path ).getOrElse( sys.error( "No value for " + id.id + " at path " + id.path ))
      }
   }
}