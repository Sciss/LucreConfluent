///*
// *  ReactionTest2.scala
// *  (LucreConfluent)
// *
// *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
// *
// *	 This software is free software; you can redistribute it and/or
// *	 modify it under the terms of the GNU General Public License
// *	 as published by the Free Software Foundation; either
// *	 version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	 This software is distributed in the hope that it will be useful,
// *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	 General Public License for more details.
// *
// *  You should have received a copy of the GNU General Public
// *  License (gpl.txt) along with this software; if not, write to the Free Software
// *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
// *
// *
// *	 For further information, please contact Hanns Holger Rutz at
// *	 contact@sciss.de
// */
//
//package de.sciss.confluent
//
//import java.awt.event.{WindowAdapter, WindowEvent, ActionListener, ActionEvent}
//import java.awt.{BorderLayout, Color, Dimension, Graphics2D, Graphics, GridLayout, EventQueue}
//import javax.swing.{AbstractAction, JButton, Box, JComponent, JTextField, BorderFactory, JLabel, GroupLayout, JPanel, WindowConstants, JFrame}
//import collection.mutable.Buffer
//import de.sciss.lucre.expr.{Spans, Longs, Strings, Span}
//import collection.immutable.{IndexedSeq => Vec}
//
//import concurrent.stm.InTxn
//import de.sciss.lucre.{DataInput, DataOutput, expr, event}
//import de.sciss.lucre.stm.{Serializer, Cursor, Sys}
//import expr.any2stringadd
//
//object ReactionTest2 extends App {
////   private def memorySys    : (InMemory, () => Unit) = (InMemory(), () => ())
////   private def confluentSys : (Confluent, () => Unit) = (Confluent(), () => ())
////   private def databaseSys( name: String )  : (Durable, () => Unit) = {
////      val dir  = new File( new File( sys.props( "user.home" ), "Desktop" ), "reaction" )
////      val db   = BerkeleyDB.open( dir, name )
////      val s    = Durable( db )
////      (s, () => s.close())
////   }
//
//   private def tmpSys() : (Confluent, () => Unit) = {
////      val dir  = File.createTempFile( "confluent_", "db" )
////      dir.delete()
////      val db   = BerkeleyDB.factory( dir, name )
////      val s    = Confluent( db )
//      val s    = Confluent.tmp()
//      (s, () => s.close())
//   }
//
//   LucreConfluent.showConfluentLog = false
//
//   defer( args.toSeq.take( 2 ) match {
//      case Seq( "--coll" )      => collections( tmpSys() )
//      case Seq( "--expr" )      => expressions( tmpSys() )
//      case _  => println( """
//Usages:
//   --coll
//   --expr
//""" )
//   })
//
//   object System {
//      def apply[ S <: Sys[ S ] with Cursor[ S ]]() /* ( implicit tx: S#Tx ) */ : System[ S ] = {
//         val strings = Strings[ S ]
//         val longs   = Longs[ S ]
//         val spans   = Spans[ S ]( longs )
//         val regions = new Regions[ S ]( strings, longs, spans )
//         new System[ S ]( regions )
//      }
//   }
//
//   class System[ S <: Sys[ S ] with Cursor[ S ]] private( val regions: Regions[ S ]) {
//      import regions._
//      import spans.spanOps
//
//      def init()( implicit tx: S#Tx ): Unit = {
//         spans.init()
//      }
//
//      final class RegionView[ R <: RegionLike ]( access: S#Tx => R, id: String ) extends JPanel {
//         private val lay = new GroupLayout( this )
//         lay.setAutoCreateContainerGaps( true )
//         setLayout( lay )
//         setBorder( BorderFactory.createTitledBorder( BorderFactory.createEtchedBorder(), id ))
//
//         private val lbName   = new JLabel( "Name:" )
//         private val lbStart  = new JLabel( "Start:" )
//         private val lbStop   = new JLabel( "Stop:" )
//
//         private val ggName   = new JTextField( 12 )
//         private val ggStart  = new JTextField( 8 )
//         private val ggStop   = new JTextField( 8 )
//
//         lay.setHorizontalGroup( lay.createSequentialGroup()
//            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.TRAILING )
//               .addComponent( lbName )
//               .addComponent( lbStart )
//               .addComponent( lbStop )
//            )
//            .addGroup( lay.createParallelGroup()
//               .addComponent( ggName )
//               .addComponent( ggStart )
//               .addComponent( ggStop )
//            )
//         )
//
//         lay.setVerticalGroup( lay.createSequentialGroup()
//            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
//               .addComponent( lbName )
//               .addComponent( ggName )
//            )
//            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
//               .addComponent( lbStart )
//               .addComponent( ggStart )
//            )
//            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
//               .addComponent( lbStop )
//               .addComponent( ggStop )
//            )
//         )
//
//         private def stringToModel( s: String, model: (Tx, R, String) => Unit )( implicit system: S ): Unit = {
////            system.atomic { implicit tx =>
////               model( tx, s )
////            }
//            system.step { tx =>
//               model( tx, access( tx ), s )
//            }
//         }
//
//         private def longToModel( n: Long, model: (Tx, R, Long) => Unit )( implicit system: S ): Unit = {
//            system.step { implicit tx => model( tx, access( tx ), n )}
//         }
//
//         def connect()( implicit tx: Tx ): Unit = {
//            connect( access( tx ))
//         }
//
//         private def connect( r: R )( implicit tx: Tx ): Unit = {
//            r.name_#.observe { v => defer( ggName.setText(  v ))}
//            r.span_#.observe( newSpan => defer {
//               ggStart.setText( newSpan.start.toString )
//               ggStop.setText( newSpan.stop.toString )
//            })
//
//            implicit val system = tx.system
//
//            ggName.addActionListener( new ActionListener {
//               def actionPerformed( e: ActionEvent ): Unit = {
//                  stringToModel( ggName.getText, (tx, r, s) => {
//                     implicit val _tx = tx
//                     r.name = s
//                  })
//               }
//            })
//
//            ggStart.addActionListener( new ActionListener {
//               def actionPerformed( e: ActionEvent ): Unit = {
//                  longToModel( ggStart.getText.toLong, (tx, r, n) => { implicit val _tx = tx
////                     r.start = n
//                     r.span = spans.Span( n, r.span.stop_# )
//                  })
//               }
//            })
//
//            ggStop.addActionListener( new ActionListener {
//               def actionPerformed( e: ActionEvent ): Unit = {
//                  longToModel( ggStop.getText.toLong, (tx, r, n) => { implicit val _tx = tx
////                     r.stop = n
//                     r.span = spans.Span( r.span.start_#, n )
//                  })
//               }
//            })
//         }
//      }
//   }
//
//   def defer( thunk: => Unit ): Unit = EventQueue.invokeLater( new Runnable { def run() { thunk }})
//
//   def expressions[ S <: KSys[ S ] with Cursor[ S ]]( tup: (S, () => Unit) ): Unit = {
//      val (system, cleanUp) = tup
//
//      val infra = System[ S ]()
//      import infra._
//      import regions._
//      val access = system.root { _ => Vec.empty[ EventRegion ]}
//
//      system.step { implicit tx =>
//         import strings.stringOps
//         import longs.longOps
//         import spans.spanOps
//
//         infra.init()
//
//         val _r1   = EventRegion( "eins", Span(    0L, 10000L ))
//         val _r2   = EventRegion( "zwei", Span( 5000L, 12000L ))
//         val _span3 = spans.Span(
//            _r1.span_#.start_#.min( _r2.span_#.start_#) + -100L,
//            _r1.span_#.stop_#.max(  _r2.span_#.stop_#)  +  100L
////            _r1.span_#.stop_# // .max( 12000L ))
//         )
//         val _r3   = EventRegion( _r1.name_#.append( "+" ).append( _r2.name_# ), _span3 )
//         access.transform( _ ++ Vec( _r1, _r2, _r3 ))
//      }
//
////      val (infra, vs) = system.step { implicit tx =>
////
////         (_infra, _vs, _rvs.last)
////      }
//
//      val rvs = Vec.tabulate( 3 ) { i => (tx: S#Tx) => access.get( tx )( i )}
//
//      val vs = rvs.zipWithIndex.map {
//         case (rv, i) => new RegionView[ EventRegion ]( rv, "Region #" + (i+1) )
//      }
//
//      import event.Change
//
//      val f    = frame( "Reaction Test", cleanUp )
//      val cp   = f.getContentPane
//
//      cp.setLayout( new GridLayout( 3, 1 ))
//
//      system.step { implicit tx =>
//         vs.foreach( _.connect() )
//         val _r3 = rvs( 2 )( tx )
////         _r3.renamed.react { case (_, EventRegion.Renamed( _, Change( _, newName ))) =>
////            println( "Renamed to '" + newName + "'" )
////         }
//         _r3.changed.react { ch => println( "Changed : " + ch )}
//      }
//
//      vs.foreach( cp.add )
//
//      showFrame( f )
//   }
//
//   case class TrackItem( /* id: Any, */ name: String, span: Span )
//
//   class TrackView extends JComponent {
//      private val items = Buffer.empty[ TrackItem ]
////      private var map = Map.empty[ Any, TrackItem ]
//      private val colrRegion = new Color( 0x00, 0x00, 0x00, 0x80 )
//
//      var start         = 0L
//      var stop          = 44100L * 20
//      var regionHeight  = 32
//
//      setPreferredSize( new Dimension( 800, 600 ))
//
//      private var cycle = 0.0f
//
//      def insert( idx: Int, r: TrackItem ): Unit = {
//         items.insert( idx, r )
////         map += ((r.id, r))
//         if( idx == items.size - 1 ) {
//            repaintTracks( r.span.start, r.span.stop, idx, idx + 1 )
//         } else {
//            repaintTracks( start, stop, idx, items.size )
//         }
//      }
//
//      def removeAt( idx: Int ): Unit = {
//         val it = items.remove( idx )
////         map -= it.id
//         if( idx == items.size ) {
//            repaintTracks( it.span.start, it.span.stop, idx, idx + 1 )
//         } else {
//            repaintTracks( start, stop, idx, items.size + 1 )
//         }
//      }
//
//      def update( idx: Int, r: TrackItem ): Unit = {
////         val old = map( r.id )
//         val old = items( idx )
////         val idx = items.indexOf( old )
//         items.update( idx, r )
////         map += ((r.id, r))
//
//         repaintTracks( math.min( r.span.start, old.span.start ), math.max( r.span.stop, old.span.stop ), idx, idx + 1 )
//      }
//
//      private def trackHeight = regionHeight + 2
//
//      private def repaintTracks( rstart: Long, rstop: Long, ystart: Int, ystop: Int ): Unit = {
//         val w          = getWidth
//         val scale      = w.toDouble / (stop - start)
//         val rx1        = (rstart * scale).toInt
//         val rx2        = (rstop * scale).toInt
//         val ry1        = ystart * trackHeight
//         val ry2        = ystop * trackHeight
//         repaint( rx1, ry1, (rx2 - rx1), (ry2 - ry1) )
//      }
//
//      override def paintComponent( g: Graphics ): Unit = {
//         val g2 = g.asInstanceOf[ Graphics2D ]
//         g2.setColor( Color.getHSBColor( cycle, 1f, 1f ))
//         cycle = (cycle + 0.1f) % 1.0f
//         val w = getWidth
//         val h = getHeight
//         g2.fillRect( 0, 0, w, h )  // show last damaged regions
//
//         val scale      = w.toDouble / (stop - start)
//         val cr         = g2.getClipBounds
//         val clipOrig   = g2.getClip
//         val fm         = g2.getFontMetrics
//
//         items.foldLeft( 0 ) { (y, it) =>
//            if( y < (cr.y + cr.height) && (y + regionHeight) > cr.y ) {
//               val x1 = (it.span.start * scale).toInt
//               val x2 = (it.span.stop  * scale).toInt
//               if( x1 < (cr.x + cr.width) && x2 > cr.x ) {
////                  g2.setColor( Color.black )
//                  g2.setColor( colrRegion )
//                  g2.fillRect( x1, y, (x2 - x1), regionHeight )
//                  g2.clipRect( x1, y, (x2 - x1), regionHeight )
//                  g2.setColor( Color.white )
//                  g2.drawString( it.name, x1 + 4, y + fm.getAscent + 2 )
//                  g2.setClip( clipOrig )
//               }
//            }
//            y + trackHeight
//         }
//      }
//   }
//
//   def button( label: String )( action: => Unit ) : JButton = {
//      val b = new JButton( new AbstractAction( label ) {
//         def actionPerformed( e: ActionEvent ): Unit = action
//      })
//      b.setFocusable( false )
//      b.putClientProperty( "JButton.buttonType", "bevel" )
//      b
//   }
//
//   def frame( label: String, cleanUp: () => Unit ) : JFrame = {
//      val f = new JFrame( label )
//      f.setResizable( false )
//      f.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
//      f.addWindowListener( new WindowAdapter {
//         override def windowClosing( e: WindowEvent ): Unit = {
//            f.dispose()
//            try {
//               cleanUp()
//            } finally {
//               sys.exit( 0 )
//            }
//         }
//      })
//      f
//   }
//
//   def showFrame( f: JFrame ): Unit = {
//      f.pack()
//      f.setLocationRelativeTo( null )
//      f.setVisible( true )
//   }
//
//   class Collections[ S <: Sys[ S ]]( val regions: Regions[ S ]) {
//      private val regionListSer = regions.RegionList.serializer
//
//      object Access {
//         def empty( implicit tx: S#Tx ) : Access = new Access {
//            val id = tx.newID()
//            val count = tx.newIntVar( id, 0 )
//            val collection = regions.RegionList.empty
//         }
//
//         implicit val serializer : Serializer[ S#Tx, S#Acc, Access ] =
//            new Serializer[ S#Tx, S#Acc, Access ] {
//               def write( v: Access, out: DataOutput ): Unit = v.write( out )
//               def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Access =
//                  Access.read( in, access )
//            }
//
//         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Access = new Access {
//            val id         = tx.readID( in, access )
//            val count      = tx.readIntVar( id, in )
//            val collection = regionListSer.read( in, access )
//         }
//      }
//      sealed trait Access {
//         def id: S#ID
//         def count : S#Var[ Int ]
//         def collection : regions.RegionList
//
//         final def write( out: DataOutput ): Unit = {
//            id.write( out )
//            count.write( out )
//            collection.write( out )
//         }
//
//         override def toString = "Collections.KEntry" + id
//      }
//
//      val rnd = TxnRandom.plain( 1L )
//
//      def newRegion( access: Access )( implicit tx: S#Tx ) : regions.EventRegion = {
//         import regions._
//         implicit val itx = tx.peer
//         val cnt = access.count
//         val c = cnt.get + 1
//         cnt.set( c )
//         val name    = "Region #" + c
//         val len     = rnd.nextInt( 10 ) + 1
//         val start   = rnd.nextInt( 21 - len )
//         val r       = EventRegion( name, Span( start * 44100L, (start + len) * 44100L ))
//         r
//      }
//   }
//
//   def collections[ S <: KSys[ S ] with Cursor[ S ]]( tup: (S, () => Unit) ): Unit = {
//      val (system, cleanUp) = tup
//
////      val id = system.atomic { implicit tx => tx.newID() }
////
////      val cnt = system.atomic { implicit tx =>
////         tx.newIntVar( id, 0 )
////      }
//
//      val tr            = new TrackView
//      val infra         = System[ S ]()
//      import infra.{ regions => _regions, _ }
//      val collections   = new Collections[ S ]( _regions )
//      import collections.{Access, newRegion, rnd, regions}
//      import regions._
//
//      def scramble( s: String )( implicit itx: InTxn ) : String = {
//         val sb = s.toBuffer
//         Seq.fill[ Char ]( s.length )( sb.remove( rnd.nextInt( sb.size ))).mkString
//      }
//
//
////      implicit val regionsSer: Serializer[ S#Tx, S#Acc, Regions[ S ]#RegionList ] = regions.RegionList.serializer
////      implicit val accessSer = CollectionsAccess.serializer[ S ]
//      val access = system.root { implicit tx => Access.empty }
//
//      system.step { implicit tx =>
//         val coll = access.get.collection
//         coll.changed.reactTx[ RegionList.Update ] { implicit tx => {
//            case RegionList.Added( _, idx, r ) =>
//               val name    = r.name.value
//               val span    = r.span.value
//               defer {
//                  tr.insert( idx, new TrackItem( /* r.id, */ name, span ))
//               }
//
//            case RegionList.Removed( _, idx, r ) =>
//               defer { tr.removeAt( idx )}
//
//            case RegionList.Element( _, changes ) =>
//               val viewChanges = changes.map { c =>
//                  val coll = access.get.collection
//                  val r = c.r
//                  val ti = new TrackItem( /* r.id, */ r.name.value, r.span.value )
//                  val idx = coll.indexOf( r )
//                  (idx, ti)
//               }
//
//               defer {
//                  viewChanges.foreach { case (idx, ti) => tr.update( idx, ti )}
//               }
//         }}
//      }
//
//      val f    = frame( "Reaction Test 2", cleanUp )
//      val cp   = f.getContentPane
//      val actionPane = Box.createHorizontalBox()
//      actionPane.add( button( "Add last" ) {
//         system.step { implicit tx =>
//            val coll = access.get.collection
//            coll.add( newRegion( access.get ))
//         }
//      })
//      actionPane.add( button( "Remove first" ) {
//         system.step { implicit tx =>
//            val coll = access.get.collection
//            if( coll.size > 0 ) coll.removeAt( 0 )
//         }
//      })
//      actionPane.add( button( "Random rename" ) {
//         system.step { implicit tx =>
//            implicit val itx = tx.peer
//            val coll = access.get.collection
//            if( coll.size > 0 ) {
//               val r    = coll.apply( rnd.nextInt( coll.size ))
//               r.name   = scramble( r.name.value )
//            }
//         }
//      })
//      actionPane.add( button( "Random move" ) {
//         system.step { implicit tx =>
//            implicit val itx = tx.peer
//            val coll = access.get.collection
//            if( coll.size > 0 ) {
//               val r       = coll.apply( rnd.nextInt( coll.size ))
//               val len     = (r.span.value.length / 44100L).toInt
//               val start   = rnd.nextInt( 21 - len )
//               r.span      = Span( start * 44100L, (start + len) * 44100L )
//            }
//         }
//      })
//
//      cp.add( tr, BorderLayout.CENTER )
//      cp.add( actionPane, BorderLayout.SOUTH )
//
//      showFrame( f )
//   }
//
//   def warn( message: String ): Unit = new Throwable( message ).printStackTrace()
//}