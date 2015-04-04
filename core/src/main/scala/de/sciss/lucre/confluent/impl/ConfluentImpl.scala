/*
 *  ConfluentImpl.scala
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

import de.sciss.lucre.stm.{DataStore, DataStoreFactory, Durable}

import scala.concurrent.stm.InTxn

object ConfluentImpl {
  def apply(storeFactory: DataStoreFactory[DataStore]): Confluent = {
    // tricky: before `durable` was a `val` in `System`, this caused
    // a NPE with `Mixin` initialising `global`.
    // (http://stackoverflow.com/questions/12647326/avoiding-npe-in-trait-initialization-without-using-lazy-vals)
    val durable = Durable(storeFactory)
    new System(storeFactory, durable)
  }

  private final class System(protected val storeFactory: DataStoreFactory[DataStore], val durable: Durable)
    extends Mixin[Confluent] with Confluent {

    def inMemory: I = durable.inMemory

    def durableTx (tx: S#Tx): D#Tx = tx.durable
    def inMemoryTx(tx: S#Tx): I#Tx = tx.inMemory

    protected def wrapRegular(dtx: D#Tx, inputAccess: S#Acc, retroactive: Boolean, cursorCache: Cache[S#Tx]): S#Tx =
      new RegularTxn(this, dtx, inputAccess, retroactive, cursorCache)

    protected def wrapRoot(peer: InTxn): S#Tx = new RootTxn(this, peer)
  }
}