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
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike, IndexedSeqLike}
import collection.immutable.{IntMap, LongMap}
import concurrent.stm.{TxnExecutor, TxnLocal, InTxn, Ref => ScalaRef}
import de.sciss.lucre.stm.{PersistentStore, TxnWriter, Writer, TxnReader, TxnSerializer}

object KSysImpl {
   private type S = System

   final class IDImpl private[KSysImpl]( val id: Int, val path: Path ) extends KSys.ID[ S#Tx, Path ] {
//      final def shortString : String = access.mkString( "<", ",", ">" )

      override def hashCode = {
         import MurmurHash._
         var h = startHash( 2 )
         val c = startMagicA
         val k = startMagicB
         h = extendHash( h, id, c, k )
         h = extendHash( h, path.##, nextMagicA( c ), nextMagicB( k ))
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

   object Path {
      def test_empty : Path = new Path( FingerTree.empty( Measure.IndexedSummedIntLong ))
   }
   final class Path private[KSysImpl]( protected val tree: FingerTree[ (Int, Long), Int ])
   extends KSys.Acc[ S ] with FingerTreeLike[ (Int, Long), Int, Path ] {
      implicit protected def m: Measure[ Int, (Int, Long) ] = Measure.IndexedSummedIntLong

      def foreach( fun: Int => Unit ) {
         sys.error( "TODO" )
      }

      override def toString = mkString( "Path(", ", ", ")" )

//      def size : Int = sys.error( "TODO" )

      def test_:+( elem: Int ) : Path = :+( elem )

      private[confluent] def :+( suffix: Int ) : Path = wrap( tree :+ suffix )

      // XXX TODO should have an efficient method in finger tree
      private[confluent] def :-|( suffix: Int ) : Path = wrap( tree.init :+ suffix )

      def write( out: DataOutput ) {
//         out.writeInt( path.size )
//         path.foreach( out.writeInt( _ ))
         sys.error( "TODO" )
      }

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

      protected def wrap( _tree: FingerTree[ (Int, Long), Int ]) : Path = new Path( _tree )

      def mkString( prefix: String, sep: String, suffix: String ) : String =
         tree.iterator.mkString( prefix, sep, suffix )
   }

   final class TxnImpl private[KSysImpl]( val system: System, val peer: InTxn )
   extends KSys.Txn[ S ] {

      def newID() : S#ID = system.newID()( this )

      override def toString = "KSys#Tx" // + system.path.mkString( "<", ",", ">" )

      def reactionMap : ReactionMap[ S ] = system.reactionMap

      private def alloc( pid: S#ID ) : S#ID = new IDImpl( system.newIDCnt()( this ), pid.path )

      def newVar[ A ]( pid: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val id   = alloc( pid )
         val res  = new VarImpl[ A ]( id, ser )
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

      def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = sys.error( "TODO" ) // new Array[ S#Var[ A ]]( size )

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

      def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val id = readSource( in, pid )
//         new Var( id, system, ser )
         sys.error( "TODO" )
      }

      def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] = readVar[ Boolean ]( pid, in )
      def readIntVar(     pid: S#ID, in: DataInput ) : S#Var[ Int ]     = readVar[ Int ](     pid, in )
      def readLongVar(    pid: S#ID, in: DataInput ) : S#Var[ Long ]    = readVar[ Long ](    pid, in )

      def readID( in: DataInput, acc: S#Acc ) : S#ID = sys.error( "TODO" ) // IDImpl.readAndAppend( in.readInt(), acc, in )

      def access[ A ]( source: S#Var[ A ]) : A = sys.error( "TODO" ) // source.access( system.path( this ))( this )
   }

   sealed trait SourceImpl[ A ] {
      protected def system: S
      protected def id: S#ID

      protected final def toString( pre: String ) = pre + id // + ": " +
//         (system.storage.getOrElse( id.id, Map.empty ).map( _._1 )).mkString( ", " )

      final def set( v: A )( implicit tx: S#Tx ) { store( v )}

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
      }

      protected def writeValue( v: A, out: DataOutput ) : Unit
      protected def readValue( in: DataInput, postfix: S#Acc )( implicit tx: S#Tx ) : A

      final def store( v: A ) {
         val out = new DataOutput()
         writeValue( v, out )
         val bytes = out.toByteArray
//         system.storage += id.id -> (system.storage.getOrElse( id.id,
//            Map.empty[ S#Acc, Array[ Byte ]]) + (id.path -> bytes))
         sys.error( "TODO" )
      }

      final def get( implicit tx: S#Tx ) : A = access( id.path )

      def access( acc: S#Acc )( implicit tx: S#Tx ) : A

//      final def access( acc: S#Acc )( implicit tx: Txn ) : A = {
//         val (in, acc1) = system.access( id.id, acc )( tx, ser )
//         readValue( in, acc1 )
//      }

      final def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      final def dispose()( implicit tx: S#Tx ) {}
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

   //   private type Obs[ A ]    = Observer[ Txn, Change[ A ]]
   //   private type ObsVar[ A ] = Var[ A ] with State[ S, Change[ A ]]

   private sealed trait BasicVar[ A ] extends Var[ A ] with BasicSource {
      protected def ser: TxnSerializer[ S#Tx, S#Acc, A ]

      def get( implicit tx: S#Tx ) : A = {
//         tx.system.read[ A ]( id )( ser.read( _, () ))
         sys.error( "TODO" )
      }

      def setInit( v: A )( implicit tx: S#Tx ) { tx.system.write( id )( ser.write( v, _ ))}
   }

   private final class VarImpl[ A ]( protected val id: S#ID, protected val ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.write( id )( ser.write( v, _ ))
      }

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

   private final class BooleanVar( protected val id: S#ID )
   extends Var[ Boolean ] with BasicSource {
      def get( implicit tx: S#Tx ): Boolean = {
         tx.system.read[ Boolean ]( id )( _.readBoolean() )
      }

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def transform( f: Boolean => Boolean )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Boolean](" + id + ")"
   }

   private final class IntVar( protected val id: S#ID )
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: S#Tx ) : Int = {
         tx.system.read[ Int ]( id )( _.readInt() )
      }

      def setInit( v: Int )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeInt( v ))
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.write( id )( _.writeInt( v ))
      }

      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
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
//         sys.error( "TODO" )
//      }
//
//      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}
//
//      override def toString = "Var[Int](" + id + ")"
//   }

   private final class LongVar( protected val id: S#ID )
   extends Var[ Long ] with BasicSource {
      def get( implicit tx: S#Tx ) : Long = {
         tx.system.read[ Long ]( id )( _.readLong() )
      }

      def setInit( v: Long )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeLong( v ))
      }

      def set( v: Long )( implicit tx: S#Tx ) {
//         assertExists()
         tx.system.write( id )( _.writeLong( v ))
      }

      def transform( f: Long => Long )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"
   }

   sealed trait Var[ @specialized A ] extends KSys.Var[ S, A ]

   final class System private[KSysImpl]( store: PersistentStore[ S#Tx ], idCnt0: Int ) extends KSys[ System ] {
      type ID                    = KSysImpl.IDImpl
      type Tx                    = KSysImpl.TxnImpl
      type Acc                   = KSysImpl.Path
      type Var[ @specialized A ] = KSysImpl.Var[ A ]

      private val idCntVar = ScalaRef( idCnt0 )

      val manifest = Predef.manifest[ System ]
      private[KSysImpl] val reactionMap : ReactionMap[ S ] = sys.error( "TODO" )

      private[KSysImpl] def newID()( implicit tx: S#Tx ) : ID = sys.error( "TODO" )
      private[KSysImpl] def newIDCnt()( implicit tx: S#Tx ) : Int = sys.error( "TODO" )

      private val storage  = ConfluentPersistentMap[ S, Any ]()
      private val cache    = ConfluentCacheMap[ S, Any ]( storage )

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

      //      def atomicAccess[ A ]( fun: (S#Tx, S#Acc) => A ) : A =
      //         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx ), () ))

      //      def atomicAccess[ A, B ]( source: S#Var[ A ])( fun: (S#Tx, A) => B ) : B = atomic { tx =>
      //         fun( tx, source.get( tx ))
      //      }

//      def debugListUserRecords()( implicit tx: S#Tx ): Seq[ ID ] = {
//         val b    = Seq.newBuilder[ ID ]
//         val cnt  = idCntVar.get
//         var i    = 1;
//         while( i <= cnt ) {
//            if( exists( i )) b += new IDImpl( i )
//            i += 1
//         }
//         b.result()
//      }

      def close() {
         store.close()
      }

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )

      def newIDValue()( implicit tx: S#Tx ) : Int = {
         implicit val itx = tx.peer
         val id = idCntVar.get + 1
//         logConfig( "new   <" + id + ">" )
         idCntVar.set( id )
         // ... and persist XXX
         sys.error( "TODO" )
         id
      }

      def write( id: S#ID )( valueFun: DataOutput => Unit )( implicit tx: S#Tx ) {
         sys.error( "TODO" )
//         logConfig( "write <" + id + ">" )
//         store.put( _.writeInt( id ))( valueFun )
      }

      def remove( id: S#ID )( implicit tx: S#Tx ) {
         sys.error( "TODO" )
//         logConfig( "remov <" + id + ">" )
//         store.remove( _.writeInt( id ))
      }

      def read[ @specialized A ]( id: S#ID )( valueFun: DataInput => A )( implicit tx: S#Tx ) : A = {
         sys.error( "TODO" )
//         logConfig( "read  <" + id + ">" )
//         store.get( _.writeInt( id ))( valueFun ).getOrElse( sys.error( "Key not found " + id ))
      }
   }
}