package de.sciss.confluent

import impl.KSysImpl
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.{DataInput, DataOutput}
import de.sciss.lucre.stm.{MutableSerializer, Mutable}

object Test1 extends App {
   val dir     = File.createTempFile( "database", "db" )
   dir.delete()
   val store   = BerkeleyDB.factory( dir )
   val s       = KSysImpl( store )
   new Test1( s )
}
class Test1[ S <: KSys[ S ]]( s: S ) {
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

      override def toString = "Node" + id
   }

//   implicit def option[ Tx, Acc, A ]( implicit peer: TxnSerializer[ Tx, Acc, A ]) : TxnSerializer[ Tx, Acc, Option[ A ]] =
//      sys.error( "TODO" )

//   implicit def varSer[ S <: Sys[ S ], A ]( implicit valueSerializer: TxnSerializer[ S#Tx, S#Acc, A ]) : TxnSerializer[ S#Tx, S#Acc, S#Var[ A ]] =
//      new TxnSerializer[ S#Tx, S#Acc, S#Var[ A ]] {
//         def write( v: S#Var[ A ], out: DataOutput ) { v.write( out )}
//         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : S#Var[ A ] = sys.error( "TODO" )
//      }

   val access = s.atomic { implicit tx =>
//      implicit val fuckYou = varSer[ S, Option[ Node ]]
      val _access = s.root( Option.empty[ Node ])
      val w0      = Node( 0 )
      _access.set( Some( w0 ))
      _access
   }

   val found = s.atomic { implicit tx =>
      val node = access.get
      node.map( n => (n.value.get, n.next.get))
   }

   // v1 : "Invert order of input linked list"

   println( "in v1, we found: " + found )
   println( "Done." )
}
