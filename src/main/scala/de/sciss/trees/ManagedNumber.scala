/*
 *  ManagedNumber.scala
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

package de.sciss.trees

/**
 *  @version  0.12, 14-Mar-10
 */
@specialized trait ManagedNumber[ N ] {
	@inline def <( x: N ) : Boolean
	@inline def >( x: N ) : Boolean
	@inline def +( x: N ) : N
	@inline def -( x: N ) : N
	@inline def *( x: N ) : N
	@inline def /( x: N ) : N
	@inline def abs : N
	@inline def min( x: N ) : N
	@inline def max( x: N ) : N
}

@specialized trait NumberManager[ N ] {
	@inline val zero : N
	@inline val one : N
	@inline val min : N
	@inline val max : N
}

class ManagedInt( i: Int ) extends ManagedNumber[ Int ] {
	@inline def <( x: Int ) = i < x
	@inline def >( x: Int ) = i > x
	@inline def +( x: Int ) = i + x
	@inline def -( x: Int ) = i - x
	@inline def *( x: Int ) = i * x
	@inline def /( x: Int ) = i / x
	@inline def abs = math.abs( i )
	@inline def min( x: Int ) = math.min( i, x )
	@inline def max( x: Int ) = math.max( i, x )
}

class ManagedLong( n: Long) extends ManagedNumber[ Long ] {
	@inline def <( x: Long ) = n < x
	@inline def >( x: Long ) = n > x
	@inline def +( x: Long ) = n + x
	@inline def -( x: Long ) = n - x
	@inline def *( x: Long ) = n * x
	@inline def /( x: Long ) = n / x
	@inline def abs = math.abs( n )
	@inline def min( x: Long ) = math.min( n, x )
	@inline def max( x: Long ) = math.max( n, x )
}

class ManagedDouble( d: Double ) extends ManagedNumber[ Double ] {
	@inline def <( x: Double ) = d < x
	@inline def >( x: Double ) = d > x
	@inline def +( x: Double ) = d + x
	@inline def -( x: Double ) = d - x
	@inline def *( x: Double ) = d * x
	@inline def /( x: Double ) = d / x
	@inline def abs = math.abs( d )
	@inline def min( x: Double ) = math.min( d, x )
	@inline def max( x: Double ) = math.max( d, x )
}

object IntManager extends NumberManager[ Int ] {
	@inline val zero = 0
	@inline val one = 1
	@inline val min = Int.MinValue
	@inline val max = Int.MaxValue
}

object LongManager extends NumberManager[ Long ] {
	@inline val zero = 0L
	@inline val one = 1L
	@inline val min = Long.MinValue
	@inline val max = Long.MaxValue
}

object DoubleManager extends NumberManager[ Double ] {
	@inline val zero = 0.0
	@inline val one = 1.0
	@inline val min = Double.MinValue
	@inline val max = Double.MaxValue
}

object Implicits {
	implicit def managedInt( i: Int ) = new ManagedInt( i )
	implicit val intManager = IntManager
}