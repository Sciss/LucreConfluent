/*
 *  ConfluentID.scala
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

import de.sciss.serial.DataOutput

import scala.util.hashing.MurmurHash3

private final class ConfluentID[S <: Sys[S]](val base: Int, val path: S#Acc) extends Identifier[S] {
  override def hashCode: Int = {
    import MurmurHash3._
    val h0  = productSeed
    val h1  = mix(h0, base)
    val h2  = mixLast(h1, path.##)
    finalizeHash(h2, 2)
  }

  def copy(newPath: S#Acc): Identifier[S] = new ConfluentID(base = base, path = newPath)

  override def equals(that: Any): Boolean = that match {
    case b: Identifier[_] => base == b.base && path == b.path
    case _ => false
  }

  def write(out: DataOutput): Unit = {
    out.writePackedInt(base) // writeInt(base)
    path.write(out)
  }

  override def toString = path.mkString(s"<$base @ ", ",", ">")

  def dispose()(implicit tx: S#Tx) = ()
}

private final class PartialID[S <: Sys[S]](val base: Int, val path: S#Acc) extends Identifier[S] {
  override def hashCode = {
    import MurmurHash3._
    val h0  = productSeed
    if (path.isEmpty) {
      val h1  = mixLast(h0, base)
      finalizeHash(h1, 1)
    } else {
      val h1  = mix(h0, base)
      val h2  = mix(h1, (path.head >> 32).toInt)
      val h3  = mixLast(h2, (path.last >> 32).toInt)
      finalizeHash(h3, 3)
    }
  }

  def copy(newPath: S#Acc): Identifier[S] = new PartialID(base = base, path = newPath)

  override def equals(that: Any): Boolean = that match {
    case b: PartialID[_] =>
      val bp = b.path
      if (path.isEmpty) {
        base == b.base && bp.isEmpty
      } else {
        base == b.base && bp.nonEmpty && path.head == bp.head && path.last == bp.last
      }

    case _ => false
  }

  def write(out: DataOutput): Unit = {
    out.writePackedInt(base) // writeInt(base)
    path.write(out)
  }

  override def toString = {
    val tail = if (path.isEmpty) ""
    else {
      val head = path.head
      val tail = path.tail
      val (mid, last) = tail.splitIndex
      mid.mkString(s"${head.toInt}(,", ",", s"),${last.toInt}")
    }
    s"<$base @ $tail>"
  }

  def dispose()(implicit tx: S#Tx) = ()
}
