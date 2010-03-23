/*
 *  Period.scala
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

import util.{ Random }
import math._

trait PeriodLike extends MutableModel[ PeriodLike ] {
//   def isInstantiated: Boolean
//   def getEval: Option[ PeriodConst ]
//   def eval = getEval getOrElse error( "Not realized" )
   def fixed: PeriodLike // = getEval getOrElse error( "Not realized" )
   def inf: PeriodConst   // PeriodConst ?
   def sup: PeriodConst   // PeriodConst ?

   def +( b: PeriodLike ) : PeriodLike // = PeriodExpr.plus( this, b )
   def -( b: PeriodLike ) : PeriodLike // = PeriodExpr.plus( this, b )
   def min( b: PeriodLike ) : PeriodLike // = PeriodExpr.min( this, b )
   def max( b: PeriodLike ) : PeriodLike // = PeriodExpr.max( this, b )

   def *( b: Double ): PeriodLike
   def /( b: Double ): PeriodLike

   def unary_- : PeriodLike

   def overlaps( b: PeriodLike ) = if( inf < b.inf ) sup > b.inf else b.sup > inf

   def ::( b: PeriodLike ) : IntervalLike = new IntervalPeriodExpr( b, this ) // note argument reversal
}

trait RandomGen {
   def realize: Double
   def name: String
}

object UniformRandomGen extends RandomGen {
   def realize = Random.nextDouble()
   def name = "uniform"
}

abstract class UnaryPeriodExpr( a: PeriodLike )
extends PeriodExpr {
   private val aDep = a.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = copy( newP ) // establishes new dependencies
         replacedBy( newThis ) // propagate
      }
   })

//   def getEval: Option[ PeriodConst ] =
//      if( isInstantiated ) Some( eval( a.eval )) else None

//   override def eval = if( isInstantiated ) eval( a.eval ) else error( "Not realized" )
   override def fixed: PeriodLike = fixed( a.fixed )
   protected def fixed( af: PeriodLike ) : PeriodLike
   protected def copy( newA: PeriodLike ) : PeriodLike
}

abstract class BinaryPeriodExpr( a: PeriodLike, b: PeriodLike )
extends PeriodExpr {
   private val aDep = a.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = copy( newP, b ) // establishes new dependencies
         replacedBy( newThis ) // propagate
      }
   })
   private val bDep = b.addDependant( new PeriodDependant {
      def modelReplaced( oldP: PeriodLike, newP: PeriodLike ) {
         val newThis = copy( a, newP ) // establishes new dependencies
         replacedBy( newThis ) // propagate
      }
   })

//   def getEval: Option[ PeriodConst ] =
//      if( isInstantiated ) Some( eval( a.eval, b.eval )) else None

//   override def eval = if( isInstantiated ) eval( a.eval, b.eval ) else error( "Not realized" )
   override def fixed: PeriodLike = fixed( a.fixed, b.fixed )
   protected def fixed( af: PeriodLike, bf: PeriodLike ) : PeriodLike
   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike
}

case class PlusPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
//   protected def eval( av: PeriodConst, bv: PeriodConst ) = av + bv
   protected def fixed( af: PeriodLike, bf: PeriodLike ) = af + bf
   def inf = a.inf + b.inf
   def sup = a.sup + b.sup

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA + newB
}

case class MinusPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
//   protected def eval( av: PeriodConst, bv: PeriodConst ) = av - bv
   protected def fixed( af: PeriodLike, bf: PeriodLike ) = af - bf
   def inf = a.inf - b.sup
   def sup = a.sup - b.inf

   protected def copy( newA: PeriodLike, newB: PeriodLike ) : PeriodLike = newA - newB
}

case class MinimumPeriodExpr( a: PeriodLike, b: PeriodLike )
extends BinaryPeriodExpr( a, b ) {
//   protected def eval( av: PeriodConst, bv: PeriodConst ) = av.min( bv )
   protected def fixed( af: PeriodLike, bf: PeriodLike ) = af.min( bf )
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
//   protected def eval( av: PeriodConst, bv: PeriodConst ) = av.max( bv )
   protected def fixed( af: PeriodLike, bf: PeriodLike ) = af.max( bf )
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
//   protected def eval( av: PeriodConst ) = av * b
   protected def fixed( af: PeriodLike ) = af * b
   def inf = (if( b >= 0 ) a.inf else a.sup) * b
   def sup = (if( b >= 0 ) a.sup else a.inf) * b

   protected def copy( newA: PeriodLike ) : PeriodLike = newA * b
}

case class DivPeriodExpr( a: PeriodLike, b: Double )
extends UnaryPeriodExpr( a ) {
//   protected def eval( av: PeriodConst ) = av / b
   protected def fixed( af: PeriodLike ) = af / b
   def inf = (if( b >= 0 ) a.inf else a.sup) / b
   def sup = (if( b >= 0 ) a.sup else a.inf) / b

   protected def copy( newA: PeriodLike ) : PeriodLike = newA / b
}

case class UnaryMinusPeriodExpr( a: PeriodLike )
extends UnaryPeriodExpr( a ) {
//   protected def eval( av: PeriodConst ) = -av
   protected def fixed( af: PeriodLike ) = -af
   def inf = -a.sup
   def sup = -a.inf

   protected def copy( newA: PeriodLike ) : PeriodLike = -newA
}

trait PeriodExprLike extends MutableModelImpl[ PeriodLike ] with PeriodLike

abstract class PeriodExpr extends PeriodExprLike {
//   def isInstantiated = false

   def +( b: PeriodLike ) : PeriodLike    = PlusPeriodExpr( this, b )
   def -( b: PeriodLike ) : PeriodLike    = MinusPeriodExpr( this, b )
   def min( b: PeriodLike ) : PeriodLike  = MinimumPeriodExpr( this, b )
   def max( b: PeriodLike ) : PeriodLike  = MaximumPeriodExpr( this, b )

   def *( b: Double ) : PeriodLike        = TimesPeriodExpr( this, b )
   def /( b: Double ) : PeriodLike        = DivPeriodExpr( this, b )

   def unary_- : PeriodLike               = UnaryMinusPeriodExpr( this )
}

case class PeriodConst( sec: Double ) extends PeriodLike {
   def +( b: PeriodConst )   = PeriodConst( sec + b.sec )
   def -( b: PeriodConst )   = PeriodConst( sec - b.sec )
   def min( b: PeriodConst ) = PeriodConst( scala.math.min( sec, b.sec ))
   def max( b: PeriodConst ) = PeriodConst( scala.math.max( sec, b.sec ))
   def ::( b: PeriodConst )  = IntervalConst( b, this )  // note argument reversal

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

   def *( d: Double )      = PeriodConst( sec * d )
   def /( d: Double )      = PeriodConst( sec / d )
   def <( b: PeriodConst ) = sec < b.sec
   def >( b: PeriodConst ) = sec > b.sec

   def unary_- = PeriodConst( -sec )

//   def isInstantiated   = true
//   def getEval          = Some( this )
//   override def eval    = this
   override def fixed    = this
   def inf              = this
   def sup              = this

   // these are no-ops for a constant period
   def addDependant( pd: PeriodDependant ) = pd
   def printDependants { println( "No dependants" )}

   override def toString = {
      val asec    = abs( sec )
      val millis  = (asec * 1000).toLong
      val milli   = (millis % 1000)
      val secs    = asec.toLong
      val s       = secs % 60
      val mins    = secs / 60
      val min     = mins % 60
      val hours   = mins / 60

      if( hours > 0 ) {
         val s1 = (if( sec < 0 ) "-" else "") + hours.toString + "⏊" + (100 + min).toString.substring( 1 ) +
            "⏊" + (100 + s).toString.substring( 1 )
         if( milli > 0 ) {
            s1 + "." + (milli + 1000).toString.substring( 1 )
         } else {
            s1
         }
      } else {
         val s1 = (if( sec < 0) "-" else "") + min.toString + "⏊" + (100 + s).toString.substring( 1 )
         if( milli > 0 ) {
            s1 + "." + (milli + 1000).toString.substring( 1 )
         } else {
            s1
         }
      }
   }
}

class PeriodConstFactory( d: Double ) {
   def hours  = new PeriodConst( d * 360 )
   def mins   = new PeriodConst( d * 60 )
   def secs   = new PeriodConst( d )
   def msecs  = new PeriodConst( d / 1000 )
   def ⏊( b: Double ) = { require( d >= 0 ) // currently -0 is not caught, so better throw an exception
      new PeriodConst( d * 60 + b )
   }
   def ⎍( implicit sr: SampleRate ) = new PeriodConst( d / sr.rate )
}

case class SampleRate( rate: Double )
