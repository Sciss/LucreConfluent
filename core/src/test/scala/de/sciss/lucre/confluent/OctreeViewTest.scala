package de.sciss.lucre
package confluent

import java.io.File
import stm.store.BerkeleyDB
import java.awt.{BorderLayout, EventQueue}
import javax.swing.{WindowConstants, JFrame}
import data.gui.InteractiveSkipOctreePanel
import data.DeterministicSkipOctree
import geom.{IntSquare, IntPoint2D, IntSpace}
import IntSpace.TwoDim

object OctreeViewTest extends App with Runnable {
   showLog = false
   EventQueue.invokeLater( this )

   def run(): Unit = {
      val dir  = args.headOption match {
         case Some( "--dbtmp" ) =>
            val dir  = File.createTempFile( "database", "db" )
            dir.delete()
            dir

         case Some( "--db" ) =>
            new File( new File( sys.props( "user.home" ), "Desktop" ), "octree_database" )

         case _ => println( "Invalid arguments: " + args.mkString( " " ))
            sys.exit( 1 )
      }
      val store            = BerkeleyDB.factory( dir )
//      type S               = Sys[ x ] with Cursor[ x ] forSome { type x <: Sys[ x ]}
//      type S = Sys[ Confluent.System ] with Cursor[ Confluent.System ]
      implicit val s = Confluent( store )
      build( s )
   }

   private val sz = 256

  def build[S <: Sys[S]](system: S): Unit = {
      // import SpaceSerializers.{IntPoint2DSerializer, IntSquareSerializer}
      implicit val pointView = (p: IntPoint2D, t: Any) => p
      implicit val reader = DeterministicSkipOctree.serializer[ S, TwoDim, IntPoint2D ]
      val (access, cursor) = system.cursorRoot { implicit tx =>
         DeterministicSkipOctree.empty[ S, TwoDim, IntPoint2D ](
            IntSquare( sz, sz, sz ), skipGap = 1 )
      } { implicit tx => _ => system.newCursor() }
      val model = new InteractiveSkipOctreePanel.Model2D[ S ](
         cursor, access, { () => println( "(Consistency not checked)" )} /*, nTimes = 2 */
      )

      val view = new InteractiveSkipOctreePanel( model )

      val f    = new JFrame( "SkipOctree" )
      val cp   = f.getContentPane
      cp.add( view, BorderLayout.CENTER )
      f.pack()
      f.setLocationRelativeTo( null )
      f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      f.setVisible( true )
   }
}
