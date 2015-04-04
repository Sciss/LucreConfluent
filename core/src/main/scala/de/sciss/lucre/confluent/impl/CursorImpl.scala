/*
 *  CursorImpl.scala
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

import de.sciss.lucre.stm
import de.sciss.serial
import de.sciss.serial.{DataInput, DataOutput}

import scala.concurrent.stm.{Txn, TxnExecutor}

object CursorImpl {
  private final val COOKIE  = 0x4375  // "Cu"

  implicit def serializer[S <: Sys[S], D1 <: stm.DurableLike[D1]](
    implicit system: S { type D = D1 }): serial.Serializer[D1#Tx, D1#Acc, Cursor[S, D1]] = new Ser[S, D1]

  private final class Ser[S <: Sys[S], D1 <: stm.DurableLike[D1]](implicit system: S { type D = D1 })
    extends serial.Serializer[D1#Tx, D1#Acc, Cursor[S, D1]] {

    def write(v: Cursor[S, D1], out: DataOutput): Unit = v.write(out)

    def read(in: DataInput, access: D1#Acc)(implicit tx: D1#Tx): Cursor[S, D1] = CursorImpl.read[S, D1](in)
  }

  private final class PathSer[S <: Sys[S], D1 <: stm.DurableLike[D1]](implicit system: S { type D = D1 })
    extends serial.Serializer[D1#Tx, D1#Acc, S#Acc] {

    def write(v: S#Acc, out: DataOutput): Unit = v.write(out)

    def read(in: DataInput, access: D1#Acc)(implicit tx: D1#Tx): S#Acc = system.readPath(in)
  }

  def apply[S <: Sys[S], D1 <: stm.DurableLike[D1]](init: S#Acc)
                                                   (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S, D1] = {
    implicit val pathSer  = new PathSer[S, D1]
    val id                = tx.newID()
    val path              = tx.newVar[S#Acc](id, init)
    new Impl[S, D1](id, path)
  }

  def read[S <: Sys[S], D1 <: stm.DurableLike[D1]](in: DataInput)
                                                  (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S, D1] = {
    implicit val pathSer  = new PathSer[S, D1]
    val cookie            = in.readShort()
    if (cookie != COOKIE) throw new IllegalStateException(s"Unexpected cookie $cookie (should be $COOKIE)")
    val id                = tx.readID(in, ())
    val path              = tx.readVar[S#Acc](id, in)
    new Impl[S, D1](id, path)
  }

  private final class Impl[S <: Sys[S], D1 <: stm.DurableLike[D1]](id: D1#ID, path: D1#Var[S#Acc])
                                                          (implicit system: S { type D = D1 })
    extends Cursor[S, D1] with Cache[S#Tx] {

    override def toString = s"Cursor$id"

    private def topLevelAtomic[A](fun: D1#Tx => A): A = {
      if (Txn.findCurrent.isDefined)
        throw new IllegalStateException("Nested transactions not supported yet by Durable system.")
      TxnExecutor.defaultAtomic { itx =>
        val dtx = system.durable.wrap(itx)
        fun(dtx)
      }
    }

    def step[A](fun: S#Tx => A): A = {
      topLevelAtomic { implicit dtx =>
        val inputAccess = path()
        performStep(inputAccess, retroactive = false, dtx = dtx, fun = fun)
      }
    }

    def stepFrom[A](inputAccess: S#Acc, retroactive: Boolean)(fun: S#Tx => A): A = {
      topLevelAtomic { implicit dtx =>
        path() = inputAccess
        performStep(inputAccess, retroactive, dtx, fun)
      }
    }

    private def performStep[A](inputAccess: S#Acc, retroactive: Boolean, dtx: D1#Tx, fun: S#Tx => A): A = {
      val tx = system.createTxn(dtx, inputAccess, retroactive, this)
      logCursor(s"${id.toString} step. input path = $inputAccess")
      fun(tx)
    }

    def flushCache(term: Long)(implicit tx: S#Tx): Unit = {
      implicit val dtx: D1#Tx = system.durableTx(tx)
      val newPath = tx.inputAccess.addTerm(term)
      path()      = newPath
      logCursor(s"${id.toString} flush path = $newPath")
    }

    def position(implicit tx: S #Tx): S#Acc = position(system.durableTx(tx))
    def position(implicit tx: D1#Tx): S#Acc = path()

    def dispose()(implicit tx: D1#Tx): Unit = {
      id  .dispose()
      path.dispose()
      logCursor(s"${id.toString} dispose")
    }

    def write(out: DataOutput): Unit = {
      out.writeShort(COOKIE)
      id  .write(out)
      path.write(out)
    }
  }
}