/*
 *  Cursor.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
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

import stm.Disposable
import de.sciss.serial.{DataInput, Writable}
import impl.{CursorImpl => Impl}

object Cursor {
  def apply[S <: Sys[S], D1 <: stm.DurableLike[D1]](init: S#Acc = Sys.Acc.root[S])
                                                   (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S, D1] =
    Impl[S, D1](init)

  def read[S <: Sys[S], D1 <: stm.DurableLike[D1]](in: DataInput)
                                                  (implicit tx: D1#Tx, system: S { type D = D1 }): Cursor[S, D1] =
    Impl.read[S, D1](in)

  implicit def serializer[S <: Sys[S], D1 <: stm.DurableLike[D1]](
    implicit system: S { type D = D1 }): serial.Serializer[D1#Tx, D1#Acc, Cursor[S, D1]] = Impl.serializer[S, D1]
}
trait Cursor[S <: Sys[S], D <: stm.DurableLike[D]] extends stm.Cursor[S] with Disposable[D#Tx] with Writable {
  def stepFrom[A](path: S#Acc, retroactive: Boolean = false)(fun: S#Tx => A): A
  def position(implicit tx: D#Tx): S#Acc
}