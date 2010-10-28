/*
 *  KContainerView.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
 *
 *	 This software is free software; you can redistribute it and/or
 *	 modify it under the terms of the GNU General Public License
 *	 as published by the Free Software Foundation; either
 *	 version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	 This software is distributed in the hope that it will be useful,
 *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	 General Public License for more details.
 *
 *	 You should have received a copy of the GNU General Public
 *	 License (gpl.txt) along with this software; if not, write to the Free Software
 *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.temporal.view

import java.awt.{ BorderLayout, Color, Font, Graphics, Graphics2D }
import java.awt.event.{ ActionEvent }
import java.awt.image.ImageObserver
import java.io.File
import javax.swing.{ AbstractAction, Box, JButton, JComponent, JFrame, JPanel }
import de.sciss.confluent.VersionPath
import de.sciss.temporal.{ /* AudioFileRegion, */ ContainerLike }
//import de.sciss.sonogram.SonogramPaintController

/**
 *    @version 0.11, 08-May-10 
 */
class KContainerView( c: ContainerLike, version: VersionPath )
extends JPanel /* with SonogramPaintController */ {
   kview =>
   
   private val timelineAxis = {
      val axis = new Axis( Axis.HORIZONTAL, Axis.TIMEFORMAT )
      axis.setFont( new Font( "Lucida Grande", Font.PLAIN, 9 ))
      axis
   }
   
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
      val dur = version.read { c.interval.dur.sup.sec }
      timelineAxis.space = VectorSpace.linlin( 0.0, dur, 0.0, 1.0 )
   }

   def makeWindow = {
      val f = new JFrame( "View : " + c.name + " <" + version.version + ">" )
      val cp = f.getContentPane
      cp.add( this, BorderLayout.CENTER )
      val bar = Box.createHorizontalBox
//      val ggPDF = new JButton( new AbstractAction( "PDF" ) {
//         def actionPerformed( e: ActionEvent ) {
//            val folder = new File( new File( System.getProperty( "user.home" ), "TemporalObjects" ), "export" )
//            folder.mkdirs
//            val file = IOUtil.nonExistentFileVariant( new File( folder, "kView.pdf" ), 5, null, null )
//            PDFExport.export( kview, file )
//         }
//      })
//      ggPDF.putClientProperty( "JComponent.sizeVariant", "small" )
//      ggPDF.putClientProperty( "JButton.buttonType", "bevel" )
//      ggPDF.setFocusable( false )
//      bar.add( ggPDF )
      bar.add( Box.createHorizontalGlue )
      cp.add( bar, BorderLayout.SOUTH )
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
         val g2 = g.asInstanceOf[ Graphics2D ]
         paintContainer( g2, c, 0, 0, getWidth, true, None )
      }

      private def paintContainer( g2: Graphics2D, c: ContainerLike, x: Int, y: Int, w: Int,
                                  expanded: Boolean, colrBg: Option[ Color ]) : Int = {
         var cnt = 0
         var ry = y
         version.read {
            val (start, stop) = {
               val i = c.interval
               (i.start.inf.sec, i.stop.sup.sec)
            }
            val dur = stop - start
            if( dur == 0.0 ) return y
            val hScale = w / dur
            c.foreach( r => {
//println( "#" + (cnt+1) + " - " + region )
               val i             = r.interval
               val startInf      = i.start.inf.sec
               val startSup      = i.start.sup.sec
               val stopInf       = i.stop.inf.sec
               val stopSup       = i.stop.sup.sec
               val stableStart   = startInf == startSup
               val stableStop    = stopInf == stopSup

               val rx = (hScale * startInf).toInt + x
               val rw = (hScale * stopSup).toInt + x - rx
               colrBg.foreach( colr => {
                  g2.setColor( colr )
                  g2.fillRect( x, ry, w, 18 )
               })
               g2.setColor( Color.black )
               g2.fillRect( rx, ry, rw, 17 )

               if( !stableStart ) {
                  // XXX
               }
               if( !stableStop ) {
                  // XXX
               }
               g2.setColor( Color.white )
               val clipOrig = g2.getClip
               g2.clipRect( rx, ry, rw, 17 )
               g2.drawString( r.name, rx + 4, ry + 13 )
               g2.setClip( clipOrig )

               ry += 18
               if( stableStart ) {
                  ry = r match {
//                     case ar: AudioFileRegion if( expanded ) => {
//                        colrBg.foreach( colr => {
//                           g2.setColor( colr )
//                           g2.fillRect( x, ry, w, 52 )
//                        })
//                        g2.setColor( Color.black )
//                        g2.fillRect( rx, ry, rw, 50 )
//                        val offset     = ar.offset
//                        val offsetSup  = offset.sup.sec
//                        val offsetInf  = offset.inf.sec
//                        if( offsetSup == offsetInf ) {
//                           val afe        = ar.audioFile
//                           SonogramManager.get( afe ).foreach( ov => {
//                              val sr         = afe.sampleRate
//                              val spanStart  = offsetInf * sr
//                              val spanStop   = (stopSup - startInf) * sr // inf?
//                              g2.clipRect( rx + 1, ry + 1, rw - 2, 48 )
//                              ov.paint( spanStart, spanStop, g2, rx + 1, ry + 1, rw - 2, 48, kview )
//                              g2.setClip( clipOrig )
//                           })
//                        }
//                        ry + 52
//                     }
                     case cSub: ContainerLike => {
                        g2.clipRect( rx + 2, ry + 2, rw - 4, 0xFFFF )
                        val yres = paintContainer( g2, cSub, rx + 1, ry + 1, rw - 2, false, Some( Color.lightGray )) + 2
                        g2.setClip( clipOrig )
                        g2.setColor( Color.black )
                        g2.drawRect( rx, ry, rw, yres - ry )
                        colrBg.foreach( colr => {
                           g2.setColor( colr )
                           g2.fillRect( x, ry, rx - x, yres - ry )
                           g2.fillRect( rx + rw, ry, w - (rx + rw), yres - ry )
                        })
                        yres
                     }
                     case _ => ry
                  }
               }

               cnt += 1
            })
         }
         ry
      }
   }
}