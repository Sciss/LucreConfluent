/*
 *  VarImpl.scala
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

import de.sciss.serial
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}

import scala.collection.immutable.LongMap

private[impl] final class HandleImpl[S <: Sys[S], A](stale: A, writeIndex: S#Acc)
                                              (implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
  extends Source[S, A] with Cache[S#Tx] {

  private var writeTerm = 0L

  override def toString = s"handle: $stale"

  def flushCache(term: Long)(implicit tx: S#Tx): Unit =
    writeTerm = term

  def meld(from: S#Acc)(implicit tx: S#Tx): A = {
    if (writeTerm == 0L) throw new IllegalStateException(s"Cannot meld a handle that was not yet flushed: $this")
    log(s"$this meld $from")
    tx.addInputVersion(from)
    apply1(from)
  }

  def apply()(implicit tx: S#Tx): A = {
    if (writeTerm == 0L) return stale // wasn't flushed yet
    apply1(tx.inputAccess)
  }

  private def apply1(readPath: S#Acc)(implicit tx: S#Tx): A = {
    val out = DataOutput()
    serializer.write(stale, out)
    val in = DataInput(out.buffer, 0, out.size)

    var entries = LongMap.empty[Long]
    Hashing.foreachPrefix(writeIndex, entries.contains) {
      case (_hash, _preSum) => entries += ((_hash, _preSum))
    }
    entries += ((writeIndex.sum, 0L)) // full cookie

    var (maxIndex, maxTerm) = readPath.splitIndex
    while (true) {
      val preLen = Hashing.maxPrefixLength(maxIndex, entries.contains)
      val index = if (preLen == maxIndex.size) {
        // maximum prefix lies in last tree
        maxIndex
      } else {
        // prefix lies in other tree
        maxIndex.take(preLen)
      }
      val preSum = index.sum
      val hash = entries(preSum)
      if (hash == 0L) {
        // full entry
        val suffix = writeTerm +: readPath.drop(preLen)
        return serializer.read(in, suffix)
      } else {
        // partial hash
        val (fullIndex, fullTerm) = maxIndex.splitAtSum(hash)
        maxIndex = fullIndex
        maxTerm = fullTerm
      }
    }
    sys.error("Never here")
  }
}

private[impl] sealed trait BasicVar[S <: Sys[S], A] extends Var[S, A] {
  protected def id: S#ID

  final def write(out: DataOutput): Unit = out./* PACKED */ writeInt(id.base)

  final def dispose()(implicit tx: S#Tx): Unit = {
    tx.removeFromCache(id)
    id.dispose()
  }

  def setInit(v: A)(implicit tx: S#Tx): Unit

  final def transform(f: A => A)(implicit tx: S#Tx): Unit = this() = f(this())
}

private[impl] final class VarImpl[S <: Sys[S], A](protected val id: S#ID, protected val ser: ImmutableSerializer[A])
  extends BasicVar[S, A] {

  def meld(from: S#Acc)(implicit tx: S#Tx): A = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id.base, from)
    tx.addInputVersion(from)
    tx.getNonTxn[A](idm)(ser)
  }

  def update(v: A)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putNonTxn(id, v)(ser)
  }

  def apply()(implicit tx: S#Tx): A = {
    log(s"$this get")
    tx.getNonTxn[A](id)(ser)
  }

  def setInit(v: A)(implicit tx: S#Tx): Unit = {
    log(s"$this ini $v")
    tx.putNonTxn(id, v)(ser)
  }

  override def toString = s"Var($id)"
}

private[impl] final class PartialVarTxImpl[S <: Sys[S], A](protected val id: S#ID)
                                                    (implicit ser: serial.Serializer[S#Tx, S#Acc, A])
  extends BasicVar[S, A] {

  def meld(from: S#Acc)(implicit tx: S#Tx): A = ???

  def update(v: A)(implicit tx: S#Tx): Unit = {
    logPartial(s"$this set $v")
    tx.putPartial(id, v)
  }

  def apply()(implicit tx: S#Tx): A = {
    logPartial(s"$this get")
    tx.getPartial(id)
  }

  def setInit(v: A)(implicit tx: S#Tx): Unit = {
    logPartial(s"$this ini $v")
    tx.putPartial(id, v)
  }

  override def toString = s"PartialVar($id)"
}

private[impl] final class VarTxImpl[S <: Sys[S], A](protected val id: S#ID)
                                             (implicit ser: serial.Serializer[S#Tx, S#Acc, A])
  extends BasicVar[S, A] {

  def meld(from: S#Acc)(implicit tx: S#Tx): A = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id.base, from)
    tx.addInputVersion(from)
    tx.getTxn[A](idm)
  }

  def update(v: A)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putTxn(id, v)
  }

  def apply()(implicit tx: S#Tx): A = {
    log(s"$this get")
    tx.getTxn(id)
  }

  def setInit(v: A)(implicit tx: S#Tx): Unit = {
    log(s"$this ini $v")
    tx.putTxn(id, v)
  }

  override def toString = s"Var($id)"
}

private final class RootVar[S <: Sys[S], A](id1: Int, name: String)
                                           (implicit val ser: serial.Serializer[S#Tx, S#Acc, A])
  extends Var[S, A] {

  def setInit(v: A)(implicit tx: S#Tx): Unit = this() = v // XXX could add require( tx.inAccess == Path.root )

  override def toString = name // "Root"

  private def id(implicit tx: S#Tx): S#ID = new ConfluentID[S](id1, tx.inputAccess)

  def meld(from: S#Acc)(implicit tx: S#Tx): A = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id1, from)
    tx.addInputVersion(from)
    tx.getTxn(idm)
  }

  def update(v: A)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putTxn(id, v)
  }

  def apply()(implicit tx: S#Tx): A = {
    log(s"$this get")
    tx.getTxn(id)
  }

  def transform(f: A => A)(implicit tx: S#Tx): Unit = this() = f(this())

  def write(out: DataOutput): Unit =
    sys.error("Unsupported Operation -- access.write")

  def dispose()(implicit tx: S#Tx) = ()
}

private[impl] final class BooleanVar[S <: Sys[S]](protected val id: S#ID)
  extends BasicVar[S, Boolean] with ImmutableSerializer[Boolean] {

  def meld(from: S#Acc)(implicit tx: S#Tx): Boolean = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id.base, from)
    tx.addInputVersion(from)
    tx.getNonTxn[Boolean](idm)
  }

  def apply()(implicit tx: S#Tx): Boolean = {
    log(s"$this get")
    tx.getNonTxn[Boolean](id)(this)
  }

  def setInit(v: Boolean)(implicit tx: S#Tx): Unit = {
    log(s"$this ini $v")
    tx.putNonTxn(id, v)(this)
  }

  def update(v: Boolean)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putNonTxn(id, v)(this)
  }

  override def toString = s"Var[Boolean]($id)"

  // ---- Serializer ----
  def write(v: Boolean, out: DataOutput): Unit = out.writeBoolean(v)

  def read(in: DataInput): Boolean = in.readBoolean()
}

private[impl] final class IntVar[S <: Sys[S]](protected val id: S#ID)
  extends BasicVar[S, Int] with ImmutableSerializer[Int] {

  def meld(from: S#Acc)(implicit tx: S#Tx): Int = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id.base, from)
    tx.addInputVersion(from)
    tx.getNonTxn[Int](idm)
  }

  def apply()(implicit tx: S#Tx): Int = {
    log(s"$this get")
    tx.getNonTxn[Int](id)(this)
  }

  def setInit(v: Int)(implicit tx: S#Tx): Unit = {
    log(s"$this ini $v")
    tx.putNonTxn(id, v)(this)
  }

  def update(v: Int)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putNonTxn(id, v)(this)
  }

  override def toString = s"Var[Int]($id)"

  // ---- Serializer ----
  def write(v: Int, out: DataOutput): Unit = out.writeInt(v)

  def read(in: DataInput): Int = in.readInt()
}

private[impl] final class LongVar[S <: Sys[S]](protected val id: S#ID)
  extends BasicVar[S, Long] with ImmutableSerializer[Long] {

  def meld(from: S#Acc)(implicit tx: S#Tx): Long = {
    log(s"$this meld $from")
    val idm = new ConfluentID[S](id.base, from)
    tx.addInputVersion(from)
    tx.getNonTxn[Long](idm)
  }

  def apply()(implicit tx: S#Tx): Long = {
    log(s"$this get")
    tx.getNonTxn[Long](id)(this)
  }

  def setInit(v: Long)(implicit tx: S#Tx): Unit = {
    log(s"$this ini $v")
    tx.putNonTxn(id, v)(this)
  }

  def update(v: Long)(implicit tx: S#Tx): Unit = {
    log(s"$this set $v")
    tx.putNonTxn(id, v)(this)
  }

  override def toString = s"Var[Long]($id)"

  // ---- Serializer ----
  def write(v: Long, out: DataOutput): Unit = out.writeLong(v)

  def read(in: DataInput): Long = in.readLong()
}