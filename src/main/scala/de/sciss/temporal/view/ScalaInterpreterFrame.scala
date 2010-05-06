/*
 *  ScalaInterpreterFrame.scala
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

import java.awt.event.{ KeyEvent }
import javax.swing._
import java.awt.GraphicsEnvironment
import de.sciss.scalainterpreter.{ LogPane, ScalaInterpreterPane }

/**
 *    @version 0.11, 11-Apr-10
 */
class ScalaInterpreterFrame
extends JFrame( "Scala Interpreter" ) {

   // ---- constructor ----
   {
      val cp = getContentPane
      val ip = new ScalaInterpreterPane

      ip.initialText = ip.initialText +
"""
// problem 1
val r1 = t { region("r1", 0.secs :< 3.secs)}
val r2 = t { region("r2", (r1.interval.stop + 2.secs) :< 5.secs) }
val r3 = t { region("r3", (r1.interval.fixed.stop + 2.secs) :< 5.secs) }
t { r1.interval = 0.secs :< 7.secs }
kView

// problem 2
val r1 = t { region("r1", 0.secs :< ?)}
val v1 = currentVersion
val r2 = t { region("r2", (r1.interval.stop + 2.secs) :< 5.secs) }
val v2 = currentVersion
v1.use
val r3 = t { region("r3", (r1.interval.stop + 3.secs) :< 7.secs) }
val v3 = currentVersion
v1.use
retroc { r1.interval = 0.secs :< 4.secs }
v2.use; kView // not yet effective
t { }; kView  // effective in children
v3.use; kView // not yet effective
t { }; kView  // effective in children

// problem 3
val c1 = t { container("c1") }
val m = multi
c1.use
val (r1, r2) = m.variant {
    (region("r1", 0.secs :< 4.secs),
     region("r2", 5.secs :< 2.secs)) }
val v3 = m.lastVariant
val (r3, r4) = m.variant {
    (region("r3", 0.secs :< 2.5.secs),
     region("r4", 3.5.secs :< 3.secs)) }
val v4 = m.lastVariant
rootContainer.use
val r5 = t { region("r5", (c1.interval.stop +
    2.secs) :< 4.secs) }
val v5 = currentVersion
m.useVariant( v3 )
r5.interval.fixed // result: 9.secs :< 4.secs
kView
m.useVariant( v4 )
r5.interval.fixed // result: 8.5.secs :< 4.secs
kView
"""

      ip.initialCode = Some(
"""
         import de.sciss.temporal._
         import de.sciss.confluent._
         import de.sciss.confluent.VersionManagement._
         import de.sciss.temporal.DomainSpecificLanguage._
         Container.root // initializes root
"""
      )

      ip.customKeyProcessAction = Some( (e: KeyEvent) => {
         e.getKeyChar match {
            case 'π' => e.setKeyChar( '⏊' )
            case '⁄' => e.setKeyChar( '⋯' )
            case '‚' => e.setKeyChar( '❞' )
            case 'µ' => e.setKeyChar( '❜' )
            case _ =>
         }
         e
      })

      val lp = new LogPane
      lp.init
      ip.out = Some( lp.writer )
      Console.setOut( lp.outputStream )
      Console.setErr( lp.outputStream )

      ip.init
      val sp = new JSplitPane( SwingConstants.HORIZONTAL )
      sp.setTopComponent( ip )
      sp.setBottomComponent( lp )
      cp.add( sp )
      val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      setSize( b.width / 2, b.height * 7 / 8 )
      sp.setDividerLocation( b.height * 2 / 3 )
      setLocationRelativeTo( null )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      setVisible( true )
   }
}