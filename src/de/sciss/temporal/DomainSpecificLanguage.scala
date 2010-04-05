package de.sciss.temporal

import java.io.File
import java.net.URI
import view.KContainerView
import de.sciss.confluent.{ Handle, VersionManagement }

import VersionManagement._

object DomainSpecificLanguage {
   // ---- commands ----

   def container( name: String = "#auto", start: PeriodLike = 0.secs ) : Handle[ Container ] = {
      val parent  = Container.current
      val cName   = if( name == "#auto" ) ("Container #" + (parent.size + 1)) else name
//      val c       = Container( cName, start )
      val cd         = new ContainerData
      val sp         = seminalPath
      val c          = cd.access( sp, sp )
      c.name         = cName
      c.interval     = start :< 0.secs
      parent.add( c )
      Handle( cd, sp )
   }

   def audioFileLocation( loc: FileLocation ) {
      FileLocations.add( loc )
   }

   def audioFile( loc: FileLocation ) : AudioFileElement = {
      AudioFileElement.fromUnresolvedLoc( loc )
   }

   def audioRegion( name: String = "#auto", offset: PeriodLike, interval: IntervalLike ) : Handle[ AudioRegion ] = {
      val afe        = AudioFileElement.current
      val rName      = if( name == "#auto" ) afe.name else name
      val ard        = new AudioRegionData
      val sp         = seminalPath
      val ar         = ard.access( sp, sp )
      ar.name        = rName
      ar.interval    = interval
      ar.audioFile   = afe
      ar.offset      = offset
      Container.current.add( ar )
      Handle( ard, sp )
   }

   def region( name: String = "#auto", interval: IntervalLike ) : Handle[ Region ] = {
      val c          = Container.current
      val rName      = if( name == "#auto" ) { "R #" + (c.size + 1) } else name
      val rd         = new RegionData
      val sp         = seminalPath
      val r          = rd.access( sp, sp )
      r.name         = rName
      r.interval     = interval
      c.add( r )
      Handle( rd, sp )
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
   implicit def handleToAccess[ T ]( h: Handle[ T ]) : T = h.substitute( readAccess, writeAccess )

//   // ---- transactions ----
//   def transactionContext: TransactionContext = DummyTransactionContext
//   private def transactionContext_=( newContext: TransactionContext ) {
//      transactionContext = newContext
//   }
}