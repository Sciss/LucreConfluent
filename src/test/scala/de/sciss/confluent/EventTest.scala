package de.sciss.confluent

import de.sciss.lucre.event.Bang

object EventTest extends App {
   val system  = Confluent.tmp()
   type S      = Confluent

   implicit val whyOhWhy = Bang.serializer[ S ]
   val access = system.root { implicit tx => Bang[ S ]}

   def bang( implicit tx: S#Tx ) = access.get

   system.step { implicit tx =>
      bang.react { _ => println( "Bang!" )}
   }

   system.step { implicit tx =>
      bang.apply()
   }

   println( "Aquí" )

//   val e2 = system.step { implicit tx => Trigger[ S, Int ]}
//
//   system.step { implicit tx =>
//      e2(  4 )  // observed
//      e2(  8 )  // observed
//      e2( 12 )  // filtered out, not observed
//   }
}