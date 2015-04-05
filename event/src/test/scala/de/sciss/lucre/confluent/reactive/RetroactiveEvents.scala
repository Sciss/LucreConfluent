package de.sciss
package lucre
package confluent
package reactive

import stm.store.BerkeleyDB
import lucre.{event => evt}
import serial.{DataInput, DataOutput}

object RetroactiveEvents extends App {
  def partial = false

  val system  = ConfluentReactive(BerkeleyDB.tmp())
  type S      = ConfluentReactive

  implicit object Ser extends evt.NodeSerializer[S, Foo] {
    def read(in: DataInput, access: S#Acc, targets: evt.Targets[S])(implicit tx: S#Tx) =
      new Foo(targets, tx.readIntVar(targets.id, in), tx.readVar[String](targets.id, in))
  }

  final class Foo(val targets: evt.Targets[S], _a: S#Var[Int], _b: S#Var[String])
    extends evt.impl.SingleGenerator[S, Either[Int, String], Foo] {

    def reader = Ser

    def a(implicit tx: S#Tx): Int = _a()
    def a_=(value: Int)(implicit tx: S#Tx): Unit = {
      val old = _a()
      if (old != value) {
        _a() = value
        fire(Left(value))
      }
    }

    def b(implicit tx: S#Tx): String = _b()
    def b_=(value: String)(implicit tx: S#Tx): Unit = {
      val old = _b()
      if (old != value) {
        _b() = value
        fire(Right(value))
      }
    }

    def writeData(out: DataOutput): Unit = {
      _a.write(out)
      _b.write(out)
    }

    def disposeData()(implicit tx: S#Tx): Unit = {
      _a.dispose()
      _b.dispose()
    }

    def print(implicit tx: S#Tx): String = s"Foo(a = ${_a()}, b = ${_b()})"
  }

  val (access, Seq(cursor1, cursor2, cursor3)) = system.cursorRoot { implicit tx =>
    val tgt = if (partial) evt.Targets.partial[S] else evt.Targets[S]
    val id  = tgt.id
    new Foo(tgt, tx.newIntVar(id, 0), tx.newVar(id, "foo"))
  } { implicit tx => _ =>
    Seq.fill(3)(system.newCursor())
  }

  cursor1.step { implicit tx =>
    access().b_=("bar")
  }

  cursor2.step { implicit tx =>
    access().b_=("baz")
  }

  cursor1.step { implicit tx =>
    println(s"Registering observer 1 in ${tx.inputAccess}")
    access().react { implicit tx =>
      i => println(s"Observer 1: $i")
    }
  }

  cursor2.step { implicit tx =>
    println(s"Registering observer 2 in ${tx.inputAccess}")
    access().react { implicit tx =>
      i => println(s"Observer 2: $i")
    }
  }

  val retroAcc = cursor3.stepFrom(Access.root, retroactive = true) { implicit tx =>
    access().a_=(666)
    tx.inputAccess
  }
  println(s"Retro input access was $retroAcc")

  println(s"(B) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().print }}")
  println(s"(B) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().print }}")

  cursor2.step { implicit tx =>
    val id = tx.newID()
    tx.newIntVar(id, 666)   // enforce write version
  }

  println(s"(C) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().print }}")
  println(s"(C) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().print }}")

  cursor1.step { implicit tx =>
    access().a_=(3)
  }

  cursor2.step { implicit tx =>
    access().a_=(4)
  }

  println(s"(A) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().print }}")
  println(s"(A) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().print }}")
}