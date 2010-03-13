package de.sciss.temporal.gui

import java.awt.event.{ InputEvent, KeyEvent }
import javax.swing.{ WindowConstants, JFrame, KeyStroke }

class ScalaInterpreterFrame extends JFrame( "Scala Interpreter" ) {

   // ---- constructor ----
   {
      val cp = getContentPane
      val ip = new ScalaInterpreterPane {
         override protected def initialCode = Some( """
            import de.sciss.temporal._
            import de.sciss.temporal.Period._
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