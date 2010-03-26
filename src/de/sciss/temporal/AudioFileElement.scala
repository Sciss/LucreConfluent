package de.sciss.temporal

import java.io.File
import de.sciss.io.AudioFile

object AudioFileElement {
   def fromUnresolvedLoc( loc: FileLocation ) : AudioFileElement = {
      val file = if( loc.uri.isAbsolute ) {
         new File( loc.uri )
      } else {
         FileLocations.toList.view.map( baseLoc => new File( baseLoc.uri.resolve( loc.uri ))).find( _.isFile ).get
      }
      val af      = AudioFile.openAsRead( file )
      val descr   = af.getDescr
      af.close
      new AudioFileElement( loc, descr.length, descr.channels, descr.rate )
   }
}

case class AudioFileElement( loc: FileLocation, numFrames: Long,
                             numChannels: Int, sampleRate: Double ) {
  def name: String = loc.name
}
