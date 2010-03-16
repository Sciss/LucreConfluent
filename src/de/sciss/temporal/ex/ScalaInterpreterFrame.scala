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
val r1 = Region( "Test", 0⏊00 :: 3⏊00 )
val r2 = Region( "Dep", r1.interval + 1⏊00 )
val r3 = Region( "Indep", 0⏊00 :: 3⏊00 )
r1.moveBy( 0⏊11 )

val iv0 = ConstantInterval( (0.0, 60.0) )
val r1 = new CRegion
r1.interval.set( iv0 )
r1.interval.get.value.span
val r2 = new CRegion
r2.interval.set( PlusInterval2( r1.interval, 11.1 ))
r2.interval.get.value.span

//makeCurrent( Version( List( 0 )))
val iv1 = PlusInterval2( r2.interval, 13.3 )
currentInc

r1.interval.get.value.span
r2.interval.get.value.span
r2.interval.set( iv1 )
r2.interval.get.value.span
"""
         }

         override protected def initialCode = Some( """
            import de.sciss.temporal._
            import de.sciss.temporal.ex._
            import de.sciss.temporal.ex.Region._
            import de.sciss.trees._
            import de.sciss.trees.Version._
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