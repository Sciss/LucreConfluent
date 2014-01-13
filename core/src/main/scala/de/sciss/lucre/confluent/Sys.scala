/*
 *  Sys.scala
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

import de.sciss.lucre.stm.{Txn => _Txn, SpecGroup => ialized, TxnLike, DataStore, Disposable, Identifier}
import data.Ancestor
import de.sciss.fingertree.FingerTree
import collection.immutable.{IndexedSeq => Vec}
import scala.{specialized => spec}
import serial.{ImmutableSerializer, DataInput, Writable}

object Sys {
  trait Entry[S <: Sys[S], A] extends stm.Var[S#Tx, A] {
    def meld(from: S#Acc)(implicit tx: S#Tx): A
  }

  trait Var[S <: Sys[S], @spec(ialized) A] extends stm.Var[S#Tx, A] {
    private[confluent] def setInit(value: A)(implicit tx: S#Tx): Unit
  }

  private[confluent] trait IndexTree[D <: stm.DurableLike[D]] extends Writable with Disposable[D#Tx] {
    def tree : Ancestor.Tree[D, Long]
    def level: Int
    def term : Long
  }

  trait IndexMapHandler[S <: Sys[S]] {
    def readIndexMap[A](in: DataInput, index: S#Acc)
                       (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    def newIndexMap[A](index: S#Acc, rootTerm: Long, rootValue: A)
                      (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    // true is term1 is ancestor of term2
    def isAncestor(/* index: S#Acc, */ term1: Long, term2: Long)(implicit tx: S#Tx): Boolean
  }

  trait PartialMapHandler[S <: Sys[S]] {
    def getIndexTreeTerm(term: Long)(implicit tx: S#Tx): Long

    def readPartialMap[A](/* access: S#Acc, */ in: DataInput)
                         (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    def newPartialMap[A](/* access: S#Acc, rootTerm: Long, */ rootValue: A)
                        (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]
  }

  trait Txn[S <: Sys[S]] extends _Txn[S] {
    def inputAccess: S#Acc

    def info: VersionInfo.Modifiable

    def isRetroactive: Boolean

    // def forceWrite(): Unit

    private[confluent] def readTreeVertexLevel(term: Long): Int
    private[confluent] def addInputVersion(path: S#Acc): Unit

    private[confluent] def putTxn[A]   (id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit
    private[confluent] def putNonTxn[A](id: S#ID, value: A)(implicit ser: ImmutableSerializer[A]): Unit

    private[confluent] def getTxn[A]   (id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A
    private[confluent] def getNonTxn[A](id: S#ID)(implicit ser: ImmutableSerializer[A]): A

    // private[confluent] def isFresh(id: S#ID): Boolean

    private[confluent] def putPartial[A](id: S#ID, value: A)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): Unit
    private[confluent] def getPartial[A](id: S#ID)(implicit ser: serial.Serializer[S#Tx, S#Acc, A]): A

    private[confluent] def removeFromCache(id: S#ID): Unit

    private[confluent] def addDirtyCache     (cache: Cache[S#Tx]): Unit
    private[confluent] def addDirtyLocalCache(cache: Cache[S#Tx]): Unit

    private[confluent] def removeDurableIDMap[A](map: stm.IdentifierMap[S#ID, S#Tx, A]): Unit

    // ---- cursors ----

    // def newCursor(init: S#Acc = inputAccess): Cursor[S, S#D]
    // def readCursor(in: DataInput): Cursor[S, S#D]
  }

  trait ID[S <: Sys[S]] extends Identifier[S#Tx] {
    def base: Int  // name, origin, base, agent, ancestry, germ, parent, root
    def path: S#Acc
  }

  object Acc {
    def root[S <: Sys[S]]: Acc[S] = impl.PathImpl.root[S]
    def info[S <: Sys[S]](access: Acc[S])(implicit tx: TxnLike, system: S): VersionInfo =
      system.versionInfo(access.term)
  }

  trait Acc[S <: Sys[S]] extends Writable with PathLike {
    def mkString(prefix: String, sep: String, suffix: String): String

    // prepend element
    private[confluent] def +:(suffix: Long): S#Acc

    // append element
    private[confluent] def :+(last: Long): S#Acc

    private[confluent] def index: S#Acc
    private[confluent] def tail:  S#Acc

    private[confluent] def term:     Long
    private[confluent] def indexSum: Long

    private[confluent] def apply(idx: Int): Long

    private[confluent] def maxPrefixLength(term: Long): Int

    def seminal: S#Acc

    private[confluent] def partial: S#Acc

    private[confluent] def tree: FingerTree[(Int, Long), Long] // :-( it's unfortunate having to expose this

    // split off last term, return index (init) and that last term
    private[confluent] def splitIndex: (S#Acc, Long)

    // split an index and term at a given point. that is
    // return the `idx` first elements of the path, and the one
    // following (the one found when applying `idx`).
    // although not enforced, `idx` should be an odd number,
    // greater than zero and less than `size`.
    private[confluent] def splitAtIndex(idx: Int): (S#Acc, Long)

    private[confluent] def splitAtSum(hash: Long): (S#Acc, Long)

    //      private[confluent] def indexOfSum( hash: Long ): Int

    //      private[confluent] def dropAndReplaceHead( dropLen: Int, newHead: Long ) : S#Acc

    private[confluent] def addTerm(term: Long)(implicit tx: S#Tx): S#Acc

    // drop initial elements
    private[confluent] def drop(num: Int): S#Acc
    private[confluent] def take(num: Int): S#Acc

    private[confluent] def head: Long
    private[confluent] def last: Long

    private[confluent] def isEmpty:  Boolean
    private[confluent] def nonEmpty: Boolean

    /**
     * Retrieves the version information associated with the access path.
     */
    def info(implicit tx: S#Tx): VersionInfo

    /**
     * Truncates the path to a prefix corresponding to the most recent
     * transaction along the path which has occurred not after a given
     * point in (system) time.
     *
     * In other words, calling `info` on the returned path results in
     * a `VersionInfo` object whose `timeStamp` field is less than or
     * equal to the `timeStamp` argument of this method. The only
     * exception is if the `timeStamp` argument is smaller than the
     * root version of system; in that case, the root path is returned
     * instead of an empty path.
     *
     * '''Note:''' This assumes that incremental versions correspond
     * with incremental time stamps. This is not enforced and if this is not the case,
     * the behaviour is undefined. Furthermore, if it is allowed that
     * multiple successive versions have the same time stamp. In that
     * case, it is undefined which of these versions is returned.
     *
     * @param   timeStamp  the query time (in terms of `System.currentTimeMillis`)
     */
    def takeUntil(timeStamp: Long)(implicit tx: S#Tx): S#Acc
  }
}

/**
 * This is analogous to a `ConfluentLike` trait. Since there is only one system in
 * `LucreConfluent`, it was decided to just name it `confluent.Sys`.
 *
 * @tparam S   the implementing system
 */
trait Sys[S <: Sys[S]] extends stm.Sys[S] {
  type D <: stm.DurableLike[D]
  type I <: stm.InMemoryLike[I]

  type Tx                          <: Sys.Txn[S]
  final type ID                     = Sys.ID[S]
  final type Acc                    = Sys.Acc[S]
  final type Var[@spec(ialized) A]  = Sys.Var[S, A]
  final type Entry[A]               = Sys.Entry[S, A]

  def durable : D
  def inMemory: I

  private[lucre] def durableTx (tx: S#Tx): D#Tx
  private[lucre] def inMemoryTx(tx: S#Tx): I#Tx

  private[confluent] def fullCache:    CacheMap.Durable[S, Int, DurablePersistentMap[S, Int]]
  private[confluent] def partialCache: CacheMap.Partial[S, Int, DurablePersistentMap[S, Int]]

  private[confluent] def newIDValue()(implicit tx: S#Tx): Int
  private[confluent] def newVersionID(implicit tx: S#Tx): Long

  private[confluent] def store: DataStore

  private[confluent] def indexMap: Sys.IndexMapHandler[S]

  private[confluent] def flushRegular(meldInfo: MeldInfo[S], newVersion: Boolean, caches: Vec[Cache[S#Tx]])(implicit tx: S#Tx): Unit
  private[confluent] def flushRoot   (meldInfo: MeldInfo[S], newVersion: Boolean, caches: Vec[Cache[S#Tx]])(implicit tx: S#Tx): Unit

  /* private[confluent] */ def readPath(in: DataInput): S#Acc

  private[confluent] def createTxn(dtx: D#Tx, inputAccess: S#Acc, retroactive: Boolean, cursorCache: Cache[S#Tx]): S#Tx

  // ---- cursors ----

  def newCursor()(implicit tx: S#Tx): Cursor[S, D]
  /* private[confluent] */ def newCursor (init: S#Acc  )(implicit tx: S#Tx): Cursor[S, D]
  /* private[confluent] */ def readCursor(in: DataInput)(implicit tx: S#Tx): Cursor[S, D]

  /** Initializes the data structure, by either reading an existing entry or generating the root entry
    * with the `init` function. The method than allows the execution of another function within the
    * same transaction, passing it the data structure root of type `A`. This is typically used to
    * generate access mechanisms, such as extracting a cursor from the data structure, or instantiating
    * a new cursor. The method then returns both the access point to the data structure and the result
    * of the second function.
    *
    * @param init         a function to initialize the data structure (if the database is fresh)
    * @param result       a function to process the data structure
    * @param serializer   a serializer to read or write the data structure
    * @tparam A           type of data structure
    * @tparam B           type of result from the second function. typically this is an `stm.Cursor[S]`
    * @return             the access to the data structure along with the result of the second function.
    */
  def cursorRoot[A, B](init: S#Tx => A)(result: S#Tx => A => B)
                      (implicit serializer: serial.Serializer[S#Tx, S#Acc, A]): (S#Entry[A], B)

  def rootWithDurable[A, B](confluent: S#Tx => A)(durable: D#Tx => B)
                           (implicit aSer: serial.Serializer[S#Tx, S#Acc, A],
                                     bSer: serial.Serializer[D#Tx, D#Acc, B]): (stm.Source[S#Tx, A], B)

  /**
   * Retrieves the In information for a given version term.
   */
  private[confluent] def versionInfo(term: Long)(implicit tx: TxnLike): VersionInfo

  private[confluent] def versionUntil(access: S#Acc, timeStamp: Long)(implicit tx: S#Tx): S#Acc // XXX TODO: can we get to TxnLike here, too?

  def debugPrintIndex(index: S#Acc)(implicit tx: S#Tx): String
}