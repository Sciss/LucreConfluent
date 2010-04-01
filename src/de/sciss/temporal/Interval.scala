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

trait IntervalLike extends MutableModel[ IntervalLike ] {
   def start: PeriodLike
   def dur: PeriodLike
   def stop: PeriodLike = start + dur
   def +( p: PeriodLike ): IntervalLike
   def -( p: PeriodLike ): IntervalLike

   def fixed: IntervalLike
   def isConstant = start.isConstant && dur.isConstant
}

case class IntervalConst( start: PeriodConst, dur: PeriodConst )
extends IntervalLike {
   def +( p: PeriodConst ) = IntervalConst( start + p, dur )
   def -( p: PeriodConst ) = IntervalConst( start - p, dur )

   def +( p: PeriodLike ) = p match {
      case pc: PeriodConst => this.+( pc )
      case _ => (start + p) :< dur
   }

   def -( p: PeriodLike ) = p match {
      case pc: PeriodConst => this.-( pc )
      case _ => (start - p) :< dur
   }

   // these are no-ops for a constant interval
   def addDependant( id: IntervalDependant ) = id
   def printDependants { println( "No dependants" )}
//   def eval: IntervalConst = this // no dependants
   def fixed: IntervalConst = this // no dependants

   override def toString = "(" + start + " :< " + dur + ")"
}

trait IntervalExprLike
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
case class IntervalPeriodExpr( start: PeriodLike, dur: PeriodLike )
extends IntervalExprLike {
   private val startDep = start.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = new IntervalPeriodExpr( newP, dur )
         replacedBy( newThis )
      }
   })
   private val durDep = dur.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = new IntervalPeriodExpr( start, newP )
         replacedBy( newThis )
      }
   })

//   def eval: IntervalConst = IntervalConst( start.eval, stop.eval )
   def fixed: IntervalLike = start.fixed :< dur.fixed

   override def toString = "Ix(" + start + " :< " + dur + ")"
}

abstract class IntervalPeriodOpExpr( a: IntervalLike, b: PeriodLike )
extends IntervalExprLike {
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
extends IntervalPeriodOpExpr( a, b ) {
   def start = a.start + b
   def dur   = a.dur

// def detach: IntervalLike = PlusIntervalPeriodExpr( a, b )
//   def eval: IntervalConst = a.eval + b.eval
   def fixed: IntervalLike = a.fixed + b.fixed
   protected def copy( newA: IntervalLike, newB: PeriodLike ) : IntervalLike = newA + newB
}

case class MinusIntervalPeriodExpr( a: IntervalLike, b: PeriodLike )
extends IntervalPeriodOpExpr( a, b ) {
   def start = a.start - b
   def dur   = a.dur

//   def detach: IntervalLike = MinusIntervalPeriodExpr( a, b )
//   def eval: IntervalConst = a.eval - b.eval
   def fixed: IntervalLike = a.fixed - b.fixed
   protected def copy( newA: IntervalLike, newB: PeriodLike ) : IntervalLike = newA - newB
}