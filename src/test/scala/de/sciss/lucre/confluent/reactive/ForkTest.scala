package de.sciss.lucre
package confluent
package reactive

import java.util.concurrent.Executors
import event.Bang
import concurrent.stm.Txn

object ForkTest extends App {
   val system  = ConfluentReactive.tmp()
   type S      = ConfluentReactive

   implicit val whyOhWhy = Bang.serializer[ S ]
   val (access, cursor0) = system.cursorRoot { implicit tx => Bang[ S ]} { tx => _ => tx.newCursor() }

   def bang( implicit tx: S#Tx ) = access.get

   val pool = Executors.newSingleThreadExecutor()

   def fork( i: Int )( implicit tx: S#Tx ) : Cursor[ S ] = {
      val newCursor = tx.newCursor()
      Txn.afterCommit( _ => pool.submit( new Runnable {
         def run() {
            Thread.sleep(1000)
            newCursor.step { implicit tx =>
               bang.react { _ => println( "Bang in Fork " + i + "!" )}
            }
         }
      }))( tx.peer )
      newCursor
   }

   val cursors = cursor0.step { implicit tx =>
      for( i <- 1 to 3 ) yield fork( i )
   }

   def debug() {
      println( "Debug" )
   }

   cursors.zipWithIndex.foreach { case (cursor, j) =>
      cursor.step { implicit tx =>
         if( j == 2 ) debug()
         println( "Banging " + (j+1) )
         bang.apply()
      }
   }

   println( "Aquí" )
}