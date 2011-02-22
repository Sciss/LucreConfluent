/*
 *  Hashing.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

import de.sciss.fingertree.FingerTree
import collection.immutable.LongMap

object Hashing {
   type IntSeq    = FingerTree.IndexedSummed[ Int, Long ]
   type IntSeqSet = LongMap[ IntSeq ]
   def IntSeq( is: Int* ) : IntSeq = FingerTree.IndexedSummed.applyWithView[ Int, Long ]( is: _* )

   /**
    * Counts the 1 bits in an integer.
    */
   def bitCount( n: Int ) : Int = {
      bitsInByte( n & 0xFF ) +
      bitsInByte( (n >> 8)  & 0xFF ) +
      bitsInByte( (n >> 16) & 0xFF ) +
      bitsInByte( n >>> 24 )
   }

   def buildSet( ss: IntSeq* ) : IntSeqSet = LongMap( ss.map( s => (s.sum, s) ): _* )

   def prefix( n: Int, j: Int ) : Int = prefix( n, j, bitCount( n ))

   def prefix( n: Int, j: Int, m: Int ) : Int = {
//      n & (0xFFFFFFFF << (m - j))
      var zero = m - j
      var b0   = n & 0xFF
      val b0c  = bitsInByte( b0 )
      if( b0c >= zero ) {
         while( zero > 0 ) { b0 &= eraseMSBMask( b0 ); zero -= 1 }
         n & 0xFFFFFF00 | b0
      } else {
         zero -= b0c
         var b1   = (n >> 8) & 0xFF
         val b1c  = bitsInByte( b1 )
         if( b1c >= zero ) {
            while( zero > 0 ) { b1 &= eraseMSBMask( b1 ); zero -= 1 }
            n & 0xFFFF0000 | (b1 << 8)
         } else {
            zero -= b1c
            var b2   = (n >> 16) & 0xFF
            val b2c  = bitsInByte( b2 )
            if( b1c >= zero ) {
               while( zero > 0 ) { b2 &= eraseMSBMask( b2 ); zero -= 1 }
               n & 0xFF000000 | (b1 << 16)
            } else {
               zero -= b2c
               var b3   = (n >> 24) & 0xFF
               val b3c  = bitsInByte( b3 )
               if( b3c >= zero ) {
                  while( zero > 0 ) { b3 &= eraseMSBMask( b3 ); zero -= 1 }
                  b3 << 24
               } else {
                  throw new IndexOutOfBoundsException( j.toString + ", " + m.toString )
               }
            }
         }
      }
   }

//   def test( n: Int ) { val m = bitCount( n ); for( i <- 0 to m ) println( (prefix( n, i, m ) | 0x100).toBinaryString.substring( 1 ))}

   /**
    * Performs ceil(log2(bitCount(sum))+1 prefix calculations and lookups.
    */
   def maxPrefix( sum: Int, set: LongMap[ _ ]) : Int = {
      val m       = bitCount( sum )
      var step    = (m + 1) >> 1
      var k       = m - step
      var found   = 0
      do {
         val pre  = prefix( sum, k, m )
         if( set.contains( pre ) {
            if( step == 0 ) return pre
            found = pre
            k    += step
            step  = if( step > 1 ) (step + 1) >> 1 else 0
         } else {
            if( step == 0 ) return found
            k    -= step
            step  = if( step > 1 ) (step + 1) >> 1 else 0
         }
      } while( true )
      error( "Never here" )
   }

//   def test( m: Int, hit: Int ) : (Int, Int) = {
//      var step    = (m + 1) >> 1
//      var k       = m - step
//      var found   = 0
//var iter = 0
//      do {
//         iter  += 1
//         if( k <= hit ) {
//            if( step == 0 ) return (iter, k)
//            found = k
//            k    += step
//            step = if( step > 1 ) (step + 1) >> 1 else 0
//         } else {
//            if( step == 0 ) return (iter, found)
//            k    -= step
//            step = if( step > 1 ) (step + 1) >> 1 else 0
//         }
//      } while( true )
//      error( "TODO" )
//   }
//
//   def suite( fun: (Int, Int) => (Int, Int), min: Int, max: Int ) {
//      for( m <- min to max ) {
//         for( hit <- min to m ) {
//            val (iter, res) = fun( m, hit )
//            assert( res == hit, (m, hit) )
////            println( "" + m + ", " + hit + ", " + iter )
//         }
//      }
//   }

//   def maxPrefix( s: IntSeq, set: IntSeqSet ) : IntSeq = {
//      val ssum = s.sum
//      if( set.contains( ssum )) s else {
//         val m = bitCount( ssum )
//
//      }
//   }

   // For a list of algorithms see:
   // http://gurmeet.net/puzzles/fast-bit-counting-routines/
   // If we figure that this is a bottleneck somehow, we can still do an 11- or 16-bit version...
   private val bitsInByte = Array.tabulate[ Byte ]( 256 )( i => {
      var cnt = 0
      var n   = i
      while( n > 0 ) {
         cnt += n & 0x01
         n >>= 1
      }
      cnt.toByte
   })

   private val eraseMSBMask = Array.tabulate[ Byte ]( 256 )( i => {
      var bit = -1
      var n   = i
      while( n > 0 ) {
         n  >>= 1
         bit += 1
      }
      (~(1 << bit)).toByte
   })
}