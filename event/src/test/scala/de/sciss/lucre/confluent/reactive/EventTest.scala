package de.sciss.lucre
package confluent
package reactive

import event.Bang
import stm.store.BerkeleyDB

object EventTest extends App {
  val system  = ConfluentReactive(BerkeleyDB.tmp())
  type S      = ConfluentReactive

  implicit val whyOhWhy = Bang.serializer[S]
  val (access, cursor) = system.cursorRoot {  implicit tx =>
    Bang[S]
  } { implicit tx => _ =>
    system.newCursor()
  }

  def bang(implicit tx: S#Tx) = access()

  cursor.step { implicit tx =>
    bang.react {
      _ => _ => println("Bang!")
    }
  }

  cursor.step { implicit tx =>
    bang.apply()
  }

  println("Aquí")

  //   val e2 = system.step { implicit tx => Trigger[ S, Int ]}
  //
  //   system.step { implicit tx =>
  //      e2(  4 )  // observed
  //      e2(  8 )  // observed
  //      e2( 12 )  // filtered out, not observed
  //   }
}