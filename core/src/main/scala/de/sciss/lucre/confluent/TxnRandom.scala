/*
 *  TxnRandom.scala
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

import java.util.concurrent.atomic.AtomicLong

import de.sciss.lucre.stm

import scala.concurrent.stm.{InTxn, Ref}

/** Like java's random, but within a transactional cell. */
object TxnRandom {
  private final val multiplier  = 0x5DEECE66DL
  private final val mask        = (1L << 48) - 1
  private final val addend      = 11L

  /** Scrambles a seed value for initializing the underlying long variable.
    * Callers who use the `wrap` method may use this to initially fill the
    * wrapped variable based on a given seed.
    */
  def initialScramble(seed: Long): Long = (seed ^ multiplier) & mask

  private def calcSeedUniquifier(): Long = {
    while (true) {
      val current = seedUniquifier.get()
      val next = current * 181783497276652981L
      if (seedUniquifier.compareAndSet(current, next)) return next
    }
    sys.error("Never here")
  }

  private val seedUniquifier = new AtomicLong(8682522807148012L)

  def plain():           TxnRandom[InTxn] = plain(calcSeedUniquifier() ^ System.nanoTime())
  def plain(seed: Long): TxnRandom[InTxn] = new PlainImpl(Ref(initialScramble(seed)))

  def apply[S <: stm.Sys[S]](id: S#ID)(implicit tx: S#Tx): TxnRandom[S#Tx] =
    apply(id, calcSeedUniquifier() ^ System.nanoTime())

  def apply[S <: stm.Sys[S]](id: S#ID, seed: Long)(implicit tx: S#Tx): TxnRandom[S#Tx] =
    new SysImpl[S#Tx](tx.newLongVar(id, initialScramble(seed)))

  def wrap[Txn](peer: stm.Var[Txn, Long]): TxnRandom[Txn] = new SysImpl[Txn](peer)

  private sealed trait Impl[Txn] extends TxnRandom[Txn] {
    protected def refSet(seed: Long)(implicit tx: Txn): Unit
    protected def refGet(implicit tx: Txn): Long

    def nextBoolean()(implicit tx: Txn): Boolean = next(1) != 0

    def nextDouble()(implicit tx: Txn): Double =
      ((next(26).toLong << 27) + next(27)) / (1L << 53).toDouble

    def nextFloat()(implicit tx: Txn): Float = next(24) / (1 << 24).toFloat

    def nextInt()(implicit tx: Txn): Int = next(32)

    def nextInt(n: Int)(implicit tx: Txn): Int = {
      require(n > 0, "n must be positive")

      if ((n & -n) == n) {
        // n is a power of 2
        return ((n * next(31).toLong) >> 31).toInt
      }

      while (true) {
        val bits = next(31)
        val res = bits % n
        if (bits - res + n >= 1) return res
      }

      sys.error("Never here")
    }

    def nextLong()(implicit tx: Txn): Long = (next(32).toLong << 32) + next(32)

    def setSeed(seed: Long)(implicit tx: Txn): Unit = refSet(initialScramble(seed))

    private def next(bits: Int)(implicit tx: Txn): Int = {
      val oldSeed = refGet
      val nextSeed = (oldSeed * multiplier + addend) & mask
      refSet(nextSeed)
      (nextSeed >>> (48 - bits)).toInt
    }
  }

  private final class PlainImpl(seedRef: Ref[Long]) extends Impl[InTxn] {
    protected def refSet(value: Long)(implicit tx: InTxn): Unit = seedRef() = value

    protected def refGet(implicit tx: InTxn): Long = seedRef()
  }

  private final class SysImpl[Txn](seedRef: stm.Var[Txn, Long]) extends Impl[Txn] {
    protected def refSet(value: Long)(implicit tx: Txn): Unit = seedRef() = value

    protected def refGet(implicit tx: Txn): Long = seedRef()
  }
}

trait TxnRandom[-Txn] {
  def nextBoolean  ()(implicit tx: Txn): Boolean
  def nextDouble   ()(implicit tx: Txn): Double
  def nextFloat    ()(implicit tx: Txn): Float
  def nextInt      ()(implicit tx: Txn): Int
  def nextInt(n: Int)(implicit tx: Txn): Int
  def nextLong     ()(implicit tx: Txn): Long

  def setSeed(seed: Long)(implicit tx: Txn): Unit
}