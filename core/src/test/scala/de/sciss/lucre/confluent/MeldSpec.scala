package de.sciss.lucre.confluent

import scala.annotation.tailrec

/*

To run only this test:

test-only de.sciss.lucre.confluent.MeldSpec

 */
class MeldSpec extends ConfluentSpec with TestHasLinkedList {
  "A confluent.Source" should "meld correctly" in { system =>
    val types   = new Types(system)
    import types._

    val (access, cursor) = s.cursorRoot { implicit tx =>
      val w0 = Node("w0", 2)
      val w1 = Node("w1", 1)
      w0.next() = Some(w1)
      Option(w0)
    } { implicit tx =>
      _ => s.newCursor()
    }

    val path0 = cursor.step(_.inputAccess)

    val h1 = cursor.step { implicit tx =>
      val no = access()
      def reverseAndInc(node: Node): Node = {
        node.value.transform(_ + 3)
        node.next() match {
          case Some(pred) =>
            val res = reverseAndInc(pred)
            pred.next() = Some(node)
            res

          case _ => node
        }
      }
      val newHead = no.map { n =>
        val res = reverseAndInc(n)
        n.next() = None
        res
      }
      tx.newHandle(newHead)
    }

    val path1 = cursor.step(_.inputAccess)

    cursor.stepFrom(path0) { implicit tx =>
      val no    = access()
      val right = h1.meld(path1)
      @tailrec def concat(pred: Node, tail: Option[Node]): Unit =
        pred.next() match {
          case None       => pred.next() = tail
          case Some(succ) => concat(succ, tail)
        }

      no.foreach(concat(_, right))
    }

    val result = cursor.step { implicit tx =>
      val node = access()
      toList(node)
    }

    val expected = List("w0" -> 2, "w1" -> 1, "w1" -> 4, "w0" -> 5)
    assert(result === expected)
  }

  "A confluent handle" should "be accessible after meld" in { system =>
    val types   = new Types(system)
    import types._

    val (access, (cursor, forkCursor)) = s.cursorRoot { implicit tx =>
      Option.empty[Node]
    } { implicit tx =>
      _ => (s.newCursor(), s.newCursor())
    }

    cursor.step { implicit tx =>
      val w0 = Node("w0", 1234)
      access() = Some(w0)
    }

    val path1 = cursor.step { implicit tx => tx.inputAccess }

    val h = forkCursor.stepFrom(path1) { implicit tx =>
      val Some(w0) = access()
      w0.value() = 5678
      tx.newHandle(w0)
    }
    val cp = forkCursor.step { implicit tx => implicit val dtx = tx.durable; forkCursor.position }
    println(s"$h - $cp")

    val path2 = forkCursor.step(_.inputAccess)
    cursor.step { implicit tx =>
      val w0_ = h.meld(path2)
      w0_.next() = access()
      access() = Some(w0_)
    }

    val result = cursor.step { implicit tx =>
      val node = access()
      toList(node)
    }

    val expected = List("w0" -> 5678, "w0" -> 1234)
    assert(result === expected)

    val (h1, ia) = cursor.step { implicit tx =>
      println(s"iterate - inputAccess ${tx.inputAccess}")
      val Some(w0) = access()
      (tx.newHandle(w0), tx.inputAccess)
    }

    forkCursor.stepFrom(ia) { implicit tx =>
      val w0 = h1()
    }
  }
}