package de.sciss
package lucre
package confluent

import stm.store.BerkeleyDB
import serial.{DataInput, DataOutput}

object RetroactiveTest extends App {
  val system    = Confluent(BerkeleyDB.tmp())
  type S        = Confluent

  //  showLog       = true
  //  showCursorLog = true

  implicit object Ser extends stm.MutableSerializer[S, Foo] {
    def readData(in: DataInput, id: S#ID)(implicit tx: S#Tx) =
      new Foo(id, tx.readIntVar(id, in), tx.readVar[String](id, in))
  }

  final class Foo(val id: S#ID, val a: S#Var[Int], val b: S#Var[String]) extends stm.Mutable.Impl[S] {
    def writeData(out: DataOutput): Unit = {
      a.write(out)
      b.write(out)
    }

    def disposeData()(implicit tx: S#Tx): Unit = {
      a.dispose()
      b.dispose()
    }

    def print(implicit tx: S#Tx): String = s"Foo(a = ${a()}, b = ${b()})"
  }

  println("\nINIT\n")

  val (access, (cursor0, cursor1)) = system.cursorRoot { implicit tx =>
    val id = tx.newID()
    new Foo(id, tx.newIntVar(id, 0), tx.newVar(id, "foo"))
  } { implicit tx => _ =>
    system.newCursor() -> system.newCursor()
  }

  println("\nMK CURSOR 2\n")

  val cursor2 = cursor1.step { implicit tx => system.newCursor() }

  println("\nIn 1 update(1)\n")

  cursor1.step { implicit tx =>
    access().b() = "bar"
  }

  println("\nIn 2 update(1)\n")

  cursor2.step { implicit tx =>
    access().b() = "baz"
  }

  println(s"(A) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().print }}")
  println(s"(A) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().print }}")

  println("\nRETRO\n")

  val retroAcc = cursor0.stepFrom(Access.root, retroactive = true) { implicit tx =>
    access().a() = 666
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

  //  println()
  //
  //  println(cursor2.step { implicit tx =>
  //    val idx = tx.inputAccess.index
  //    system.debugPrintIndex(idx)
  //  })
}