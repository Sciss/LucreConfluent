package de.sciss.confluent

import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.stm.Cursor
import java.awt.{BorderLayout, EventQueue}
import javax.swing.{WindowConstants, JFrame}
import de.sciss.collection.txn.view.InteractiveSkipOctreePanel
import de.sciss.collection.geom.Space.TwoDim
import de.sciss.collection.txn.{SpaceSerializers, DeterministicSkipOctree}
import de.sciss.collection.geom.{Square, Point2D}

object OctreeViewTest extends App with Runnable {
   TemporalObjects.showConfluentLog = false
   EventQueue.invokeLater( this )

   def run() {
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
//      type S               = KSys[ x ] with Cursor[ x ] forSome { type x <: KSys[ x ]}
//      type S = KSys[ Confluent.System ] with Cursor[ Confluent.System ]
      implicit val s = Confluent( store )
      build( s )
   }

   private val sz = 256

   def build[ S <: KSys[ S ] with Cursor[ S ]]( implicit cursor: S ) {
      import SpaceSerializers.{Point2DSerializer, SquareSerializer}
      implicit val pointView = (p: Point2D, t: Any) => p
      implicit val reader = DeterministicSkipOctree.serializer[ S, TwoDim, Point2D ]
      val access = cursor.root { implicit tx =>
         DeterministicSkipOctree.empty[ S, TwoDim, Point2D ](
            Square( sz, sz, sz ), skipGap = 1 )
      }
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
