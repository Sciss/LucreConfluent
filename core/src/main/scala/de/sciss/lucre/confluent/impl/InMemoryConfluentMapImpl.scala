/*
 *  InMemoryConfluentMapImpl.scala
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
package impl

import concurrent.stm.TMap
import collection.immutable.LongMap
import scala.{specialized => spec}
import data.{KeySpec, ValueSpec}

object InMemoryConfluentMapImpl {
  private trait Entry[+A]
  private final case class EntryPre                     (hash: Long)       extends Entry[Nothing]
  private final case class EntryFull[@spec(ValueSpec) A](term: Long, v: A) extends Entry[A]
}
final class InMemoryConfluentMapImpl[S <: Sys[S], @spec(KeySpec) K] extends InMemoryConfluentMap[S, K] {
  import InMemoryConfluentMapImpl._

  private type Entries = Map[Long, Entry[Any]]
  private val store = TMap.empty[K, Entries]

  override def toString = "InMemoryConfluentMap(" + store + ")"

  def put[@spec(ValueSpec) A](key: K, path: S#Acc, value: A)(implicit tx: S#Tx): Unit = {
    implicit val itx = tx.peer
    val (index, term) = path.splitIndex

    var entries = store.get(key).getOrElse(LongMap.empty)
    Hashing.foreachPrefix(index, entries.contains) {
      // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
      case (hash, preSum) => entries += (hash -> EntryPre(preSum))
    }
    // then store full value
    entries += (index.sum -> EntryFull(term, value))
    store.put(key, entries)
  }

  def remove(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean = {
    implicit val itx = tx.peer
    store.get(key) match {
      case Some(entries) =>
        val index       = path.index
        var newEntries  = entries
        Hashing.foreachPrefix(index, entries.contains) {
          case (hash, _) => newEntries -= hash
        }
        val indexSum  = index.sum
        val res       = newEntries.contains(indexSum)
        if (res) newEntries -= indexSum
        if (newEntries.isEmpty) {
          store.remove(key)
        } else {
          store.put(key, newEntries)
        }
        res

      case _ => false
    }
  }

  def get[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[A] = {
    if (path.isEmpty) return None
    store.get(key)(tx.peer).flatMap { entries =>
      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[A, A](maxIndex, maxTerm, entries)((_, _, value) => value)
    }
  }

  def getWithSuffix[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[(S#Acc, A)] = {
    if (path.isEmpty) return None
    store.get(key)(tx.peer).flatMap { entries =>
      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[A, (S#Acc, A)](maxIndex, maxTerm, entries)((preLen, writeTerm, value) =>
        (writeTerm +: path.drop(preLen), value)
      )
    }
  }

  private def getWithPrefixLen[A, B](maxIndex: S#Acc, maxTerm: Long, entries: Entries)
                                    (fun: (Int, Long, A) => B): Option[B] = {

    val preLen  = Hashing.maxPrefixLength(maxIndex, entries.contains)
    val index   = if (preLen == maxIndex.size) {
      // maximum prefix lies in last tree
      maxIndex
    } else {
      // prefix lies in other tree
      maxIndex.take(preLen)
    }
    val preSum = index.sum
    entries.get(preSum).flatMap {
      case EntryPre(hash) => // partial hash
        val (fullIndex, fullTerm) = maxIndex.splitAtSum(hash)
        getWithPrefixLen(fullIndex, fullTerm, entries)(fun)

      case EntryFull(term2, value) => Some(fun(preLen, term2, value.asInstanceOf[A]))
    }
  }
}
