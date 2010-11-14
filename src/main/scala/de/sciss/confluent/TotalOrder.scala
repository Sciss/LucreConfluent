/*
 *  TotalOrder
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

package de.sciss.confluent

import math.Ordering.{ Long => LongOrd }

/**
 *    This is derived from an implementation of the "Simple O(log n) Amortized Time Algorithm"
 *    by Dietz and Sleator. I'm not smart enough to implement the worst-case version,
 *    so this might eventually produce trouble in a real-time context.
 *
 *    The original insert would _not_ produce a pre-order traversal of a tree as
 *    claimed by DSST. to fix this, we add a special stop-marker record in the pre-order
 *    version that allows us to jump to the end of a parent's children list in O(1)
 *
 * XXX THIS IS MUTABLE AND SHOULD NOT BE. NOTE THAT WE ONLY USE INSERTION, SO WE MIGHT
 * GET AWAY JUST WITH ATOMIC ENCAPSULATION...
 *
 *    @version 0.13, 11-Apr-10
 */
object TotalOrder {
   type Tag = Long

   trait Record[ T, Repr ] {
      var v: Tag
      var pred: Repr
      var succ: Repr
      def moveRight: Repr
      def elem: T
   }

   // note: we need headroom to perform wj * k!
   // this is the maximum mask for which holds:
   // val test = mask * nmax; test / nmax == mask
   private val m: Tag      = 0x0000040000000000L
   private val mask: Tag   = m - 1;
   private val nmax        = (math.sqrt( m ) - 1).toInt  // i.e. 2965819
}

abstract class TotalOrder[ T, Rec <: TotalOrder.Record[ T, Rec ]]
extends Ordering[ Rec ] {
   import TotalOrder._

   protected var n     = 1   // i suppose we count the base...

   def insertChild( parent: Rec, child: T ) : Rec
   def insertRetroParent( child: Rec, parent: T ) : Rec
   def insertRetroChild( parent: Rec, child: T ) : Rec

   protected def createRecord( elem: T, vch: Tag, pred: Rec, succ: Rec ) : Rec
   val base: Rec

   /**
    *    Implementation of the Ordering trait
    */
   def compare( x: Rec, y: Rec ) : Int = {
      val vb  = base.v
      val vbx = (x.v - vb) & mask
      val vby = (y.v - vb) & mask
      LongOrd.compare( vbx, vby )
   }

   /**
    *    Inserts the first element
    *    into the order. This throws an
    *    exception if there already elements
    *    in the order.
    *
    *    @param   root  the element to insert
    *    @return     the corresponding record
    */
   def insertRoot( root: T ) : Rec = {
      require( n == 1 )
      insertAfter( base, base.v, root )
   }

   def root : Rec = base.succ // XXX ok?

   /**
    *    Inserts a child element
    *
    * @param   parent the parent after which to insert
    * @param   child the element to insert
    * @returns the child's record
    */
   protected def insertAfter( pred: Rec, v0: Tag, elem: T ) : Rec = {
      if( n == nmax ) error( "Maximum capacity reached" )

      var j       = 1L  // careful to use long since we do j*j
      val sp      = pred.moveRight
      var iter    = sp
      var wj      = if( j < n ) ((iter.v - v0) & mask) else m
      while( wj <= (j*j) ) {
         iter     = iter.moveRight
         j       += 1
         wj       = if( j < n ) ((iter.v - v0) & mask) else m
      }
      var k       = 1L
      iter        = sp
      while( k < j ) {
         iter.v   = (wj * k / j + v0) & mask
         iter     = iter.moveRight
         k       += 1
      }
      val vb      = base.v
      val vbpstar = if( sp == base ) m else ((sp.v - vb) & mask)
      val vch     = (((v0 - vb) & mask) + vbpstar) / 2

      val rec     = createRecord( elem, vch, pred, pred.succ ) // use parent.succ here to _not_ skip tail marker

      n += 1
      rec
   }

   def inspect {
      var iter    = base.moveRight
      var succ    = false
      print( "[")
      while( iter != base ) {
         if( succ ) print( ", " ) else succ = true
         print( iter )
         iter     = iter.moveRight
      }
      println( "]")
   }

   def inspectVerbose {
      var iter    = base
      var succ    = false
      print( "[")
      do {
         if( succ ) print( ", " ) else succ = true
         print( iter )
         iter     = iter.succ
      } while( iter != base )
      println( "]")
   }
}

object PreOrder {
   trait Record[ T ] extends TotalOrder.Record[ T, Record[ T ]] {
      def tail: Record[ T ]
      def moveLeft: Record[ T ]  = pred.skipLeft
      def moveRight: Record[ T ] = succ.skipRight
      def skipLeft: Record[ T ]  = this
      def skipRight: Record[ T ] = this
   }
}

//////////////////////////////////////////////////////////////////
//  P r e O r d e r  /////////////////////////////////////////////
//////////////////////////////////////////////////////////////////

/**
 *    @todo    tailmark could be created lazily. for d(V) >> e(V) this would
 *             save quite some space
 */
class PreOrder[ T ] extends TotalOrder[ T, PreOrder.Record[ T ]] {
   import TotalOrder.{ Tag }
   import PreOrder.{ Record }
   type Rec = Record[ T ]

   def insertChild( parent: Rec, child: T ) : Rec = {
      require( n > 1 )
      val tail = parent.tail
      val pred = tail.pred
      val v0   = tail.moveLeft.v 
      insertAfter( pred, v0, child )
   }

   def insertRetroParent( child: Rec, parent: T ) : Rec = {
      val pred = child.moveLeft
      insertAfter( pred, pred.v, parent )
   }

   def insertRetroChild( parent: Rec, child: T ) : Rec =
      insertAfter( parent, parent.v, child )

   protected def createRecord( child: T, vch: Tag, parent: Rec, sp: Rec ) : URec = {
      val rec     = new URec( child, vch, parent, sp )
      val tail    = rec.tail
      parent.succ = rec
      sp.pred     = tail
      rec
   }

   val base = Base

   protected object Base extends Rec {
      var v: Tag = 0 // arbitrary
      var pred: Rec = this
      var succ: Rec = this
      def elem = error( "Illegal Access" ) // not very pretty, but we had to escape from type hell
      def tail = error( "Illegal Access" )

      override def toString = "Base"
   }

   protected class TailMark( ref: Rec, var succ: Rec ) extends Rec {
      var pred = ref
//      var v: Tag = 0 // never used
      def v: Tag = error( "Illegal Access" )
      def v_=( newTag: Tag ) = error( "Illegal Access" ) 
      override def skipLeft: Rec  = moveLeft
      override def skipRight: Rec = moveRight
      def elem = error( "Illegal Access" )
      def tail = error( "Illegal Access" )

      override def toString = "M(" + ref + ")"
   }

   class URec( val elem: T, var v: Tag, var pred: Rec, tailSucc: Rec )
   extends Rec {
      val tail = new TailMark( this, tailSucc )
      var succ: Rec = tail

      override def toString = elem.toString // + " #" + v
   }
}

//////////////////////////////////////////////////////////////////
//  P o s t O r d e r  ///////////////////////////////////////////
//////////////////////////////////////////////////////////////////

object PostOrder {
   trait Record[ T ] extends TotalOrder.Record[ T, Record[ T ]]
}

class PostOrder[ T ] extends TotalOrder[ T, PostOrder.Record[ T ]] {
   import TotalOrder.{ Tag }
   import PostOrder.{ Record }
   type Rec = Record[ T ]

   def insertChild( parent: Rec, child: T ) : Rec = {
      require( n > 1 )
      val pred = parent.pred
      insertAfter( pred, pred.v, child )
   }

   def insertRetroParent( child: Rec, parent: T ) : Rec =
      insertAfter( child, child.v, parent )

   def insertRetroChild( parent: Rec, child: T ) : Rec =
      insertChild( parent, child ) // i.e. insertAfter( parent.pred, ... )

   protected def createRecord( child: T, vch: Tag, parent: Rec, sp: Rec ) : Rec = {
      val rec     = new URec( child, vch, parent, sp )
      parent.succ = rec
      sp.pred     = rec
      rec
   }

   val base = Base

   protected object Base extends Rec {
      var v: Tag = 0 // arbitrary
      var pred: Rec = this
      var succ: Rec = this
      def moveRight = succ
      def elem = error( "Illegal Access" )

      override def toString = "Base"
   }

   class URec( val elem: T, var v: Tag, var pred: Rec, var succ: Rec )
   extends Rec {
      def moveRight: Rec = succ
      override def toString = elem.toString
   }
}

