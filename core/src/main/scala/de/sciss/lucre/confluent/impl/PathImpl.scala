/*
 *  PathImpl.scala
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
package impl

import de.sciss.fingertree.{FingerTree, FingerTreeLike}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Durable
import de.sciss.serial.{DataInput, DataOutput}
import de.sciss.{fingertree, serial}

import scala.util.hashing.MurmurHash3

private[confluent] object PathImpl {
  private implicit object PathMeasure extends fingertree.Measure[Long, (Int, Long)] {
    override def toString = "PathMeasure"

    val zero = (0, 0L)

    def apply(c: Long) = (1, c >> 32)

    def |+|(a: (Int, Long), b: (Int, Long)) = (a._1 + b._1, a._2 + b._2)
    override def |+|(a: (Int, Long), b: (Int, Long), c: (Int, Long)) = (a._1 + b._1 + c._1, a._2 + b._2 + c._2)
  }

  implicit def serializer[S <: Sys[S], D <: stm.DurableLike[D]]: serial.Serializer[D#Tx, D#Acc, S#Acc] =
    anySer.asInstanceOf[Ser[S, D]]

  private val anySer = new Ser[Confluent, Durable]

  private final class Ser[S <: Sys[S], D <: stm.DurableLike[D]] extends serial.Serializer[D#Tx, D#Acc, S#Acc] {
    def write(v: S#Acc, out: DataOutput): Unit = v.write(out)

    def read(in: DataInput, acc: D#Acc)(implicit tx: D#Tx): S#Acc = {
      //        implicit val m  = PathMeasure
      val sz          = in.readInt()
      var tree        = FingerTree.empty(PathMeasure)
      var i = 0
      while (i < sz) {
        tree :+= in.readLong()
        i += 1
      }
      new Path[S](tree)
    }
  }

  //    def test_empty[S <: Sys[S]]: S#Acc = empty

  private val anyEmpty = new Path[Confluent](FingerTree.empty(PathMeasure))

  def empty[S <: Sys[S]]: S#Acc = anyEmpty.asInstanceOf[Path[S]]

  def root[S <: Sys[S]]: S#Acc = new Path[S](FingerTree(1L << 32, 1L << 32)(PathMeasure))

  def read[S <: Sys[S]](in: DataInput): S#Acc = {
    // implicit val m  = PathMeasure
    val sz          = in.readInt()
    var tree        = FingerTree.empty(PathMeasure)
    var i = 0
    while (i < sz) {
      tree :+= in.readLong()
      i += 1
    }
    new Path[S](tree)
  }

  def readAndAppend[S <: Sys[S]](in: DataInput, acc: S#Acc)(implicit tx: S#Tx): S#Acc = {
    // implicit val m = PathMeasure
    val sz      = in.readInt()
    val accTree = acc.tree

    val res     = if (accTree.isEmpty) {
      var i = 0
      var tree = FingerTree.empty(PathMeasure)
      while (i < sz) {
        tree :+= in.readLong()
        i += 1
      }
      tree

    } else if (sz == 0) {
      accTree

    } else {
      var tree    = FingerTree.empty(PathMeasure)
      val szm = sz - 1
      var i = 0
      while (i < szm) {
        tree :+= in.readLong()
        i += 1
      }
      val lastTerm  = in.readLong()
      val oldLevel  = tx.readTreeVertexLevel(lastTerm)
      val writeTerm = accTree.head
      val newLevel  = tx.readTreeVertexLevel(writeTerm)

      if (oldLevel != newLevel) { // reconstruct a tree split
        tree :+= lastTerm
        tree ++ accTree
      } else {
        tree ++ accTree.tail      // replace terminal version
      }
    }
    new Path[S](res)
  }

  /**
   * The finger tree has elements of type `Long` where the upper 32 bits are the randomized version,
   * and the lower 32 bits are the incremental version. The measure is taking the index and running sum
   * of the tree.
   */
  private final class Path[S <: Sys[S]](val tree: FingerTree[(Int, Long), Long])
    extends Access[S] with FingerTreeLike[(Int, Long), Long, Path[S]] {
    implicit protected def m: fingertree.Measure[Long, (Int, Long)] = PathMeasure

    override def toString = mkString("Path(", ", ", ")")

    override def hashCode = {
      import MurmurHash3._
      val m   = sum
      val h0  = productSeed
      val h1  = mix(h0, (m >> 32).toInt)
      val h2  = mixLast(h1, m.toInt)
      finalizeHash(h2, 2)
    }

    override def equals(that: Any): Boolean =
      that.isInstanceOf[PathLike] && that.asInstanceOf[PathLike].sum == sum

    def :+(last: Long): S#Acc = wrap(tree :+ last)
    def +:(head: Long): S#Acc = wrap(head +: tree)

    def apply(idx: Int): Long = tree.find1(_._1 > idx)._2

    // XXX TODO testing one two
    def partial: S#Acc = {
      val sz = size
      if (sz == 0) return this

      var res = FingerTree.empty(PathMeasure)
      if (sz % 2 != 0) {
        println(s"?? partial from index $this")
      }
      res :+= head
      res :+= last
      wrap(res)
    }

    def maxPrefixLength(term: Long): Int = {
      val pre = tree.takeWhile(_._2 < term)
      if (pre.isEmpty || pre.last != term) 0 else pre.measure._1
    }

//    def maxPrefixLength(that: S#Acc): Int = {
//      val ita = tree.iterator
//      val itb = that.tree.iterator
//      var j = 0
//      while (ita.hasNext && itb.hasNext) {
//        val na = ita.next()
//        val nb = itb.next()
//        if (na != nb) return 0
//        j += 1
//      }
//      j
//    }

    def addTerm(term: Long)(implicit tx: S#Tx): S#Acc = {
      val t = if (tree.isEmpty) {
        FingerTree.two[(Int, Long), Long](term, term)
      } else {
        val oldLevel = tx.readTreeVertexLevel(this.term)
        val newLevel = tx.readTreeVertexLevel(term)
        if (oldLevel == newLevel) {
          tree.init :+ term
        } else {
          tree :+ term :+ term
        }
      }
      wrap(t)
    }

    def seminal: S#Acc = {
      val e = indexTerm
      val t = term
      wrap(FingerTree.two[(Int, Long), Long](e, t))
    }

    def indexTerm: Long = apply(size - 2)
    def indexSum : Long = sum - (last >> 32)

    def :-|(suffix: Long): S#Acc = wrap(tree.init :+ suffix)

    def drop(n: Int): S#Acc = {
      val right = tree.dropWhile(_._1 <= n)
      wrap(right)
    }

    def splitIndex: (S#Acc, Long) = (init, last)

    def splitAtIndex(idx: Int): (S#Acc, Long) = {
      val (pre, t, _) = tree.span1(_._1 <= idx)
      (wrap(pre), t)
    }

    def splitAtSum(hash: Long): (S#Acc, Long) = {
      val (pre, t, _) = tree.span1(_._2 <= hash)
      (wrap(pre), t)
    }

    def write(out: DataOutput): Unit = {
      out.writeInt(size)
      tree.iterator.foreach(out.writeLong)
    }

    def index: S#Acc  = wrap(tree.init)
    def term: Long    = tree.last
    def size: Int     = tree.measure._1
    def sum: Long     = tree.measure._2

    def sumUntil_OLD(n: Int): Long = {
      val res = tree.takeWhile(_._1 <= n).measure._2
//      val test = sumUntil_X(n)
//      if (res != test) {
//        println(s"$this.sumUntil($n) == $res and not $test")
//      }
////      assert(res == sumUntil_X(n))
      res
    }

    def sumUntil(n: Int): Long = tree.find1(_._1 > n)._1._2

    def take(n: Int): S#Acc = {
      val left = tree.takeWhile(_._1 <= n)
      wrap(left)
    }

    def wrap(_tree: FingerTree[(Int, Long), Long]): Path[S] = new Path(_tree)

    def mkString(prefix: String, sep: String, suffix: String): String =
      tree.iterator.map(_.toInt).mkString(prefix, sep, suffix)

//    def mkString(prefix: String, sep: String, suffix: String): String =
//      tree.iterator.map(i => (i >> 32).toInt).mkString(prefix, sep, suffix)

    def info(implicit tx: S#Tx): VersionInfo = tx.system.versionInfo(term)

    def takeUntil(timeStamp: Long)(implicit tx: S#Tx): S#Acc = tx.system.versionUntil(this, timeStamp)
  }
}