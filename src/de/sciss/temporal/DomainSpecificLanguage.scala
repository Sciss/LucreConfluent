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
      val ar         = new AudioRegion( seminalPath )
      ar.name        = rName
      ar.interval    = interval
      ar.audioFile   = afe
      ar.offset      = offset
      Container.current.add( ar )
      ar
   }

//   def ref( ar: AudioRegion ) : AudioRegion = {
//      val arRef = ar.ref
//      Container.current.add( arRef )
//      arRef
//   }

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

   def t[ T ]( thunk: => T ) : T = {
      val current = currentVersion
      val write   = current.newBranch
      makeWrite( write )
      try {
         thunk
      } finally {
         makeCurrent( write )
      }
   }

   def meld[ T ]( thunk: => T ) : Meld[ T ] = {
      val current    = currentVersion
      val write      = current.newBranch
      makeWrite( write )
//      val oldContext = transactionContext
//      val meld       = new MeldTransactionContext
//      transactionContext = meld
      try {
         Meld( thunk, write )
      } finally {
         makeCurrent( current )
//         transactionContext = oldContext
//         meld.createdObjects.foreach( _ match {
//         })
      }
   }

   // ---- implicits ----

   implicit def stringToFileLocation( s: String ) : FileLocation = {
      val f = new File( s )
      URIFileLocation( if( f.exists ) f.toURI else new URI( null, null, s, null ))
   }
   
   implicit def fileToFileLocation( f: File ) : FileLocation = URIFileLocation( f.toURI )

//   // ---- transactions ----
//   def transactionContext: TransactionContext = DummyTransactionContext
//   private def transactionContext_=( newContext: TransactionContext ) {
//      transactionContext = newContext
//   }
}