/*
 *  ConfluentImpl.scala
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

import stm.{InMemory, DataStore, DataStoreFactory, Durable, IdentifierMap}
import serial.{ImmutableSerializer, DataInput, DataOutput}
import concurrent.stm.{InTxn, TxnExecutor, TxnLocal, Txn => ScalaTxn}
import collection.immutable.{IndexedSeq => IIdxSeq, LongMap, IntMap, Queue => IQueue}
import de.sciss.fingertree
import fingertree.{FingerTreeLike, FingerTree}
import data.Ancestor
import annotation.tailrec
import util.hashing.MurmurHash3
import impl.{PathImpl => Path}

object ConfluentImpl {
  def apply(storeFactory: DataStoreFactory[DataStore]): Confluent = {
    // tricky: before `durable` was a `val` in `System`, this caused
    // a NPE with `Mixin` initialising `global`.
    // (http://stackoverflow.com/questions/12647326/avoiding-npe-in-trait-initialization-without-using-lazy-vals)
    val durable = Durable(storeFactory)
    new System(storeFactory, durable)
  }

  // an index tree holds the pre- and post-lists for each version (full) tree
  private final class IndexTreeImpl[D <: stm.DurableLike[D]](val tree: Ancestor.Tree[D, Long], val level: Int)
    extends Sys.IndexTree[D] {

    override def hashCode: Int = term.toInt

    def term: Long = tree.root.version

    override def equals(that: Any): Boolean = {
      that.isInstanceOf[Sys.IndexTree[_]] && (term == that.asInstanceOf[Sys.IndexTree[_]].term)
    }

    def write(out: DataOutput) {
      tree.write(out)
      out.writeInt(level)
    }

    def dispose()(implicit tx: D#Tx) {
      tree.dispose()
    }

    override def toString = "IndexTree<v=" + term.toInt + ", l=" + level + ">"
  }

  // ----------------------------------------------
  // ------------- BEGIN transactions -------------
  // ----------------------------------------------

  private def emptySeq[A]: IIdxSeq[A] = anyEmptySeq

  private val anyEmptySeq = IIdxSeq.empty[Nothing]

  trait TxnMixin[S <: Sys[S]]
    extends Sys.Txn[S] with stm.impl.BasicTxnImpl[S] with VersionInfo.Modifiable {
    _: S#Tx =>

    // ---- abstract ----

    protected def flushCaches(meld: MeldInfo[S], caches: IIdxSeq[Cache[S#Tx]]): Unit

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
    private var dirtyMaps         = emptySeq[Cache[S#Tx]]
    private var beforeCommitFuns  = IQueue.empty[S#Tx => Unit]

    // indicates whether we have added the cache maps to dirty maps
    private var markDirtyFlag = false
    // indicates whether any before commit handling is needed
    // (either dirtyMaps got non-empty, or a user before-commit handler got registered)
    private var markBeforeCommitFlag = false

    final private def markDirty() {
      if (!markDirtyFlag) {
        markDirtyFlag = true
        addDirtyCache(fullCache)
        addDirtyCache(partialCache)
      }
    }

    final def forceWrite() {
      markDirty()
    }

    final def addDirtyCache(map: Cache[S#Tx]) {
      dirtyMaps :+= map
      markBeforeCommit()
    }

    final override def beforeCommit(fun: S#Tx => Unit) {
      beforeCommitFuns = beforeCommitFuns.enqueue(fun)
      markBeforeCommit()
    }

    private def markBeforeCommit() {
      if (!markBeforeCommitFlag) {
        markBeforeCommitFlag = true
        log("....... txn dirty .......")
        ScalaTxn.beforeCommit(handleBeforeCommit)(peer)
      }
    }

    // first execute before commit handlers, then flush
    private def handleBeforeCommit(itx: InTxn) {
      while (beforeCommitFuns.nonEmpty) {
        val (fun, q) = beforeCommitFuns.dequeue
        beforeCommitFuns = q
        fun(this)
      }
      flushCaches(meld, dirtyMaps)
    }

    final protected def fullCache     = system.fullCache
    final protected def partialCache  = system.partialCache

    final def newID(): S#ID = {
      val res = new ConfluentID[S](system.newIDValue()(this), Path.empty[S])
      log("txn newID " + res)
      res
    }

    final def newPartialID(): S#ID = {
      if (Confluent.DEBUG_DISABLE_PARTIAL) return newID()

      val res = new PartialID[S](system.newIDValue()(this), Path.empty[S])
      log("txn newPartialID " + res)
      res
    }

    final def readTreeVertexLevel(term: Long): Int = {
      system.store.get(out => {
        out.writeByte(0)
        out.writeInt(term.toInt)
      })(in => {
        in.readInt() // tree index!
        in.readInt()
      })(this).getOrElse(sys.error("Trying to access inexistent vertex " + term.toInt))
    }

    final def addInputVersion(path: S#Acc) {
      val sem1 = path.seminal
      val sem2 = inputAccess.seminal
      if (sem1 == sem2) return
      if (sem1 != sem2) {
        val m = meld
        // note: before we were reading the index tree; but since only the level
        // is needed, we can read the vertex instead which also stores the
        // the level.
        //               val tree1 = readIndexTree( sem1.head )
        val tree1Level = readTreeVertexLevel(sem1.head)
        val m1 = if (m.isEmpty) {
          // val tree2 = readIndexTree( sem2.head )
          val tree2Level = readTreeVertexLevel(sem2.head)
          m.add(tree2Level, sem2)
        } else m
        meld = m1.add(tree1Level, sem1)
      }
    }

    final def newHandle[A](value: A)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): stm.Source[S#Tx, A] = {
      // val id   = new ConfluentID[ S ]( 0, Path.empty[ S ])
      val h = new HandleImpl(value, inputAccess.index)
      addDirtyCache(h)
      h
    }

    final def getNonTxn[A](id: S#ID)(implicit ser: ImmutableSerializer[A]): A = {
      log("txn get " + id)
      fullCache.getCacheNonTxn[A](id.seminal, id.path)(this, ser).getOrElse(sys.error("No value for " + id))
    }

    final def getTxn[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A = {
      log("txn get' " + id)
      fullCache.getCacheTxn[A](id.seminal, id.path)(this, ser).getOrElse(sys.error("No value for " + id))
    }

    final def putTxn[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]) {
      // logConfig( "txn put " + id )
      fullCache.putCacheTxn[A](id.seminal, id.path, value)(this, ser)
      markDirty()
    }

    final def putNonTxn[A](id: S#ID, value: A)(implicit ser: ImmutableSerializer[A]) {
      // logConfig( "txn put " + id )
      fullCache.putCacheNonTxn[A](id.seminal, id.path, value)(this, ser)
      markDirty()
    }

    final def putPartial[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]) {
      partialCache.putPartial(id.seminal, id.path, value)(this, ser)
      markDirty()
    }

    final def getPartial[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A = {
      // logPartial( "txn get " + id )
      partialCache.getPartial[A](id.seminal, id.path)(this, ser).getOrElse(sys.error("No value for " + id))
    }

    final def isFresh(id: S#ID): Boolean = {
      val id1   = id.seminal
      val path  = id.path
      // either the value was written during this transaction (implies freshness)...
      // ...or the path is empty (it was just created) -> also implies freshness...
      fullCache.cacheContains(id1, path)(this) || path.isEmpty || {
        // ...or we have currently an ongoing meld which will produce a new
        // index tree---in that case (it wasn't in the cache!) the value is definitely not fresh...
        if (meld.requiresNewTree) false
        else {
          // ...or otherwise freshness means the most recent write index corresponds
          // to the input access index
          // store....
          fullCache.store.isFresh(id1, path)(this)
        }
      }
    }

    final def removeFromCache(id: S#ID) {
      fullCache.removeCacheOnly(id.seminal, id.path)(this)
    }

    @inline final protected def alloc       (pid: S#ID): S#ID = new ConfluentID(system.newIDValue()(this), pid.path)
    @inline final protected def allocPartial(pid: S#ID): S#ID = new PartialID(system.newIDValue()  (this), pid.path)

    final def newVar[A](pid: S#ID, init: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val res = makeVar[A](alloc(pid))
      log("txn newVar " + res) // + " - init = " + init
      res.setInit(init)(this)
      res
    }

    final def newLocalVar[A](init: S#Tx => A): stm.LocalVar[S#Tx, A] = new stm.impl.LocalVarImpl[S, A](init)

    final def newPartialVar[A](pid: S#ID, init: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      if (Confluent.DEBUG_DISABLE_PARTIAL) return newVar(pid, init)

      val res = new PartialVarTxImpl[S, A](allocPartial(pid))
      log("txn newPartialVar " + res)
      res.setInit(init)(this)
      res
    }

    final def newBooleanVar(pid: S#ID, init: Boolean): S#Var[Boolean] = {
      val id  = alloc(pid)
      val res = new BooleanVar(id)
      log("txn newVar " + res) // + " - init = " + init
      res.setInit(init)(this)
      res
    }

    final def newIntVar(pid: S#ID, init: Int): S#Var[Int] = {
      val id  = alloc(pid)
      val res = new IntVar(id)
      log("txn newVar " + res) // + " - init = " + init
      res.setInit(init)(this)
      res
    }

    final def newLongVar(pid: S#ID, init: Long): S#Var[Long] = {
      val id  = alloc(pid)
      val res = new LongVar(id)
      log("txn newVar " + res) // + " - init = " + init
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

    final def removeDurableIDMap[A](map: IdentifierMap[S#ID, S#Tx, A]) {
      durableIDMaps -= map.id.seminal
    }

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
      log("txn read " + res)
      res
    }

    final def readPartialVar[A](pid: S#ID, in: DataInput)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      if (Confluent.DEBUG_DISABLE_PARTIAL) return readVar(pid, in)

      val res = new PartialVarTxImpl[S, A](readPartialSource(in, pid))
      log("txn read " + res)
      res
    }

    final def readBooleanVar(pid: S#ID, in: DataInput): S#Var[Boolean] = {
      val res = new BooleanVar(readSource(in, pid))
      log("txn read " + res)
      res
    }

    final def readIntVar(pid: S#ID, in: DataInput): S#Var[Int] = {
      val res = new IntVar(readSource(in, pid))
      log("txn read " + res)
      res
    }

    final def readLongVar(pid: S#ID, in: DataInput): S#Var[Long] = {
      val res = new LongVar(readSource(in, pid))
      log("txn read " + res)
      res
    }

    final def readID(in: DataInput, acc: S#Acc): S#ID = {
      val res = new ConfluentID(in.readInt(), Path.readAndAppend[S](in, acc)(this))
      log("txn readID " + res)
      res
    }

    final def readPartialID(in: DataInput, acc: S#Acc): S#ID = {
      if (Confluent.DEBUG_DISABLE_PARTIAL) return readID(in, acc)

      val res = new PartialID(in.readInt(), Path.readAndAppend(in, acc)(this))
      log("txn readPartialID " + res)
      res
    }

    final def readDurableIDMap[A](in: DataInput)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = {
      val id = in.readInt()
      durableIDMaps.get(id) match {
        case Some(existing) => existing.asInstanceOf[DurableIDMapImpl[S, A]]
        case _              => mkDurableIDMap(id)
      }
    }

    //    final def newCursor (init: S#Acc  ): Cursor[S, S#D] = system.newCursor (init)(this)
    //    final def readCursor(in: DataInput): Cursor[S, S#D] = system.readCursor(in  )(this)

    override def toString = "confluent.Sys#Tx" + inputAccess // + system.path.mkString( "<", ",", ">" )
  }

  trait RegularTxnMixin[S <: Sys[S], D <: stm.DurableLike[D]] extends TxnMixin[S] {
    _: S#Tx =>

    protected def cursorCache: Cache[S#Tx]

    final protected def flushCaches(meldInfo: MeldInfo[S], caches: IIdxSeq[Cache[S#Tx]]) {
      system.flushRegular(meldInfo, caches :+ cursorCache)(this)
    }

    override def toString = "Confluent#Tx" + inputAccess
  }

  trait RootTxnMixin[S <: Sys[S], D <: stm.DurableLike[D]]
    extends TxnMixin[S] {
    _: S#Tx =>

    final val inputAccess = Path.root[S]

    final protected def flushCaches(meldInfo: MeldInfo[S], caches: IIdxSeq[Cache[S#Tx]]) {
      system.flushRoot(meldInfo, caches)(this)
    }

    override def toString = "Confluent.RootTxn"
  }

  private sealed trait TxnImpl extends Confluent.Txn {
    final lazy val inMemory: InMemory#Tx = system.inMemory.wrap(peer)
  }

  private final class RegularTxn(val system: Confluent, val durable: Durable#Tx,
                                 val inputAccess: Confluent#Acc, val cursorCache: Cache[Confluent#Tx])
    extends RegularTxnMixin[Confluent, Durable] with TxnImpl {

    lazy val peer = durable.peer
  }

  private final class RootTxn(val system: Confluent, val peer: InTxn)
    extends RootTxnMixin[Confluent, Durable] with TxnImpl {

    lazy val durable: Durable#Tx = {
      log("txn durable")
      system.durable.wrap(peer)
    }
  }

  // ----------------------------------------------
  // -------------- END transactions --------------
  // ----------------------------------------------

  // -----------------------------------------------
  // -------------- BEGIN identifiers --------------
  // -----------------------------------------------

  private final class ConfluentID[S <: Sys[S]](val seminal: Int, val path: S#Acc) extends Sys.ID[S] {
    override def hashCode = {
      import MurmurHash3._
      val h0  = productSeed
      val h1  = mix(h0, seminal)
      val h2  = mixLast(h1, path.##)
      finalizeHash(h2, 2)
    }

    override def equals(that: Any): Boolean =
      that.isInstanceOf[Sys.ID[_]] && {
        val b = that.asInstanceOf[Sys.ID[_]]
        seminal == b.seminal && path == b.path
      }

    def write(out: DataOutput) {
      out.writeInt(seminal)
      path.write(out)
    }

    override def toString = "<" + seminal + path.mkString(" @ ", ",", ">")

    def dispose()(implicit tx: S#Tx) {}
  }

  private final class PartialID[S <: Sys[S]](val seminal: Int, val path: S#Acc) extends Sys.ID[S] {
    override def hashCode = {
      import MurmurHash3._
      val h0  = productSeed
      if (path.isEmpty) {
        val h1  = mixLast(h0, seminal)
        finalizeHash(h1, 1)
      } else {
        val h1  = mix(h0, seminal)
        val h2  = mix(h1, (path.head >> 32).toInt)
        val h3  = mixLast(h2, (path.last >> 32).toInt)
        finalizeHash(h3, 3)
      }
    }

    override def equals(that: Any): Boolean =
      that.isInstanceOf[PartialID[_]] && {
        val b  = that.asInstanceOf[PartialID[_]]
        val bp = b.path
        if (path.isEmpty) {
          seminal == b.seminal && bp.isEmpty
        } else {
          seminal == b.seminal && bp.nonEmpty && path.head == bp.head && path.last == bp.last
        }
      }

    def write(out: DataOutput) {
      out.writeInt(seminal)
      path.write(out)
    }

    override def toString = "<" + seminal + " @ " + {
      if (path.isEmpty) ">"
      else {
        val head = path.head
        val tail = path.tail
        val (mid, last) = tail.splitIndex
        mid.mkString(head.toInt.toString + "(,", ",", ")," + last.toInt + ">")
      }
    }

    def dispose()(implicit tx: S#Tx) {}
  }

  // -----------------------------------------------
  // --------------- END identifiers ---------------
  // -----------------------------------------------

  // ---------------------------------------------
  // -------------- BEGIN variables --------------
  // ---------------------------------------------

  private final class HandleImpl[S <: Sys[S], A](stale: A, writeIndex: S#Acc)
                                                (implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
    extends stm.Source[S#Tx, A] with Cache[S#Tx] {

    private var writeTerm = 0L

    override def toString = "handle: " + stale

    def flushCache(term: Long)(implicit tx: S#Tx) {
      writeTerm = term
    }

    def apply()(implicit tx: S#Tx): A = {
      if (writeTerm == 0L) return stale // wasn't flushed yet

      val readPath = tx.inputAccess

      val out = DataOutput()
      serializer.write(stale, out)
      val in = DataInput(out.buffer, 0, out.size)

      var entries = LongMap.empty[Long]
      Hashing.foreachPrefix(writeIndex, entries.contains) {
        case (_hash, _preSum) => entries += (_hash, _preSum)
      }
      entries += (writeIndex.sum, 0L) // full cookie

      var (maxIndex, maxTerm) = readPath.splitIndex
      while (true) {
        val preLen = Hashing.maxPrefixLength(maxIndex, entries.contains)
        val index = if (preLen == maxIndex.size) {
          // maximum prefix lies in last tree
          maxIndex
        } else {
          // prefix lies in other tree
          maxIndex.take(preLen)
        }
        val preSum = index.sum
        val hash = entries(preSum)
        if (hash == 0L) {
          // full entry
          val suffix = writeTerm +: readPath.drop(preLen)
          return serializer.read(in, suffix)
        } else {
          // partial hash
          val (fullIndex, fullTerm) = maxIndex.splitAtSum(hash)
          maxIndex = fullIndex
          maxTerm = fullTerm
        }
      }
      sys.error("Never here")
    }
  }

  private sealed trait BasicVar[S <: Sys[S], A] extends Sys.Var[S, A] {
    protected def id: S#ID

    final def write(out: DataOutput) {
      out.writeInt(id.seminal)
    }

    final def dispose()(implicit tx: S#Tx) {
      tx.removeFromCache(id)
      id.dispose()
    }

    def setInit(v: A)(implicit tx: S#Tx): Unit

    final def transform(f: A => A)(implicit tx: S#Tx) {
      this() = f(this())
    }

    final def isFresh(implicit tx: S#Tx): Boolean = tx.isFresh(id)
  }

  private final class VarImpl[S <: Sys[S], A](protected val id: S#ID, protected val ser: ImmutableSerializer[A])
    extends BasicVar[S, A] {

    def update(v: A)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putNonTxn(id, v)(ser)
    }

    def apply()(implicit tx: S#Tx): A = {
      log(this.toString + " get")
      tx.getNonTxn[A](id)(ser)
    }

    def setInit(v: A)(implicit tx: S#Tx) {
      log(this.toString + " ini " + v)
      tx.putNonTxn(id, v)(ser)
    }

    override def toString = "Var(" + id + ")"
  }

  private final class PartialVarTxImpl[S <: Sys[S], A](protected val id: S#ID)
                                                      (implicit ser: serial.Serializer[S#Tx, S#Acc, A])
    extends BasicVar[S, A] {

    def update(v: A)(implicit tx: S#Tx) {
      logPartial(this.toString + " set " + v)
      tx.putPartial(id, v)
    }

    def apply()(implicit tx: S#Tx): A = {
      logPartial(this.toString + " get")
      tx.getPartial(id)
    }

    def setInit(v: A)(implicit tx: S#Tx) {
      logPartial(this.toString + " ini " + v)
      tx.putPartial(id, v)
    }

    override def toString = "PartialVar(" + id + ")"
  }

  private final class VarTxImpl[S <: Sys[S], A](protected val id: S#ID)
                                               (implicit ser: serial.Serializer[S#Tx, S#Acc, A])
    extends BasicVar[S, A] {

    def update(v: A)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putTxn(id, v)
    }

    def apply()(implicit tx: S#Tx): A = {
      log(this.toString + " get")
      tx.getTxn(id)
    }

    def setInit(v: A)(implicit tx: S#Tx) {
      log(this.toString + " ini " + v)
      tx.putTxn(id, v)
    }

    override def toString = "Var(" + id + ")"
  }

  private final class RootVar[S <: Sys[S], A](id1: Int, name: String)
                                             (implicit val ser: serial.Serializer[S#Tx, S#Acc, A])
    extends Sys.Entry[S, A] {

    def setInit(v: A)(implicit tx: S#Tx) {
      this() = v // XXX could add require( tx.inAccess == Path.root )
    }

    override def toString = name // "Root"

    private def id(implicit tx: S#Tx): S#ID = new ConfluentID[S](id1, tx.inputAccess)

    def meld(from: S#Acc)(implicit tx: S#Tx): A = {
      log(this.toString + " meld " + from)
      val idm = new ConfluentID[S](id1, from)
      tx.addInputVersion(from)
      tx.getTxn(idm)
    }

    def update(v: A)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putTxn(id, v)
    }

    def apply()(implicit tx: S#Tx): A = {
      log(this.toString + " get")
      tx.getTxn(id)
    }

    def transform(f: A => A)(implicit tx: S#Tx) {
      this() = f(this())
    }

    def isFresh(implicit tx: S#Tx): Boolean = tx.isFresh(id)

    def write(out: DataOutput) {
      sys.error("Unsupported Operation -- access.write")
    }

    def dispose()(implicit tx: S#Tx) {}
  }

  private final class BooleanVar[S <: Sys[S]](protected val id: S#ID)
    extends BasicVar[S, Boolean] with ImmutableSerializer[Boolean] {

    def apply()(implicit tx: S#Tx): Boolean = {
      log(this.toString + " get")
      tx.getNonTxn[Boolean](id)(this)
    }

    def setInit(v: Boolean)(implicit tx: S#Tx) {
      log(this.toString + " ini " + v)
      tx.putNonTxn(id, v)(this)
    }

    def update(v: Boolean)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putNonTxn(id, v)(this)
    }

    override def toString = "Var[Boolean](" + id + ")"

    // ---- Serializer ----
    def write(v: Boolean, out: DataOutput) {
      out.writeBoolean(v)
    }

    def read(in: DataInput): Boolean = in.readBoolean()
  }

  private final class IntVar[S <: Sys[S]](protected val id: S#ID)
    extends BasicVar[S, Int] with ImmutableSerializer[Int] {

    def apply()(implicit tx: S#Tx): Int = {
      log(this.toString + " get")
      tx.getNonTxn[Int](id)(this)
    }

    def setInit(v: Int)(implicit tx: S#Tx) {
      log(this.toString + " ini " + v)
      tx.putNonTxn(id, v)(this)
    }

    def update(v: Int)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putNonTxn(id, v)(this)
    }

    override def toString = "Var[Int](" + id + ")"

    // ---- Serializer ----
    def write(v: Int, out: DataOutput) {
      out.writeInt(v)
    }

    def read(in: DataInput): Int = in.readInt()
  }

  private final class LongVar[S <: Sys[S]](protected val id: S#ID)
    extends BasicVar[S, Long] with ImmutableSerializer[Long] {

    def apply()(implicit tx: S#Tx): Long = {
      log(this.toString + " get")
      tx.getNonTxn[Long](id)(this)
    }

    def setInit(v: Long)(implicit tx: S#Tx) {
      log(this.toString + " ini " + v)
      tx.putNonTxn(id, v)(this)
    }

    def update(v: Long)(implicit tx: S#Tx) {
      log(this.toString + " set " + v)
      tx.putNonTxn(id, v)(this)
    }

    override def toString = "Var[Long](" + id + ")"

    // ---- Serializer ----
    def write(v: Long, out: DataOutput) {
      out.writeLong(v)
    }

    def read(in: DataInput): Long = in.readLong()
  }

  // ---------------------------------------------
  // --------------- END variables ---------------
  // ---------------------------------------------

  // ----------------------------------------------
  // ----------------- BEGIN maps -----------------
  // ----------------------------------------------

  private final class InMemoryIDMapImpl[S <: Sys[S], A](val store: InMemoryConfluentMap[S, Int])
    extends IdentifierMap[S#ID, S#Tx, A] with InMemoryCacheMapImpl[S, Int] {

    private val markDirtyFlag = TxnLocal(false)

    def id: S#ID = new ConfluentID(0, Path.empty[S])

    private def markDirty()(implicit tx: S#Tx) {
      if (!markDirtyFlag.swap(true)(tx.peer)) tx.addDirtyCache(this)
    }

    protected def emptyCache: Map[Int, Any] = CacheMapImpl.emptyIntMapVal

    def get(id: S#ID)(implicit tx: S#Tx): Option[A] = {
      getCache[A](id.seminal, id.path)
    }

    def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A = {
      get(id).getOrElse(default)
    }

    def put(id: S#ID, value: A)(implicit tx: S#Tx) {
      putCache[A](id.seminal, id.path, value)
      markDirty()
    }

    def contains(id: S#ID)(implicit tx: S#Tx): Boolean = {
      get(id).isDefined // XXX TODO more efficient implementation
    }

    def remove(id: S#ID)(implicit tx: S#Tx) {
      if (removeCache(id.seminal, id.path)) markDirty()
    }

    def write(out: DataOutput) {}
    def dispose()(implicit tx: S#Tx) {}

    override def toString = "IdentifierMap<" + hashCode().toHexString + ">"
  }

  private final class DurableIDMapImpl[S <: Sys[S], A](val id: S#ID,
                                                       val store: DurablePersistentMap[S, Long])
                                                      (implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
    extends IdentifierMap[S#ID, S#Tx, A] with DurableCacheMapImpl[S, Long] {

    private val nid           = id.seminal.toLong << 32
    private val markDirtyFlag = TxnLocal(false)

    private def markDirty()(implicit tx: S#Tx) {
      if (!markDirtyFlag.swap(true)(tx.peer)) tx.addDirtyCache(this)
    }

    protected def emptyCache: Map[Long, Any] = CacheMapImpl.emptyLongMapVal

    def get(id: S#ID)(implicit tx: S#Tx): Option[A] = {
      val key = nid | (id.seminal.toLong & 0xFFFFFFFFL)
      getCacheTxn[A](key, id.path)
    }

    def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A = {
      get(id).getOrElse(default)
    }

    def put(id: S#ID, value: A)(implicit tx: S#Tx) {
      val key = nid | (id.seminal.toLong & 0xFFFFFFFFL)
      putCacheTxn[A](key, id.path, value)
      markDirty()
    }

    def contains(id: S#ID)(implicit tx: S#Tx): Boolean = {
      get(id).isDefined // XXX TODO more efficient implementation
    }

    def remove(id: S#ID)(implicit tx: S#Tx) {
      if (removeCacheOnly(id.seminal, id.path)) markDirty()
    }

    def write(out: DataOutput) {
      out.writeInt(id.seminal)
    }

    def dispose()(implicit tx: S#Tx) {
      println("WARNING: Durable IDMap.dispose : not yet implemented")
      tx.removeDurableIDMap(this)
    }

    override def toString = "IdentifierMap<" + id.seminal + ">"
  }

  // ----------------------------------------------
  // ------------------ END maps ------------------
  // ----------------------------------------------

  private object GlobalState {
    private val SER_VERSION = 0x436F6E666C6E7400L  // "Conflnt\0"

    implicit def serializer[S <: Sys[S], D <: stm.DurableLike[D]]: serial.Serializer[D#Tx, D#Acc, GlobalState[S, D]] =
      new Ser[S, D]

    private final class Ser[S <: Sys[S], D <: stm.DurableLike[D]]
      extends serial.Serializer[D#Tx, D#Acc, GlobalState[S, D]] {

      def write(v: GlobalState[S, D], out: DataOutput) {
        import v._
        out.writeLong(SER_VERSION)
        out.writeInt(durRootID)
        idCnt        .write(out)
        versionLinear.write(out)
        versionRandom.write(out)
        partialTree  .write(out)
      }

      def read(in: DataInput, acc: D#Acc)(implicit tx: D#Tx): GlobalState[S, D] = {
        val serVer = in.readLong()
        require(serVer == SER_VERSION, s"Incompatible serialized version. Found $serVer but require $SER_VERSION")
        val durRootID     = in.readInt()
        val idCnt         = tx.readCachedIntVar(in)
        val versionLinear = tx.readCachedIntVar(in)
        val versionRandom = tx.readCachedLongVar(in)
        val partialTree   = Ancestor.readTree[D, Long](in, acc)(tx, ImmutableSerializer.Long, _.toInt)
        GlobalState[S, D](durRootID = durRootID, idCnt = idCnt, versionLinear = versionLinear,
          versionRandom = versionRandom, partialTree = partialTree)
      }
    }

  }

  private final case class GlobalState[S <: Sys[S], D <: stm.DurableLike[D]](
    durRootID: Int, idCnt: D#Var[Int], versionLinear: D#Var[Int], versionRandom: D#Var[Long],
    partialTree: Ancestor.Tree[D, Long])

  // ---------------------------------------------
  // --------------- BEGIN systems ---------------
  // ---------------------------------------------

  private final class System(protected val storeFactory: DataStoreFactory[DataStore], val durable: Durable)
    extends Mixin[Confluent] with Confluent {

    def inMemory: I = durable.inMemory

    def durableTx (tx: S#Tx): D#Tx = tx.durable
    def inMemoryTx(tx: S#Tx): I#Tx = tx.inMemory

    protected def wrapRegular(dtx: D#Tx, inputAccess: S#Acc, cursorCache: Cache[S#Tx]): S#Tx =
      new RegularTxn(this, dtx, inputAccess, cursorCache)

    protected def wrapRoot(peer: InTxn): S#Tx = new RootTxn(this, peer)
  }

  trait Mixin[S <: Sys[S]] extends Sys[S] with Sys.IndexMapHandler[S] with Sys.PartialMapHandler[S] {
    system: S =>

    // ---- abstract methods ----

    protected def storeFactory: DataStoreFactory[DataStore]

    protected def wrapRegular(dtx: D#Tx, inputAccess: S#Acc, cursorCache: Cache[S#Tx]): S#Tx
    protected def wrapRoot(peer: InTxn): S#Tx

    // ---- init ----

    final val store         = storeFactory.open("data")
    private val varMap      = DurablePersistentMap.newConfluentIntMap[S](store, this, isOblivious = false)
    final val fullCache     = DurableCacheMapImpl.newIntCache(varMap)
    final val partialCache  = PartialCacheMapImpl.newIntCache(DurablePersistentMap.newPartialMap[S](store, this))

    private val global: GlobalState[S, D] = durable.step { implicit tx =>
      val root = durable.root { implicit tx =>
        val durRootID     = stm.DurableSurgery.newIDValue(durable)
        val idCnt         = tx.newCachedIntVar(0)
        val versionLinear = tx.newCachedIntVar(0)
        val versionRandom = tx.newCachedLongVar(TxnRandom.initialScramble(0L)) // scramble !!!
        val partialTree   = Ancestor.newTree[D, Long](1L << 32)(tx, ImmutableSerializer.Long, _.toInt)
        GlobalState[S, D](durRootID = durRootID, idCnt = idCnt, versionLinear = versionLinear,
          versionRandom = versionRandom, partialTree = partialTree)
      }
      root()
    }

    private val versionRandom = TxnRandom.wrap(global.versionRandom)

    override def toString = "Confluent"

    final def indexMap: Sys.IndexMapHandler[S] = this

    @inline private def partialTree: Ancestor.Tree[D, Long] = global.partialTree

    final def newVersionID(implicit tx: S#Tx): Long = {
      implicit val dtx = durableTx(tx) // tx.durable
      val lin = global.versionLinear() + 1
      global.versionLinear() = lin
      var rnd = 0
      do {
        rnd = versionRandom.nextInt()
      } while (rnd == 0)
      (rnd.toLong << 32) | (lin.toLong & 0xFFFFFFFFL)
    }

    final def newIDValue()(implicit tx: S#Tx): Int = {
      implicit val dtx = durableTx(tx) // tx.durable
      val res = global.idCnt() + 1
      // logConfig( "new   <" + id + ">" )
      global.idCnt() = res
      res
    }

    final def createTxn(dtx: D#Tx, inputAccess: S#Acc, cursorCache: Cache[S#Tx]): S#Tx = {
      log("::::::: atomic - input access = " + inputAccess + " :::::::")
      wrapRegular(dtx, inputAccess, cursorCache)
    }

    final def readPath(in: DataInput): S#Acc = Path.read[S](in)

    final def newCursor()(implicit tx: S#Tx): Cursor[S, D] = newCursor(tx.inputAccess)

    final def newCursor(init: S#Acc)(implicit tx: S#Tx): Cursor[S, D] =
      Cursor[S, D](init)(durableTx(tx), this)

    final def readCursor(in: DataInput)(implicit tx: S#Tx): Cursor[S, D] =
      Cursor.read[S, D](in)(durableTx(tx), this)

    final def root[A](init: S#Tx => A)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): S#Entry[A] =
      executeRoot { implicit tx =>
        val (rootVar, _, _) = initRoot(init, _ => (), _ => ())
        rootVar
      }

    def cursorRoot[A, B](init: S#Tx => A)(result: S#Tx => A => B)
                        (implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): (S#Entry[A], B) =
      executeRoot { implicit tx =>
        val (rootVar, rootVal, _) = initRoot(init, _ => (), _ => ())
        rootVar -> result(tx)(rootVal)
      }

    final def rootWithDurable[A, B](confInt: S#Tx => A)(durInit: D#Tx => B)
                                   (implicit aSer: serial.Serializer[S#Tx, S#Acc, A],
                                             bSer: serial.Serializer[D#Tx, D#Acc, B]): (stm.Source[S#Tx, A], B) =
      executeRoot { implicit tx =>
        implicit val dtx = durableTx(tx)
        val (_, confV, durV) = initRoot(confInt, { tx =>
          // read durable
          val did = global.durRootID
          stm.DurableSurgery.read (durable)(did)(bSer.read(_, ()))
        }, { tx =>
          // create durable
          val _durV = durInit(dtx)
          val did = global.durRootID // val did   = stm.DurableSurgery.newIDValue(durable)
          // println(s"DURABLE ROOT ID $did")
          stm.DurableSurgery.write(durable)(did)(bSer.write(_durV, _))
          _durV
        })
        tx.newHandle(confV) -> durV
      }

    private def executeRoot[A](fun: S#Tx => A): A = {
      require(ScalaTxn.findCurrent.isEmpty, "root must be called outside of a transaction")
      log("::::::: root :::::::")
      TxnExecutor.defaultAtomic { itx =>
        val tx = wrapRoot(itx)
        fun(tx)
      }
    }

    private def initRoot[A, B](initA: S#Tx => A, readB: S#Tx => B, initB: S#Tx => B)
                              (implicit tx: S#Tx, serA: serial.Serializer[S#Tx, S#Acc, A]): (S#Entry[A], A, B) = {
      val rootVar     = new RootVar[S, A](0, "Root") // serializer
      val rootPath    = tx.inputAccess
      val arrOpt      = varMap.get[Array[Byte]](0, rootPath)(tx, ByteArraySerializer)
      val (aVal, bVal) = arrOpt match {
        case Some(arr) =>
          val in      = DataInput(arr)
          val aRead   = serA.read(in, rootPath)
          val bRead   = readB(tx)
          (aRead, bRead)

        case _ =>
          implicit val dtx = durableTx(tx) // created on demand (now)
          val aNew    = initA(tx)
          rootVar.setInit(aNew)
          val bNew    = initB(tx)

          writeNewTree(rootPath.index, 0)
          writePartialTreeVertex(partialTree.root)
          (aNew, bNew)
      }
      (rootVar, aVal, bVal)
    }

    final def flushRoot(meldInfo: MeldInfo[S], caches: IIdxSeq[Cache[S#Tx]])(implicit tx: S#Tx) {
      require(!meldInfo.requiresNewTree, "Cannot meld in the root version")
      val outTerm = tx.inputAccess.term
      writeVersionInfo(outTerm)
      flush(outTerm, caches)
    }

    final def flushRegular(meldInfo: MeldInfo[S], caches: IIdxSeq[Cache[S#Tx]])(implicit tx: S#Tx) {
      val newTree = meldInfo.requiresNewTree
      val outTerm = if (newTree) {
        flushNewTree(meldInfo.outputLevel)
      } else {
        flushOldTree()
      }
      log("::::::: txn flush - " + (if (newTree) "meld " else "") + "term = " + outTerm.toInt + " :::::::")
      writeVersionInfo(outTerm)
      flush(outTerm, caches)
    }

    // writes the version info (using cookie `4`).
    private def writeVersionInfo(term: Long)(implicit tx: S#Tx) {
      store.put { out =>
        out.writeByte(4)
        out.writeInt(term.toInt)
      } { out =>
        val i = tx.info
        val m = i.message
        out.writeUTF(/*if (m.length == 0) null else */ m)
        out.writeLong(i.timeStamp)
      }
    }

    /**
     * Retrieves the version information for a given version term.
     */
    final def versionInfo(term: Long)(implicit tx: S#Tx): VersionInfo = {
      val vInt = term.toInt
      val opt = store.get { out =>
        out.writeByte(4)
        out.writeInt(vInt)
      } { in =>
        val m = in.readUTF()
        val timeStamp = in.readLong()
        VersionInfo(/*if (m == null) "" else */ m, timeStamp)
      }
      opt.getOrElse(sys.error("No version information stored for " + vInt))
    }

    final def versionUntil(access: S#Acc, timeStamp: Long)(implicit tx: S#Tx): S#Acc = {
      @tailrec def loop(low: Int, high: Int): Int = {
        if (low <= high) {
          val index = ((high + low) >> 1) & ~1 // we want entry vertices, thus ensure index is even
          val thatTerm = access(index)
          val thatInfo = versionInfo(thatTerm)
          val thatTime = thatInfo.timeStamp
          if (thatTime == timeStamp) {
            index
          } else if (thatTime < timeStamp) {
            loop(index + 2, high)
          } else {
            loop(low, index - 2)
          }
        } else {
          -low - 1
        }
      }

      val sz = access.size
      require(sz % 2 == 0, "Provided path is index, not full terminating path " + access)
      val idx = loop(0, sz - 1)
      // if idx is zero or positive, a time stamp was found, we can simply return
      // the appropriate prefix. if idx is -1, it means the query time is smaller
      // than the seminal version's time stamp; so in that case, return the
      // seminal path (`max(0, -1) + 1 == 1`)
      if (idx >= -1) {
        val index = access.take(math.max(0, idx) + 1)
        index :+ index.term
      } else {
        // otherwise, check if the last exit version is smaller than the query time,
        // and we return the full input access argument. otherwise, we calculate
        // the insertion index `idxP` which is an even number. the entry vertex
        // at that index would have a time stamp greater than the query time stamp,
        // and the entry vertex at that index minus 2 would have a time stamp less
        // than the query time step. therefore, we have to look at the time stamp
        // map for the entry vertex at that index minus 2, and find the ancestor
        // of the tree's exit vetex at idxP - 1.
        val idxP = -idx - 1
        if ((idxP == sz) && (versionInfo(access.term).timeStamp <= timeStamp)) {
          access
        } else {
          val (index, treeExit) = access.take(idxP).splitIndex
          val anc               = readTimeStampMap(index)
          val resOpt            = anc.nearestUntil(timeStamp = timeStamp, term = treeExit)
          val res               = resOpt.getOrElse(sys.error("No version info found for " + index))
          index :+ res._1
        }
      }
    }

    private def flush(outTerm: Long, caches: IIdxSeq[Cache[S#Tx]])(implicit tx: S#Tx) {
      caches.foreach(_.flushCache(outTerm))
    }

    private def flushOldTree()(implicit tx: S#Tx): Long = {
      implicit val dtx        = durableTx(tx)
      val childTerm           = newVersionID(tx)
      val (index, parentTerm) = tx.inputAccess.splitIndex
      val tree                = readIndexTree(index.term)
      val parent              = readTreeVertex(tree.tree, /* index, */ parentTerm)._1
      val child               = tree.tree.insertChild(parent, childTerm)
      writeTreeVertex(tree, child)
      val tsMap               = readTimeStampMap(index)
      tsMap.add(childTerm, ()) // XXX TODO: more efficient would be to pass in `child` directly

      // ---- partial ----
      val pParent             = readPartialTreeVertex(/* index, */ parentTerm)
      val pChild              = partialTree.insertChild(pParent, childTerm)
      writePartialTreeVertex(pChild)

      childTerm
    }

    private def flushNewTree(level: Int)(implicit tx: S#Tx): Long = {
      implicit val dtx  = durableTx(tx)
      val term          = newVersionID(tx)
      val oldPath       = tx.inputAccess

      // ---- full ----
      writeNewTree(oldPath :+ term, level)

      // ---- partial ----
      // val (oldIndex, parentTerm) = oldPath.splitIndex
      val parentTerm = oldPath.term
      val pParent   = readPartialTreeVertex(/* oldIndex, */ parentTerm)
      val pChild    = partialTree.insertChild(pParent, term) // ( durable )
      writePartialTreeVertex(pChild)

      term
    }

    // do not make this final
    def close() {
      store.close()
    }

    def numRecords    (implicit tx: S#Tx): Int = store.numEntries
    def numUserRecords(implicit tx: S#Tx): Int = math.max(0, numRecords - 1)

    // ---- index tree handler ----

    private final class IndexMapImpl[A](/* protected val index: S#Acc, */
                                        protected val map: Ancestor.Map[D, Long, A])
      extends IndexMap[S, A] {

      override def toString = s"IndexMap($map)" // index.mkString("IndexMap(<", ",", ">, " + map + ")")

      def nearest(term: Long)(implicit tx: S#Tx): (Long, A) = {
        implicit val dtx = durableTx(tx)
        val v = readTreeVertex(map.full, /* index, */ term)._1
        val (v2, value) = map.nearest(v)
        (v2.version, value)
      }

      // XXX TODO: DRY
      def nearestOption(term: Long)(implicit tx: S#Tx): Option[(Long, A)] = {
        implicit val dtx = durableTx(tx)
        val v = readTreeVertex(map.full, /* index, */ term)._1
        map.nearestOption(v) map {
          case (v2, value) => (v2.version, value)
        }
      }

      // XXX TODO: DRY
      def nearestUntil(timeStamp: Long, term: Long)(implicit tx: S#Tx): Option[(Long, A)] = {
        implicit val dtx = durableTx(tx)
        val v = readTreeVertex(map.full, /* index, */ term)._1
        // timeStamp lies somewhere between the time stamp for the tree's root vertex and
        // the exit vertex given by the `term` argument (it may indeed be greater than
        // the time stamp of the `term` = exit vertex argument).
        // In order to find the correct entry, we need to find the nearest ancestor of
        // the vertex associated with `term`, i.e. `v`, for which the additional constraint
        // holds that the versionInfo stored with any candidate vertex is smaller than or equal
        // to the query `timeStamp`.
        //
        // the ancestor search may call the predicate function with any arbitrary z-coordinate,
        // even beyond versions that have already been created. thus, a pre-check is needed
        // before invoking `versionInfo`, so that only valid versions are checked. This is
        // achieved by the conditional `vInt <= maxVersionInt`.
        val maxVersionInt = term.toInt
        map.nearestWithFilter(v) { vInt =>
          if (vInt <= maxVersionInt) {
            // note: while versionInfo formally takes a `Long` term, it only really uses the 32-bit version int
            val info = versionInfo(vInt)
            info.timeStamp <= timeStamp
          } else {
            false // query version higher than exit vertex, possibly an inexisting version!
          }
        } map {
          case (v2, value) => (v2.version, value)
        }
      }

      def add(term: Long, value: A)(implicit tx: S#Tx) {
        implicit val dtx = durableTx(tx)
        val v = readTreeVertex(map.full, /* index, */ term)._1
        map.add((v, value))
      }

      def write(out: DataOutput) {
        map.write(out)
      }
    }

    // writes the vertex information (pre- and post-order entries) of a full tree's leaf (using cookie `0`).
    private def writeTreeVertex(tree: Sys.IndexTree[D], v: Ancestor.Vertex[D, Long])(implicit tx: D#Tx) {
      store.put { out =>
        out.writeByte(0)
        out.writeInt(v.version.toInt)
      } { out =>
        out.writeInt(tree.term.toInt)
        out.writeInt(tree.level)
        tree.tree.vertexSerializer.write(v, out)
      }
    }

    // creates a new index tree. this _writes_ the tree (using cookie `1`), as well as the root vertex.
    // it also creates and writes an empty index map for the tree, used for timeStamp search
    // (using cookie `5`).
    private def writeNewTree(index: S#Acc, level: Int)(implicit tx: S#Tx) {
      val dtx   = durableTx(tx)
      val term  = index.term
      log("txn new tree " + term.toInt)
      val tree  = Ancestor.newTree[D, Long](term)(dtx, ImmutableSerializer.Long, _.toInt)
      val it    = new IndexTreeImpl(tree, level)
      val vInt  = term.toInt
      store.put { out =>
        out.writeByte(1)
        out.writeInt(vInt)
      } {
        it.write _
      }
      writeTreeVertex(it, tree.root)(dtx)

      val map = newIndexMap(index, term, ())(tx, ImmutableSerializer.Unit)
      store.put { out =>
        out.writeByte(5)
        out.writeInt(vInt)
      } {
        map.write _
      }
    }

    // reads the index map maintained for full trees allowing time stamp search
    // (using cookie `5`).
    private def readTimeStampMap(index: S#Acc)(implicit tx: S#Tx): IndexMap[S, Unit] = {
      val opt = store.get { out =>
        out.writeByte(5)
        out.writeInt(index.term.toInt)
      } { in =>
        readIndexMap[Unit](in, index)(tx, ImmutableSerializer.Unit)
      }
      opt.getOrElse(sys.error("No time stamp map found for " + index))
    }

    private def readIndexTree(term: Long)(implicit tx: D#Tx): Sys.IndexTree[D] = {
      val st = store
      st.get { out =>
        out.writeByte(1)
        out.writeInt(term.toInt)
      } { in =>
        val tree = Ancestor.readTree[D, Long](in, ())(tx, ImmutableSerializer.Long, _.toInt) // tx.durable
        val level = in.readInt()
        new IndexTreeImpl(tree, level)
      } getOrElse {
        // sys.error( "Trying to access inexisting tree " + term.toInt )

        // `term` does not form a tree index. it may be a tree vertex, though. thus,
        // in this conditional step, we try to (partially) read `term` as vertex, thereby retrieving
        // the underlying tree index, and then retrying with that index (`term2`).
        st.get { out =>
          out.writeByte(0)
          out.writeInt(term.toInt)
        } { in =>
          val term2 = in.readInt() // tree index!
          require(term2 != term, "Trying to access inexisting tree " + term.toInt)
          readIndexTree(term2)
        } getOrElse {
          sys.error("Trying to access inexisting tree " + term.toInt)
        }
      }
    }

    // reeds the vertex along with the tree level
    final def readTreeVertex(tree: Ancestor.Tree[D, Long], /* index: S#Acc, */ term: Long)
                            (implicit tx: D#Tx): (Ancestor.Vertex[D, Long], Int) = {
      store.get { out =>
        out.writeByte(0)
        out.writeInt(term.toInt)
      } { in =>
        in.readInt() // tree index!
        // val access  = index :+ term
        val level   = in.readInt()
        val v       = tree.vertexSerializer.read(in, ()) // , access) // durableTx( tx )) // tx.durable )
        (v, level)
      } getOrElse sys.error("Trying to access inexisting vertex " + term.toInt)
    }

    // writes the partial tree leaf information, i.e. pre- and post-order entries (using cookie `3`).
    private def writePartialTreeVertex(v: Ancestor.Vertex[D, Long])(implicit tx: S#Tx) {
      store.put { out =>
        out.writeByte(3)
        out.writeInt(v.version.toInt)
      } { out =>
        partialTree.vertexSerializer.write(v, out)
      }
    }

    // ---- index map handler ----

    // creates a new index map for marked values and returns that map. it does not _write_ that map
    // anywhere.
    final def newIndexMap[A](index: S#Acc, rootTerm: Long, rootValue: A)
                            (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A] = {
      implicit val dtx  = durableTx(tx)
      val tree          = readIndexTree(index.term)
      val full          = tree.tree
      val rootVertex    = if (rootTerm == tree.term) {
        full.root
      } else {
        readTreeVertex(full, /* index, */ rootTerm)._1
      }
      val map           = Ancestor.newMap[D, Long, A](full, rootVertex, rootValue)
      new IndexMapImpl[ /* S, D, */ A](/* index, */ map)
    }

    final def readIndexMap[A](in: DataInput, index: S#Acc)
                             (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A] = {
      implicit val dtx  = durableTx(tx)
      val term          = index.term
      val tree          = readIndexTree(term)
      val map           = Ancestor.readMap[D, Long, A](in, (), tree.tree)
      new IndexMapImpl[A](/* index, */ map)
    }

    // true is term1 is ancestor of term2
    def isAncestor(/* index: S#Acc, */ term1: Long, term2: Long)(implicit tx: S#Tx): Boolean = {
      implicit val dtx = durableTx(tx)
      if (term1 == term2) return true // same vertex
      if (term1.toInt > term2.toInt) return false // can't be an ancestor if newer

      val tree = readIndexTree(term1)
      if (tree.term == term1) return true // if term1 is the root then it must be ancestor of term2

      val v1 = readTreeVertex(tree.tree, /* index, */ term1)._1
      val v2 = readTreeVertex(tree.tree, /* index, */ term2)._1
      v1.isAncestorOf(v2)
    }

    // ---- partial map handler ----

    private final class PartialMapImpl[A](/* protected val index: S#Acc, */
                                          protected val map: Ancestor.Map[D, Long, A])
      extends IndexMap[S, A] {

      override def toString = s"PartialMap($map)" // index.mkString("PartialMap(<", ",", ">, " + map + ")")

      def nearest(term: Long)(implicit tx: S#Tx): (Long, A) = {
        implicit val dtx = durableTx(tx)
        val v = readPartialTreeVertex(/* index, */ term)
        val (v2, value) = map.nearest(v)
        (v2.version, value)
      }

      // XXX TODO: DRY
      def nearestOption(term: Long)(implicit tx: S#Tx): Option[(Long, A)] = {
        implicit val dtx = durableTx(tx)
        val v = readPartialTreeVertex(/*  index, */ term)
        map.nearestOption(v).map {
          case (v2, value) => (v2.version, value)
        }
      }

      def nearestUntil(timeStamp: Long, term: Long)(implicit tx: S#Tx): Option[(Long, A)] = {
        ???
      }

      def add(term: Long, value: A)(implicit tx: S#Tx) {
        implicit val dtx = durableTx(tx)
        val v = readPartialTreeVertex(/* index, */ term)
        map.add((v, value))
      }

      def write(out: DataOutput) {
        map.write(out)
      }
    }

    private def readPartialTreeVertex(/* index: S#Acc, */ term: Long)(implicit tx: D#Tx): Ancestor.Vertex[D, Long] = {
      store.get { out =>
        out.writeByte(3)
        out.writeInt(term.toInt)
      } { in =>
        // val access = index :+ term
        partialTree.vertexSerializer.read(in, ()) // access)
      } getOrElse {
        sys.error("Trying to access inexisting vertex " + term.toInt)
      }
    }

    final def getIndexTreeTerm(term: Long)(implicit tx: S#Tx): Long = {
      implicit val dtx = durableTx(tx)
      readIndexTree(term).term
    }

    final def newPartialMap[A](/* access: S#Acc, rootTerm: Long, */ rootValue: A)
                              (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A] = {
      implicit val dtx = durableTx(tx)
      val map   = Ancestor.newMap[D, Long, A](partialTree, partialTree.root, rootValue)
      // val index = access.take(1) // XXX correct ?
      new PartialMapImpl[A](/* index, */ map)
    }

    final def readPartialMap[A](/* access: S#Acc, */ in: DataInput)
                               (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A] = {
      implicit val dtx = durableTx(tx)
      val map   = Ancestor.readMap[D, Long, A](in, (), partialTree)
      // val index = access.take(1) // XXX correct ?
      new PartialMapImpl[A](/* index, */ map)
    }
  }

  // ---------------------------------------------
  // ---------------- END systems ----------------
  // ---------------------------------------------
}
