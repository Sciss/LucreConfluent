package de.sciss.temporal.view

import de.sciss.gui.VectorSpace
import de.sciss.confluent.VersionPath
import javax.swing.{ JComponent, JFrame, JPanel }
import de.sciss.temporal.{AudioRegion, ContainerLike}
import java.awt._
import de.sciss.sonogram.SonogramPaintController
import image.ImageObserver

class KContainerView( c: ContainerLike[ _ ], version: VersionPath )
extends JPanel with SonogramPaintController {
   kview =>
   
   private val timelineAxis = {
      val axis = new Axis( Axis.HORIZONTAL, Axis.TIMEFORMAT )
      axis.setFont( new Font( "Lucida Grande", Font.PLAIN, 9 ))
      axis
   }

   private var startSec = 0.0
   private var stopSec  = 0.0

   private val regionPane = {
      val pane = new RegionPane
      pane.setFont( new Font( "Lucida Grande", Font.PLAIN, 10 ))
      pane
   }

   // ---- constructor ----
   {
      setLayout( new BorderLayout )
      add( timelineAxis, BorderLayout.NORTH )
      add( regionPane, BorderLayout.CENTER )
      updateAxis
   }

   private def updateAxis {
      val (start, stop) = version.use {
         val i = c.interval
         (i.start.inf.sec, i.stop.sup.sec)
      }
      startSec             = start
      stopSec              = stop
      timelineAxis.space   = VectorSpace.createLinSpace( 0.0, stop - start, 0.0, 1.0, null, null, null, null )
   }

   def makeWindow = {
      val f = new JFrame( "View : " + c.name )
      f.getContentPane.add( this, BorderLayout.CENTER )
      f.setSize( 600, 300 )
      f.setLocationRelativeTo( null )
      f.setVisible( true )
      f
   }

   // ---- SonogramPaintController ----

   def adjustGain( amp: Float, pos: Double ) = amp * 8 // XXX
   def imageObserver: ImageObserver = this

   // ---- internal classes ----

   private class RegionPane extends JComponent {
      override def paintComponent( g: Graphics ) {
         var cnt = 0
         var y   = 0
         val dur = stopSec - startSec
         if( dur == 0.0 ) return
         val hScale = getWidth / dur
         val g2 = g.asInstanceOf[ Graphics2D ]
         version.use {
            c.foreach( r => {
//println( "#" + (cnt+1) + " - " + region )
               val i             = r.interval
               val startInf      = i.start.inf.sec
               val startSup      = i.start.sup.sec
               val stopInf       = i.stop.inf.sec
               val stopSup       = i.stop.sup.sec
               val stableStart   = startInf == startSup
               val stableStop    = stopInf == stopSup

               g2.setColor( Color.black )
               val x = (hScale * startInf).toInt
               val w = (hScale * stopSup).toInt - x
               g2.fillRect( x, y, w, 17 )
               g2.fillRect( x, y + 18, w, 45 )
               if( !stableStart ) {
                  // XXX
               }
               if( !stableStop ) {
                  // XXX
               }
               g2.setColor( Color.white )
               val clipOrig = g2.getClip
               g2.clipRect( x, y, w, 17 )
               g2.drawString( r.name, x + 4, y + 13 )
               g2.setClip( clipOrig )

               if( stableStart ) r match {
                  case ar: AudioRegion => {
                     val offset     = ar.offset
                     val offsetSup  = offset.sup.sec
                     val offsetInf  = offset.inf.sec
                     if( offsetSup == offsetInf ) {
                        val afe        = ar.audioFile
                        SonogramManager.get( afe ).foreach( ov => {
                           val sr         = afe.sampleRate
                           val spanStart  = offsetInf * sr
                           val spanStop   = (stopSup - startInf) * sr // inf?
                           ov.paint( spanStart, spanStop, g2, x + 1, y + 19, w - 2, 48, kview )
                        })
                     }
                  }
                  case _ =>
               }

               y += 70
               cnt += 1
            })
         }
      }
   }
}