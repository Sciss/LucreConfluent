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

   def toList( next: Option[ Node ])( implicit tx: S#Tx ) : List[ Int ] = next match {
      case Some( n ) => n.value.get :: toList( n.next.get )
      case _ => Nil
   }

   // v0 : "Allocate nodes w0, w1, with x=2 and x=1, concatenate them"

   val access = s.root[ Option[ Node ]] { implicit tx =>
      val w0      = Node( 2 )
      val w1      = Node( 1 )
      w0.next.set( Some( w1 ))
      Some( w0 )
   }

   val res0 = s.atomic { implicit tx => toList( access.get )}
   println( "list after writing v0: " + res0 )
   println()

   // v1 : "Invert order of input linked list"

   s.atomic { implicit tx =>
      access.transform { no =>
         def reverse( node: Node ) : Node = node.next.get match {
            case Some( pred ) =>
               val res = reverse( pred )
               pred.next.set( Some( node ))
               res

            case _ => node
         }
         no.map( reverse )
      }
   }

   val res1 = s.atomic { implicit tx => toList( access.get )}
   println( "list after writing v1: " + res1 )
   println()

   println( "Done." )
}
