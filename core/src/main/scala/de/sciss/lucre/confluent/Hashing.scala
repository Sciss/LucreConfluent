/*
 *  Hashing.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.confluent

/** A utility object implementing the prefix calculation for the randomized hash approach to storing
  * access paths.
  */
object Hashing {
  private val bitsInByte = Array.tabulate[Byte](256) { i =>
    var cnt = 0
    var n   = i
    while (n > 0) {
      cnt += n & 0x01
      n  >>= 1
    }
    cnt.toByte
  }

  private val eraseLSBMask = Array.tabulate[Byte](256) { i =>
    if (i == 0) 0xFF.toByte
    else {
      var bit = 0
      var n   = i
      while ((n & 1) == 0) {
        n  >>= 1
        bit += 1
      }
      (~(1 << bit)).toByte
    }
  }

  //   def prefixes( s: PathLike, contains: Long => Boolean ) : Iterator[ (Long, PathLike) ] = new Iterator[ (Long, PathLike) ]\

  /** Iterates over all the hash prefixes of a given path (excluding the full prefix itself).
    * The caller would typically test whether the returned element's sub path is empty or not, and store
    * an appropriate empty or partial tag in its representation. After the method returns, the
    * caller will typically add an entry for the full hash (`s.sum`), an entry which is not provided by the
    * iterator itself.
    *
    * @param s          the path for which to calculate the prefixes
    * @param contains   a test function whether a given hash is already stored in the caller's representation. only
    *                   prefixes are provided for hashes which are not already present according to this function
    * @return  an iterator over the prefixes.
    */
  def foreachPrefix(s: PathLike, contains: Long => Boolean)(fun: (Long, Long) => Unit): Unit = {
    val sz = s.size
    val m  = bitCount(sz)
    var j  = 1

    while (j < m) {
      val i = prefix(sz, j, m)
      //            val sp   = s.take( i )
      //            val sps  = sp.sum                             // "... we insert the values sum(\tau') ... to the table H"
      val sps = s.sumUntil(i)
      if (!contains(sps)) {
        // ", if they are not already there."
        val pre = maxPrefixKey(s, i, contains) // "... we compute ... the longest prefix of \tau' in \Pi"
        //            nextVar  = (sps, pre)                        // ", and store a pointer to a representation of this sequence."
        //            return true
        if (pre != 0L) fun(sps, pre)
      }
      j += 1
    }
  }

  //   def maxPrefixValue( s: PathLike, contains: Long => Boolean ) : Option[ V ] = {
  //      val pre1Len = maxPrefix1Len( s, s.size, contains )
  //      hash.get( pre1.sum ).orElse( hash.get( pre1.dropRight( 1 ).sum ))
  //   }

  private def prefix(n: Int, j: Int, m: Int): Int = {
    var zero = m - j
    var b0   = n & 0xFF
    val b0c  = bitsInByte(b0)
    if (b0c >= zero) {
      while (zero > 0) { b0 &= eraseLSBMask(b0); zero -= 1 }
      (n & 0xFFFFFF00) | b0
    } else {
      zero   -= b0c
      var b1  = (n >> 8) & 0xFF
      val b1c = bitsInByte(b1)
      if (b1c >= zero) {
        while (zero > 0) { b1 &= eraseLSBMask(b1); zero -= 1 }
        n & 0xFFFF0000 | (b1 << 8)
      } else {
        zero   -= b1c
        var b2  = (n >> 16) & 0xFF
        val b2c = bitsInByte(b2)
        if (b2c >= zero) {
          while (zero > 0) { b2 &= eraseLSBMask(b2); zero -= 1 }
          n & 0xFF000000 | (b2 << 16)
        } else {
          zero   -= b2c
          var b3  = (n >> 24) & 0xFF
          val b3c = bitsInByte(b3)
          if (b3c >= zero) {
            while (zero > 0) { b3 &= eraseLSBMask(b3); zero -= 1 }
            b3 << 24
          } else {
            throw new IndexOutOfBoundsException(s"$n, $j, $m")
          }
        }
      }
    }
  }

  def maxPrefixKey(s: PathLike, contains: Long => Boolean): Long = maxPrefixKey(s, s.size, contains)

  def maxPrefixKey(s: PathLike, sz: Int, contains: Long => Boolean): Long = {
    val pre1Len = maxPrefixLength(s, sz, contains)
    val pre1Sum = s.sumUntil(pre1Len)
    if (contains(pre1Sum)) pre1Sum else s.sumUntil(pre1Len - 1)
  }

  /** Searches for the maximum prefix of a path `s` that is also
    * a prefix of this path. Assumes that `s` _extends_ this path.
    *
    * @param s          the extension of this path
    * @param contains   a function to check the hash map built before using `foreachPrefix`
    * @return           the length of the prefix
    */
  def maxPrefixLength(s: PathLike, contains: Long => Boolean): Int = {
    val sz      = s.size
    val pre1Len = maxPrefixLength(s, sz, contains)
    val pre1Sum = s.sumUntil(pre1Len)
    if (contains(pre1Sum)) pre1Len else pre1Len - 1
  }

  private def maxPrefixLength(s: PathLike, sz: Int, contains: Long => Boolean): Int = {
    val m = bitCount(sz)
    // "We search for the minimum j, 1 <= j <= m(r), such that sum(p_i_j(r)) is not stored in the hash table H"
    val isPre = new Array[Int](m)
    var _i = 0
    while (_i < m) {
      val _i1   = _i + 1
      isPre(_i) = prefix(sz, _i1, m)
      _i        = _i1
    }

    var j  = -1
    var ij = -1
    do {
      j += 1
      // if( j == m ) return s   // all prefixes already contained
      if (j == m) return sz // all prefixes already contained
      ij = isPre(j)
    } while (contains(s.sumUntil(ij)))

    val ijm = if (j == 0) 0 else isPre(j - 1)

    val twoPk   = ij - ijm
    var d       = twoPk >> 1
    var twoPRho = d
    while (twoPRho >= 2) {
      twoPRho >>= 1
      val pre = s.sumUntil(ijm + d)
      d = if (contains(pre)) d + twoPRho else d - twoPRho
    }
    // s.take( ijm + d )
    ijm + d
  }

  /* Counts the 1 bits in an integer. */
  private def bitCount(n: Int): Int =
    bitsInByte( n         & 0xFF) +
    bitsInByte((n >>   8) & 0xFF) +
    bitsInByte((n >>  16) & 0xFF) +
    bitsInByte( n >>> 24        )
}