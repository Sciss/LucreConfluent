package de.sciss.temporal

import java.io.File
import de.sciss.synth.io.AudioFile

object AudioFileElement {
   def fromUnresolvedLoc( loc: FileLocation ) : AudioFileElement = {
      val path = if( loc.uri.isAbsolute ) {
         new File( loc.uri )
      } else {
         FileLocations.toList.view.map( baseLoc => new File( baseLoc.uri.resolve( loc.uri ))).find( _.isFile ).get
      }
//      val af      = AudioFile.openRead( path )
//      af.close
      val spec = AudioFile.readSpec( path )
      new AudioFileElement( loc, spec.numFrames, spec.numChannels, spec.sampleRate, Some( path ))
   }

   private var currentVar: AudioFileElement = _

   def current = currentVar

   def use[ T ]( c: AudioFileElement, thunk: => T ) = {
      val oldC = currentVar
      currentVar = c
      try {
         thunk
      } finally {
         currentVar = oldC
      }
   }

   def use( c: AudioFileElement ) {
      currentVar = c
   }
}

case class AudioFileElement( loc: FileLocation, numFrames: Long,
                             numChannels: Int, sampleRate: Double, path: Option[ File ]) {
  def name: String = loc.name

   def use[ T ]( thunk: => T ) =
      AudioFileElement.use( this, thunk )

   def use = { AudioFileElement.use( this ); this }
}