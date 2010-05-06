/*
 *  Rect.scala
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

import collection.immutable.Vector

@specialized
case class Rect[ U ]( intervals: Vector[ Interval[ U ]])( implicit view: (U) => ManagedNumber[ U ], mgr: NumberManager[ U ])
extends Shape[ U ] {

	import mgr._

	val dim : Int = intervals.size

	lazy val area : U = {
		var i = 0
		var a = one
		while( i < dim ) {
			a *= interval( i ).span
			i += 1
		}
		a
	}

	def enlargement( s2: Shape[ U ]) : U = {
		union( s2 ).area - area
	}

	def union( s2: Shape[ U ]) : Shape[ U ] = {
		if( s2.dim != dim ) throw new IllegalArgumentException( "Dimensions do not match (" + dim + " vs. " + s2.dim + ")" )

		val v2 = new Array[ Interval[ U ]]( dim )
	    var i = 0
	    while( i < dim ) {
	    	v2( i ) = interval( i ).union( s2.interval( i ))
	    	i += 1
	    }
		Rect( Vector( v2:_* ))
	}

	def overlaps( s2: Shape[ U ]) : Boolean = {
		if( s2.dim != dim ) throw new IllegalArgumentException( "Dimensions do not match (" + dim + " vs. " + s2.dim + ")" )

		var i = 0
		while( i < dim ) {
			if( !interval( i ).overlaps( s2.interval( i ))) return false
			i += 1
		}
		true
	}

	def interval( dim: Int ) : Interval[ U ] = intervals( dim )
}
