package de.sciss.temporal

import de.sciss.confluent.{ FatPointer => FPtr, FatValue => FVal, _ }
import VersionManagement._
import collection.{IterableLike, SeqLike}

trait ContainerLike[ +Repr ]
extends RegionLike[ Repr ] with Iterable[ RegionLike[ _ ]] {
   def add( c: RegionLike[ _ ]) : Repr
   def interval: IntervalLike
//   def iterator: Iterator
}

object Container {
//   type ContainerRepr = ContainerLike[ R <: ContainerLike[ R ]]

   val root = Container( "root", 0⏊00 )   // workspace?
   private var currentVar: ContainerLike[ _ ] = root

   def current = currentVar

   def use[ T ]( c: ContainerLike[ _ ], thunk: => T ) = {
      val oldC = currentVar
      currentVar = c
      try {
         thunk
      } finally {
         currentVar = oldC
      }
   }

   def use( c: ContainerLike[ _ ]) {
      currentVar = c
   }

   def apply( name: String, start: PeriodLike ) : Container = {
      val r = new Container( name, start, currentVersion.path.takeRight( 2 ))
      r
   }
}

class Container private ( val name: String, start: PeriodLike, val sp: CompressedPath )
extends ContainerLike[ Container ] {
   private val regions      = new FVal[ ListEntry ]
   private val numRegionsF  = new FVal[ Int ]

   private val fi = new FVal[ IntervalLike ]
   def interval: IntervalLike = new IntervalProxy( fi, sp )

   // ---- constructor ----
   {
      set( fi, new IntervalPeriodExpr( start, start ), sp )
      set( numRegionsF, 0, sp )
   }

   def use[ T ]( thunk: => T ) =
      Container.use( this, thunk )

   def use = { Container.use( this ); this }
   
   def add( r: RegionLike[ _ ]) = {
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
      val ri = r.interval
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
   def iterator: Iterator[ RegionLike[ _ ]] = new ListIterator( currentAccess )
//   def apply( idx: Int ) : RegionLike[ _ ] = iterator.drop( idx ).next
   def numRegions = get( numRegionsF, sp )
   override def size = numRegions

   private class ListEntry( val elem: RegionLike[ _ ]) {
      val next = new FVal[ ListEntry ]
   }

   private class ListIterator( access: CompressedPath ) extends Iterator[ RegionLike[ _ ]] {
      private var nextF = regions

      def next: RegionLike[ _ ] = {
         val x = get( nextF, sp, access )
         nextF = x.next
         x.elem
      }

      def hasNext: Boolean = getO( nextF, sp, access ).isDefined 
   }
}