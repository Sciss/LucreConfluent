/*
 *  TemporalObjects.scala
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

package de.sciss.temporal

import java.awt.EventQueue

object TemporalObjects {
   val name          = "TemporalObjects"
   val version       = 0.15
   val copyright     = "(C)opyright 2010 Hanns Holger Rutz"
   def versionString = (version + 0.001).toString.substring( 0, 4 )

   def main( args: Array[ String ]) {
      EventQueue.invokeLater( new Runnable { def run {
      args.toList match {
         case "--test1" :: Nil => test1
         case "--test2" :: Nil => test2
         case "--test3" :: Nil => test3
         case _ => error( "This is a library that cannot be executed directly" )
      }}})
//       EventQueue.invokeLater( this )
    }

//    def run {
//       new ScalaInterpreterFrame
//    }

   import de.sciss.temporal._
   import de.sciss.confluent._
   import de.sciss.confluent.VersionManagement._
   import de.sciss.temporal.DomainSpecificLanguage._

   // problem 1
   def test1 {
      Container.root // initializes root

      val r1 = t { region("r1", 0.secs :< 3.secs)}
      val r2 = t { region("r2", (r1.interval.stop + 2.secs) :< 5.secs) }
      val r3 = t { region("r3", (r1.interval.fixed.stop + 2.secs) :< 5.secs) }
      t { r1.interval = 0.secs :< 7.secs }
//      val test = r2.interval.fixed
      kView
      r2.inspect
      r1.inspect
   }

   // problem 2
   def test2 {
      Container.root // initializes root

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
   }

   // problem 3
   def test3 {
      Container.root // initializes root

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
//      println( "c1 #" + c1.numRegions + "; root #" + rootContainer.numRegions + "; sz " + rootContainer.iterator.size )
      rootContainer.use
      val r5 = t { region("r5", (c1.interval.stop +
          2.secs) :< 4.secs) }
      val v5 = currentVersion
//      println( "c1 #" + c1.numRegions + "; root #" + rootContainer.numRegions + "; sz " + rootContainer.iterator.size )
      m.useVariant( v3 )
//      println( "c1 #" + c1.numRegions + "; sz " + rootContainer.iterator.size + " ; c1.ival " + c1.interval.fixed )
//      val f1 = r1.interval.fixed
//      val f2 = r2.interval.fixed
//      val test = c1.iterator.toList.map(_.interval.fixed)

      r5.interval.fixed // result: 9.secs :< 4.secs
//      println( "CURRENT = " + currentVersion + " ; #" + c1.iterator.toList.size )
//      println( "VERSUS #" + rootContainer.iterator.next.asInstanceOf[ContainerLike].iterator.toList.size )
      kView
      m.useVariant( v4 )
//      println( "c1 #" + c1.numRegions + "; sz " + rootContainer.iterator.size + " ; c1.ival " + c1.interval.fixed )
      r5.interval.fixed // result: 8.5.secs :< 4.secs
      kView
   }
}
