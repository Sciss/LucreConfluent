///*
// *  AudioRegion.scala
// *  (TemporalObjects)
// *
// *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
// *
// *	 This software is free software; you can redistribute it and/or
// *	 modify it under the terms of the GNU General Public License
// *	 as published by the Free Software Foundation; either
// *	 version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	 This software is distributed in the hope that it will be useful,
// *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	 General Public License for more details.
// *
// *	 You should have received a copy of the GNU General Public
// *	 License (gpl.txt) along with this software; if not, write to the Free Software
// *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *	 For further information, please contact Hanns Holger Rutz at
// *	 contact@sciss.de
// *
// *
// *  Changelog:
// */
//
//package de.sciss.temporal
//
//import de.sciss.confluent.{ FatValue => FVal, _ }
//import VersionManagement._
//
///**
// *    @version 0.11, 08-May-10
// */
//class AudioFileRegionData extends NodeAccess[ AudioFileRegion ] {
//   var name       = FVal.empty[ String ]
//   var interval   = FVal.empty[ IntervalLike ]
//   var audioFile  = FVal.empty[ AudioFileElement ]
//   var offset     = FVal.empty[ PeriodLike ]
//
//   def access( readPath: Path, writePath: Path ) = new AudioFileRegion( this, readPath, writePath )
//}
//
//class AudioFileRegion( data: AudioFileRegionData, protected val readPath: Path, writePath: Path )
//extends RegionLike with NodeID[ AudioFileRegion ] {
//   def name       = get( data.name, readPath )         // XXX +proxy
//   def interval: IntervalLike = new IntervalProxy( data.interval, readPath )
//   def audioFile  = get( data.audioFile, readPath )    // XXX +proxy
//   def offset     = get( data.offset, readPath )       // XXX +proxy
//
//   def name_=( newName: String ) { data.name =set( data.name, writePath, newName )} // XXX WRONG
//   def interval_=( newInterval: IntervalLike ) { data.interval = set( data.interval, writePath, newInterval )} // XXX WRONG
//   def audioFile_=( newAudioFile: AudioFileElement ) { data.audioFile = set( data.audioFile, writePath, newAudioFile )} // XXX WRONG
//   def offset_=( newOffset: PeriodLike ) { data.offset = set( data.offset, writePath, newOffset )} // XXX WRONG
//
//   protected def nodeAccess = data
//
//   def moveBy( delta: PeriodLike ) : AudioFileRegion = {
//      interval = interval + delta
//      this
//   }
//
//   override def toString = try {
//      "AudioFileRegion( " + name + ", " + interval + ", " + audioFile + ", " + offset + " )"
//   } catch { case _ => super.toString }
//
//   def inspect {
//      println( toString )
//      println( "read = " + readPath + "; write = " + writePath )
//      println( "NAME:" )
//      data.name.inspect
//      println( "INTERVAL:" )
//      data.interval.inspect
//      println( "AUDIOFILE:" )
//      data.audioFile.inspect
//      println( "OFFSET:" )
//      data.offset.inspect
//   }
//}
