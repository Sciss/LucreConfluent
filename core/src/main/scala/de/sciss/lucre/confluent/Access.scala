/*
 *  Access.scala
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

import de.sciss.fingertree.FingerTree
import de.sciss.lucre.stm.TxnLike
import de.sciss.serial.Writable

object Access {
  def root[S <: Sys[S]]: Access[S] = impl.PathImpl.root[S]
  def info[S <: Sys[S]](access: Access[S])(implicit tx: TxnLike, system: S): VersionInfo =
    system.versionInfo(access.term)
}

trait Access[S <: Sys[S]] extends Writable with PathLike {
  def mkString(prefix: String, sep: String, suffix: String): String

  /** Prepends a single element. */
  def +:(head: Long): S#Acc

  /** Appends a single element. */
  def :+(last: Long): S#Acc

  /** Drops the last element. */
  def index: S#Acc

  /** Drops the head element. */
  def tail:  S#Acc

  def term: Long

  private[confluent] def indexSum: Long

  private[confluent] def apply(idx: Int): Long

  private[confluent] def maxPrefixLength(term: Long): Int

  def seminal: S#Acc

  private[confluent] def partial: S#Acc

  private[confluent] def tree: FingerTree[(Int, Long), Long] // :-( it's unfortunate having to expose this

  /** Splits off last term, returning index (init) and that last term. */
  def splitIndex: (S#Acc, Long)

  // split an index and term at a given point. that is
  // return the `idx` first elements of the path, and the one
  // following (the one found when applying `idx`).
  // although not enforced, `idx` should be an odd number,
  // greater than zero and less than `size`.
  private[confluent] def splitAtIndex(idx: Int): (S#Acc, Long)

  private[confluent] def splitAtSum(hash: Long): (S#Acc, Long)

  /** Replaces the terminal version with the given `term`.
    * If the new term is on the same tree level as the old term,
    * the term is replaced, otherwise a new tree is entered
    * (the new term is appended twice).
    */
  def addTerm(term: Long)(implicit tx: S#Tx): S#Acc

  // drop initial elements
  def drop(num: Int): S#Acc
  def take(num: Int): S#Acc

  def head: Long
  def last: Long

  def isEmpty:  Boolean
  def nonEmpty: Boolean

  /** Retrieves the version information associated with the access path. */
  def info(implicit tx: S#Tx): VersionInfo

  /** Truncates the path to a prefix corresponding to the most recent
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