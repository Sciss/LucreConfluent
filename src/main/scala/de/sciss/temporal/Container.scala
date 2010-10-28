/*
 *  Container.scala
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
import collection.immutable.{ Queue => IQueue }
import VersionManagement._

/**
 *    @version 0.13, 21-Apr-10
 */
trait ContainerLike
extends RegionLike with Iterable[ RegionLike ] {
   def add( c: RegionLike ) : ContainerLike
//   def interval: IntervalLike
}

object Container {
   private val rootVar = {
      val sp      = seminalPath
      val c       = (new ContainerData( sp, "root", 0.secs :< 0.secs )).access( sp, sp )
      c
   }
   private var currentVar: ContainerLike = rootVar
   
   def root : Container = {
      resolve( readAccess, writeAccess, rootVar )
   }

   def current : ContainerLike = {
      resolve( readAccess, writeAccess, currentVar )
   }

   def use[ T ]( c: ContainerLike, thunk: => T ) = {
      val oldC = currentVar
      currentVar = c
      try {
         thunk
      } finally {
         currentVar = oldC
      }
   }

   def use( c: ContainerLike ) {
      currentVar = c
   }
}

class FatLinkedListElem[ T ]( val elem: T ) {
   var next = FVal.empty[ FatLinkedListElem[ T ]]
}

class ContainerData( seminalPath: Path, iniName: String, iniInterval: IntervalLike ) extends NodeAccess[ Container ] {
   var name       = FVal.empty[ String ]
   var interval   = FVal.empty[ IntervalLike ]
   var numRegions = FVal.empty[ Int ]
//   var regions    = FVal.empty[ FatLinkedListElem[ RegionLike ]] // head of linked list
   var regions    = FVal.empty[ IQueue[ RegionLike ]] // head of linked list

   def access( readPath: Path, writePath: Path ) = new Container( this, readPath, writePath )

   {
      name        = set( name, seminalPath, iniName )
      interval    = set( interval, seminalPath, iniInterval )
      numRegions  = set( numRegions, seminalPath, 0 )
   }
}

class Container( data: ContainerData, protected val readPath: Path, writePath: Path )
extends ContainerLike with NodeID[ Container ] {
   def name: String = get( data.name, readPath )
   def name_=( n: String ) { data.name = set( data.name, writePath, n )}
   def interval: IntervalLike = new IntervalProxy( data.interval, readPath )
   def interval_=( i: IntervalLike ) { data.interval = set( data.interval, writePath, i )}

   protected def nodeAccess: NodeAccess[ Container ] = data

   def use[ T ]( thunk: => T ) =
      Container.use( this, thunk )

   def use = { Container.use( this ); this }

   def add( r: RegionLike ) = {
//      val newEntry = new FatLinkedListElem( r )
//      var entryF  = data.regions
//      // @todo this is a little tricky: we use writePath not readPath,
//      //       because otherwise adding more than one region per
//      //       transaction won't work. This should be handled more
//      //       elegant in the future.
//      var entryO  = getO( entryF, writePath )
//      var cnt     = 1
//      while( entryO.isDefined ) {
//         val entry = entryO.get
//         entryF   = entry.next
//         entryO   = getO( entryF, writePath )
//         cnt     += 1
//      }
//      ??? = set( entryF, writePath, newEntry )

      data.regions = set( data.regions, writePath, get( data.regions, writePath ) enqueue r )

      // update bounding interval
      val ivOld = get( data.interval, writePath )
      val ri = r.interval

      val oldStart = ivOld.start
      val oldDur   = ivOld.dur
      val childStop= ri.stop
      val newDur   = oldDur.max( childStop )
      val newIv    = oldStart :< newDur
      data.interval = set( data.interval, writePath, newIv )

      // @todo this is a little tricky: we use writePath not readPath,
      //       because otherwise adding more than one region per
      //       transaction won't work. This should be handled more
      //       elegant in the future.
      val cnt = get( data.numRegions, writePath ) + 1

      // update numRegions
      data.numRegions = set( data.numRegions, writePath, cnt )
      
      this
   }

   override def toString = "Container( " + name + ", " +
      (try { get( data.interval, readPath ).toString } catch { case _ => data.interval.toString }) + " )"

   def inspect {
      println( toString )
      println( "read = " + readPath + "; write = " + writePath )
      println( "NAME:" )
      data.name.inspect
      println( "INTERVAL:" )
      data.interval.inspect
      println( "NUMREGIONS:" )
      data.numRegions.inspect
      println( "REGIONS:" )
      data.regions.inspect
   }

   // ---- Iterable ----
//   def iterator: Iterator[ RegionLike ] = new ListIterator( readPath )
   def iterator: Iterator[ RegionLike ] = get( data.regions, readPath ).iterator
   def numRegions = get( data.numRegions, readPath )
   override def size = numRegions

//   private class ListIterator( p: Path )
//   extends Iterator[ RegionLike ] {
//      private var nextF = data.regions
//
//      def next: RegionLike = {
//         val x = get( nextF, p )
//         nextF = x.next
//         resolve( p, writePath, x.elem )
//      }
//
//      def hasNext: Boolean = getO( nextF, p ).isDefined
//   }
}