/*
 *  DomainSpecificLanguage.scala
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

import java.io.File
import java.net.URI
import view.KContainerView
import de.sciss.confluent.{ Handle, Multiplicity, VersionManagement }

import VersionManagement._

/**
 *    @version 0.13, 08-May-10
 */
object DomainSpecificLanguage {
   // ---- commands ----

   def container( name: String = "#auto", start: PeriodLike = 0.secs ) : Handle[ Container ] = {
      val parent  = Container.current
      val cName   = if( name == "#auto" ) ("Container #" + (parent.size + 1)) else name
      val sp         = seminalPath
      val cd         = new ContainerData( sp, cName, start :< 0.secs )
      val c          = cd.access( sp, sp )
      parent.add( c )
      Handle( cd, sp )
   }

   def rootContainer = Container.root // XXX handle?
//   def rootContainer : Handle[ Container ] = Handle[ Container.root ]

   def audioFileLocation( loc: FileLocation ) {
      FileLocations.add( loc )
   }

   def audioFile( loc: FileLocation ) : AudioFileElement = {
      AudioFileElement.fromUnresolvedLoc( loc )
   }

   def audioFileRegion( name: String = "#auto", offset: PeriodLike, interval: IntervalLike ) : Handle[ AudioFileRegion ] = {
      val afe        = AudioFileElement.current
      val rName      = if( name == "#auto" ) afe.name else name
      val ard        = new AudioFileRegionData
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

//   def transport : Transport = {
//      error( "Not yet implemented" )
//   }

   def kView : KContainerView = {
      val view = new KContainerView( Container.current, currentVersion )
      view.makeWindow
      view
   }

//   def pView {
//      error( "Not yet implemented" )
//   }

   // ---- implicits ----

   implicit def stringToFileLocation( s: String ) : FileLocation = {
      val f = new File( s )
      URIFileLocation( if( f.exists ) f.toURI else new URI( null, null, s, null ))
   }
   
   implicit def fileToFileLocation( f: File ) : FileLocation = URIFileLocation( f.toURI )
   implicit def handleToAccess[ T ]( h: Handle[ T ]) : T = h.substitute( readAccess, writeAccess )

   val ? : PeriodLike = PeriodUnknown
}