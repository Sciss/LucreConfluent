package de.sciss.lucre
package confluent

import stm.store.BerkeleyDB
import annotation.tailrec
import org.scalatest.{FunSpec, GivenWhenThen}

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.FiatKaplanSuite
 */
class FiatKaplanSuite extends FunSpec with GivenWhenThen with TestHasLinkedList {

  type S = Confluent

  describe("A Confluently Persistent Linked List") {
    val store   = BerkeleyDB.tmp()
    val _s      = Confluent(store)
    val types   = new Types(_s)

    import types._

    it("should yield the same sequences as those in Fiat/Kaplan fig. 3") {

      ///////////////////////////// v0 /////////////////////////////

      Given("v0 : Allocate nodes w0, w1, with x=2 and x=1, concatenate them")
      val (access, cursor) = s.cursorRoot { implicit tx =>
        val w0 = Node("w0", 2)
        val w1 = Node("w1", 1)
        w0.next() = Some(w1)
        Option(w0)
      } { implicit tx =>
        _ => s.newCursor()
      }

      val path0 = cursor.step(_.inputAccess) // ?

      When("the result is converted to a plain list in a new transaction")
      val (_, res0) = cursor.step { implicit tx =>
        val node = access()
        (tx.inputAccess, toList(node))
      }

      val exp0 = List("w0" -> 2, "w1" -> 1)
      Then("is should equal " + exp0)
      assert(res0 === exp0)

      ///////////////////////////// v1 /////////////////////////////

      Given("v1 : Invert order of input linked list")
      cursor.step { implicit tx =>
        access.transform { no =>
          // urrgh, this got pretty ugly. but well, it does its job...
          def reverse(node: Node): Node = node.next() match {
            case Some(pred) =>
              val res = reverse(pred)
              pred.next() = Some(node)
              res

            case _ => node
          }
          val newHead = no.map { n =>
            val res = reverse(n)
            n.next() = None
            res
          }
          newHead
        }
      }

      When("the result is converted to a plain list in a new transaction")
      val (v1, res1) = cursor.step { implicit tx =>
        val node = access()
        tx.inputAccess -> toList(node)
      }

      val exp1 = List("w1" -> 1, "w0" -> 2)
      Then("is should equal " + exp1)
      assert(res1 === exp1)

      ///////////////////////////// v2 /////////////////////////////

      // --> use a variant to better verify the results: set x=3 instead
      Given("v2 : Delete first node of list, allocate new node x=3 (!), concatenate to input list")
      cursor.stepFrom(path0) { implicit tx =>
        access.transform {
          case Some(n) =>
            val res = n.next()
            @tailrec def step(last: Node): Unit =
              last.next() match {
                case None     => last.next() = Some(Node("w2", 3))
                case Some(n1) => step(n1)
              }

            step(n)
            res

          case none => none
        }
      }

      When("the result is converted to a plain list in a new transaction")
      val (v2, res2) = cursor.step { implicit tx =>
        val node = access()
        tx.inputAccess -> toList(node)
      }

      val exp2 = List("w1" -> 1, "w2" -> 3)
      Then("is should equal " + exp2)
      assert(res2 === exp2)

      ///////////////////////////// v3 /////////////////////////////

      // --> use a variant of adding +3 instead of +2 to better distinguish the results
      Given("v3: Add +3 to all elements of right list. Concatenate left and right lists")
      cursor.stepFrom(v1) { implicit tx =>
        val right = access.meld(v2)
        @tailrec def concat(pred: Node, tail: Option[Node]): Unit =
          pred.next() match {
            case None       => pred.next() = tail
            case Some(succ) => concat(succ, tail)
          }

        @tailrec def inc(pred: Option[Node], amount: Int): Unit =
          pred match {
            case None =>
            case Some(n) =>
              n.value.transform(_ + amount)
              inc(n.next(), amount)
          }

        inc(right, 3)
        access().foreach(concat(_, right))
      }

      When("the result is converted to a plain list in a new transaction")
      val (_, res3) = cursor.step { implicit tx =>
        val node = access()
        tx.inputAccess -> toList(node)
      }

      val exp3 = List("w1" -> 1, "w0" -> 2, "w1" -> 4, "w2" -> 6)
      Then("is should equal " + exp3)
      assert(res3 === exp3)

      ///////////////////////////// v4 /////////////////////////////

      Given("v4: Concatenate Left and Right Lists")
      cursor.step { implicit tx =>
        val right = access.meld(v2)
        @tailrec def concat(pred: Node, tail: Option[Node]): Unit =
          pred.next() match {
            case None       => pred.next() = tail
            case Some(succ) => concat(succ, tail)
          }

        access().foreach(concat(_, right))
      }

      When("the result is converted to a plain list in a new transaction")
        val (_, res4) = cursor.step { implicit tx =>
          val node = access()

          def loop(opt: Option[Node]): List[S#ID] = opt match {
            case None => Nil
            case Some(n) => n.id :: loop(n.next())
          }

          info(s"The node succession is ${loop(node).mkString}")

          tx.inputAccess -> toList(node)
        }

        val exp4 = List("w1" -> 1, "w0" -> 2, "w1" -> 4, "w2" -> 6, "w1" -> 1, "w2" -> 3)
        Then("is should equal " + exp4)
        assert(res4 === exp4)
      }
   }
}