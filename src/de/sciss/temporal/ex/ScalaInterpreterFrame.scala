package de.sciss.temporal.ex

import java.awt.event.{ InputEvent, KeyEvent }
import javax.swing.{ WindowConstants, JFrame, KeyStroke }
import scala.tools.nsc.{ Interpreter }

class ScalaInterpreterFrame( implicit mgr: RegionManager )
extends JFrame( "Scala Interpreter" ) {

   // ---- constructor ----
   {
      val cp = getContentPane
      val ip = new ScalaInterpreterPane {
         override protected def createInitialBindings( in: Interpreter ) {
            in.bind( "regionMgr", mgr.getClass.getName, mgr )
         }

         override protected def initialText = {
            super.initialText + """
val r1 = Region( "Test", 0⏊00 :: 3⏊00 )"""
         }

         override protected def initialCode = Some( """
            import de.sciss.temporal._
            import de.sciss.temporal.Period._
            import de.sciss.temporal.ex._
            import de.sciss.temporal.ex.Region._
            implicit val rm = regionMgr
         """ )

         override protected lazy val customKeyProcessAction = Some( (e: KeyEvent) => {
//            println( "GOT " + e )

//            if( /*(e.getID == KeyEvent.KEY_TYPED) &&*/ (e.getKeyCode == KeyEvent.VK_P) &&
//               ((e.getModifiers & InputEvent.ALT_MASK) != 0) ) {}
            e.getKeyChar match {
               case 'π' => e.setKeyChar( '⏊' )
               case '⁄' => e.setKeyChar( '⋯' )
               case _ =>
            }
            e
         })
      }
      cp.add( ip )
      setSize( 400, 400 )
      setLocationRelativeTo( null )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      setVisible( true )
   }
}