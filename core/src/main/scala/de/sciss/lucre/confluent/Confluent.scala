/*
 *  Confluent.scala
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

package de.sciss.lucre
package confluent

import stm.{DataStoreFactory, DataStore}
import impl.{ConfluentImpl => Impl}
import language.implicitConversions

object Confluent {
  var DEBUG_DISABLE_PARTIAL = true

  def apply(storeFactory: DataStoreFactory[DataStore]): Confluent = Impl(storeFactory)

  trait Txn extends Sys.Txn[Confluent] {
    private[confluent] def durable:  stm.Durable#Tx
    private[confluent] def inMemory: stm.InMemory#Tx
  }

  implicit def inMemory(tx: Confluent#Tx): stm.InMemory#Tx = tx.inMemory
}

trait Confluent extends Sys[Confluent] {
  final protected type S = Confluent
  final type D  = stm.Durable
  final type I  = stm.InMemory
  final type Tx = Confluent.Txn
}