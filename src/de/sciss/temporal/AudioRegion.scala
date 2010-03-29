/**
 *  AudioRegion.scala
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
import VersionManagement._

class AudioRegion( sp: Path ) extends RegionLike {
   private val nameF       = new FVal[ String ]
   private val intervalF   = new FVal[ IntervalLike ]
   private val audioFileF  = new FVal[ AudioFileElement ]
   private val offsetF     = new FVal[ PeriodLike ]

   def name       = get( nameF, sp )         // XXX +proxy
   def interval: IntervalLike = get( intervalF, sp )
   def intervalRef: IntervalLike = new IntervalProxy( intervalF, sp )
   def audioFile  = get( audioFileF, sp )    // XXX +proxy
   def offset     = get( offsetF, sp )       // XXX +proxy

   def name_=( newName: String ) = set( nameF, newName, sp )
   def interval_=( newInterval: IntervalLike ) = set( intervalF, newInterval, sp )
   def audioFile_=( newAudioFile: AudioFileElement ) = set( audioFileF, newAudioFile, sp )
   def offset_=( newOffset: PeriodLike ) = set( offsetF, newOffset, sp )

   def ref : AudioRegion = {
      println( "WARNING: AudioRegion:ref not yet implemented" )
      val ar = new AudioRegion( currentVersion.tail )
      ar.interval = intervalRef
      ar.name = name // XXX Ref
      ar.audioFile = audioFile // XXX Ref
      ar.offset = offset // XXX Ref
      ar
   }

   override def toString = try {
      "AudioRegion( " + name + ", " + interval + ", " + audioFile + ", " + offset + " )"
   } catch { case _ => super.toString }

   def inspect {
      println( toString )
      println( "...name:" )
      nameF.inspect
      println( "...interval:" )
      intervalF.inspect
      println( "...audioFile:" )
      audioFileF.inspect
      println( "...offset:" )
      offsetF.inspect
   }
}
