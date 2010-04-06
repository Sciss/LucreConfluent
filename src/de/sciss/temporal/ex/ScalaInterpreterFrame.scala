/**
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

package de.sciss.temporal.ex

import java.awt.event.{ InputEvent, KeyEvent }
import scala.tools.nsc.{ Interpreter }
import javax.swing._
import java.awt.GraphicsEnvironment
import de.sciss.scalainterpreter.{ LogPane, ScalaInterpreterPane }

class ScalaInterpreterFrame // ( implicit mgr: RegionManager )
extends JFrame( "Scala Interpreter" ) {

   // ---- constructor ----
   {
      val cp = getContentPane
      val ip = new ScalaInterpreterPane

//      ip.bindingsCreator = Some( in => {
//         in.bind( "regionMgr", mgr.getClass.getName, mgr )
//      })

      ip.initialText = ip.initialText +
/*            super.initialText + """
val r1 = Region( "Test", 0⏊00 :: 3⏊00 )
val r2 = Region( "Dep", r1.interval + 1⏊00 )
val r3 = Region( "Indep", 0⏊00 :: 3⏊00 )
r1.moveBy( 0⏊11 )

val iv0 = ConstantInterval( (0.0, 60.0) )
val r1 = new CRegion
r1.interval.set( iv0 )
r1.interval.get.value.span
val r2 = new CRegion
r2.interval.set( PlusInterval3( r1.interval, 11.1 ))
r2.interval.get.value.span

//makeCurrent( Version( List( 0 )))
val v0 = current
currentInc  // 01

r1.interval.get2.value.span   // 0.0 ok
r2.interval.get2.value.span   // 11.1 ok
val iv1 = PlusInterval3( r2.interval, 13.3 )
r2.interval.set( iv1 )
r2.interval.get2.value.span  // 24.4 ok

makeCurrent( v0 )
currentInc  // 02

val iv2 = PlusInterval3( r1.interval, 17.7 )
r1.interval.set( iv2 )
r1.interval.get2.value.span  // 17.7 ok
r2.interval.get2.value.span  // 28.8 ok

val v01 = Version( List( 0, 1 ))
makeCurrent( v01 )
r1.interval.get2.value.span  // 0.0 YES
r2.interval.get2.value.span  // 24.4 YES

val v021= Version( List( 0, 2, 1 ))
makeCurrent( v021 )
r1.interval.get2.value.span   // 17.7 ok
r2.interval.get2.value.span   // 42.1 ok

makeCurrent( v0 )
currentInc
val v03 = current
r1.interval.get2.value.span  // 0.0 ok
r2.interval.get2.value.span  // 11.1 ok
val iv3 = PlusInterval3( r1.interval, 19.9 )
r1.interval.set( iv3 )
r1.interval.get2.value.span  // 19.9 ok
r2.interval.get2.value.span  // 31.0 ok

val iv4 = ConstantInterval( (55.0, 66.0) )
val r3 = new CRegion
r3.interval.set( iv4 )  // XXX TO-DO : inspect to see if path is <3> or <0,3> (should be the former?)
r3.interval.get2.value.span  // 55.0 ok

currentInc
val v04 = current
r3.interval.get2.value.span

r1.interval.inspect
r2.interval.inspect
r3.interval.inspect

makeCurrent( v01 )
val iv4 = PlusInterval3( r3.interval, 1.111 )
iv4.span    // --> nope
val iv4b = PlusInterval2( r3.interval, 1.111 )
iv4b.span   // --> nope
"""*/

/*
            """
val x = new FatValue[ Double ]
val id0 = VersionPath.init
x.assign( id0.path, 33.3 )
x.access( id0.path )
x.inspect
val id01 = versionStep
x.assign( id01.path, 44.4 )
x.inspect
val id012 = versionStep
x.access( id012.path )  // --> 44.4 OK
x.access( id01.path )   // --> 44.4 OK
x.access( id0.path )    // --> 33.3 OK
makeCurrent( id0 )
val id03 = versionStep
x.access( id03.path )    // --> 33.3 OK
x.assign( id03.path, 99.9 )
x.access( id03.path )    // --> 99.9 OK
x.access( id012.path )  // --> 44.4 OK

val x = new FatValue[ Double ]
val z = new FatValue[ Double ]
val y = new FatPointer[ FatValue[ Double ]]
val id0 = VersionPath.init
y.access( id0.path )
x.assign( id0.path, 33.3 )
z.assign( id0.path, 66.6 )
y.assign( id0.path, FatIdentifier( id0.path, x ))
val yval = y.access( id0.path ).get
yval.value.access( yval.path )
val id01 = id0.newBranch
y.assign( id01.path, FatIdentifier( id01.path, z ))
val yval = y.access( id0.path ).get
yval.value.access( yval.path )  // 33.3 OK
val yval = y.access( id01.path ).get
yval.value.access( yval.path )  // 66.6OK

val r1 = Region( "Schoko", 0⏊00 :: 0⏊05 )
r1.interval.fixed
val id0 = VersionPath.init
val id01 = versionStep
val r2 = Region( "Gaga", r1.interval )
r2.interval.fixed
r2.inspect
val id012 = versionStep
makeCurrent( id0 )
r1  // 0 bis 5 OK
r2  // No Value OK
//val id031 = id03 ... ??
val id03 = versionStep
r1.interval = 0⏊07 :: 0⏊11
makeCurrent( id012 )
r1 // OK
r2 // OK
val id0124 = versionStep
r1 // OK
r2 // OK

val id0 = VersionPath.init
val id1 = versionStep
val id2 = versionStep
val id3 = versionStep
makeCurrent( id2 )
val id4 = versionStep
//makeCurrent( id2 )
//val id5 = versionStep   // OK
//val id5 = appendRetro   // OK
makeCurrent( id3 )
val id5 = prependRetro    // OK
id0.version.tree.inspect
"""
*/
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
v2.use; kView
v3.use; kView

val c = container().use
audioFileLocation( "/Users/rutz/Library/Application Support/SuperCollider/nuages/tapes" )
val af = audioFile( "MetallScheibe1TestN.aif" ).use
val ar1 = audioRegion( "R #1", 0⏊01, 0⏊02 :: 0⏊05 )
val ar1c = meld { ar1.moveBy( ar1.interval.dur )} into c.add _
kView

val ar2 = audioRegion( "R #2", 0⏊02, 0⏊07 :: 0⏊13 )
val ar1r = ref( ar1 )
ar1r.interval = ar1r.interval + (ar1r.interval.stop - ar1r.interval.start)
c.interval // 0:00 ... 0:03 OK
kView
versionStep
val ar3 = audioRegion( "R #3", 0⏊04, 0⏊05 :: 0⏊10 )
kView
ar1.interval = 0⏊00 :: 0⏊03
val v1 = currentVersion
val v0 = VersionPath.init
makeCurrent( v0 )
val v2 = appendRetro
ar2.interval = ar2.interval.fixed - 0⏊02
makeCurrent( v1 )
ar2.interval  // 0⏊05 :: 0⏊11 OK
val v3 = versionStep
Container.root.use
val c2 = container()
c2.add( c )
c2.use { kView }
"""

//            var t = new BinaryTreeMap[ Int, String ]
//            t += (10 -> "ten")
//            t += (20 -> "twenty")
//            t += (30 -> "thirty")
//            t += (100 -> "hundred")
//            t += (0 -> "zero")

      ip.initialCode = Some(
/*"""
            import de.sciss.temporal._
            import de.sciss.temporal.ex._
            import de.sciss.temporal.ex.Region._
            import de.sciss.trees._
            import de.sciss.trees.Version._
            implicit val rm = regionMgr
         """*/
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
//      sp.setDividerLocation( 0.8 )
      sp.setDividerLocation( b.height * 2 / 3 )
      setLocationRelativeTo( null )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      setVisible( true )
   }
}