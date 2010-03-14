package de.sciss.temporal.ex

import de.sciss.temporal.{ SampleRate }

class Example {
   // ---- constructor ----
   {
      implicit val sr         = SampleRate( 44100 )
      implicit val regionMgr  = new RegionManager

      new RegionViewFrame
      new ScalaInterpreterFrame
   }
}