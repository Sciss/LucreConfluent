package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, _ }

object Region {
   import VersionManagement._

   def apply( name: String, i: IntervalLike ) = {
      val r = new Region( name, currentVersion.path.takeRight( 2 ))
      r.interval = i
      r
   }
}

trait RegionLike[ +Repr ] {
   def interval: IntervalLike
   def name: String
}

// note: eventually 'name' should also be confluent
class Region private ( val name: String, val sp: CompressedPath )
extends RegionLike[ Region ] {
   import VersionManagement._

   private val fi = new FVal[ IntervalLike ]
//   def interval: IntervalLike = new IntervalAccess( currentAccess, intervalPtr )
   def interval: IntervalLike = new IntervalProxy( fi, sp )
   def interval_=( i: IntervalLike ) = {
       set( fi, i, sp )
   }

   override def toString = "Region( " + name + ", " + (try { get( fi, sp ).toString } catch { case _ => fi.toString }) + " )"

   def inspect {
      println( toString )
      fi.inspect
   }

   def lulu {
      println( get( fi, sp ))
   }
}
