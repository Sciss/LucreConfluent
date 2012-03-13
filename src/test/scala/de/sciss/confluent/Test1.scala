package de.sciss.confluent

import impl.KSysImpl
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.stm.{MutableSerializer, Mutable}
import de.sciss.lucre.{DataInput, DataOutput}

object Test1 extends App {
   type S      = KSysImpl.System

   val dir     = File.createTempFile( "database", "db" )
   dir.delete()
   val store   = BerkeleyDB.factory( dir )
   val s       = KSysImpl( store )

   object Node {
      implicit object ser extends MutableSerializer[ S, Node ] {
         def readData( in: DataInput, _id: S#ID )( implicit tx: S#Tx ) : Node = new Node {
            val id      = _id
            val value   = tx.readIntVar( id, in )
            val next    = tx.readVar[ Option[ Node ]]( id, in )
         }
      }

      def apply( init: Int )( implicit tx: S#Tx ) : Node = new Node {
         val id      = tx.newID()
         val value   = tx.newIntVar( id, init )
         val next    = tx.newVar[ Option[ Node ]]( id, None )
      }
   }
   trait Node extends Mutable[ S ] {
      def value: S#Var[ Int ]
      def next: S#Var[ Option[ Node ]]
      protected def disposeData()( implicit tx: S#Tx ) {
         value.dispose()
         next.dispose()
      }
      protected def writeData( out: DataOutput ) {
         value.write( out )
         next.write( out )
      }
   }

   s.atomic { implicit tx =>
      val w0 = Node( 0 )
   }
}
