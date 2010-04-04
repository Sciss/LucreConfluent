/*
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

object AudioRegion {
   class Data {
      val nameF       = new FVal[ String ]
      val intervalF   = new FVal[ IntervalLike ]
      val audioFileF  = new FVal[ AudioFileElement ]
      val offsetF     = new FVal[ PeriodLike ]
   }
}

class AudioRegion private ( sp: Path, d: AudioRegion.Data )
extends RegionLike {
   def this( sp: Path ) = this( sp, new AudioRegion.Data )

   def access = sp // XXX for debugging

   def name       = get( d.nameF, sp )         // XXX +proxy
   def interval: IntervalLike = get( d.intervalF, sp )
   def intervalRef: IntervalLike = new IntervalProxy( d.intervalF, sp )
   def audioFile  = get( d.audioFileF, sp )    // XXX +proxy
   def offset     = get( d.offsetF, sp )       // XXX +proxy

   def name_=( newName: String ) = set( d.nameF, sp, newName ) // XXX WRONG
   def interval_=( newInterval: IntervalLike ) = set( d.intervalF, sp, newInterval ) // XXX WRONG
   def audioFile_=( newAudioFile: AudioFileElement ) = set( d.audioFileF, sp, newAudioFile ) // XXX WRONG
   def offset_=( newOffset: PeriodLike ) = set( d.offsetF, sp, newOffset ) // XXX WRONG

   def moveBy( delta: PeriodLike ) : AudioRegion = {
      interval = interval + delta
      newAccess
   }

   def gugu = get( d.intervalF, sp ) 

   private def newAccess = new AudioRegion( substitute( writeAccess, sp ), d )

   override def toString = try {
      "AudioRegion( " + name + ", " + interval + ", " + audioFile + ", " + offset + " )"
   } catch { case _ => super.toString }

   def inspect {
      println( toString )
      println( "...name:" )
      d.nameF.inspect
      println( "...interval:" )
      d.intervalF.inspect
      println( "...audioFile:" )
      d.audioFileF.inspect
      println( "...offset:" )
      d.offsetF.inspect
   }
}
