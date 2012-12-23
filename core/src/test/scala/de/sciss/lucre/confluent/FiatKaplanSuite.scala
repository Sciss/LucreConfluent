package de.sciss.lucre
package confluent

import java.io.File
import stm.store.BerkeleyDB
import stm.{MutableSerializer, Mutable}
import annotation.tailrec
import org.scalatest.{FunSpec, GivenWhenThen}

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.FiatKaplanSuite
 */
class FiatKaplanSuite extends FunSpec with GivenWhenThen with TestHasLinkedList {
   // showLog = true

   type S = Confluent

   describe( "A Confluently Persistent Linked List" ) {
      val dir     = File.createTempFile( "database", "db" )
      dir.delete()
      val store   = BerkeleyDB.factory( dir )
      val _s      = Confluent( store )
      val types   = new Types( _s )

      import types._

//      def timeWarp( path: Sys#Acc )( implicit cursor: Cursor[ S ]) {
//         cursor.step( cursor.position_=( path )( _ ))
//      }

      it( "should yield the same sequences as those in Fiat/Kaplan fig. 3" ) {

         ///////////////////////////// v0 /////////////////////////////

         given( "v0 : Allocate nodes w0, w1, with x=2 and x=1, concatenate them" )
         implicit val whyOhWhy = Node.ser
         val (access, cursor) = s.cursorRoot[ Option[ Node ], Cursor[ S ]] { implicit tx =>
            val w0      = Node( "w0", 2 )
            val w1      = Node( "w1", 1 )
            w0.next.set( Some( w1 ))
            Some( w0 )
         } { implicit tx => _ => tx.newCursor() }

         val path0 = cursor.step( _.inputAccess ) // ?

         when( "the result is converted to a plain list in a new transaction" )
         val (_, res0) = cursor.step { implicit tx =>
            val node = access.get
            (tx.inputAccess, toList( node ))
         }

         val exp0 = List( "w0" -> 2, "w1" -> 1 )
         then( "is should equal " + exp0 )
         assert( res0 === exp0 )

         ///////////////////////////// v1 /////////////////////////////

         given( "v1 : Invert order of input linked list" )
         cursor.step { implicit tx =>
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
         val (v1, res1) = cursor.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp1 = List( "w1" -> 1, "w0" -> 2 )
         then( "is should equal " + exp1 )
         assert( res1 === exp1 )

         ///////////////////////////// v2 /////////////////////////////

         // --> use a variant to better verify the results: set x=3 instead
         given( "v2 : Delete first node of list, allocate new node x=3 (!), concatenate to input list" )
//         timeWarp( path0 )( cursor ) //  Confluent.Path.root
         cursor.stepFrom( path0 ) { implicit tx =>
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
         val (v2, res2) = cursor.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp2 = List( "w1" -> 1, "w2" -> 3 )
         then( "is should equal " + exp2 )
         assert( res2 === exp2 )

         ///////////////////////////// v3 /////////////////////////////

         given( "v3: Add +2 to all elements of right list. Concatenate left and right lists" )
//         timeWarp( v1 )( cursor )
         cursor.stepFrom( v1 ) { implicit tx =>
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
         val (_, res3) = cursor.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp3 = List( "w1" -> 1, "w0" -> 2, "w1" -> 3, "w2" -> 5 )
         then( "is should equal " + exp3 )
         assert( res3 === exp3 )

         ///////////////////////////// v4 /////////////////////////////

         given( "v4: Concatenate Left and Right Lists" )
         cursor.step { implicit tx =>
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
         val (_, res4) = cursor.step { implicit tx =>
            val node = access.get
            tx.inputAccess -> toList( node )
         }

         val exp4 = List( "w1" -> 1, "w0" -> 2, "w1" -> 3, "w2" -> 5, "w1" -> 1, "w2" -> 3 )
         then( "is should equal " + exp4 )
         assert( res4 === exp4 )
      }
   }
}