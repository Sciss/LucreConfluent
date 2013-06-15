package de.sciss
package lucre
package confluent
package reactive

import stm.store.BerkeleyDB
import lucre.{event => evt}
import serial.{DataInput, DataOutput}

object RetroactiveEvents extends App {
  val system  = ConfluentReactive(BerkeleyDB.tmp())
  type S      = ConfluentReactive

  implicit object Ser extends evt.NodeSerializer[S, Foo] {
    def read(in: DataInput, access: S#Acc, targets: evt.Targets[S])(implicit tx: S#Tx) =
      new Foo(targets, tx.readIntVar(targets.id, in))
  }

  final class Foo(val targets: evt.Targets[S], vr: S#Var[Int]) extends evt.impl.SingleGenerator[S, Int, Foo] {
    def reader = Ser

    def apply()(implicit tx: S#Tx): Int = vr()
    def update(value: Int)(implicit tx: S#Tx) {
      val old = vr()
      if (old != value) {
        vr() = value
        fire(value)
      }
    }

    def writeData(out: DataOutput) {
      vr.write(out)
    }

    def disposeData()(implicit tx: RetroactiveEvents.S#Tx) {
      vr.dispose()
    }
  }

  val (access, cursor1) = system.cursorRoot { implicit tx =>
    val tgt = evt.Targets[S]
    new Foo(tgt, tx.newIntVar(tgt.id, 0))
  } { implicit tx => _ =>
    system.newCursor()
  }

  val cursor2 = cursor1.step { implicit tx => system.newCursor() }

  cursor1.step { implicit tx =>
    access().update(1)
  }

  cursor2.step { implicit tx =>
    access().update(2)
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

  cursor1.step { implicit tx =>
    access().update(3)
  }

  cursor2.step { implicit tx =>
    access().update(4)
  }

  println(s"(A) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().apply() }}")
  println(s"(A) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().apply() }}")

  val retroAcc = cursor1.stepFrom(Sys.Acc.root, retroactive = true) { implicit tx =>
    access().update(5)
    tx.inputAccess
  }
  println(s"Retro input access was $retroAcc")

  println(s"(B) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().apply() }}")
  println(s"(B) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().apply() }}")

  cursor2.step { implicit tx =>
    val id = tx.newID()
    tx.newIntVar(id, 666)   // enforce write version
  }

  println(s"(C) In cursor 1: ${cursor1.step { implicit tx => tx.inputAccess -> access().apply() }}")
  println(s"(C) In cursor 2: ${cursor2.step { implicit tx => tx.inputAccess -> access().apply() }}")
}