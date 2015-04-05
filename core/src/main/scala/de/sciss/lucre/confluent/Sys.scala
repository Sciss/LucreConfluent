/*
 *  Sys.scala
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

import de.sciss.lucre.data.Ancestor
import de.sciss.lucre.stm.{DataStore, Disposable, Identifier, Txn => _Txn, TxnLike}
import de.sciss.lucre.{confluent, stm}
import de.sciss.serial
import de.sciss.serial.{DataInput, ImmutableSerializer, Writable}

import scala.collection.immutable.{IndexedSeq => Vec}

object Sys {
  trait Entry[S <: Sys[S], A] extends stm.Var[S#Tx, A] {
    def meld(from: S#Acc)(implicit tx: S#Tx): A
  }

  trait Var[S <: Sys[S], /* @spec(ialized) */ A] extends stm.Var[S#Tx, A] {
    private[confluent] def setInit(value: A)(implicit tx: S#Tx): Unit
  }

  trait IndexMapHandler[S <: Sys[S]] {
    def readIndexMap[A](in: DataInput, index: S#Acc)
                       (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    def newIndexMap[A](index: S#Acc, rootTerm: Long, rootValue: A)
                      (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    // true is term1 is ancestor of term2
    def isAncestor(term1: Long, term2: Long)(implicit tx: S#Tx): Boolean
  }

  trait PartialMapHandler[S <: Sys[S]] {
    def getIndexTreeTerm(term: Long)(implicit tx: S#Tx): Long

    def readPartialMap[A](in: DataInput)
                         (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]

    def newPartialMap[A](rootValue: A)
                        (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): IndexMap[S, A]
  }

  trait Txn[S <: Sys[S]] extends _Txn[S] {
    implicit def durable: S#D#Tx

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
}

/** This is analogous to a `ConfluentLike` trait. Since there is only one system in
  * `LucreConfluent`, it was decided to just name it `confluent.Sys`.
  *
  * @tparam S   the implementing system
  */
trait Sys[S <: Sys[S]] extends stm.Sys[S] {
  type D <: stm.DurableLike [D]
  type I <: stm.InMemoryLike[I]

  type Tx                          <: Sys.Txn[S]
  final type ID                     = Sys.ID[S]
  final type Acc                    = confluent.Acc[S]
  final type Var[/* @spec(ialized) */ A]  = Sys.Var[S, A]
  final type Entry[A]               = Sys.Entry[S, A]

  def durable : D
  // def inMemory: I

  def durableTx (tx: S#Tx): D#Tx
  //  /* private[lucre] */ def inMemoryTx(tx: S#Tx): I#Tx

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

  /** Initializes the data structure both with a confluently persisted and an ephemeral-durable value.
    *
    * @param confluent    a function that provides the initial confluent data (if the database is fresh)
    * @param durable      a function that provides the initial ephemeral data (if the database is fresh)
    * @param aSer         a serializer to read or write the confluent data structure
    * @param bSer         a serializer to read or write the ephemeral data structure
    * @tparam A           type of confluent data structure
    * @tparam B           type of ephemeral data structure
    * @return             a tuple consisting of a handle to the confluent structure and the
    *                     ephemeral datum. The ephemeral datum, although written to disk, does not
    *                     require an `stm.Source` because `D#Acc` is `Unit` and does not need refresh.
    */
  def rootWithDurable[A, B](confluent: S#Tx => A)(durable: D#Tx => B)
                           (implicit aSer: serial.Serializer[S#Tx, S#Acc, A],
                                     bSer: serial.Serializer[D#Tx, D#Acc, B]): (stm.Source[S#Tx, A], B)

  /** Retrieves the In information for a given version term. */
  private[confluent] def versionInfo(term: Long)(implicit tx: TxnLike): VersionInfo

  private[confluent] def versionUntil(access: S#Acc, timeStamp: Long)(implicit tx: S#Tx): S#Acc // XXX TODO: can we get to TxnLike here, too?

  def debugPrintIndex(index: S#Acc)(implicit tx: S#Tx): String
}