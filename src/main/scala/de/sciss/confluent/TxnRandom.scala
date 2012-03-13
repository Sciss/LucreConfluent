/*
 *  TxnRandom.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 */

package de.sciss.confluent

import concurrent.stm.{Ref, InTxn}
import java.util.concurrent.atomic.AtomicLong

/**
 * Like java's random, but within a transactional cell
 */
object TxnRandom {
   private val multiplier  = 0x5DEECE66DL
   private val mask        = (1L << 48) - 1
   private val addend      = 11L

   private def initialScramble( seed: Long ) = (seed ^ multiplier) & mask

   private def calcSeedUniquifier() : Long = {
      while( true ) {
         val current = seedUniquifier.get()
         val next    = current * 181783497276652981L
         if( seedUniquifier.compareAndSet( current, next )) return next
      }
      sys.error( "Never here" )
   }

   private val seedUniquifier = new AtomicLong( 8682522807148012L )

   def apply() : TxnRandom = apply( calcSeedUniquifier() ^ System.nanoTime() )
   def apply( seed: Long ) : TxnRandom = new Impl( Ref( initialScramble( seed )))

   private final class Impl( seedRef: Ref[ Long ]) extends TxnRandom {
      def nextBoolean()( implicit tx: InTxn ) : Boolean = next( 1 ) != 0

      def nextDouble()( implicit tx: InTxn ) : Double =
         ((next( 26 ).toLong << 27) + next( 27 )) / (1L << 53).toDouble

      def nextFloat()( implicit tx: InTxn ) : Float = next( 24 ) / (1 << 24).toFloat

      def nextInt()( implicit tx: InTxn ) : Int = next( 32 )

      def nextInt( n: Int )( implicit tx: InTxn ) : Int = {
         require( n > 0, "n must be positive" )

         if( (n & -n) == n ) {  // n is a power of 2
            return ((n * next( 31 ).toLong) >> 31).toInt
         }

         do {
            val bits = next( 31 )
            val res  = bits % n
            if( (bits - res + n) >= 1 ) return res
         } while( true )

         sys.error( "Never here" )
      }

      def nextLong()( implicit tx: InTxn ) : Long = (next( 32 ).toLong << 32) + next( 32 )

      def setSeed( seed: Long )( implicit tx: InTxn ) {
         seedRef.set( initialScramble( seed ))
//         haveNextNextGaussian = false
      }

      private def next( bits: Int )( implicit tx: InTxn ) : Int = {
         val oldSeed    = seedRef.get
         val nextSeed   = (oldSeed * multiplier + addend) & mask
         seedRef.set( nextSeed )
         (nextSeed >>> (48 - bits)).toInt
      }
   }
}
trait TxnRandom {
   def nextBoolean()( implicit tx: InTxn ) : Boolean
   def nextDouble()( implicit tx: InTxn ) : Double
   def nextFloat()( implicit tx: InTxn ) : Float
   def nextInt()( implicit tx: InTxn ) : Int
   def nextInt( n: Int )( implicit tx: InTxn ) : Int
   def nextLong()( implicit tx: InTxn ) : Long
   def setSeed( seed: Long )( implicit tx: InTxn ) : Unit
}