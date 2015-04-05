/*
 *  TxnImpl.scala
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

import de.sciss.lucre.confluent.impl.{PathImpl => Path}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Durable, InMemory, IdentifierMap}
import de.sciss.serial
import de.sciss.serial.{DataInput, ImmutableSerializer}

import scala.collection.immutable.{Queue => IQueue, IntMap, IndexedSeq => Vec}
import scala.concurrent.stm.{InTxn, Txn => ScalaTxn}

trait TxnMixin[S <: Sys[S]]
  extends Txn[S] with stm.impl.BasicTxnImpl[S] with VersionInfo.Modifiable {
  _: S#Tx =>

  // ---- abstract ----

  protected def flushCaches(meld: MeldInfo[S], newVersion: Boolean, caches: Vec[Cache[S#Tx]]): Unit

  // ---- info ----

  final def info: VersionInfo.Modifiable = this

  final var message: String = ""
  final val timeStamp: Long = System.currentTimeMillis()

  // ---- init ----

  /*
   * Because durable maps are persisted, they may be deserialized multiple times per transaction.
   * This could potentially cause a problem: imagine two instances A1 and A2. A1 is read, a `put`
   * is performed, making A1 call `markDirty`. Next, A2 is read, again a `put` performed, and A2
   * calls `markDirty`. Next, another `put` on A1 is performed. In the final flush, because A2
   * was marked after A1, it's cached value will override A2's, even though it is older.
   *
   * To avoid that, durable maps are maintained by their id's in a transaction local map. That way,
   * only one instance per id is available in a single transaction.
   */
  private var durableIDMaps     = IntMap.empty[DurableIDMapImpl[_, _]]
  private var meld              = MeldInfo.empty[S]
  private var dirtyMaps         = Vector.empty[Cache[S#Tx]]
  private var beforeCommitFuns  = IQueue.empty[S#Tx => Unit]

  // indicates whether we have added cache maps to dirty maps
  private var markDirtyFlag       = false
  // indicates whether we have added cache maps that require version updates (e.g. non-event writes)
  private var markNewVersionFlag  = false
  // indicates whether any before commit handling is needed
  // (either dirtyMaps got non-empty, or a user before-commit handler got registered)
  private var markBeforeCommitFlag = false

  final protected def meldInfo: MeldInfo[S] = meld

  final private def markDirty(): Unit =
    if (!markDirtyFlag) {
      markDirtyFlag = true
      addDirtyCache(fullCache)
      addDirtyCache(partialCache)
    }

  final def addDirtyCache(map: Cache[S#Tx]): Unit = {
    dirtyMaps :+= map
    markNewVersionFlag = true
    markBeforeCommit()
  }

  /** A local cache is one which is re-created upon application restart. It should
    * probably be called transient instead of local, but we already have `stm.LocalVar`...
    *
    * If the dirty maps only contain local caches, no new version is created upon flush.
    */
  final def addDirtyLocalCache(map: Cache[S#Tx]): Unit = {
    dirtyMaps :+= map
    markBeforeCommit()
  }

  final override def beforeCommit(fun: S#Tx => Unit): Unit = {
    beforeCommitFuns = beforeCommitFuns.enqueue(fun)
    markBeforeCommit()
  }

  private def markBeforeCommit(): Unit =
    if (!markBeforeCommitFlag) {
      markBeforeCommitFlag = true
      log("....... txn dirty .......")
      ScalaTxn.beforeCommit(handleBeforeCommit)(peer)
    }

  // first execute before commit handlers, then flush
  private def handleBeforeCommit(itx: InTxn): Unit = {
    while (beforeCommitFuns.nonEmpty) {
      val (fun, q) = beforeCommitFuns.dequeue
      beforeCommitFuns = q
      fun(this)
    }
    flushCaches(meld, markNewVersionFlag, dirtyMaps)
  }

  final protected def fullCache     = system.fullCache
  final protected def partialCache  = system.partialCache

  final def newID(): S#ID = {
    val res = new ConfluentID[S](system.newIDValue()(this), Path.empty[S])
    log(s"txn newID $res")
    res
  }

  final def newPartialID(): S#ID = {
    if (Confluent.DEBUG_DISABLE_PARTIAL) return newID()

    val res = new PartialID[S](system.newIDValue()(this), Path.empty[S])
    log(s"txn newPartialID $res")
    res
  }

  final def readTreeVertexLevel(term: Long): Int = {
    system.store.get(out => {
      out.writeByte(0)
      out.writeInt(term.toInt)
    })(in => {
      in.readInt() // tree index!
      in.readInt()
    })(this).getOrElse(sys.error(s"Trying to access inexistent vertex ${term.toInt}"))
  }

  final def addInputVersion(path: S#Acc): Unit = {
    val sem1 = path.seminal
    val sem2 = inputAccess.seminal
    if (sem1 == sem2) return
    if (sem1 != sem2) {
      val m = meld
      // note: before we were reading the index tree; but since only the level
      // is needed, we can read the vertex instead which also stores the
      // the level.
      //               val tree1 = readIndexTree(sem1.head)
      val tree1Level = readTreeVertexLevel(sem1.head)
      val m1 = if (m.isEmpty) {
        val tree2Level = readTreeVertexLevel(sem2.head)
        m.add(tree2Level, sem2)
      } else m
      meld = m1.add(tree1Level, sem1)
    }
  }

  final def newHandle[A](value: A)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): Source[S, A] = {
    val h = new HandleImpl[S, A](value, inputAccess.index)
    addDirtyLocalCache(h)
    h
  }

  final def getNonTxn[A](id: S#ID)(implicit ser: ImmutableSerializer[A]): A = {
    log(s"txn get $id")
    fullCache.getCacheNonTxn[A](id.base, id.path)(this, ser).getOrElse(sys.error("No value for " + id))
  }

  final def getTxn[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A = {
    log(s"txn get' $id")
    fullCache.getCacheTxn[A](id.base, id.path)(this, ser).getOrElse(sys.error("No value for " + id))
  }

  final def putTxn[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit = {
    fullCache.putCacheTxn[A](id.base, id.path, value)(this, ser)
    markDirty()
  }

  final def putNonTxn[A](id: S#ID, value: A)(implicit ser: ImmutableSerializer[A]): Unit = {
    fullCache.putCacheNonTxn[A](id.base, id.path, value)(this, ser)
    markDirty()
  }

  final def putPartial[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit = {
    partialCache.putPartial(id.base, id.path, value)(this, ser)
    markDirty()
  }

  final def getPartial[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A =
    partialCache.getPartial[A](id.base, id.path)(this, ser).getOrElse(sys.error(s"No value for $id"))

  final def removeFromCache(id: S#ID): Unit =
    fullCache.removeCacheOnly(id.base, id.path)(this)

  @inline final protected def alloc       (pid: S#ID): S#ID = new ConfluentID(system.newIDValue()(this), pid.path)
  @inline final protected def allocPartial(pid: S#ID): S#ID = new PartialID  (system.newIDValue()(this), pid.path)

  final def newVar[A](pid: S#ID, init: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
    val res = makeVar[A](alloc(pid))
    log(s"txn newVar $res")
    res.setInit(init)(this)
    res
  }

  final def newLocalVar[A](init: S#Tx => A): stm.LocalVar[S#Tx, A] = new stm.impl.LocalVarImpl[S, A](init)

  final def newPartialVar[A](pid: S#ID, init: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
    if (Confluent.DEBUG_DISABLE_PARTIAL) return newVar(pid, init)

    val res = new PartialVarTxImpl[S, A](allocPartial(pid))
    log(s"txn newPartialVar $res")
    res.setInit(init)(this)
    res
  }

  final def newBooleanVar(pid: S#ID, init: Boolean): S#Var[Boolean] = {
    val id  = alloc(pid)
    val res = new BooleanVar(id)
    log(s"txn newVar $res")
    res.setInit(init)(this)
    res
  }

  final def newIntVar(pid: S#ID, init: Int): S#Var[Int] = {
    val id  = alloc(pid)
    val res = new IntVar(id)
    log(s"txn newVar $res")
    res.setInit(init)(this)
    res
  }

  final def newLongVar(pid: S#ID, init: Long): S#Var[Long] = {
    val id  = alloc(pid)
    val res = new LongVar(id)
    log(s"txn newVar $res")
    res.setInit(init)(this)
    res
  }

  final def newVarArray[A](size: Int): Array[S#Var[A]] = new Array[S#Var[A]](size)

  final def newInMemoryIDMap[A]: IdentifierMap[S#ID, S#Tx, A] = {
    val map = InMemoryConfluentMap.newIntMap[S]
    new InMemoryIDMapImpl[S, A](map)
  }

  final def newDurableIDMap[A](implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = {
    mkDurableIDMap(system.newIDValue()(this))
  }

  final def removeDurableIDMap[A](map: IdentifierMap[S#ID, S#Tx, A]): Unit =
    durableIDMaps -= map.id.base

  private def mkDurableIDMap[A](id: Int)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = {
    val map = DurablePersistentMap.newConfluentLongMap[S](system.store, system.indexMap, isOblivious = false)
    val idi = new ConfluentID(id, Path.empty[S])
    val res = new DurableIDMapImpl[S, A](idi, map)
    durableIDMaps += id -> res
    res
  }

  final protected def readSource(in: DataInput, pid: S#ID): S#ID = {
    val id = in.readInt()
    new ConfluentID(id, pid.path)
  }

  final protected def readPartialSource(in: DataInput, pid: S#ID): S#ID = {
    val id = in.readInt()
    new PartialID(id, pid.path)
  }

  private def makeVar[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] /* BasicVar[ S, A ] */ = {
    ser match {
      case plain: ImmutableSerializer[_] =>
        new VarImpl[S, A](id, plain.asInstanceOf[ImmutableSerializer[A]])
      case _ =>
        new VarTxImpl[S, A](id)
    }
  }

  final def readVar[A](pid: S#ID, in: DataInput)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
    val res = makeVar[A](readSource(in, pid))
    log(s"txn read $res")
    res
  }

  final def readPartialVar[A](pid: S#ID, in: DataInput)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
    if (Confluent.DEBUG_DISABLE_PARTIAL) return readVar(pid, in)

    val res = new PartialVarTxImpl[S, A](readPartialSource(in, pid))
    log(s"txn read $res")
    res
  }

  final def readBooleanVar(pid: S#ID, in: DataInput): S#Var[Boolean] = {
    val res = new BooleanVar(readSource(in, pid))
    log(s"txn read $res")
    res
  }

  final def readIntVar(pid: S#ID, in: DataInput): S#Var[Int] = {
    val res = new IntVar(readSource(in, pid))
    log(s"txn read $res")
    res
  }

  final def readLongVar(pid: S#ID, in: DataInput): S#Var[Long] = {
    val res = new LongVar(readSource(in, pid))
    log(s"txn read $res")
    res
  }

  final def readID(in: DataInput, acc: S#Acc): S#ID = {
    val res = new ConfluentID(in.readInt(), Path.readAndAppend[S](in, acc)(this))
    log(s"txn readID $res")
    res
  }

  final def readPartialID(in: DataInput, acc: S#Acc): S#ID = {
    if (Confluent.DEBUG_DISABLE_PARTIAL) return readID(in, acc)

    val res = new PartialID(in.readInt(), Path.readAndAppend(in, acc)(this))
    log(s"txn readPartialID $res")
    res
  }

  final def readDurableIDMap[A](in: DataInput)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = {
    val id = in.readInt()
    durableIDMaps.get(id) match {
      case Some(existing) => existing.asInstanceOf[DurableIDMapImpl[S, A]]
      case _              => mkDurableIDMap(id)
    }
  }

  override def toString = s"confluent.Sys#Tx$inputAccess"
}

trait RegularTxnMixin[S <: Sys[S], D <: stm.DurableLike[D]] extends TxnMixin[S] {
  _: S#Tx =>

  protected def cursorCache: Cache[S#Tx]

  final protected def flushCaches(meldInfo: MeldInfo[S], newVersion: Boolean, caches: Vec[Cache[S#Tx]]): Unit =
    system.flushRegular(meldInfo, newVersion, caches :+ cursorCache)(this)

  override def toString = s"Confluent#Tx$inputAccess"
}

trait RootTxnMixin[S <: Sys[S], D <: stm.DurableLike[D]]
  extends TxnMixin[S] {
  _: S#Tx =>

  final val inputAccess = Path.root[S]

  final def isRetroactive = false

  final protected def flushCaches(meldInfo: MeldInfo[S], newVersion: Boolean, caches: Vec[Cache[S#Tx]]): Unit =
    system.flushRoot(meldInfo, newVersion, caches)(this)

  override def toString = "Confluent.RootTxn"
}

private[impl] sealed trait TxnImpl extends Txn[Confluent] {
  final lazy val inMemory: InMemory#Tx = system.inMemory.wrap(peer)
}

private[impl] final class RegularTxn(val system: Confluent, val durable: Durable#Tx,
                               val inputAccess: Confluent#Acc, val isRetroactive: Boolean,
                               val cursorCache: Cache[Confluent#Tx])
  extends RegularTxnMixin[Confluent, Durable] with TxnImpl {

  lazy val peer = durable.peer
}

private[impl] final class RootTxn(val system: Confluent, val peer: InTxn)
  extends RootTxnMixin[Confluent, Durable] with TxnImpl {

  lazy val durable: Durable#Tx = {
    log("txn durable")
    system.durable.wrap(peer)
  }
}