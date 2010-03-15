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

trait IntervalLike extends MutableModel[ IntervalLike ] {
   def start: PeriodLike
   def stop: PeriodLike
   def +( p: PeriodLike ): IntervalLike
   def -( p: PeriodLike ): IntervalLike

   def detach: IntervalLike
}

case class IntervalConst( val start: PeriodConst, val stop: PeriodConst )
extends IntervalLike {
   def +( p: PeriodConst ) = IntervalConst( start + p, stop + p )
   def -( p: PeriodConst ) = IntervalConst( start - p, stop - p )

   def +( p: PeriodLike ) = p match {
      case pc: PeriodConst => this.+( pc )
      case _ => (start + p) :: (stop + p)
   }

   def -( p: PeriodLike ) = p match {
      case pc: PeriodConst => this.-( pc )
      case _ => (start - p) :: (stop - p) // XXX ?
   }

   // these are no-ops for a constant interval
   def addDependant( id: IntervalDependant ) = id
   def printDependants { println( "No dependants" )}
   def detach: IntervalLike = this // no dependants

   override def toString = "(" + start + " :: " + stop + ")"
}

trait IntervalVarLike
extends MutableModelImpl[ IntervalLike ] with IntervalLike {
   def +( p: PeriodLike ) = PlusIntervalPeriodExpr( this, p )
   def -( p: PeriodLike ) = MinusIntervalPeriodExpr( this, p )
}

/**
 *    @todo should be possible to react to compound updates from start and stop
 *          such that moving an interval on which this interval depends will not
 *          propagate two successive interval updates but just one, also
 *          eliminating the necessity to check for temporarily illegal intervals
 *          (start > stop)
 */
case class IntervalVar( start: PeriodLike, stop: PeriodLike )
extends IntervalVarLike {
   private val startDep = start.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = IntervalVar( newP, stop )
         replacedBy( newThis )
      }
   })
   private val stopDep = stop.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = IntervalVar( start, newP )
         replacedBy( newThis )
      }
   })

   def detach: IntervalLike = IntervalVar( start, stop )

   override def toString = "IV(" + start + " :: " + stop + ")"
}

abstract class IntervalPeriodExpr( a: IntervalLike, b: PeriodLike )
extends IntervalVarLike {
   private val aDep = a.addDependant( new IntervalDependant {
      def modelReplaced( oldI: IntervalLike, newI: IntervalLike ) {
         val newThis = copy( newI, b ) // establishes new dependencies
         replacedBy( newThis ) // propagate
      }
   })
   private val bDep = b.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = copy( a, newP ) // establishes new dependencies
         replacedBy( newThis ) // propagate
      }
   })

   protected def copy( newA: IntervalLike, newB: PeriodLike ) : IntervalLike
}

case class PlusIntervalPeriodExpr( a: IntervalLike, b: PeriodLike )
extends IntervalPeriodExpr( a, b ) {
   def start = a.start + b
   def stop  = a.stop + b

   def detach: IntervalLike = PlusIntervalPeriodExpr( a, b )
   protected def copy( newA: IntervalLike, newB: PeriodLike ) : IntervalLike = newA + newB
}

case class MinusIntervalPeriodExpr( a: IntervalLike, b: PeriodLike )
extends IntervalPeriodExpr( a, b ) {
   def start = a.start - b
   def stop  = a.stop - b

   def detach: IntervalLike = MinusIntervalPeriodExpr( a, b )
   protected def copy( newA: IntervalLike, newB: PeriodLike ) : IntervalLike = newA - newB
}