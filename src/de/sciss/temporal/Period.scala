/*
 *  Point.scala
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
import scala.util.{ Random }

trait PeriodDependant {
   def periodReplaced( oldP: PeriodLike, newP: PeriodLike ) : Unit
}

trait PeriodLike {
   def isInstantiated: Boolean
   def getValue: Option[ PeriodConst ]
   def value = getValue getOrElse error( "Not realized" )
   def inf: PeriodConst   // PeriodConst ?
   def sup: PeriodConst   // PeriodConst ?

   def +( b: PeriodLike ) : PeriodLike // = PeriodExpr.plus( this, b )
   def -( b: PeriodLike ) : PeriodLike // = PeriodExpr.plus( this, b )
   def min( b: PeriodLike ) : PeriodLike // = PeriodExpr.min( this, b )
   def max( b: PeriodLike ) : PeriodLike // = PeriodExpr.max( this, b )

   def *( b: Double ): PeriodLike
   def /( b: Double ): PeriodLike

//   def ⋯( b: PeriodLike ) = new ⋯( this, b )
//   def ⟛( b: Period ) = new ⋯( this, b )

   def overlaps( b: PeriodLike ) = if( inf < b.inf ) sup > b.inf else b.sup > inf

   def addDependant( pd: PeriodDependant ) : Unit
   def removeDependant( pd: PeriodDependant ) : Unit
}
 
//trait IntervalLike {
//   def start: Period
//   def stop: Period
//   def +( p: Period ): IntervalLike
//}
//
//case class ⋯( start: Period, stop: Period )
//extends IntervalLike {
//   def +( p: Period ) = ⋯( start + p, stop + p )
//}

trait RandomGen {
//   def inf: Double
//   def sup: Double
   def realize: Double
   def name: String
}

object UniformRandomGen extends RandomGen {
   def realize = Random.nextDouble()
   def name = "uniform"
}

/*
case class BoundedRandomPeriod( lo: PeriodLike, hi: PeriodLike, gen: RandomGen = UniformRandomGen )
extends PeriodLike {
//   private var isRealized = false
   private var realized: Option[ PeriodConst ] = None
   def isInstantiated = realized.isDefined
   def getValue = realized
   def inf = lo.inf
   def sup = hi.sup

//   def +( b: Period ) = PeriodExpr.plus( this, b )

   override def toString = "RRand(" + lo + "," + hi + "," + gen.name + ")"
}
*/

//case class UniformBoundedRandomPeriod( lo: Period, hi: Period ) extends Period {
//
//}

//object PeriodExpr {
//   def plus( a: PeriodLike, b: PeriodLike ) : PeriodLike = new PlusPeriodExpr( a, b )
//   def min( a: PeriodLike, b: PeriodLike ) : PeriodLike  = new MinimumPeriodExpr( a, b )
//   def max( a: PeriodLike, b: PeriodLike ) : PeriodLike  = new MaximumPeriodExpr( a, b )
//}

abstract class UnaryPeriodExpr( a: PeriodLike )
extends PeriodVar with PeriodDependant {
   // ---- constructor ----
   {
      a.addDependant( this )
   }

   def getValue: Option[ PeriodConst ] =
      if( isInstantiated ) Some( eval( a.value )) else None

   override def value = if( isInstantiated ) eval( a.value ) else error( "Not realized" )
   protected def eval( av: PeriodConst ) : PeriodConst
   protected def copy( newA: PeriodLike ) : PeriodLike

   def periodReplaced( oldP: PeriodLike, newP: PeriodLike ) {
      oldP.removeDependant( this )
      val newThis = copy( newP ) // establishes new dependencies
      replacedBy( newThis ) // propagate
   }
}

abstract class BinaryPeriodExpr( a: PeriodLike, b: PeriodLike )
extends PeriodVar with PeriodDependant {
   // ---- constructor ----
   {
      a.addDependant( this )
      b.addDependant( this )
   }

//   def isInstantiated = a.isInstantiated && b.isInstantiated
   def getValue: Option[ PeriodConst ] =
      if( isInstantiated ) Some( eval( a.value, b.value )) else None

   override def value = if( isInstantiated ) eval( a.value, b.value ) else error( "Not realized" )
//   def inf = a.min( b )
//   def sup = a.max( b )
   protected def eval( av: PeriodConst, bv: PeriodConst ) : PeriodConst
   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike

   def periodReplaced( oldP: PeriodLike, newP: PeriodLike ) {
      val newA = if( oldP eq a ) {
         oldP.removeDependant( this )
         newP
      } else a

      val newB = if( oldP eq b ) {
         oldP.removeDependant( this )
         newP
      } else b

      val newThis = copy( newA, newB ) // establishes new dependencies
      replacedBy( newThis ) // propagate
   }
}

case class PlusPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
   protected def eval( av: PeriodConst, bv: PeriodConst ) = av + bv
   def inf = a.inf + b.inf
   def sup = a.sup + b.sup

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA + newB
}

case class MinusPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
   protected def eval( av: PeriodConst, bv: PeriodConst ) = av - bv
   def inf = a.inf - b.sup
   def sup = a.sup - b.inf

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA - newB
}

case class MinimumPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
   protected def eval( av: PeriodConst, bv: PeriodConst ) = av.min( bv )
   def inf = a.inf.min( b.inf )
   def sup = {
      if( a overlaps b ) {
         a.sup.min( b.sup )
      } else {
         (if( a.inf < b.inf ) a else b).sup
      }
   }

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA.min( newB )
}

case class MaximumPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
   protected def eval( av: PeriodConst, bv: PeriodConst ) = av.max( bv )
   def inf = {
      if( a overlaps b ) {
         a.inf.min( b.inf )
      } else {
         (if( a.inf > b.inf ) a else b).inf
      }
   }
   def sup = a.sup.max( b.sup )

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA.max( newB )
}

case class TimesPeriodExpr( a: PeriodLike, b: Double )
extends UnaryPeriodExpr( a ) {
   protected def eval( av: PeriodConst ) = av * b
   def inf = (if( b >= 0 ) a.inf else a.sup) * b
   def sup = (if( b >= 0 ) a.sup else a.inf) * b

   protected def copy( newA: PeriodLike ) : PeriodLike = newA * b
}

case class DivPeriodExpr( a: PeriodLike, b: Double )
extends UnaryPeriodExpr( a ) {
   protected def eval( av: PeriodConst ) = av / b
   def inf = (if( b >= 0 ) a.inf else a.sup) / b
   def sup = (if( b >= 0 ) a.sup else a.inf) / b

   protected def copy( newA: PeriodLike ) : PeriodLike = newA / b
}

/*
trait IntervalVarDependant {
   def intervalReplaced( oldI: IntervalVar, newI: IntervalVar ) : Unit
}

trait IntervalLike {
   def start: PeriodLike
   def stop: PeriodLike
//   def +( p: Period ): IntervalLike
}

case class IntervalVar( start: PeriodVar, stop: PeriodVar )
extends IntervalLike with IntervalVarDependant {
   private var dependants = new WeakHashMap[ IntervalVarDependant, Int ]() // maps to use count

   // ---- constructor ----
   {
      lazy val startDep: PeriodDependant = new PeriodDependant {
         def periodReplaced( oldP: PeriodVar, newP: PeriodVar ) {
            oldP.removeDependant( startDep )
            val newThis = IntervalVar( newP, stop )
            replacedBy( newThis )
         }
      }
      start.addDependant( startDep )

      lazy val stopDep: PeriodDependant = new PeriodDependant {
         def periodReplaced( oldP: PeriodVar, newP: PeriodVar ) {
            oldP.removeDependant( stopDep )
            val newThis = IntervalVar( start, newP )
            replacedBy( newThis )
         }
      }
      stop.addDependant( stopDep )
   }

   def replacedBy( iv: IntervalVar ) {
      dependants.keys.foreach( _.intervalReplaced( this, iv ))
   }

   def addDependant( id: IntervalVarDependant ) {
      dependants += id -> (dependants.getOrElse( id, 0 ) + 1)
   }

   def removeDependant( id: IntervalVarDependant ) {
      dependants.remove( id ).foreach( cnt => {
         if( cnt > 1 ) {
            dependants += id -> (cnt - 1)
         }
      })
   }
}
*/

trait PeriodVar extends PeriodLike {
   private var dependants = new WeakHashMap[ PeriodDependant, Int ]() // maps to use count
   def isInstantiated = false

   protected def replacedBy( p: PeriodLike ) {
      dependants.keysIterator.foreach( _.periodReplaced( this, p ))
   }

   def addDependant( pd: PeriodDependant ) {
      dependants += pd -> (dependants.getOrElse( pd, 0 ) + 1)
   }

   def removeDependant( pd: PeriodDependant ) {
      dependants.remove( pd ).foreach( cnt => {
         if( cnt > 1 ) {
            dependants += pd -> (cnt - 1)
         }
      })
   }

   def +( b: PeriodLike ) : PeriodLike    = PlusPeriodExpr( this, b )
   def -( b: PeriodLike ) : PeriodLike    = MinusPeriodExpr( this, b )
   def min( b: PeriodLike ) : PeriodLike  = MinimumPeriodExpr( this, b )
   def max( b: PeriodLike ) : PeriodLike  = MaximumPeriodExpr( this, b )

   def *( b: Double ) : PeriodLike        = TimesPeriodExpr( this, b )
   def /( b: Double ) : PeriodLike        = DivPeriodExpr( this, b )
}

class PeriodHolder( initial: PeriodLike )
extends PeriodVar with PeriodDependant {  // XXX TO-DO : needs to extend PeriodLike instead!
   private var periodVar = initial

   // ---- constructor ----
   {
      initial.addDependant( this )
   }

   def period: PeriodLike = periodVar
   def period_=( newP: PeriodLike ) {
      periodVar.removeDependant( this )
      periodVar = newP
      periodVar.addDependant( this )
      replacedBy( this )   // XXX not so elegant
   }

   def periodReplaced( oldP: PeriodLike, newP: PeriodLike ) {
      period = newP
   }

   def inf: PeriodConst = period.inf
   def sup: PeriodConst = period.sup
   def getValue: Option[ PeriodConst ] = period.getValue
}

case class PeriodConst( sec: Double ) extends PeriodLike {
   def +( b: PeriodConst )   = PeriodConst( sec + b.sec )
   def -( b: PeriodConst )   = PeriodConst( sec - b.sec )
   def min( b: PeriodConst ) = PeriodConst( scala.math.min( sec, b.sec ))
   def max( b: PeriodConst ) = PeriodConst( scala.math.max( sec, b.sec ))

   def +( b: PeriodLike ) : PeriodLike = b match {
      case pc: PeriodConst => this.+( pc )   // why do we need this. ?
      case _ => b + this 
   }

   def -( b: PeriodLike ) : PeriodLike = b match {
      case pc: PeriodConst => this.-( pc )
      case _ => b - this 
   }

   def min( b: PeriodLike ) : PeriodLike = b match {
      case pc: PeriodConst => min( pc )
      case _ => b.min( this )
   }

   def max( b: PeriodLike ) : PeriodLike = b match {
      case pc: PeriodConst => max( pc )
      case _ => b.max( this ) 
   }

   def *( d: Double )      = PeriodConst( sec + d )
   def /( d: Double )      = PeriodConst( sec / d )
   def <( b: PeriodConst ) = sec < b.sec
   def >( b: PeriodConst ) = sec > b.sec

//   def +( b: Period ) : Period = b match {
//      case plit: PeriodConst => PeriodConst( sec + plit.sec )
//      case _ => PeriodExpr.plus( this, b )
//   }

//   def xx( b: PeriodConst ) : PeriodConst

   def isInstantiated   = true
   def getValue         = Some( this )
   override def value   = this
   def inf              = this
   def sup              = this

   // these are no-ops for a constant period
   def addDependant( pd: PeriodDependant ) {}
   def removeDependant( pd: PeriodDependant ) {}

   override def toString = {
      val millis  = (sec * 1000).toLong
      val milli   = (millis % 1000)
      val secs    = sec.toLong
      val s       = secs % 60
      val mins    = secs / 60
      val min     = mins % 60
      val hours   = mins / 60

      if( hours > 0 ) {
         if( milli > 0 ) {
            hours.toString + "⏊" + (100 + min).toString.substring( 1 ) + "⏊" + (100 + s).toString.substring( 1 ) + "." +
               (milli + 1000).toString.substring( 1 )
         } else {
            hours.toString + "⏊" + (100 + min).toString.substring( 1 ) + "⏊" + (100 + s).toString.substring( 1 )
         }
      } else {
         if( milli > 0 ) {
            min.toString + "⏊" + (100 + s).toString.substring( 1 ) + "." +
               (milli + 1000).toString.substring( 1 )
         } else {
            min.toString + "⏊" + (100 + s).toString.substring( 1 )
         }
      }
   }
}

// class FuzzyPeriodSeconds( minSec: Double, maxSec: Double )


class PeriodConstFactory( d: Double ) {
   def hours  = new PeriodConst( d * 360 )
   def mins   = new PeriodConst( d * 60 )
   def secs   = new PeriodConst( d )
   def msecs  = new PeriodConst( d / 1000 )
   def ⏊( b: Double ) = new PeriodConst( d * 60 + b )
   def ⎍( implicit sr: SampleRate ) = new PeriodConst( d / sr.rate )
}

//class IntervalFactory( p: Period ) {
//   def hours  = new PeriodConst( d * 360 )
//   def mins   = new PeriodConst( d * 60 )
//   def secs   = new PeriodConst( d )
//   def msecs  = new PeriodConst( d / 1000 )
//   def ⏊( b: Double ) = new PeriodConst( d * 60 + b )
//}

//case class XX(start: Period, stop: Period)

case class SampleRate( rate: Double )

object Period {
//   implicit def intToTemporalSource( i: Int ) = new TemporalSource( i )
   implicit def doubleToPeriodConst( d: Double ) = new PeriodConstFactory( d )
//   implicit def tuple2ToIntervalLiteral( t: Tuple2[ Period, Period ]) = Interval( t._1, t._2 )

//   (3 hours, 4 mins, 33 secs)
//
//   3.hours+4.mins+33.secs

   def test {
      implicit val sr = SampleRate( 44100 )

      val p1 = new PeriodHolder( 0⏊10 )
      val p2 = p1 + 0⏊20
      p1.period = 0⏊11
      println( "p2 = " + p2.value )
/*
      val iv1 = ⋯(0⏊00, 1⏊00)
      val dt  = BoundedRandomPeriod( 0⏊10, 0⏊20 ) 
      val iv2 = iv1 + dt

      println( iv1 + " ; " + dt )
      println( iv2.start.inf )
      println( iv2.stop.sup )

//      val x = 44100⎍
//      val x = 44100¬
      val x = 44100⎍
//      val x = 44100
      val y = 4⏊33 ⋯ (5⏊05)
//      val y = 4⏊33 ¬ 5⏊05
//        val z = 4⏊33 ⟛ 5⏊05
//      val y = 4⏊33 ⧦ 5⏊05
//      val y = 4⏊33 ⋯ 5⏊05
      val intvl = ⋯(4⏊33, 6⏊03)
//      (4⏊33, 6⏊03)
      val plit: PeriodConst = 4⏊33 + 5⏊45
      val plit2: PeriodConst = 8⏊01
      println( plit )
      println( plit + x )
      println( intvl )
      println( y )
      println( plit.min( plit2 ))
      println( plit.max( plit2 ))
//      (4∶33, 44⏊44, 3⊹44, 6⋮44⋮54, 3⌖56, 6⌽55, 3⏀55, 4⏊33, 4◌55, 3⟘33, 3⟝44, 3⟡45, 3!45, 1!45!33, 3¶45, 3°45)
//     ⋯ \u22EF "MIDDLE HORIZONTAL ELLIPSIS
//      1◌04◌55
//      1⋮04⋮55
//      1⊤04⊤55
//      1⏀04⏀55
//      1⏊04⏊55
//      1⟘04⟘55
//      1⟡04⟡55
//      1!04!55
//      1°04°55
//      4⏊55
      // ⏊ \u23C9 \u23CA  "DENTISTRY SYMBOL LIGHT UP AND HORIZONTAL"
      */
   }
}