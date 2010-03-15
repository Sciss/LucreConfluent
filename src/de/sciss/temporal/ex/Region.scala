package de.sciss.temporal.ex

import de.sciss.temporal._

case class RegionHandle( mgr: RegionManager, id: Long ) {
   override def toString = (mgr.getRegion( this ) getOrElse "(Removed Region)").toString
   protected[ex] def region = mgr.getRegion( this ) getOrElse error( "(Removed Region)" )
}

object Region {
   implicit def handleToRegion( rh: RegionHandle ) : Region = rh.region
   def apply( name: String, interval: IntervalLike )( implicit mgr: RegionManager ) : RegionHandle = {
      val r = new Region( name, interval, mgr.newHandle )
      mgr.add( r )
      r.handle
   }
}

class Region private( val name: String, val interval: IntervalVarLike, protected[ex] val handle: RegionHandle ) {
   private var removed = false

   private val ivDep = interval.addDependant( new IntervalDependant {
      def modelReplaced( oldInterval: IntervalLike, newInterval: IntervalLike ) {
         if( !removed ) replace( newInterval )
      }
   })

   private def replace( newInterval: IntervalLike ) {
      val newThis = new Region( name, newInterval, handle )
      dispose
      handle.mgr.replace( this, newThis )
   }

   private def dispose {
//      interval.removeDependant( ivDep )
      removed = true
   }

   def remove {
      dispose
      handle.mgr.remove( this )
   }

   def moveBy( delta: PeriodLike ) {
      interval.replacedBy( interval.detach + delta ) // triggers replace
   }

   override def toString = "Region(" + name + ", " + interval + ")"
}