/*
 *  Region.scala
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

package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, FatRef => FRef, _ }

//object Region {
//   import VersionManagement._
//
//   def apply( name: String, i: IntervalLike ) = {
//      val r = new Region( name, seminalPath )
//      r.interval = i
//      r
//   }
//}

trait RegionLike {
   def interval: IntervalLike
//   def intervalRef : IntervalLike
   def name: String
//   def ref : RegionLike
}

//class RegionProxy( r: Region, sp: Path ) extends RegionLike {
//   def name : String = r.name
//   def interval
//}

class RegionData extends NodeAccess[ Region ] {
   val name     = new FVal[ String ]
   val interval = new FVal[ IntervalLike ]

   def access( readPath: Path, writePath: Path ) = new Region( this, readPath, writePath )
}

class Region( data: RegionData, protected val readPath: Path, writePath: Path )
extends RegionLike with NodeID[ Region ] {
   import VersionManagement._

   def name: String = get( data.name, readPath )
   def name_=( n: String ) = set( data.name, writePath, n )
   def interval_=( i: IntervalLike ) = set( data.interval, writePath, i )
//   def interval: IntervalLike = get( data.interval, readPath )
//   def intervalRef: IntervalLike = new IntervalProxy( data.interval, readPath )
   def interval: IntervalLike = new IntervalProxy( data.interval, readPath )

//   def access( readPath: Path, writePath: Path ) : Region = {
//
//   }

   protected def nodeAccess: NodeAccess[ Region ] = data
   
   //   def intervalRef : IntervalLike = new IntervalProxy( fi, sp )

//   def ref : Region = {
//      error( "WARNING: Region:ref not yet implemented" )
//   }

//   override def toString = "Region( " + name + ", " + (try { get( fi, sp ).toString } catch { case _ => fi.toString }) + " )"

   def inspect {
      println( toString )
      println( "read = " + readPath + "; write = " + writePath )
      println( "NAME:" )
      data.name.inspect
      println( "INTERVAL:" )
      data.interval.inspect
   }

   def lulu {
//      println( get( fi, sp ))
   }
}
