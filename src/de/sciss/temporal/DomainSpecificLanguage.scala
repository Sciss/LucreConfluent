package de.sciss.temporal

import java.io.File
import java.net.URI
import view.KContainerView
import de.sciss.confluent.VersionManagement

import VersionManagement._

object DomainSpecificLanguage {
   // ---- commands ----

   def container( name: String = "#auto", start: PeriodLike = 0⏊00 ) : Container = {
      val parent  = Container.current
      val cName   = if( name == "#auto" ) ("Container #" + (parent.size + 1)) else name
      val c       = Container( cName, start )
      parent.add( c )
      c
   }

   def audioFileLocation( loc: FileLocation ) {
      FileLocations.add( loc )
   }

   def audioFile( loc: FileLocation ) : AudioFileElement = {
      AudioFileElement.fromUnresolvedLoc( loc )
   }

   def audioRegion( name: String = "#auto", offset: PeriodLike, interval: IntervalLike ) : AudioRegion = {
      val afe        = AudioFileElement.current
      val rName      = if( name == "#auto" ) afe.name else name
      val ar         = new AudioRegion( currentAccess )
      ar.name        = rName
      ar.interval    = interval
      ar.audioFile   = afe
      ar.offset      = offset
      Container.current.add( ar )
      ar
   }

   def transport : Transport = {
      error( "Not yet implemented" )
   }

   def kView : KContainerView = {
      val view = new KContainerView( Container.current, currentVersion )
      view.makeWindow
      view
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