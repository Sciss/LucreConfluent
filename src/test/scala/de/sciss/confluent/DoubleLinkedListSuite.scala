package de.sciss.confluent

import impl.KSysImpl
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.{DataInput, DataOutput}
import de.sciss.lucre.stm.{MutableSerializer, Mutable}
import annotation.tailrec
import org.scalatest.{FunSpec, GivenWhenThen}

/**
 * To run only this test:
 * test-only de.sciss.confluent.DoubleLinkedListSuite
 */
class  DoubleLinkedListSuite extends FunSpec with GivenWhenThen {
   describe( "A Confluently Persistent Double Linked List" ) {
      val dir     = File.createTempFile( "database", "db" )
      dir.delete()
      val store   = BerkeleyDB.factory( dir )
      val _s      = KSysImpl( store )
      val types   = new Types( _s )

      import types._

      def timeWarp( path: Sys#Acc ) {
         val s1 = s.asInstanceOf[ KSysImpl.System ]   // XXX ugly
         s1.step( s1.position_=( path )( _ ))
      }

      it( "should be possible to navigate forward and backward and do updates" ) {

         ///////////////////////////// v0 /////////////////////////////

         given( "v0 : Allocate node w0, with x = 1" )
         implicit val whyOhWhy = Node.ser
         val access = s.root[ Option[ Node ]] { implicit tx =>
            val w0 = Node( "w0", 1 )
            Some( w0 )
         }

         ///////////////////////////// v1 /////////////////////////////

         given( "v1 : Append a new node w1 with x = 2" )
         s.step { implicit tx =>
            val head    = access.get
            val newLast = Some( Node( "w1", 2 ))
            @tailrec def step( last: Node ) {
               last.next.get match {
                  case None =>
                     last.next.set( newLast )
                  case Some( n1 ) => step( n1 )
               }
            }
            head match {
               case Some( n ) => step( n )
               case None => access.set( newLast )
            }
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (v1, res1) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp1 = List( "w0" -> 1, "w1" -> 2 )
         then( "is should equal " + exp1 )
         assert( res1 === exp1 )

         ///////////////////////////// v2 /////////////////////////////

         given( "v2 : Increment all nodes by 2" )
//         timeWarp( KSysImpl.Path.root )
         s.step { implicit tx =>
            @tailrec def step( last: Option[ Node ]) { last match {
               case None =>
               case Some( n ) =>
                  n.value.transform( _ + 2 )
                  step( n.next.get )
            }}
            step( access.get )
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (v2, res2) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp2 = List( "w0" -> 3, "w1" -> 4 )
         then( "is should equal " + exp2 )
         assert( res2 === exp2 )
      }
   }

   class Types[ S <: KSys[ S ]]( val s: S ) {
      type Sys = S

      object Node {
         implicit object ser extends MutableSerializer[ S, Node ] {
            def readData( in: DataInput, _id: S#ID )( implicit tx: S#Tx ) : Node = new Node {
               val id      = _id
               val name    = in.readString()
               val value   = tx.readIntVar( id, in )
               val prev    = tx.readVar[ Option[ Node ]]( id, in )
               val next    = tx.readVar[ Option[ Node ]]( id, in )
            }
         }

         def apply( _name: String, init: Int )( implicit tx: S#Tx ) : Node = new Node {
            val id      = tx.newID()
            val name    = _name
            val value   = tx.newIntVar( id, init )
            val prev    = tx.newVar[ Option[ Node ]]( id, None )
            val next    = tx.newVar[ Option[ Node ]]( id, None )
         }
      }
      trait Node extends Mutable[ S ] {
         def name: String
         def value: S#Var[ Int ]
         def prev: S#Var[ Option[ Node ]]
         def next: S#Var[ Option[ Node ]]

         protected def disposeData()( implicit tx: S#Tx ) {
            value.dispose()
            prev.dispose()
            next.dispose()
         }

         protected def writeData( out: DataOutput ) {
            out.writeString( name )
            value.write( out )
            prev.write( out )
            next.write( out )
         }

         override def toString = "Node(" + name + ", " + id + ")"
      }

      def toList( next: Option[ Node ])( implicit tx: S#Tx ) : List[ (String, Int) ] = next match {
         case Some( n ) => (n.name, n.value.get) :: toList( n.next.get )
         case _ => Nil
      }
   }
}