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
  //   implicit def serializer[ S <: Sys[ S ], D <: stm.DurableLike[ D ]]( implicit bridge: S#Tx => D#Tx ) : stm.Serializer[ S#Tx, S#Acc, Cursor[ S ]] =

  //   private def pathSerializer[ S <: Sys[ S ]]( system: S ) : stm.Serializer[ S#Tx, S#Acc, S#Acc ] = anyPathSer.asInstanceOf[ PathSer[ S ]]
  //
  //   private val anyPathSer = new PathSer[ Confluent ]

  private final class PathSer[S <: Sys[S], D1 <: stm.DurableLike[D1]](implicit system: S { type D = D1 })
    extends serial.Serializer[D1#Tx, D1#Acc, S#Acc] {
    def write(v: S#Acc, out: DataOutput) {
      v.write(out)
    }

    def read(in: DataInput, access: D1#Acc)(implicit tx: D1#Tx): S#Acc = system.readPath(in)
  }

  def apply[S <: Sys[S], D1 <: stm.DurableLike[D1]](init: S#Acc)
                                                   (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S] = {
    implicit val pathSer  = new PathSer[S, D1]
    val id                = tx.newID()
    val path              = tx.newVar[S#Acc](id, init)
    new Impl[S, D1](id, path)
  }

  def read[S <: Sys[S], D1 <: stm.DurableLike[D1]](in: DataInput)
                                                  (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S] = {
    implicit val pathSer  = new PathSer[S, D1]
    val id                = tx.readID(in, ())
    val path              = tx.readVar[S#Acc](id, in)
    new Impl[S, D1](id, path)
  }

  private final class Impl[S <: Sys[S], D1 <: stm.DurableLike[D1]](id: D1#ID, path: D1#Var[S#Acc])
                                                                  (implicit system: S {type D = D1})
    extends Cursor[S] with Cache[S#Tx] {
    override def toString = "Cursor" + id

    def step[A](fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic { itx =>
        implicit val dtx  = system.durable.wrap(itx)
        val inputAccess   = path()
        performStep(inputAccess, dtx, fun)
      }
    }

    def stepFrom[A](inputAccess: S#Acc)(fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic { itx =>
        implicit val dtx  = system.durable.wrap(itx)
        path()            = inputAccess
        performStep(inputAccess, dtx, fun)
      }
    }

    private def performStep[A](inputAccess: S#Acc, dtx: D1#Tx, fun: S#Tx => A): A = {
      val tx = system.createTxn(dtx, inputAccess, this)
      logCursor(s"${id.toString} step. input path = $inputAccess")
      fun(tx)
    }

    def flushCache(term: Long)(implicit tx: S#Tx) {
      implicit val dtx: D1#Tx = system.durableTx(tx)
      val newPath = tx.inputAccess.addTerm(term)
      path()      = newPath
      logCursor(s"${id.toString} flush path = $newPath")
    }

    def position(implicit tx: S#Tx): S#Acc = {
      implicit val dtx: D1#Tx = system.durableTx(tx)
      path()
    }

    //      def position_=( pathVal: S#Acc )( implicit tx: S#Tx ) {
    //         implicit val dtx: D1#Tx = system.durableTx( tx )
    //         path.set( pathVal )
    //      }

    def dispose()(implicit tx: S#Tx) {
      implicit val dtx: D1#Tx = system.durableTx(tx)
      id  .dispose()
      path.dispose()
      logCursor(s"${id.toString} dispose")
    }

    def write(out: DataOutput) {
      id  .write(out)
      path.write(out)
    }
  }
}