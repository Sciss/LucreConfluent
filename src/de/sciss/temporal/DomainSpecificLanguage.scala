package de.sciss.temporal

import java.io.File
import java.net.URI

object DomainSpecificLanguage {
   // ---- commands ----

   def container : Container = {
      error( "Not yet implemented" )
   }

   def audioFileLocation( loc: FileLocation ) {
      FileLocations.add( loc )
   }

   def audioFile( loc: FileLocation ) : AudioFileElement = {
      AudioFileElement.fromUnresolvedLoc( loc )
   }

   def audioRegion( name: String = "#auto" ) : AudioRegion = {
      error( "Not yet implemented" )
   }

   def transport : Transport = {
      error( "Not yet implemented" )
   }

   def kView {
      error( "Not yet implemented" )
   }

   def pView {
      error( "Not yet implemented" )
   }

   def t {
      error( "Not yet implemented" )
   }

   // ---- implicits ----

   implicit def stringToFileLocation( s: String ) : FileLocation = {
      val f = new File( s )
      URIFileLocation( if( f.exists ) f.toURI else new URI( null, null, s, null ))
   }
   
   implicit def fileToFileLocation( f: File ) : FileLocation = URIFileLocation( f.toURI )
}