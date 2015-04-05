/*
 *  Acc.scala
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