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

import de.sciss.confluent.{ FatPointer => FPtr, FatValue => FVal, _ }
import VersionManagement._
import collection.{IterableLike, SeqLike}

trait ContainerLike
extends RegionLike with Iterable[ RegionLike ] {
   def add( c: RegionLike ) : ContainerLike
   def interval: IntervalLike
   def ref: ContainerLike
}

object Container {
//   type ContainerRepr = ContainerLike[ R <: ContainerLike[ R ]]

   val root = Container( "root", 0⏊00 )   // workspace?
   private var currentVar: ContainerLike = root

   def current = currentVar

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

   def apply( name: String, start: PeriodLike ) : Container = {
      val r = new Container( name, start, currentVersion.tail )
      r
   }
}

class Container private ( val name: String, start: PeriodLike, val sp: Path )
extends ContainerLike {
   private val regions      = new FVal[ ListEntry ]
   private val numRegionsF  = new FVal[ Int ]

   private val fi = new FVal[ IntervalLike ]
   def interval: IntervalLike = get( fi, sp )
   def intervalRef: IntervalLike = new IntervalProxy( fi, sp )

   // ---- constructor ----
   {
      set( fi, new IntervalPeriodExpr( start, start ), sp )
      set( numRegionsF, 0, sp )
   }

   def ref : Container = {
      error( "WARNING: Container:ref not yet implemented" )
   }
   
   def use[ T ]( thunk: => T ) =
      Container.use( this, thunk )

   def use = { Container.use( this ); this }
   
   def add( r: RegionLike ) = {
      val newEntry = new ListEntry( r )
      var entryF  = regions
      var entryO  = getO( entryF, sp )
      var cnt     = 1
      while( entryO.isDefined ) {
         val entry = entryO.get
         entryF   = entry.next
         entryO   = getO( entryF, sp )
         cnt     += 1
      }
      set( entryF, newEntry, sp )

      // update bounding interval
      val ivOld = get( fi, sp )
      val ri = r.intervalRef
      set( fi, new IntervalPeriodExpr( ivOld.start, ivOld.stop.max( start + ri.stop )), sp )

      // update numRegions
      set( numRegionsF, cnt, sp )
      
      this
   }

   override def toString = "Container( " + name + ", " + (try { get( fi, sp ).toString } catch { case _ => fi.toString }) + " )"

   def inspect {
      println( toString )
      fi.inspect
   }
   
   // ---- Iterable ----
   def iterator: Iterator[ RegionLike ] = new ListIterator( currentAccess )
//   def apply( idx: Int ) : RegionLike[ _ ] = iterator.drop( idx ).next
   def numRegions = get( numRegionsF, sp )
   override def size = numRegions

   private class ListEntry( val elem: RegionLike ) {
      val next = new FVal[ ListEntry ]
   }

   private class ListIterator( access: Path ) extends Iterator[ RegionLike ] {
      private var nextF = regions

      def next: RegionLike = {
         val x = get( nextF, sp, access )
         nextF = x.next
         x.elem
      }

      def hasNext: Boolean = getO( nextF, sp, access ).isDefined 
   }
}