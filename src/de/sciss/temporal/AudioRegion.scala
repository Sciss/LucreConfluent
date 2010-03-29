package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, _ }
import VersionManagement._

class AudioRegion( sp: CompressedPath ) extends RegionLike[ AudioRegion ] {
   private val nameF       = new FVal[ String ]
   private val intervalF   = new FVal[ IntervalLike ]
   private val audioFileF  = new FVal[ AudioFileElement ]
   private val offsetF     = new FVal[ PeriodLike ]

   def name       = get( nameF, sp )   // XXX proxy
   def interval: IntervalLike = new IntervalProxy( intervalF, sp )
   def audioFile  = get( audioFileF, sp )   // XXX proxy
   def offset     = get( offsetF, sp )   // XXX proxy

   def name_=( newName: String ) = set( nameF, newName, sp )
   def interval_=( newInterval: IntervalLike ) = set( intervalF, newInterval, sp )
   def audioFile_=( newAudioFile: AudioFileElement ) = set( audioFileF, newAudioFile, sp )
   def offset_=( newOffset: PeriodLike ) = set( offsetF, newOffset, sp )

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
