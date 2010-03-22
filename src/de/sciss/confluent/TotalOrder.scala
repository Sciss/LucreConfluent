/**
 *  TotalOrder
 *  (de.sciss.confluent package)
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

package de.sciss.confluent

import math.Ordering.{ Long => LongOrd }

/**
 *    This is an implementation of the "Simple O(log n) Amortized Time Algorithm"
 *    by Dietz and Sleator. I'm not smart enough to implement the worst-case version,
 *    so this might eventually produce trouble in a real-time context.
 *
 *    @version 0.10, 21-Mar-09
 */
object TotalOrder {
   private type Tag = Long

   trait Record {
      private[TotalOrder] var v: Tag
      private[TotalOrder] var pred: Record
      private[TotalOrder] var succ: Record
   }

   class UserRecord[ T ]( val elem: T, private[TotalOrder] var v: Long /* Tag */,
                          private[TotalOrder] var pred: Record,
                          private[TotalOrder] var succ: Record )
   extends Record

   // note: we need headroom to perform wj * k!
   // this is the maximum mask for which holds:
   // val test = mask * nmax; test / nmax == mask
   private val m: Tag      = 0x0000040000000000L
   private val mask: Tag   = m - 1;
   private val nmax        = (math.sqrt( m ) - 1).toInt  // i.e. 2965819
}

class TotalOrder[ T ] extends Ordering[ TotalOrder.Record ] {
   import TotalOrder._

   type URec = UserRecord[ T ]
//   private val b     = new Record( 0 )
   private var n     = 1   // i suppose we count the base... 

//   // ---- constructor ----
//   {
//      b.pred      = b
//      b.succ      = b
//   }

   /**
    *    Queries the order between two records x and y
    *
    *    @param   x  the first record to compare
    *    @param   y  the second record to compare
    *    @returns    true if x is an ancestor if y, false otherwise
    */
   def getOrder( x: Record, y: Record ) : Boolean = compare( x, y ) < 0

   /**
    *    Implementation of the Ordering trait
    */
   def compare( x: Record, y: Record ) : Int = {
      val vb  = Base.v
      val vbx = (x.v - vb) & mask
      val vby = (y.v - vb) & mask
      LongOrd.compare( vbx, vby )
   }

   /**
    *     Removes a record x from the order list
    *
    * @param x the record to remove
    *
    * @throws NullPointerException if trying to remove a record
    *             that had already been previously removed
    */
   def remove( x: Record ) {
      val pred    = x.pred
      val succ    = x.succ
      pred.succ   = succ
      succ.pred   = pred
      x.pred      = null
      x.succ      = null

      n -= 1
   }

//   /**
//    *    Inserts the first element
//    *    into the order. This throws an
//    *    exception if there already elements
//    *    in the order.
//    *
//    *    @param   y  the element to insert
//    *    @return     the corresponding record
//    */
//   def insert( y: T ) : Rec = {
//      require( n == 1 )
//      insert( Base, y )
//   }

   /**
    *    Inserts an element 'y' immediately after 
    *    a given record 'x', creating and returning
    *    a new record for it, and performing relabeling
    *    if necessary
    *
    * @param x the predecessor after which to insert
    * @param y the element to insert
    * @returns the record for 'y'
    */
   def insert( x: Record, y: T ) : URec = {
      if( n == nmax ) error( "Maximum capacity reached" )
      if( (x == Base) && (n != 1) ) error( "Should only insert first element after base")

      val v0      = x.v
      var j       = 1
      val sx      = x.succ
      var iter    = sx
      var wj      = ((iter.v - v0) & mask)
      while( wj <= (j*j) ) {
         iter     = iter.succ
         j       += 1
         wj       = ((iter.v - v0) & mask)
      }
      var k       = 1
      iter        = sx
      while( k < j ) {
         iter.v   = (wj * k / j + v0) & mask
         iter     = iter.succ
         k       += 1
      }
      val vb      = Base.v
      val vbxstar = if( sx == Base ) m else ((sx.v - vb) & mask)
      val vy      = (((v0 - vb) & mask) + vbxstar) / 2

      val rec     = new URec( y, vy, x, sx )

      n += 1
      rec
   }

   object Base extends Record {
      var v: Tag = 0 // arbitrary
      var pred: Record = this
      var succ: Record = this
   }
}