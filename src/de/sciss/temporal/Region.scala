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

import de.sciss.confluent.{ FatValue => FVal, _ }

/**
 *    @version 0.11, 11-Apr-10
 */
trait RegionLike {
   def interval: IntervalLike
   def name: String
}

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
   def interval: IntervalLike = new IntervalProxy( data.interval, readPath )

   protected def nodeAccess: NodeAccess[ Region ] = data
   
   override def toString = "Region( " + name + ", " + (try { get( data.interval, readPath ).toString } catch { case _ => data.interval.toString }) + " )"

   def inspect {
      println( toString )
      println( "read = " + readPath + "; write = " + writePath )
      println( "NAME:" )
      data.name.inspect
      println( "INTERVAL:" )
      data.interval.inspect
   }
}
