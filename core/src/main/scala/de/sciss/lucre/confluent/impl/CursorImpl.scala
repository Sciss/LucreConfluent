/*
 *  CursorImpl.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2013 Hanns Holger Rutz. All rights reserved.
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
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 */

package de.sciss
package lucre
package confluent
package impl

import concurrent.stm.TxnExecutor
import serial.{DataInput, DataOutput}

object CursorImpl {
  private final val COOKIE  = 0x4375  // "Cu"

  implicit def serializer[S <: Sys[S], D1 <: stm.DurableLike[D1]](
    implicit system: S { type D = D1 }): serial.Serializer[D1#Tx, D1#Acc, Cursor[S, D1]] = new Ser[S, D1]

  //   private def pathSerializer[ S <: Sys[ S ]]( system: S ) : stm.Serializer[ S#Tx, S#Acc, S#Acc ] = anyPathSer.asInstanceOf[ PathSer[ S ]]
  //
  //   private val anyPathSer = new PathSer[ Confluent ]

  private final class Ser[S <: Sys[S], D1 <: stm.DurableLike[D1]](implicit system: S { type D = D1 })
    extends serial.Serializer[D1#Tx, D1#Acc, Cursor[S, D1]] {

    def write(v: Cursor[S, D1], out: DataOutput): Unit = {
      // println(s"Cursor serializer writes $v")
      v.write(out)
    }
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
    require(cookie == COOKIE, s"Unexpected cookie $cookie (should be $COOKIE)")
    val id                = tx.readID(in, ())
    val path              = tx.readVar[S#Acc](id, in)
    new Impl[S, D1](id, path)
  }

  private final class Impl[S <: Sys[S], D1 <: stm.DurableLike[D1]](id: D1#ID, path: D1#Var[S#Acc])
                                                                  (implicit system: S {type D = D1})
    extends Cursor[S, D1] with Cache[S#Tx] {

    override def toString = "Cursor" + id

    def step[A](fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic { itx =>
        implicit val dtx  = system.durable.wrap(itx)
        val inputAccess   = path()
        performStep(inputAccess, retroactive = false, dtx = dtx, fun = fun)
      }
    }

    def stepFrom[A](inputAccess: S#Acc, retroactive: Boolean)(fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic { itx =>
        implicit val dtx  = system.durable.wrap(itx)
        path()            = inputAccess
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

    def position(implicit tx: S#Tx): S#Acc = position(system.durableTx(tx))

    def position(implicit tx: D1#Tx): S#Acc = path()

    //      def position_=( pathVal: S#Acc )( implicit tx: S#Tx ): Unit = {
    //         implicit val dtx: D1#Tx = system.durableTx( tx )
    //         path.set( pathVal )
    //      }

    def dispose()(implicit tx: D1#Tx): Unit = {
      id  .dispose()
      path.dispose()
      logCursor(s"${id.toString} dispose")
    }

    def write(out: DataOutput): Unit = {
      // println(s"Writing $COOKIE")
      out.writeShort(COOKIE)
      id  .write(out)
      path.write(out)
    }
  }
}