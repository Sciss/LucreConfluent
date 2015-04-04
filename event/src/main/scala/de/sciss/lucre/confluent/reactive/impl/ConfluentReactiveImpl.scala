/*
 *  ConfluentReactiveImpl.scala
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

package de.sciss
package lucre
package confluent
package reactive
package impl

import stm.{DataStoreFactory, DataStore}
import lucre.{event => evt}
import concurrent.stm.InTxn
import evt.ReactionMap
import confluent.impl.DurableCacheMapImpl
import serial.{DataInput, DataOutput, ImmutableSerializer}

object ConfluentReactiveImpl {
  private type S = ConfluentReactive

  def apply(storeFactory: DataStoreFactory[DataStore]): S = {
    // tricky: before `durable` was a `val` in `System`, this caused
    // a NPE with `Mixin` initialising `global`.
    // (http://stackoverflow.com/questions/12647326/avoiding-npe-in-trait-initialization-without-using-lazy-vals)
    val mainStore   = storeFactory.open("data")
    val eventStore  = storeFactory.open("event", overwrite = true)
    val durable     = event.Durable(mainStore, eventStore)
    new System(storeFactory, eventStore, durable)
  }

  // --------------------------------------------
  // ------------- BEGIN event vars -------------
  // --------------------------------------------

  private sealed trait BasicEventVar[S <: ConfluentReactiveLike[S], A] extends evt.Var[S, A] {
    protected def id: S#ID

    final def write(out: DataOutput): Unit = out.writeInt(id.base)

    final def dispose()(implicit tx: S#Tx): Unit = {
      tx.removeFromCache(id)
      id.dispose()
    }

    final def getOrElse(default: => A)(implicit tx: S#Tx): A = get.getOrElse(default)

    final def transform(default: => A)(f: A => A)(implicit tx: S#Tx): Unit =
      this() = f(getOrElse(default))

    // final def isFresh(implicit tx: S#Tx): Boolean = tx.isFresh(id)

    override def toString = "evt.Var(" + id + ")"
  }

  private final class EventVarTxImpl[S <: ConfluentReactiveLike[S], A](
    protected val id: S#ID)(implicit protected val ser: serial.Serializer[S#Tx, S#Acc, A])
    extends BasicEventVar[S, A] {

    def update(v: A)(implicit tx: S#Tx): Unit = {
      log(this.toString + " set " + v)
      tx.putEventTxn(id, v)
    }

    def get(implicit tx: S#Tx): Option[A] = {
      log(this.toString + " get")
      tx.getEventTxn(id)
    }
  }

  private final class EventVarImpl[S <: ConfluentReactiveLike[S], A](protected val id: S#ID,
                                                                     protected val ser: ImmutableSerializer[A])
    extends BasicEventVar[S, A] {
    def update(v: A)(implicit tx: S#Tx): Unit = {
      log(this.toString + " set " + v)
      tx.putEventNonTxn(id, v)(ser)
    }

    def get(implicit tx: S#Tx): Option[A] = {
      log(this.toString + " get")
      tx.getEventNonTxn[A](id)(ser)
    }
  }

  private final class ValidityImpl[S <: ConfluentReactiveLike[S]](protected val id: S#ID)
    extends BasicEventVar[S, Int] with ImmutableSerializer[Int] with evt.Validity[S#Tx] {

    // query validity
    def apply()(implicit tx: S#Tx): Boolean = {
      if (id.path.isEmpty) return true

      get match {
        case Some(term) => tx.inputAccess.index.term.toInt == term // XXX TODO: we need this? && !tx.meldInfo.requiresNewTree
        case _          => true
      }
    }

    // mark as validated
    def update()(implicit tx: S#Tx): Unit = {
      val v = tx.inputAccess.index.term.toInt
      update(v)
    }

    def update(v: Int)(implicit tx: S#Tx): Unit = {
      log(this.toString + " set " + v)
      tx.putEventNonTxn(id, v)(this)
    }

    def get(implicit tx: S#Tx): Option[Int] = {
      log(this.toString + " get")
      tx.getEventNonTxn[Int](id)(this)
    }

    def write(v: Int, out: DataOutput): Unit = out.writeInt(v)

    def read(in: DataInput): Int = in.readInt()

    override def toString = s"evt.Validity($id)"
  }

  //  private final class IntEventVar[S <: ConfluentReactiveLike[S]](protected val id: S#ID)
  //    extends BasicEventVar[S, Int] with ImmutableSerializer[Int] {
  //    def get(implicit tx: S#Tx): Option[Int] = {
  //      log(this.toString + " get")
  //      tx.getEventNonTxn[Int](id)(this)
  //    }
  //
  //    def setInit(v: Int)(implicit tx: S#Tx): Unit = {
  //      log(this.toString + " ini " + v)
  //      tx.putEventNonTxn(id, v)(this)
  //    }
  //
  //    def update(v: Int)(implicit tx: S#Tx): Unit = {
  //      log(this.toString + " set " + v)
  //      tx.putEventNonTxn(id, v)(this)
  //    }
  //
  //    override def toString = "evt.Var[Int](" + id + ")"
  //
  //    // ---- Serializer ----
  //    def write(v: Int, out: DataOutput): Unit = {
  //      out.writeInt(v)
  //    }
  //
  //    def read(in: DataInput): Int = in.readInt()
  //  }

  // ------------------------------------------
  // ------------- END event vars -------------
  // ------------------------------------------

  // ----------------------------------------------
  // ------------- BEGIN transactions -------------
  // ----------------------------------------------

  trait TxnMixin[S <: ConfluentReactiveLike[S]]
    extends confluent.impl.TxnMixin[S] // gimme `alloc` and `readSource`
    with ConfluentReactiveLike.Txn[S] {
    _: S#Tx =>

    final def reactionMap: ReactionMap[S] = system.reactionMap

    private val eventCache: CacheMap.Durable[S, Int, DurablePersistentMap[S, Int]] = system.eventCache

    private var markDirtyFlag = false

    //    final def isFresh(id: S#ID): Boolean = {
    //      if (isFresh__(id)) return true
    //      println(s"eventCache.store.isFresh(${id.seminal}, ${id.path} == false")
    //      false
    //    }

    //    private def isFresh(id: S#ID): Boolean = {
    //      val id1   = id.base
    //      val path  = id.path
    //      // either the value was written during this transaction (implies freshness)...
    //      // ...or the path is empty (it was just created) -> also implies freshness...
    //      eventCache.cacheContains(id1, path)(this) || path.isEmpty || {
    //        // ...or we have currently an ongoing meld which will produce a new
    //        // index tree---in that case (it wasn't in the cache!) the value is definitely not fresh...
    //        if (meldInfo.requiresNewTree) false
    //        else {
    //          // ...or otherwise freshness means the most recent write index corresponds
    //          // to the input access index
    //          // store....
    //          eventCache.store.isFresh(id1, path)(this)
    //        }
    //      }
    //    }

    final private def markEventDirty(): Unit =
      if (!markDirtyFlag) {
        markDirtyFlag = true
        // addDirtyCache(eventCache)
        addDirtyLocalCache(eventCache)
      }

    private[reactive] def putEventTxn[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit = {
      eventCache.putCacheTxn[A](id.base, id.path, value)(this, ser)
      markEventDirty()
    }

    private[reactive] def putEventNonTxn[A](id: S#ID, value: A)(implicit ser: ImmutableSerializer[A]): Unit = {
      eventCache.putCacheNonTxn(id.base, id.path, value)(this, ser)
      markEventDirty()
    }

    private[reactive] def getEventTxn[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Option[A] = {
      // OBSOLETE: note: eventCache.getCacheTxn will call store.get if the value is not in cache.
      // that assumes that the value has been written, and id.path.splitIndex is called.
      // for a fresh variable this fails obviously because the path is empty. therefore,
      // we decompose the call at this site.
      eventCache.getCacheTxn[A](id.base, id.path)(this, ser)
    }

    private[reactive] def getEventNonTxn[A](id: S#ID)(implicit ser: ImmutableSerializer[A]): Option[A] = {
      // OBSOLETE: see comment in getEventTxn
      eventCache.getCacheNonTxn[A](id.base, id.path)(this, ser)
    }

    private def makeEventVar[A](id: S#ID)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): evt.Var[S, A] = {
      serializer match {
        case plain: ImmutableSerializer[_] =>
          new EventVarImpl[S, A](id, plain.asInstanceOf[ImmutableSerializer[A]])
        case _ =>
          new EventVarTxImpl[S, A](id)
      }
    }

    final def newEventVar[A](pid: S#ID)
                            (implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): evt.Var[S, A] = {
      val id  = alloc(pid)
      val res = makeEventVar[A](id)
      log("new evt var " + res)
      res
    }

    //    final def newEventIntVar[A](pid: S#ID): evt.Var[S, Int] = {
    //      val id  = alloc(pid)
    //      val res = new IntEventVar(id)
    //      log("new evt var " + res)
    //      res
    //    }

    final def readEventVar[A](pid: S#ID, in: DataInput)
                             (implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): evt.Var[S, A] = {
      val res = makeEventVar[A](readSource(in, pid))
      log("read evt " + res)
      res
    }

    //    final def readEventIntVar[A](pid: S#ID, in: DataInput): evt.Var[S, Int] = {
    //      val res = new IntEventVar(readSource(in, pid))
    //      log("read evt " + res)
    //      res
    //    }

    final def newEventValidity (pid: S#ID): evt.Validity[S#Tx] = {
      val id  = alloc(pid)
      val res = new ValidityImpl[S](id)
      log("new evt validity " + res)
      res
    }

    final def readEventValidity(pid: S#ID, in: DataInput): evt.Validity[S#Tx] = {
      val id  = readSource(in, pid)
      val res = new ValidityImpl[S](id)
      log("read evt validity " + res)
      res
    }
  }

  private sealed trait TxnImpl extends TxnMixin[S] with ConfluentReactive.Txn {
    final lazy val inMemory: event.InMemory#Tx = system.inMemory.wrap(peer)
  }

  private final class RegularTxn(val system: S, val durable: event.Durable#Tx,
                                 val inputAccess: S#Acc, val isRetroactive: Boolean,
                                 val cursorCache: Cache[S#Tx])
    extends confluent.impl.RegularTxnMixin[S, event.Durable] with TxnImpl {

    lazy val peer = durable.peer
  }

  private final class RootTxn(val system: S, val peer: InTxn)
    extends confluent.impl.RootTxnMixin[S, event.Durable] with TxnImpl {

    lazy val durable: event.Durable#Tx = {
      log("txn durable")
      system.durable.wrap(peer)
    }
  }

  // --------------------------------------------
  // ------------- END transactions -------------
  // --------------------------------------------

  // ------------------------------------------
  // ------------- BEGIN systems  -------------
  // ------------------------------------------

  trait Mixin[S <: ConfluentReactiveLike[S]]
    extends confluent.impl.Mixin[S] with evt.impl.ReactionMapImpl.Mixin[S] {
    _: S =>

    protected def eventStore: DataStore
    private val eventVarMap = DurablePersistentMap.newConfluentIntMap[S](eventStore, this, isOblivious = true)

    final val eventCache: CacheMap.Durable[S, Int, DurablePersistentMap[S, Int]] =
      DurableCacheMapImpl.newIntCache(eventVarMap)

    override def close(): Unit = {
      super     .close()
      eventStore.close()
    }
  }

  private final class System(protected val storeFactory: DataStoreFactory[DataStore],
                             protected val eventStore: DataStore, val durable: event.Durable)
    extends Mixin[S] with ConfluentReactive {

    def inMemory              = durable.inMemory
    def durableTx (tx: S#Tx)  = tx.durable
    def inMemoryTx(tx: S#Tx)  = tx.inMemory

    protected def wrapRegular(dtx: event.Durable#Tx, inputAccess: S#Acc, retroactive: Boolean, cursorCache: Cache[S#Tx]) =
      new RegularTxn(this, dtx, inputAccess, retroactive, cursorCache)

    protected def wrapRoot(peer: InTxn) = new RootTxn(this, peer)
  }
}
