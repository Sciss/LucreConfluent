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

  ////////////////////////////////////////////////////////////////////////////////////////////////

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
    // val cp = forkCursor.step { implicit tx => implicit val dtx = tx.durable; forkCursor.position }
    // println(s"$h - $cp")

    val path2 = forkCursor.step(_.inputAccess)
    val h0 = cursor.step { implicit tx =>
      val w0_ = h.meld(path2)
      w0_.next() = access()
      access() = Some(w0_)
      tx.newHandle(w0_)
    }

    val result = cursor.step { implicit tx =>
      val node = access()
      h0()
      toList(node)
    }

    val expected = List("w0" -> 5678, "w0" -> 1234)
    assert(result === expected)

    val (h1, ia) = cursor.step { implicit tx =>
      // println(s"iterate - inputAccess ${tx.inputAccess}")
      val Some(w0) = access()
      (tx.newHandle(w0), tx.inputAccess)
    }

    forkCursor.stepFrom(ia) { implicit tx =>
      h1()  // this failed before
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  ignore /* "A confluent system" */ should "allow multiple melds of mutable objects" in { system =>
    val types   = new Types(system)
    import types._

    val (access, (cursor, forkCursor)) = s.cursorRoot { implicit tx =>
      List.empty[Node]
    } { implicit tx =>
      _ => (s.newCursor(), s.newCursor())
    }

    cursor.step { implicit tx =>
      val w0 = Node("a", 1)
      val w1 = Node("b", 2)
      val w2 = Node("c", 3)
      val w3 = Node("d", 4)
      val w4 = Node("e", 5)
      val w5 = Node("f", 6)

      w0.next() = Some(w1)
      w1.next() = Some(w2)
      w3.next() = Some(w4)
      w4.next() = Some(w5)

      access() = List(w0, w3)
    }

    def iterate(i1: Int, j1: Int, split1: Int, i2: Int, j2: Int, split2: Int): Unit = {
      val path1 = cursor.step { implicit tx => tx.inputAccess }

      def crossover(i: Int, j: Int, split: Int): (Source[S, List[Node]], S#Acc) = {
        val h = forkCursor.stepFrom(path1) { implicit tx =>
          val acc = access()
          val a = acc(i)
          val b = acc(j)

          // @tailrec def skip(n: Node, rem: Int): Node = if (rem == 0) n else skip(n.next().get, rem - 1)

          @tailrec def skip(rem: Int, next: S#Var[Option[Node]]): S#Var[Option[Node]] =
            if (rem == 0) next else {
              val nn = next().get
              skip(rem - 1, nn.next)
            }


          val c = skip(split - 1, a.next)
          val d = skip(split - 1, b.next)
          val cv = c()
          val dv = d()
          c() = dv
          d() = cv
          tx.newHandle(List(a, b))
        }
        val path2 = forkCursor.step { implicit tx => tx.inputAccess }

        (h, path2)
      }

      val c = List(crossover(i1, j1, split1), crossover(i2, j2, split2))

      cursor.step { implicit tx =>
        val acc = c.flatMap { case (src, path) =>
          src.meld(path)
        }
        access() = acc
      }
    }

    // [ a b c ], [ d e f ]
    iterate(i1 = 0, j1 = 1, split1 = 1, i2 = 0, j2 = 1, split2 = 2)
    // [ a e f ], [ d b c ], [ a b f ], [ d e c ]
    val res1 = cursor.step { implicit tx =>
      access().map(a => toList(Some(a)))
    }
    val exp1 = List(
      List(("a",1), ("e",5), ("f",6)),
      List(("d",4), ("b",2), ("c",3)),
      List(("a",1), ("b",2), ("f",6)),
      List(("d",4), ("e",5), ("c",3)))
    assert(res1 === exp1)

    iterate(i1 = 2, j1 = 0, split1 = 1, i2 = 2, j2 = 0, split2 = 1)
    // [ a b f ], [ a e f ], [ a b f ], [ a e f ]
    val res2 = cursor.step { implicit tx =>
      access().map(a => toList(Some(a)))
    }
    val exp2 = List(
      List(("a",1), ("b",2), ("f",6)),
      List(("a",1), ("e",5), ("f",6)),
      List(("a",1), ("b",2), ("f",6)),
      List(("a",1), ("e",5), ("f",6)))
    assert(res2 === exp2)
  }
}
