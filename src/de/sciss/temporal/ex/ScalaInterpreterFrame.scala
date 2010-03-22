/**
 *  ScalaInterpreterFrame
 *  (de.sciss.temporal.ex package)
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
import javax.swing.{ WindowConstants, JFrame, KeyStroke }
import scala.tools.nsc.{ Interpreter }

class ScalaInterpreterFrame( implicit mgr: RegionManager )
extends JFrame( "Scala Interpreter" ) {

   // ---- constructor ----
   {
      val cp = getContentPane
      val ip = new ScalaInterpreterPane {
         override protected def createInitialBindings( in: Interpreter ) {
            in.bind( "regionMgr", mgr.getClass.getName, mgr )
         }

         override protected def initialText = {
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
            super.initialText + """
val x = new FatValue[ Double ]
val id0 = VersionPath.init
x.assign( id0.path, 33.3 )
x.inspect
val id01 = id0.newBranch
x.assign( id01.path, 44.4 )
x.inspect
val id012 = id01.newBranch
x.access( id012.path )  // --> 44.4 OK
x.access( id01.path )   // --> 44.4 OK
x.access( id0.path )    // --> 33.3 OK
val id03 = id0.newBranch
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
"""
//            var t = new BinaryTreeMap[ Int, String ]
//            t += (10 -> "ten")
//            t += (20 -> "twenty")
//            t += (30 -> "thirty")
//            t += (100 -> "hundred")
//            t += (0 -> "zero")
         }

         override protected def initialCode = Some(
/*"""
            import de.sciss.temporal._
            import de.sciss.temporal.ex._
            import de.sciss.temporal.ex.Region._
            import de.sciss.trees._
            import de.sciss.trees.Version._
            implicit val rm = regionMgr
         """*/
"""
            import de.sciss.confluent._
"""
)

         override protected lazy val customKeyProcessAction = Some( (e: KeyEvent) => {
//            println( "GOT " + e )

//            if( /*(e.getID == KeyEvent.KEY_TYPED) &&*/ (e.getKeyCode == KeyEvent.VK_P) &&
//               ((e.getModifiers & InputEvent.ALT_MASK) != 0) ) {}
            e.getKeyChar match {
               case 'π' => e.setKeyChar( '⏊' )
               case '⁄' => e.setKeyChar( '⋯' )
               case _ =>
            }
            e
         })
      }
      cp.add( ip )
      setSize( 400, 400 )
      setLocationRelativeTo( null )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      setVisible( true )
   }
}