package de.sciss.lucre
package confluent

import java.io.File
import stm.Cursor
import stm.impl.BerkeleyDB
import data.HASkipList
import data.gui.InteractiveSkipListView
import java.awt.{BorderLayout, EventQueue}
import javax.swing.{WindowConstants, JFrame}

object SkipListViewTest extends App with Runnable {
   EventQueue.invokeLater( this )

   def run() {
      val dir  = args.headOption match {
         case Some( "--dbtmp" ) =>
            val dir  = File.createTempFile( "database", "db" )
            dir.delete()
            dir

         case Some( "--db" ) =>
            new File( new File( sys.props( "user.home" ), "Desktop" ), "skiplist_database" )

         case _ => println( "Invalid arguments: " + args.mkString( " " ))
            sys.exit( 1 )
      }
      val store            = BerkeleyDB.factory( dir )
//      type S               = KSys[ x ] with Cursor[ x ] forSome { type x <: KSys[ x ]}
//      type S = KSys[ Confluent.System ] with Cursor[ Confluent.System ]
      implicit val s = Confluent( store )
      build( s )
   }

   def build[ S <: KSys[ S ] with Cursor[ S ]]( implicit cursor: S ) {
      val fut = new InteractiveSkipListView.FutureObserver[ S ]
      implicit val ser = HASkipList.Set.serializer[ S, Int ]( fut )
      val access = cursor.root { implicit tx =>
         HASkipList.Set.empty[ S, Int ]( minGap = 1, keyObserver = fut )
      }

//      println( "We are in " + cursor.step { implicit tx => cursor.position })

      val view = new InteractiveSkipListView[ S ]( access )

      val f    = new JFrame( "SkipList" )
      val cp   = f.getContentPane
      cp.add( view, BorderLayout.CENTER )
      f.pack()
      f.setLocationRelativeTo( null )
      f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      f.setVisible( true )
   }
}
