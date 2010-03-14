package de.sciss.temporal.ex

import java.awt.{ Color, Font, Graphics, Graphics2D }
import java.awt.geom.{ Rectangle2D }
import javax.swing.{ JComponent }
import de.sciss.trees.{ LongManager, Interval => TInterval, ManagedLong, Rect, RTree, Shaped }
import de.sciss.temporal.{ IntervalLike, SampleRate }
import scala.math._

class RegionViewPane( implicit sr: SampleRate, mgr: RegionManager ) extends JComponent {
   import RegionManager._

   implicit private def numberView( n: Long ) = new ManagedLong( n )
   implicit private val numberManager = LongManager
   private type LongRect   = Rect[ Long ]
   private type LongInterval = TInterval[ Long ]

   private val tree = new RTree[ Long, StoredRegion ]( 2 )
   private val colrIndet = new Color( 0xFF, 0x7F, 0x00 )

   private val regionListener = (msg: AnyRef) => msg match {
      case RegionAdded( r ) => addRegion( r )
      case RegionRemoved( r ) => removeRegion( r )
      case RegionReplaced( oldR, newR ) => { removeRegion( oldR ); addRegion( newR )}
   }

   // ---- constructor ----
   {
      setFont( new Font( "Helvetica", Font.PLAIN, 12 ))
      mgr.addListener( regionListener )
   }

   override def paintComponent( g: Graphics ) {
      val g2      = g.asInstanceOf[ Graphics2D ]
      val bounds  = tree.getRoot.bounds
      val hoff    = -min( bounds.low( 0 ), bounds.low( 1 ))
      val hscale  = getWidth.toDouble / (max( bounds.high( 0 ), bounds.high( 1 )) + hoff)
//println( "bounds = " + bounds )

      var y  = 0
      val rect = new Rectangle2D.Double()
      val clipOrig = g2.getClip
      tree.findOverlapping( bounds, sreg => {
//println( "visited " + sreg.r + sreg.shape )
         g2.setColor( colrIndet )
         val x1 = (sreg.shape.low( 0 ) + hoff) * hscale
         rect.setRect( x1, y, (sreg.shape.high( 1 ) - 1 + hoff) * hscale - x1, 56 )
         g2.fill( rect )
         g2.clip( rect )
         g2.setColor( Color.black )
         val x2 = (sreg.shape.high( 0 ) - 1 + hoff) * hscale
         rect.setRect( x1, y, (sreg.shape.low( 1 ) + hoff) * hscale - x2, 56 )
         g2.fill( rect )
         g2.setColor( Color.white )
         g2.drawString( sreg.r.name, x1.toFloat + 4, y + 12 )
         g2.setClip( clipOrig )

         y += 60
      })
   }

   private def addRegion( r: Region ) {
println( "view addRegion : " + r )
      tree.insert( StoredRegion( r ))
      repaint()
   }

   private def removeRegion( r: Region ) {
println( "view removeRegion : " + r )
      tree.remove( StoredRegion( r ))
      repaint()
   }

   private def intervalToRect( iv: IntervalLike ) = {
      val r = sr.rate
      // WARNING: the interval must not be empty, hence we add 1 to the interval high ends
      // ; make sure to subtract that again upon visiting
      val xiv = new LongInterval( (iv.start.inf.sec * r).toLong, (iv.start.sup.sec * r).toLong + 1 )
      val yiv = new LongInterval( (iv.stop.inf.sec * r).toLong, (iv.stop.sup.sec * r).toLong + 1 )
      new LongRect( Vector( xiv, yiv ))
   }

   private case class StoredRegion( val r: Region ) extends Shaped[ Long ] {
      val shape = intervalToRect( r.interval )
   }
}