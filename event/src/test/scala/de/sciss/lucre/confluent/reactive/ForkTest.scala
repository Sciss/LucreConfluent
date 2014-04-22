package de.sciss.lucre
package confluent
package reactive

import event.Bang
import stm.store.BerkeleyDB

object ForkTest extends App {
   val system  = ConfluentReactive( BerkeleyDB.tmp() )
   type S      = ConfluentReactive

   implicit val whyOhWhy = Bang.serializer[ S ]
   val (access, cursor0) = system.cursorRoot { implicit tx => Bang[ S ]} { implicit tx => _ => system.newCursor() }

   def bang( implicit tx: S#Tx ) = access()

   println( "Creating cursors" )

   val cursors = cursor0.step { implicit tx =>
      for( i <- 1 to 3 ) yield system.newCursor() // fork( i )
   }

   def debug(): Unit = {
      println( "Debug" )
   }

   println( "Creating observers" )

//   de.sciss.lucre.confluent.showLog = true

   cursors.zipWithIndex.foreach { case (cursor, j) =>
      cursor.step { implicit tx =>
         println( "Registering fork " + (j+1) )
//         if( j == 1 ) debug()
         bang.react { _ => _ => println( "Bang in Fork " + (j+1) + "!" )}
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