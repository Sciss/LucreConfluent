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
import de.sciss.lucre.stm.{MutableSerializer, InMemory}
import de.sciss.serial.{DataInput, DataOutput, Serializer}

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

  private sealed trait SysLike[Txn] extends Impl[Txn] {
    protected def seedRef: stm.Var[Txn, Long]

    protected final def refSet(value: Long)(implicit tx: Txn): Unit = seedRef() = value

    protected final def refGet(implicit tx: Txn): Long = seedRef()
  }

  private final class SysImpl[Txn](protected val seedRef: stm.Var[Txn, Long]) extends SysLike[Txn]

  private final class PersistentImpl[S <: stm.Sys[S]](val id: S#ID, protected val seedRef: stm.Var[S#Tx, Long])
    extends SysLike[S#Tx] with Persistent[S] {

    def write(out: DataOutput): Unit = {
      id     .write(out)
      seedRef.write(out)
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      id     .dispose()
      seedRef.dispose()
    }
  }

  object Persistent {
    def apply[S <: stm.Sys[S]]()(implicit tx: S#Tx): Persistent[S] =
      apply(calcSeedUniquifier() ^ System.nanoTime())

    def apply[S <: stm.Sys[S]](seed: Long)(implicit tx: S#Tx): Persistent[S] = {
      val id = tx.newID()
      new PersistentImpl[S](id, tx.newLongVar(id, initialScramble(seed)))
    }

    def read[S <: stm.Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Persistent[S] =
      serializer[S].read(in, access)

    implicit def serializer[S <: stm.Sys[S]]: Serializer[S#Tx, S#Acc, Persistent[S]] = anySer.asInstanceOf[Ser[S]]

    private val anySer = new Ser[InMemory]

    private final class Ser[S <: stm.Sys[S]] extends MutableSerializer[S, Persistent[S]] {
      protected def readData(in: DataInput, id: S#ID)(implicit tx: S#Tx): Persistent[S] = {
        val seedRef = tx.readVar[Long](id, in)
        new PersistentImpl(id, seedRef)
      }
    }
  }
  /** A random number generator that can be persisted (written and read). */
  sealed trait Persistent[S <: stm.Sys[S]] extends TxnRandom[S#Tx] with stm.Mutable[S#ID, S#Tx]
}

/** A transactional pseudo-random number generator which
  * behaves numerically like `java.util.Random`.
  */
trait TxnRandom[-Txn] {
  /** Generates a random `Boolean` value. */
  def nextBoolean  ()(implicit tx: Txn): Boolean

  /** Generates a random `Double` value, uniformly distributed
    * between `0.0` (inclusive) and `1.0` (exclusive).
    */
  def nextDouble   ()(implicit tx: Txn): Double

  /** Generates a random `Float` value, uniformly distributed
    * between `0.0f` (inclusive) and `1.0f` (exclusive).
    */
  def nextFloat    ()(implicit tx: Txn): Float

  /** Generates a random `Int` value in the range `Int.MinValue` to `Int.MaxValue`. */
  def nextInt      ()(implicit tx: Txn): Int

  /** Generates a random `Int` value in the range of 0 (inclusive) until the specified value `n` (exclusive). */
  def nextInt(n: Int)(implicit tx: Txn): Int

  /** Generates a random `Long` value in the range `Long.MinValue` to `Long.MaxValue`.
    *
    * __WARNING:__
    * Because it uses the same algorithm as `java.util.Random`, with a seed of only 48 bits,
    * this function will not return all possible long values!
    */
  def nextLong     ()(implicit tx: Txn): Long

  /** Resets the internal seed value to the given argument. */
  def setSeed(seed: Long)(implicit tx: Txn): Unit
}