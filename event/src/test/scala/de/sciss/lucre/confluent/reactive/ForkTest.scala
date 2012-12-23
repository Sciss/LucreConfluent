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

//   val pool = Executors.newSingleThreadExecutor()
//
//   def fork( i: Int )( implicit tx: S#Tx ) : Cursor[ S ] = {
//      val newCursor = tx.newCursor()
//      Txn.afterCommit( _ => pool.submit( new Runnable {
//         def run() {
////            Thread.sleep(1000) // NOT a race condition
//            newCursor.step { implicit tx =>
//               bang.react { _ => println( "Bang in Fork " + i + "!" )}
//            }
//         }
//      }))( tx.peer )
//      newCursor
//   }

   println( "Creating cursors" )

   val cursors = cursor0.step { implicit tx =>
      for( i <- 1 to 3 ) yield tx.newCursor() // fork( i )
   }

   def debug() {
      println( "Debug" )
   }

   println( "Creating observers" )

//   de.sciss.lucre.confluent.showLog = true

   cursors.zipWithIndex.foreach { case (cursor, j) =>
      cursor.step { implicit tx =>
         println( "Registering fork " + (j+1) )
//         if( j == 1 ) debug()
         bang.react { _ => println( "Bang in Fork " + (j+1) + "!" )}
      }
   }

   println( "Firing events" )

   cursors.zipWithIndex.foreach { case (cursor, j) =>
      cursor.step { implicit tx =>
//         if( j == 2 ) debug()
         println( "Banging " + (j+1) )
         bang.apply()
      }
   }

   println( "Aquí" )
}