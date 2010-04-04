/**
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

import de.sciss.confluent.{ FatRef => FRef, FatValue => FVal, _ }
import VersionManagement._
import collection.{IterableLike, SeqLike}

trait ContainerLike
extends RegionLike with Iterable[ RegionLike ] {
   def add( c: RegionLike ) : ContainerLike
   def interval: IntervalLike
//   def ref: ContainerLike
}

object Container {
//   type ContainerRepr = ContainerLike[ R <: ContainerLike[ R ]]

   private val rootVar = {
      val sp      = seminalPath
      val c       = (new ContainerData).access( sp, sp )
      c.name      = "root"
      c.interval  = 0.secs :< 0.secs
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

//   def apply( name: String, start: PeriodLike ) : Container = {
//      val r = new Container( name, start, seminalPath )
//      r
//   }
}

class FatLinkedListElem[ T ]( val elem: T ) {
   val next = new FVal[ FatLinkedListElem[ T ]]
}

class ContainerData extends NodeAccess[ Container ] {
   val name       = new FVal[ String ]
   val interval   = new FRef[ IntervalLike ]
   val numRegions = new FVal[ Int ]
   val regions    = new FVal[ FatLinkedListElem[ RegionLike ]] // head of linked list

   def access( readPath: Path, writePath: Path ) = new Container( this, readPath, writePath )
}

class Container( data: ContainerData, protected val readPath: Path, writePath: Path )
extends ContainerLike with NodeID[ Container ] {
//   def intervalRef: IntervalLike = new IntervalProxy( fi, sp )

   // ---- constructor ----
   {
//      set( data.interval, writePath, 0.secs :< 0.secs ) // new IntervalPeriodExpr( start, 0.secs )
      set( data.numRegions, writePath, 0 )
   }

//   def ref : Container = {
//      error( "WARNING: Container:ref not yet implemented" )
//   }

//   def start_=( p: PeriodLike ) {
////      set( data.interval, new IntervalPeriodExpr( start, data.interval.dur )
//   }

   def name: String = get( data.name, readPath )
   def name_=( n: String ) = set( data.name, writePath, n )
   def interval: IntervalLike = get( data.interval, readPath, writePath )
   def interval_=( i: IntervalLike ) = set( data.interval, writePath, i )
   
   protected def nodeAccess: NodeAccess[ Container ] = data

   def use[ T ]( thunk: => T ) =
      Container.use( this, thunk )

   def use = { Container.use( this ); this }
   
   def add( r: RegionLike ) = {
      val newEntry = new FatLinkedListElem( r )
      var entryF  = data.regions
      var entryO  = getO( entryF, readPath )
      var cnt     = 1
      while( entryO.isDefined ) {
         val entry = entryO.get
         entryF   = entry.next
         entryO   = getO( entryF, readPath )
         cnt     += 1
      }
      set( entryF, writePath, newEntry )

      // update bounding interval
      val ivOld = get( data.interval, readPath, writePath )
      val ri = r.interval // XXXX XXXX intervalRef
//      set( fi, new IntervalPeriodExpr( ivOld.start, ivOld.stop.max( start + ri.stop )), sp )

      val oldStart = ivOld.start
      val oldDur   = ivOld.dur
      val childStop= ri.stop
      val newDur   = oldDur.max( childStop )
      val newIv    = oldStart :< newDur // new IntervalPeriodExpr( oldStart, newDur )
      set( data.interval, writePath, newIv )

      // update numRegions
      set( data.numRegions, writePath, cnt )
      
      this
   }

//   override def toString = "Container( " + name + ", " + (try { get( fi, sp ).toString } catch { case _ => fi.toString }) + " )"

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
   def iterator: Iterator[ RegionLike ] = new ListIterator // ( readAccess )
//   def apply( idx: Int ) : RegionLike[ _ ] = iterator.drop( idx ).next
   def numRegions = get( data.numRegions, readPath )
   override def size = numRegions

   private class ListIterator
   extends Iterator[ RegionLike ] {
      private var nextF = data.regions

      def next: RegionLike = {
         val x = get( nextF, readPath )
         nextF = x.next
         resolve( readPath, writePath, x.elem )
      }

      def hasNext: Boolean = getO( nextF, readPath ).isDefined 
   }
}