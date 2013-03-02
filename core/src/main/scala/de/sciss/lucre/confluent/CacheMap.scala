/*
 *  CacheMap.scala
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

package de.sciss.lucre
package confluent

import io.ImmutableSerializer
import scala.{specialized => spec}
import data.KeySpec

object CacheMap {
  trait InMemory[S <: stm.Sys[S], @spec(KeySpec) K, +Store] extends CacheMap[S, K, Store] {
    def putCache[A](key: K, path: S#Acc, value: A)(implicit tx: S#Tx): Unit
    def getCache[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[A]
  }

  trait Durable[S <: stm.Sys[S], @spec(KeySpec) K, +Store] extends CacheMap[S, K, Store] {
    def putCacheTxn[A](key: K, path: S#Acc, value: A)
                      (implicit tx: S#Tx, serializer: io.Serializer[S#Tx, S#Acc, A]): Unit

    def putCacheNonTxn[A](key: K, path: S#Acc, value: A)
                         (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Unit

    def getCacheTxn[A](key: K, path: S#Acc)
                      (implicit tx: S#Tx, serializer: io.Serializer[S#Tx, S#Acc, A]): Option[A]

    def getCacheNonTxn[A](key: K, path: S#Acc)
                         (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Option[A]
  }

  trait Partial[S <: stm.Sys[S], @spec(KeySpec) K, +Store] extends CacheMap[S, K, Store] {
    def putPartial[A](key: K, path: S#Acc, value: A)
                     (implicit tx: S#Tx, serializer: io.Serializer[S#Tx, S#Acc, A]): Unit

    def getPartial[A](key: K, path: S#Acc)
                     (implicit tx: S#Tx, serializer: io.Serializer[S#Tx, S#Acc, A]): Option[A]
  }

}

trait CacheMap[S <: stm.Sys[S], @spec(KeySpec) K, +Store] extends Cache[S#Tx] {
  // ---- abstract ----

  /**
   * The persistent map to which the data is flushed or from which it is retrieved when not residing in cache.
   */
  def store: Store

  /**
   * Implementations may provide a particular map implementation for the cache (e.g. `IntMap` or `LongMap`).
   * The value type of the returned map (which must be immutable and empty) is cast to the internal cache
   * entries.
   */
  protected def emptyCache: Map[K, Any]

  // ---- implementation ----

  def getCacheOnly[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[A]

  def cacheContains(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean

  /**
   * Removes an entry from the cache, and only the cache. This will not affect any
   * values also persisted to `persistent`! If the cache does not contain an entry
   * at the given `key`, this method simply returns.
   *
   * @param key        key at which the entry is stored
   * @param tx         the current transaction
   */
  def removeCacheOnly(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean

  /**
   * This method should be invoked from the implementations flush hook after it has
   * determined the terminal version at which the entries in the cache are written
   * to the persistent store. If this method is not called, the cache will just
   * vanish and not be written out to the `persistent` store.
   *
   * @param term    the new version to append to the paths in the cache (using the `PathLike`'s `addTerm` method)
   * @param tx      the current transaction (should be in commit or right-before commit phase)
   */
  def flushCache(term: Long)(implicit tx: S#Tx): Unit

  def removeCache(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean
}