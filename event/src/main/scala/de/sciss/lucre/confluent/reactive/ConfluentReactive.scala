/*
 *  ConfluentReactive.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package confluent
package reactive

import de.sciss.lucre.{event => evt}
import stm.{DataStoreFactory, DataStore}
import impl.{ConfluentReactiveImpl => Impl}
import language.implicitConversions
import serial.ImmutableSerializer

object ConfluentReactive {
  private type S = ConfluentReactive

  def apply(storeFactory: DataStoreFactory[DataStore]): S = Impl(storeFactory)

  trait Txn extends ConfluentReactiveLike.Txn[S] {
//    private[confluent] def durable:  stm.Durable#Tx
//    private[confluent] def inMemory: stm.InMemory#Tx
  }

  // implicit def inMemory(tx: S#Tx): stm.InMemory#Tx = tx.inMemory
}

object ConfluentReactiveLike {
  trait Txn[S <: ConfluentReactiveLike[S]] extends confluent.Sys.Txn[S] with evt.Txn[S] {
    // private[reactive] def isFresh(id: S#ID): Boolean

    private[reactive] def putEventTxn[A]   (id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit
    private[reactive] def putEventNonTxn[A](id: S#ID, value: A)(implicit ser: ImmutableSerializer[A]): Unit

    private[reactive] def getEventTxn[A]   (id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Option[A]
    private[reactive] def getEventNonTxn[A](id: S#ID)(implicit ser: ImmutableSerializer[A]): Option[A]
  }
}

trait ConfluentReactiveLike[S <: ConfluentReactiveLike[S]] extends confluent.Sys[S] with evt.Sys[S] {
  type Tx <: ConfluentReactiveLike.Txn[S]

  private[reactive] def eventCache: CacheMap.Durable[S, Int, DurablePersistentMap[S, Int]]
}

trait ConfluentReactive extends ConfluentReactiveLike[ConfluentReactive] {
  final protected type S = ConfluentReactive
  final type D  = event.Durable
  final type I  = event.InMemory
  final type Tx = ConfluentReactive.Txn
}