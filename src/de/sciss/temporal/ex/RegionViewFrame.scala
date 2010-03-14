package de.sciss.temporal.ex

import java.awt.{ BorderLayout }
import javax.swing.{ JFrame, WindowConstants }
import de.sciss.temporal.{ SampleRate }

class RegionViewFrame( implicit sr: SampleRate, mgr: RegionManager )
extends JFrame( "Region View" ) {
   val rvp = new RegionViewPane

   // ---- constructor ----
   {
      val cp = getContentPane
      cp.add( rvp, BorderLayout.CENTER )
      setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
      setSize( 400, 400 )
      setVisible( true )
   }
}
