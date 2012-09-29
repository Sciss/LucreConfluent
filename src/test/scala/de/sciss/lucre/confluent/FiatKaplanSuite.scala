package de.sciss.lucre
package confluent

import java.io.File
import stm.impl.BerkeleyDB
import stm.{MutableSerializer, Mutable}
import annotation.tailrec
import org.scalatest.{FunSpec, GivenWhenThen}

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.FiatKaplanSuite
 */
class FiatKaplanSuite extends FunSpec with GivenWhenThen {
   // showLog = true

   describe( "A Confluently Persistent Linked List" ) {
      val dir     = File.createTempFile( "database", "db" )
      dir.delete()
      val store   = BerkeleyDB.factory( dir )
      val _s      = Confluent( store )
      val types   = new Types( _s )

      import types._

      def timeWarp( path: Sys#Acc ) {
         s.step( s.position_=( path )( _ ))
      }

      it( "should yield the same sequences as those in Fiat/Kaplan fig. 3" ) {

         ///////////////////////////// v0 /////////////////////////////

         given( "v0 : Allocate nodes w0, w1, with x=2 and x=1, concatenate them" )
         implicit val whyOhWhy = Node.ser
         val access = s.root[ Option[ Node ]] { implicit tx =>
            val w0      = Node( "w0", 2 )
            val w1      = Node( "w1", 1 )
            w0.next.set( Some( w1 ))
            Some( w0 )
         }
         val path0 = s.step( _.inputAccess ) // ?

         when( "the result is converted to a plain list in a new transaction" )
         val (_, res0) = s.step { implicit tx =>
            val node = access.get
            (tx.inputAccess, toList( node ))
         }

         val exp0 = List( "w0" -> 2, "w1" -> 1 )
         then( "is should equal " + exp0 )
         assert( res0 === exp0 )

         ///////////////////////////// v1 /////////////////////////////

         given( "v1 : Invert order of input linked list" )
         s.step { implicit tx =>
            // urrgh, this got pretty ugly. but well, it does its job...
            access.transform { no =>
               def reverse( node: Node ) : Node = node.next.get match {
                  case Some( pred ) =>
                     val res = reverse( pred )
                     pred.next.set( Some( node ))
                     res

                  case _ => node
               }
               val newHead = no.map { n =>
                  val res = reverse( n )
                  n.next.set( None )
                  res
               }
               newHead
            }
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (v1, res1) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp1 = List( "w1" -> 1, "w0" -> 2 )
         then( "is should equal " + exp1 )
         assert( res1 === exp1 )

         ///////////////////////////// v2 /////////////////////////////

         // --> use a variant to better verify the results: set x=3 instead
         given( "v2 : Delete first node of list, allocate new node x=3 (!), concatenate to input list" )
         timeWarp( path0 ) //  Confluent.Path.root
         s.step { implicit tx =>
            access.transform {
               case Some( n ) =>
                  val res = n.next.get
                  @tailrec def step( last: Node ) {
                     last.next.get match {
                        case None =>
                           last.next.set( Some( Node( "w2", 3 )))
                        case Some( n1 ) => step( n1 )
                     }
                  }
                  step( n )
                  res

               case none => none
            }
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (v2, res2) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp2 = List( "w1" -> 1, "w2" -> 3 )
         then( "is should equal " + exp2 )
         assert( res2 === exp2 )

         ///////////////////////////// v3 /////////////////////////////

         given( "v3: Add +2 to all elements of right list. Concatenate left and right lists" )
         timeWarp( v1 )
         s.step { implicit tx =>
            val right = access.meld( v2 )
            @tailrec def concat( pred: Node, tail: Option[ Node ]) {
               pred.next.get match {
                  case None => pred.next.set( tail )
                  case Some( succ ) => concat( succ, tail )
               }
            }
            @tailrec def inc( pred: Option[ Node ], amount: Int ) {
               pred match {
                  case None =>
                  case Some( n ) =>
                     n.value.transform( _ + amount )
                     inc( n.next.get, amount )
               }
            }
            inc( right, 2 )
            access.get.foreach( concat( _, right ))
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (_, res3) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp3 = List( "w1" -> 1, "w0" -> 2, "w1" -> 3, "w2" -> 5 )
         then( "is should equal " + exp3 )
         assert( res3 === exp3 )

         ///////////////////////////// v4 /////////////////////////////

         given( "v4: Concatenate Left and Right Lists" )
         s.step { implicit tx =>
            val right = access.meld( v2 )
            @tailrec def concat( pred: Node, tail: Option[ Node ]) {
               pred.next.get match {
                  case None => pred.next.set( tail )
                  case Some( succ ) => concat( succ, tail )
               }
            }
            access.get.foreach( concat( _, right ))
         }

         when( "the result is converted to a plain list in a new transaction" )
         val (_, res4) = s.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp4 = List( "w1" -> 1, "w0" -> 2, "w1" -> 3, "w2" -> 5, "w1" -> 1, "w2" -> 3 )
         then( "is should equal " + exp4 )
         assert( res4 === exp4 )
      }
   }

   class Types[ S <: Sys[ S ]]( val s: S ) {
      type Sys = S

      object Node {
         implicit object ser extends MutableSerializer[ S, Node ] {
            def readData( in: DataInput, _id: S#ID )( implicit tx: S#Tx ) : Node = new Node with Mutable.Impl[ S ] {
               val id      = _id
               val name    = in.readString()
               val value   = tx.readIntVar( id, in )
               val next    = tx.readVar[ Option[ Node ]]( id, in )
            }
         }

         def apply( _name: String, init: Int )( implicit tx: S#Tx ) : Node = new Node with Mutable.Impl[ S ] {
            val id      = tx.newID()
            val name    = _name
            val value   = tx.newIntVar( id, init )
            val next    = tx.newVar[ Option[ Node ]]( id, None )
         }
      }
      trait Node extends Mutable[ S#ID, S#Tx ] {
         def name: String
         def value: S#Var[ Int ]
         def next: S#Var[ Option[ Node ]]
         protected def disposeData()( implicit tx: S#Tx ) {
            value.dispose()
            next.dispose()
         }
         protected def writeData( out: DataOutput ) {
            out.writeString( name )
            value.write( out )
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