/*
 *  GlobalState.scala
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

import de.sciss.lucre.data.Ancestor
import de.sciss.lucre.stm
import de.sciss.serial
import de.sciss.serial.{ImmutableSerializer, DataInput, DataOutput}

private[impl] object GlobalState {
  private val SER_VERSION = 0x436F6E666C6E7401L  // "Conflnt\1"

  implicit def serializer[S <: Sys[S], D <: stm.DurableLike[D]]: serial.Serializer[D#Tx, D#Acc, GlobalState[S, D]] =
    new Ser[S, D]

  private final class Ser[S <: Sys[S], D <: stm.DurableLike[D]]
    extends serial.Serializer[D#Tx, D#Acc, GlobalState[S, D]] {

    def write(v: GlobalState[S, D], out: DataOutput): Unit = {
      import v._
      out.writeLong(SER_VERSION)
      out.writePackedInt(durRootID) // writeInt(durRootID)
      idCnt        .write(out)
      versionLinear.write(out)
      versionRandom.write(out)
      partialTree  .write(out)
    }

    def read(in: DataInput, acc: D#Acc)(implicit tx: D#Tx): GlobalState[S, D] = {
      val serVer = in.readLong()
      if (serVer != SER_VERSION)
        throw new IllegalStateException(s"Incompatible serialized version. Found $serVer but require $SER_VERSION")
      val durRootID     = in.readPackedInt() // readInt()
      val idCnt         = tx.readCachedIntVar(in)
      val versionLinear = tx.readCachedIntVar(in)
      val versionRandom = tx.readCachedLongVar(in)
      val partialTree   = Ancestor.readTree[D, Long](in, acc)(tx, ImmutableSerializer.Long, _.toInt)
      GlobalState[S, D](durRootID = durRootID, idCnt = idCnt, versionLinear = versionLinear,
        versionRandom = versionRandom, partialTree = partialTree)
    }
  }
}

private[impl] final case class GlobalState[S <: Sys[S], D <: stm.DurableLike[D]](
    durRootID: Int, idCnt: D#Var[Int], versionLinear: D#Var[Int], versionRandom: D#Var[Long],
    partialTree: Ancestor.Tree[D, Long])

