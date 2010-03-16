package de.sciss.temporal.ex
//package de.sciss.trees

import _root_.java.awt.{ BasicStroke, BorderLayout, Color, Dimension, Paint, Stroke }
import _root_.java.awt.event.{ ComponentAdapter, ComponentEvent, InputEvent, MouseEvent }
import _root_.javax.swing.{ JFrame, JLabel, JPanel, WindowConstants }
import _root_.javax.swing.event.{ AncestorEvent, AncestorListener }

//import _root_.de.sciss.tint.sc.{ Node, Server }

import _root_.edu.uci.ics.jung.algorithms.layout.{ BalloonLayout, DAGLayout, FRLayout,
	ISOMLayout, StaticLayout, TreeLayout }
import _root_.edu.uci.ics.jung.algorithms.layout.util.VisRunner
import _root_.edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath
import _root_.edu.uci.ics.jung.graph.{ DelegateTree, DirectedGraph, DirectedSparseGraph, ObservableGraph }
import _root_.edu.uci.ics.jung.graph.event.{ GraphEvent, GraphEventListener }
import _root_.edu.uci.ics.jung.graph.util.TreeUtils
import _root_.edu.uci.ics.jung.visualization.VisualizationViewer
import _root_.edu.uci.ics.jung.visualization.control.{ CrossoverScalingControl, DefaultModalGraphMouse,
                                                      PickingGraphMousePlugin, PluggableGraphMouse,
                                                      ScalingGraphMousePlugin, TranslatingGraphMousePlugin }
import _root_.edu.uci.ics.jung.visualization.decorators.ToStringLabeller
import _root_.edu.uci.ics.jung.visualization.layout.LayoutTransition
import _root_.edu.uci.ics.jung.visualization.renderers.Renderer
import _root_.edu.uci.ics.jung.visualization.util.Animator

import _root_.scala.collection.JavaConversions
import _root_.scala.collection.mutable.ListBuffer
import _root_.scala.math._
import _root_.org.apache.commons.collections15.Transformer

import de.sciss.trees.{ Version }


/**
 *	@author		Hanns Holger Rutz
 *	@version	0.10, 27-Nov-09
 */
class VersionGraphPanel
extends JPanel
with GraphEventListener[ Int, Long ] {
	private val graph = Version.graph
//    private val graph = new DirectedSparseGraph[ Int, Long ]()
    private val ograph = Version.ograph
	private val smartLayout = new FRLayout( graph )
//	private val forest = new DelegateTree( server.nodeMgr.graph )
//	println( "forest roots = " + TreeUtils.getRoots( server.nodeMgr.graph ))
//	private val testLayout = new TreeLayout( server.nodeMgr.graph )
//	forest.addVertex( new de.sciss.tint.sc.Group( server, 1 ))
//	private val smartLayout = new BalloonLayout( server.nodeMgr.graph ) // XXX ugly cast
//	private val smartLayout = new ISOMLayout( graph )
//	private val smartLayout = new DAGLayout( graph )
	private var vv: VisualizationViewer[ Int, Long ] = _

	// ---- constructor ----
	{
        smartLayout.setSize( new Dimension( 400, 400 ))
//		val staticLayout = new StaticLayout( server.nodeMgr.graph, smartLayout )

//      vv = new VisualizationViewer( staticLayout, new Dimension( 480, 440 ))
        vv = new VisualizationViewer( smartLayout, new Dimension( 480, 440 ))
//        vv.setGraphMouse( new DefaultModalGraphMouse() )
        val gm = new PluggableGraphMouse()
        gm.add( new TranslatingGraphMousePlugin( InputEvent.BUTTON1_MASK | InputEvent.META_MASK ))
        gm.add( new ScalingGraphMousePlugin( new CrossoverScalingControl(), 0, 1.1f, 0.9f ))
        gm.add( new PickingGraphMousePlugin( InputEvent.BUTTON1_MASK, InputEvent.BUTTON1_MASK | InputEvent.SHIFT_MASK ) {
            override def mousePressed( e: MouseEvent ) {
                super.mousePressed( e )
                if( e.getClickCount() == 2 ) {
//                    println( "DOUBLE TROUBLE" )
                    val picked = vv.getPickedVertexState().getPicked
                    if( picked.size() == 1 ) {
                        val v = picked.iterator().next()
                        if( v != Version.current.path.last ) {
                            val path = if( v == 0 ) {
                                List( v )
                            } else {
                                val edges = new DijkstraShortestPath( graph ).getPath( 0, v )
                                val b = new ListBuffer[ Int ]()
                                JavaConversions.asBuffer( edges ).foreach( e => {
                                    b += ((e >> 32) & 0xFFFFFFFF).toInt
                                })
                                b += v
                                b.toList
                            }
                            Version.makeCurrent( Version( path ))
                            vv.repaint()
                        }
                    }
                }
            }
        })
        vv.setGraphMouse( gm )
        vv.getRenderer().getVertexLabelRenderer().setPosition( Renderer.VertexLabel.Position.CNTR )
        vv.getRenderContext().setVertexLabelTransformer( new ToStringLabeller() )
        vv.setBackground( Color.gray )
        vv.setForeground( Color.white )

        val rc = vv.getRenderContext()
        rc.setVertexStrokeTransformer( new Transformer[ Int, Stroke ] {
            private val strkThin  = new BasicStroke( 1 )
            private val strkThick = new BasicStroke( 3 )

            def transform( v: Int ) : Stroke = {
                val isPicked = vv.getPickedVertexState().isPicked( v )
                if( isPicked ) strkThick else strkThin
            }
        })

        rc.setVertexFillPaintTransformer( new Transformer[ Int, Paint ] {
            private val colrInPath  = Color.blue
            private val colrVersion = Color.red
            private val colrOther   = Color.darkGray // white

            def transform( v: Int ) : Paint = {
                val path = Version.current.path
                // XXX not very efficient
                if( path.last == v ) colrVersion
                else if( path.contains( v )) colrInPath
                else colrOther
            }
        })

      	addComponentListener( new ComponentAdapter() {
			override def componentResized( e: ComponentEvent ) {
				val dim = getSize()
				dim.width  = min( 32, dim.width  - 80 )
				dim.height = min( 32, dim.height - 40 )
				smartLayout.setSize( dim )
			}
        })
       	add( vv, BorderLayout.CENTER )

		addAncestorListener( new AncestorListener {
			def ancestorAdded( e: AncestorEvent ) {
				addListener
				updateLayout
			}

			def ancestorRemoved( e: AncestorEvent ) {
				removeListener
			}

			def ancestorMoved( e: AncestorEvent ) {}
		})
	}

	private def updateLayout {
//		smartLayout.initialize()

//		val relaxer = new VisRunner( smartLayout )
//		relaxer.stop();
//		relaxer.prerelax();
//		val staticLayout = new StaticLayout( server.nodeMgr.graph, smartLayout )
//		val lt = new LayoutTransition( vv, vv.getGraphLayout, staticLayout )
//		val animator = new Animator( lt )
//		animator.start()
		vv.repaint()
	}

	private var addedListener = false

	private def addListener {
		if( !addedListener ) {
			addedListener = true
			ograph.addGraphEventListener( this )
//            println( "ADDED" )
		}
	}

	private def removeListener {
		if( !addedListener ) {
			ograph.removeGraphEventListener( this )
			addedListener = false
		}
	}

	def makeWindow: JFrame = {
		val frame = new JFrame( "Version DAG" )
//		frame.setResizable( false )
		frame.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
        val cp = frame.getContentPane()
        cp.add( this, BorderLayout.CENTER )
        cp.add( new JLabel( "Double click to activate version"), BorderLayout.SOUTH )
		frame.pack()
		frame.setVisible( true )
		frame
	}

	// ---- GraphListener interface ----
	def handleGraphEvent( e: GraphEvent[ Int, Long ]) {
//        println( "YUHUUU" )
		updateLayout
	}
}
