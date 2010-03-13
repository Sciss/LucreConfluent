/*
 *  Interval.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2010 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.temporal

import scala.collection.mutable.{ WeakHashMap }

trait IntervalLike {
   def start: PeriodLike
   def stop: PeriodLike
   def +( p: PeriodLike ): IntervalLike
}

//object ⋯ {
//   def apply( start: PeriodConst, stop: PeriodConst ) = new ⋯( start, stop )
//   def apply( start: PeriodLike, stop: PeriodLike ) : IntervalLike =
//      new IntervalVar( start, stop )
//}

case class IntervalConst( val start: PeriodConst, val stop: PeriodConst )
extends IntervalLike {
   def +( p: PeriodConst ) = IntervalConst( start + p, stop + p )

   def +( p: PeriodLike ) = (start + p) :: (stop + p)

   override def toString = "(" + start + " :: " + stop + ")"
}

trait IntervalVarLike extends MutableModelImpl[ IntervalLike ] with IntervalLike

/**
 *    @todo should be possible to react to compound updates from start and stop
 *          such that moving an interval on which this interval depends will not
 *          propagate two successive interval updates but just one, also
 *          eliminating the necessity to check for temporarily illegal intervals
 *          (start > stop)
 */
case class IntervalVar( start: PeriodLike, stop: PeriodLike )
extends IntervalVarLike {

   // ---- constructor ----
   {
      start.addDependant( new PeriodDependant {
         pd =>
         def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
            oldP.removeDependant( pd )
            val newThis = IntervalVar( newP, stop )
            replacedBy( newThis )
         }
      })
      stop.addDependant( new PeriodDependant {
         pd =>
         def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
            oldP.removeDependant( pd )
            val newThis = IntervalVar( start, newP )
            replacedBy( newThis )
         }
      })
   }

   def +( p: PeriodLike ) = IntervalVar( start + p, stop + p )
}
