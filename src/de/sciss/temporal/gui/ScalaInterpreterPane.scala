/*
 *  ScalaInterpreterPane.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2010 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.temporal.gui

import java.awt.{ BorderLayout, Dimension, Font, GraphicsEnvironment, Toolkit }
import java.awt.event.{ ActionEvent, KeyEvent, KeyListener }
import java.io.{ File }
import javax.swing.{ AbstractAction, Box, JComponent, JEditorPane, JLabel, JPanel, JProgressBar, JScrollPane,
   KeyStroke, OverlayLayout, SwingWorker }

import jsyntaxpane.{ DefaultSyntaxKit, SyntaxDocument }
import scala.tools.nsc.{ Interpreter, InterpreterResults => IR, Settings }

/**
 *    @version 0.10, 13-Mar-10
 */
class ScalaInterpreterPane
extends JPanel {
   pane =>

   protected var interpreter: Option[ Interpreter ] = None
   protected var doc: Option[ SyntaxDocument ] = None

   // subclasses may override this
   protected def executeKeyStroke = KeyStroke.getKeyStroke( KeyEvent.VK_E, Toolkit.getDefaultToolkit.getMenuShortcutKeyMask )

   // subclasses may override this
   protected def initialCode: Option[ String ] = None

   // subclasses may override this to change the order of
   // preferred fonts. note: font sizes are specified for
   // mac os x, they scale down on linux and windows by
   // 3/4 (hope that is correct regarding default dpi settings of 72 versus 96)
   protected def preferredFonts = List( "Menlo" -> 12, "Monaco" -> 12, "Anonymous Pro" -> 12,
      "Bitstream Vera Sans Mono" -> 12 )

   protected def customKeyMapActions:    Map[ KeyStroke, Function0[ Unit ]] = Map.empty
   protected def customKeyProcessAction: Option[ Function1[ KeyEvent, KeyEvent ]] = None

   private val ggStatus = new JLabel( "Initializing..." )

   protected val editorPane      = new JEditorPane() {
      override protected def processKeyEvent( e: KeyEvent ) {
         super.processKeyEvent( customKeyProcessAction.map( fun => {
            fun.apply( e )
         }) getOrElse e )
      }
   }
   private val progressPane      = new JPanel()
   private val ggProgress        = new JProgressBar()
   private val ggProgressInvis   = new JComponent {
      override def getMinimumSize   = ggProgress.getMinimumSize
      override def getPreferredSize = ggProgress.getPreferredSize
      override def getMaximumSize   = ggProgress.getMaximumSize
   }

   // ---- constructor ----
   {
      // spawn interpreter creation
      (new SwingWorker[ Unit, Unit ] {
         override def doInBackground {
            val settings = {
               val set = new Settings()
               set.classpath.value += File.pathSeparator + System.getProperty( "java.class.path" )
               set
            }

            val in = new Interpreter( settings /*, out*/ ) {
               override protected def parentClassLoader = pane.getClass.getClassLoader
            }
            in.setContextClassLoader()
            createInitialBindings( in )
            interpreter = Some( in )
            initialCode.foreach( code => in.interpret( code ))
            DefaultSyntaxKit.initKit()
         }

         override protected def done {
            ggProgressInvis.setVisible( true )
            ggProgress.setVisible( false )
            editorPane.setContentType( "text/scala" )
            editorPane.setText( "// Type Scala code here.\n// Press '" + executeKeyStroke + "' to execute selected text\n// or current line.\n" )
            doc = editorPane.getDocument() match {
               case sdoc: SyntaxDocument => Some( sdoc )
               case _ => None
            }
            val osName                 = System.getProperty( "os.name" )
            val isMac                  = osName.startsWith( "Mac OS" )
            val allFontNames           = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
            val (fontName, fontSize)   = preferredFonts.find( spec => allFontNames.contains( spec._1 ))
               .getOrElse( "Monospaced" -> 12 )

            editorPane.setFont( new Font( fontName, Font.PLAIN, /*if( isMac )*/ fontSize /*else fontSize * 3/4*/ ))
            editorPane.setEnabled( true )
            editorPane.requestFocus
            status( "Ready." )
         }
      }).execute()

      val ggScroll   = new JScrollPane( editorPane )

      ggProgress.putClientProperty( "JProgressBar.style", "circular" )
      ggProgress.setIndeterminate( true )
      ggProgressInvis.setVisible( false )
      editorPane.setEnabled( false )

      val imap = editorPane.getInputMap( JComponent.WHEN_FOCUSED )
      val amap = editorPane.getActionMap()
      imap.put( executeKeyStroke, "de.sciss.exec" )
      amap.put( "de.sciss.exec", new AbstractAction {
         def actionPerformed( e: ActionEvent ) {
            var txt = editorPane.getSelectedText
            if( txt == null ) {
               doc.foreach( d => txt = d.getLineAt( editorPane.getCaretPosition ))
            }
            if( txt != null ) interpret( txt )
         }
      })
      customKeyMapActions.iterator.zipWithIndex.foreach( tup => {
         val (spec, idx) = tup
         val name = "de.sciss.user" + idx
         imap.put( spec._1, name )
         amap.put( name, new AbstractAction {
            def actionPerformed( e: ActionEvent ) {
               spec._2.apply()
            }
         })
      })

      progressPane.setLayout( new OverlayLayout( progressPane ))
      progressPane.add( ggProgress )
      progressPane.add( ggProgressInvis )
      ggStatus.putClientProperty( "JComponent.sizeVariant", "small" )
      val statusPane = Box.createHorizontalBox()
      statusPane.add( Box.createHorizontalStrut( 4 ))
      statusPane.add( progressPane )
      statusPane.add( Box.createHorizontalStrut( 4 ))
      statusPane.add( ggStatus )

//      setLayout( new BorderLayout )
      setLayout( new BorderLayout() )
      add( ggScroll, BorderLayout.CENTER )
      add( statusPane, BorderLayout.SOUTH )
   }

   /**
    *    Subclasses may override this to
    *    create initial bindings for the interpreter.
    *    Note that this is not necessarily executed
    *    on the event thread. 
    */
   protected def createInitialBindings( in: Interpreter ) {}

   protected def status( s: String ) {
      ggStatus.setText( s )
   }

   def interpret( code: String ) {
      interpreter.foreach( in => {
         status( null )
         val result = in.interpret( code )
         match {
   //       case IR.Error       => None
   //       case IR.Success     => Some(code)
            case IR.Incomplete  => {
               status( "! Code incomplete !" )
            }
            case _ =>
         }
      })
    }
}
