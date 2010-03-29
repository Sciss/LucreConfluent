/**
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
import de.sciss.gui.VectorSpace
import de.sciss.confluent.VersionPath
import de.sciss.temporal.{AudioRegion, ContainerLike}
import de.sciss.sonogram.SonogramPaintController
import de.sciss.io.IOUtil

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
      val f = new JFrame( "View : " + c.name + " <" + version.version + ">" )
      val cp = f.getContentPane
      cp.add( this, BorderLayout.CENTER )
      val bar = Box.createHorizontalBox
      val ggPDF = new JButton( new AbstractAction( "PDF" ) {
         def actionPerformed( e: ActionEvent ) {
            val folder = new File( new File( System.getProperty( "user.home" ), "TemporalObjects" ), "export" )
            folder.mkdirs
            val file = IOUtil.nonExistentFileVariant( new File( folder, "kView.pdf" ), 5, null, ".pdf" )
            PDFExport.export( kview, file )
         }
      })
      ggPDF.putClientProperty( "JComponent.sizeVariant", "small" )
      ggPDF.putClientProperty( "JButton.buttonType", "bevel" )
      ggPDF.setFocusable( false )
      bar.add( ggPDF )
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
               g2.fillRect( x, y + 18, w, 50 )
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
                           g2.clipRect( x + 1, y + 19, w - 2, 48 )
                           ov.paint( spanStart, spanStop, g2, x + 1, y + 19, w - 2, 48, kview )
                           g2.setClip( clipOrig )
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