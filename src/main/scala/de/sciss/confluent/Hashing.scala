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
import collection.breakOut
import collection.immutable.{IntMap, LongMap}
import collection.mutable.{Set => MSet}
import util.Random

object Hashing {
   type IntSeq    = FingerTree.IndexedSummed[ Int, Long ]
   type IntSeqMap = LongMap[ IntSeq ]
   def IntSeq( is: Int* ) : IntSeq = FingerTree.IndexedSummed.applyWithView[ Int, Long ]( is: _* )
   val emptyIntSeq = IntSeq()

   private val rndTaken    = MSet( 0 ) // .empty[ Int ]
   private val sumsTaken   = MSet.empty[ Long ]
   private val rnd         = new Random()

   /**
    * Test function to create a new version vertex
    * and append it to a given path. Warning: Not synchronized.
    */
   def append( s: IntSeq ) : IntSeq = {
      while( true ) {
         val r = rnd.nextInt() & 0x7FFFFFFF
         if( !rndTaken.contains( r )) {   // unique vertices
            val res = s :+ r
            if( sumsTaken.add( res.sum )) {  // unique sums
               rndTaken.add( r )
               return res
            }
         }
      }
      error( "Never here" )
   }

   def appendn( s: IntSeq, n: Int ) : IntSeq = (0 until n).foldLeft( s )( (s1, _ ) => append( s1 ))

   def genSeq( n: Int ) : IntSeq = appendn( emptyIntSeq, n )

   /**
    * Counts the 1 bits in an integer.
    */
   def bitCount( n: Int ) : Int = {
      bitsInByte( n & 0xFF ) +
      bitsInByte( (n >> 8)  & 0xFF ) +
      bitsInByte( (n >> 16) & 0xFF ) +
      bitsInByte( n >>> 24 )
   }

//   def bitCount( n: Long ) : Int = bitCount( n.toInt ) + bitCount( (n >> 32).toInt )

   def buildFullSeqMap( ss: IntSeq* ) : IntSeqMap  = ss.map( s => s.sum -> s )( breakOut )
   def buildPreSeqMap( ss: IntSeq* ) : IntSeqMap   = ss.flatMap( buildPrefixes( _ ).map( s => s.sum -> s ))( breakOut )

   def buildHashTable( ss: IntSeq* ) : IntSeqMap = {
      val fullSeqMap = buildFullSeqMap( ss: _* )
      val preSeqMap  = buildPreSeqMap( ss: _* )
      buildHashTable( fullSeqMap, preSeqMap )
   }

   def buildHashTable( fullSeqMap: IntSeqMap, preSeqMap: IntSeqMap ) : IntSeqMap =
      preSeqMap.view.map( entry => entry._1 -> maxPrefix( entry._2, fullSeqMap )).filter( _._2.nonEmpty )
         .force[ (Long, IntSeq), IntSeqMap ]( breakOut )

   def buildPrefixes( s: IntSeq ) : Seq[ IntSeq ] = {
      val sz   = s.size
      val m    = bitCount( sz )
//      (1 to m).map( j => s.take( prefix( sz, j, m )))
      (1 until m).map( j => s.take( prefix( sz, j, m ))) :+ s
   }

   def main( args: Array[ String ]) {
      val (seq, hash) = example
      val Seq( p, q, k ) = seq
//      println( "Assert maxPrefix( q :+ _ ) == q         ?  " + (maxPrefix( append( q ), hash ).toList      == q.toList) )
//      println( "Assert maxPrefix( p ) == p              ?  " + (maxPrefix( p, hash ).toList                == p.toList) )
      println( "Assert maxPrefix( p.dropRight(1) ) == k ?  " + (maxPrefix( p.dropRight( 1 ), hash ).toList == k.toList) )
   }

   def example : (Seq[ IntSeq ], IntSeqMap) = {
      val p    = genSeq( 298 )
      val q    = genSeq( 17 )
      val k    = p.take( 272 )
      val seq  = List( p, q, k )
//      val hash = buildHashTable( seq: _* )
println( "Warning: buildHashTable not yet working" )
//      val hash = collection.immutable.LongMap( p.take(288).sum -> k, p.take(296).sum -> k, k.sum -> k, p.sum -> p, q.sum -> q )
val hash = collection.immutable.LongMap( p.take(288).sum -> k, p.take(296).sum -> k, k.sum -> k, p.sum -> p, q.sum -> q, q.take(16).sum -> q )
      seq -> hash
   }

//   def prefix( n: Long, j: Int ) : Long = prefix( n, j, bitCount( n ))
//
//   def prefix( n: Long, j: Int, m: Int ) : Long = {
//      var zero    = m - j
//      var shifted = n
//      var shift   = 0
//      var mask    = 0xFFFFFFFFFFFFFF00L
//      while( shifted != 0 ) {
//         var b       = (shifted & 0xFF).toInt
//         val bc      = bitsInByte( b )
//         if( bc >= zero ) {
//            while( zero > 0 ) { b &= eraseMSBMask( b ); zero -= 1 }
////            return( n & mask | b.toLong << shift )
//            return( n & mask | (b.toLong << shift) )
//         }
//         shift     += 8
//         shifted >>>= 8
//         mask     <<= 8
//      }
//      throw new IndexOutOfBoundsException( j.toString + ", " + m.toString )
//   }

   def prefix( n: Int, j: Int ) : Int = prefix( n, j, bitCount( n ))

   def prefix( n: Int, j: Int, m: Int ) : Int = {
      var zero    = m - j
      var b0      = n & 0xFF
      val b0c     = bitsInByte( b0 )
      if( b0c >= zero ) {
         while( zero > 0 ) { b0 &= eraseLSBMask( b0 ); zero -= 1 }
         (n & 0xFFFFFF00) | b0
      } else {
         zero       -= b0c
         var b1      = (n >> 8) & 0xFF
         val b1c     = bitsInByte( b1 )
         if( b1c >= zero ) {
            while( zero > 0 ) { b1 &= eraseLSBMask( b1 ); zero -= 1 }
            n & 0xFFFF0000 | (b1 << 8)
         } else {
            zero       -= b1c
            var b2      = (n >> 16) & 0xFF
            val b2c     = bitsInByte( b2 )
            if( b2c >= zero ) {
               while( zero > 0 ) { b2 &= eraseLSBMask( b2 ); zero -= 1 }
               n & 0xFF000000 | (b2 << 16)
            } else {
               zero       -= b2c
               var b3      = (n >> 24) & 0xFF
               val b3c     = bitsInByte( b3 )
               if( b3c >= zero ) {
                  while( zero > 0 ) { b3 &= eraseLSBMask( b3 ); zero -= 1 }
                  b3 << 24
               } else {
                  throw new IndexOutOfBoundsException( n.toString + ", " + j.toString + ", " + m.toString )
               }
            }
         }
      }
   }

//   def test( n: Int ) { val m = bitCount( n ); for( i <- 0 to m ) println( (prefix( n, i, m ) | 0x100).toBinaryString.substring( 1 ))}

   def test2 {
      val (Seq( p, q, k ), hash) = example
      val res = maxPrefix( p, hash ).size
      println( res )
   }

   def test {
      val (Seq( p, q, k ), set) = example
      val set2 = LongMap( p.take(288).sum -> k, p.take(296).sum -> k, k.sum -> k, p.sum -> p, q.sum -> q, q.take(16).sum -> q )
      assert( set == set2, "assertion 1" )
      assert( maxPrefix( k, set ).toList == k.toList, "assertion 2" )
   }

   def maxPrefix( s: IntSeq, hash: LongMap[ _ ]) : IntSeq = {
      val sz      = s.size
      val m       = bitCount( sz )
// Oki, here's my guess how it should work. forget about the next nine lines
//      // "We search for the minimum j, 1 <= j <= m(r), such that sum(p_i_j(r)) is not stored in the hash table H"
//      val is      = Array.tabulate( m )( i => i -> prefix( sz, i + 1, m ))
//      println( "is : " + is.map( tup => (tup._1 + 1).toString + " -> " + tup._2.toBinaryString ).mkString( ", " ))
//      val noPres  = is.filter( tup => !hash.contains( s.take( tup._2 ).sum ))
//      println( "noPres : " + noPres.map( tup => (tup._1 + 1).toString + " -> " + tup._2.toBinaryString ).mkString( ", " ))
//      // "If there is no such j then sum(r) itself is stored in the hash table H so r' = r"
//      if( noPres.isEmpty ) return s
//      val (j, ij) = noPres.min      // j - 1 actually
//      val ijm     = if( j == 0 ) 0 else is( j - 1 )._2
// ...and instead determine j this way:
      val is      = Array.tabulate( m )( i => prefix( sz, i + 1, m ))
println( "is : " + is.zipWithIndex.map( tup => (tup._2 + 1).toString + " -> " + tup._1 + " (= " + tup._1.toBinaryString + "b)" ).mkString( ", " ))
      val j       = is.lastIndexWhere( i => hash.contains( s.take( i ).sum )) + 1
      if( j == m ) return s
      val ij      = is( j )
      val ijm     = if( j == 0 ) 0 else is( j - 1 )

      val twopk   = ij - ijm
println( "j = " + (j + 1 ) + ", i_j = " + ij + ", i_j-1 = " + ijm + ", 2^k = " + twopk )
      var d       = twopk >> 1
      var twoprho = d
println( "d = " + d + ", 2^rho = " + twoprho )
      while( twoprho >= 2 ) {
         twoprho >>= 1
         val pre  = s.take( ijm + d )
         d = if( hash.contains( pre.sum )) d + twoprho else d - twoprho
println( "d = " + d + ", 2^rho = " + twoprho )
      }
      val pre1 = s.take( ijm + d )
      if( hash.contains( pre1.sum )) pre1 else pre1.dropRight( 1 )
   }

   /**
    * Performs ceil(log2(bitCount(sum))+1 prefix calculations and lookups.
    */
   def maxPrefixXXX( s: IntSeq, set: LongMap[ _ ]) : IntSeq = {
      val sz      = s.size
      val m       = bitCount( sz )
      var k       = (m + 1) >> 1
      var found   = emptyIntSeq
      var ceil    = m
      var floor   = 0
      var kp      = 0
      do {
println( "floor = " + floor + ", ceil = " + ceil + ", k = " + k )
         val presz   = prefix( sz, k, m )
         kp          = k
         val pre     = s.take( presz )
         if( set.contains( pre.sum )) {
println( "   " + presz + " -> found" )
            found    = pre
            k        = (ceil + 1 + k) >> 1
            ceil     = kp
         } else {
println( "   " + presz + " -> no" )
            k        = (floor + k) >> 1
            floor    = kp
         }
      } while( kp != k )
      found
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

//   def maxPrefix( s: IntSeq, set: IntSeqMap ) : IntSeq = {
//      val ssum = s.sum
//      if( set.contains( ssum )) s else {
//         val m = bitCount( ssum )
//
//      }
//   }

   // For a list of algorithms see:
   // http://gurmeet.net/puzzles/fast-bit-counting-routines/
   // If we figure that this is a bottleneck somehow, we can still do an 11- or 16-bit version...
   val bitsInByte = Array.tabulate[ Byte ]( 256 )( i => {
      var cnt = 0
      var n   = i
      while( n > 0 ) {
         cnt += n & 0x01
         n >>= 1
      }
      cnt.toByte
   })

//   val eraseMSBMask = Array.tabulate[ Byte ]( 256 )( i => {
//      var bit = -1
//      var n   = i
//      while( n > 0 ) {
//         n  >>= 1
//         bit += 1
//      }
//      (~(1 << bit)).toByte
//   })

   val eraseLSBMask = Array.tabulate[ Byte ]( 256 )( i => {
      if( i == 0 ) 0xFF.toByte else {
         var bit = 0
         var n   = i
         while( (n & 1) == 0 ) {
            n  >>= 1
            bit += 1
         }
         (~(1 << bit)).toByte
      }
   })
}