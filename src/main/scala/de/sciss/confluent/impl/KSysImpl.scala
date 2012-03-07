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
import concurrent.stm.InTxn
import de.sciss.lucre.event.ReactionMap
import de.sciss.lucre.{DataOutput, DataInput}
import de.sciss.lucre.stm.{Writer, TxnReader, TxnSerializer}
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike, IndexedSeqLike}

object KSysImpl {
   private type S = System

   final class ID private[KSysImpl]( val id: Int, val path: Path ) extends KSys.ID[ Txn, Path ] {
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

   final class Path private[KSysImpl]( protected val tree: FingerTree[ (Int, Long), Int ])
   extends KSys.Acc with FingerTreeLike[ (Int, Long), Int, Path ] {
      implicit protected def m: Measure[ Int, (Int, Long) ] = Measure.IndexedSummedIntLong

      def foreach( fun: Int => Unit ) {
         sys.error( "TODO" )
      }

//      def size : Int = sys.error( "TODO" )

      def write( out: DataOutput ) {
//         out.writeInt( path.size )
//         path.foreach( out.writeInt( _ ))
         sys.error( "TODO" )
      }

      protected def wrap( _tree: FingerTree[ (Int, Long), Int ]) : Path = new Path( _tree )

      def mkString( prefix: String, sep: String, suffix: String ) : String = sys.error( "TODO" )
   }

   final class Txn private[KSysImpl]( val system: System, val peer: InTxn ) extends KSys.Txn[ S ] {
      def newID() : S#ID = system.newID()( this )

      override def toString = "KSys#Tx" // + system.path.mkString( "<", ",", ">" )

      def reactionMap : ReactionMap[ S ] = system.reactionMap

      private def alloc( pid: S#ID ) : S#ID = new ID( system.newIDCnt()( this ), pid.path )

      def newVar[ A ]( pid: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val id   = alloc( pid )
//         val res  = new Var[ A ]( id, system, ser )
//         res.store( init )
//         res
         sys.error( "TODO" )
      }

      def newBooleanVar( pid: S#ID, init: Boolean ) : S#Var[ Boolean ] = newVar[ Boolean ]( pid, init )
      def newIntVar(     pid: S#ID, init: Int ) :     S#Var[ Int ]     = newVar[ Int ](     pid, init )
      def newLongVar(    pid: S#ID, init: Long ) :    S#Var[ Long ]    = newVar[ Long ](    pid, init )

      def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]] = sys.error( "TODO" ) // new Array[ S#Var[ A ]]( size )

      private def readSource( in: DataInput, pid: S#ID ) : S#ID = {
         val id = in.readInt()
         new ID( id, pid.path )
      }

      def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
//         val (in, acc) = system.access( id.id, parent.path )( this )
//         reader.read( in, acc )( this )
         sys.error( "TODO" )
      }

      def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         val out = new DataOutput()
         ser.write( value, out )
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

      final def set( v: A )( implicit tx: S#Tx ) {
         store( v )
      }

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

      final def get( implicit tx: Txn ) : A = access( id.path )

      final def access( acc: S#Acc )( implicit tx: Txn ) : A = {
//         val (in, acc1) = system.access( id.id, acc )
//         readValue( in, acc1 )
         sys.error( "TODO" )
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) {}
   }

   private final class Var[ A ]( val id: ID, protected val system: S, ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends KSys.Var[ S, A ] with SourceImpl[ A ] {

      override def toString = toString( "Var" )

      protected def writeValue( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      protected def readValue( in: DataInput, postfix: S#Acc )( implicit tx: S#Tx ) : A = {
         ser.read( in, postfix )
      }
   }

   final class System private[KSysImpl]() extends KSys[ System ] {
      type ID  = KSysImpl.ID
      type Tx  = KSysImpl.Txn
      type Acc = KSysImpl.Path

      val manifest = Predef.manifest[ System ]
      private[KSysImpl] val reactionMap : ReactionMap[ S ] = sys.error( "TODO" )

      private[KSysImpl] def newID()( implicit tx: S#Tx ) : ID = sys.error( "TODO" )
      private[KSysImpl] def newIDCnt()( implicit tx: S#Tx ) : Int = sys.error( "TODO" )

      def atomic[ A ]( fun: Txn => A ) : A = sys.error( "TODO" )
   }
}