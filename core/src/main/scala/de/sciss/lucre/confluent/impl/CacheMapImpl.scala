/*
 *  CacheMapImpl.scala
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
package impl

import concurrent.stm.TxnLocal
import serial.{DataInput, DataOutput, ImmutableSerializer}

object CacheMapImpl {
   /** Instances of `Entry` are stored for each variable write in a transaction. They
     * are flushed at the commit to the persistent store. There are two sub types, a
     * transactional and a non-transactional one. A non-transactional cache entry can de-serialize
     * the value without transactional context, e.g. this is true for all primitive types.
     * A transactional entry is backed by a `Serializer`. To be saved in the store which uses
     * a sub system (`Durable`), serialization is a two-step process, using an intermediate
     * binary representation.
     */
   sealed trait Entry[S <: Sys[S], /* @spec(KeySpec) */ -K, -Store] {
     def path: S#Acc

     def flush(key: K, outTerm: Long, store: Store)(implicit tx: S#Tx): Unit

     def value: Any
   }
}

/** A cache map puts an in-memory transaction local cache in front of a persistent store. Entries written
  * during the transaction are held in this cache for fast retrieval. But the cache serves a second purpose:
  * In the confluent system, the write paths are incomplete during the transaction, as it is not known in
  * advance whether a meld forces a new index tree to be generated or not. In this case, the implementation
  * needs to gather this information during the transaction, and when the flush is performed, the new
  * terminal version is appended before writing the cached entries to the persistent store.
  *
  * @tparam S   the underlying system
  * @tparam K   the key type (typically `Int` for a variable map or `Long` for an identifier map)
  */
sealed trait CacheMapImpl[S <: Sys[S], /* @spec(KeySpec) */ K, Store]
  extends CacheMap[S, K, Store] {

  import CacheMapImpl._

  private val cache = TxnLocal(Map.empty[K, Map[Long, Entry[S, K, Store]]])

  // ---- implementation ----

  final def getCacheOnly[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[A] =
    cache.get(tx.peer).get(key).flatMap(_.get(path.sum).map { e =>
      e.value.asInstanceOf[A]
    })

  final def cacheContains(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean =
    cache.get(tx.peer).get(key) match {
      case Some(map) => map.contains(path.sum)
      case _ => false
    }

  /** Removes an entry from the cache, and only the cache. This will not affect any
    * values also persisted to `persistent`! If the cache does not contain an entry
    * at the given `key`, this method simply returns.
    *
    * @param key        key at which the entry is stored
    * @param tx         the current transaction
    */
  final def removeCacheOnly(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean = {
    implicit val itx = tx.peer
    //      cache.transform( _ - key )( tx.peer )
    val m = cache.get
    val km = m.getOrElse(key, Map.empty)
    val hash = path.sum
    if (km.contains(hash)) {
      val km1 = km - hash
      val m1 = if (km1.isEmpty) {
        m - key
      } else {
        m + (key -> km1)
      }
      cache.set(m1)
      true
    } else {
      false
    }
  }

  final protected def putCacheOnly(key: K, e: Entry[S, K, Store])(implicit tx: S#Tx): Unit = {
    implicit val itx = tx.peer
    cache.transform { mapMap =>
      val mapOld = mapMap.getOrElse(key, Map.empty[Long, Entry[S, K, Store]])
      val mapNew = mapOld + (e.path.sum -> e)
      mapMap + ((key, mapNew))
    }
  }

  /** This method should be invoked from the implementations flush hook after it has
    * determined the terminal version at which the entries in the cache are written
    * to the persistent store. If this method is not called, the cache will just
    * vanish and not be written out to the `persistent` store.
    *
    * @param term    the new version to append to the paths in the cache (using the `PathLike`'s `addTerm` method)
    * @param tx      the current transaction (should be in commit or right-before commit phase)
    */
  final def flushCache(term: Long)(implicit tx: S#Tx): Unit = {
    val p = store
    cache.get(tx.peer).foreach { case (key, map) =>
      map.foreach { tup2 =>
        val e = tup2._2
        e.flush(key, term, p)
      }
    }
  }
}

object DurableCacheMapImpl {
  import CacheMapImpl.Entry

  private type Store[S <: Sys[S], K] = DurablePersistentMap[S, K]

  def newIntCache[S <: Sys[S]](map: Store[S, Int]): CacheMap.Durable[S, Int, Store[S, Int]] =
    new DurableCacheMapImpl[S, Int] {
      // final protected def emptyCache: Map[Int, Any] = Map.empty

      final val store: Store[S, Int] = map
    }

  private final class NonTxnEntry[S <: Sys[S], /* @spec(KeySpec) */ K, /* @spec(ValueSpec) */ A]
  (val path: S#Acc, val value: A)(implicit serializer: ImmutableSerializer[A])
    extends Entry[S, K, Store[S, K]] {
    override def toString = s"NonTxnEntry($value)"

    def flush(key: K, outTerm: Long, store: Store[S, K])(implicit tx: S#Tx): Unit = {
      val pathOut = path.addTerm(outTerm)
      log("txn flush write " + value + " for " + pathOut.mkString("<" + key + " @ ", ",", ">"))
      store.put(key, pathOut, value)
    }
  }

  private final class TxnEntry[S <: Sys[S], /* @spec(KeySpec) */ K, /* @spec(ValueSpec) */ A]
  (val path: S#Acc, val value: A)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
    extends Entry[S, K, Store[S, K]] {
    override def toString = s"TxnEntry($value)"

    def flush(key: K, outTerm: Long, store: Store[S, K])(implicit tx: S#Tx): Unit = {
      val pathOut = path.addTerm(outTerm)
      log("txn flush write " + value + " for " + pathOut.mkString("<" + key + " @ ", ",", ">"))
      val out = DataOutput()
      serializer.write(value, out)
      val arr = out.toByteArray
      store.put(key, pathOut, arr)(tx, ByteArraySerializer)
    }
  }
}

trait DurableCacheMapImpl[S <: Sys[S], /* @spec(KeySpec) */ K]
  extends CacheMap.Durable[S, K, DurablePersistentMap[S, K]]
  with CacheMapImpl[S, K, DurablePersistentMap[S, K]] {

  import DurableCacheMapImpl._

  /** Stores an entry in the cache for which 'only' a transactional serializer exists.
    *
    * Note that the caller is responsible for monitoring this call, and if necessary installing
    * a before-commit handler which will call into the abstract method `flushCache()`.
    *
    * @param key        key at which the entry will be stored
    * @param path       write path when persisting
    * @param value      value to be stored (entry)
    * @param tx         the current transaction
    * @param serializer the serializer to use for the value
    * @tparam A         the type of value stored
    */
  final def putCacheTxn[/* @spec(ValueSpec) */ A](key: K, path: S#Acc, value: A)
                                           (implicit tx: S#Tx, serializer: serial.Serializer[S#Tx, S#Acc, A]): Unit =
    putCacheOnly(key, new TxnEntry[S, K, A](path, value))

  /** Stores an entry in the cache for which a non-transactional serializer exists.
    *
    * Note that the caller is responsible for monitoring this call, and if necessary installing
    * a before-commit handler which will call into the abstract method `flushCache()`.
    *
    * @param key        key at which the entry will be stored
    * @param path       write path when persisting
    * @param value      value to be stored (entry)
    * @param tx         the current transaction
    * @param serializer the serializer to use for the value
    * @tparam A         the type of value stored
    */
  final def putCacheNonTxn[A](key: K, path: S#Acc, value: A)
                             (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Unit =
    putCacheOnly(key, new NonTxnEntry[S, K, A](path, value))

  /** Retrieves a value from the cache _or_ the underlying store (if not found in the cache), where 'only'
    * a transactional serializer exists.
    *
    * If no value is found for the current path, this will try to read the most recent entry along the path.
    *
    * @param key        key at which the entry is stored
    * @param path       access path for the read
    * @param tx         the current transaction
    * @param serializer the serializer to use for the value
    * @tparam A         the type of value stored
    * @return           the most recent value found, or `None` if a value cannot be found for the given path,
    *                   neither in the cache nor in the persistent store.
    */
  final def getCacheTxn[A](key: K, path: S#Acc)
                          (implicit tx: S#Tx, serializer: serial.Serializer[S#Tx, S#Acc, A]): Option[A] =
    getCacheOnly(key, path).orElse {
      store.getWithSuffix[Array[Byte]](key, path)(tx, ByteArraySerializer).map { tup =>
        val access = tup._1
        val arr = tup._2
        val in = DataInput(arr)
        serializer.read(in, access)
      }
    }

  /** Retrieves a value from the cache _or_ the underlying store (if not found in the cache), where a
    * non-transactional serializer exists.
    *
    * If no value is found for the current path, this will try to read the most recent entry along the path.
    *
    * @param key        key at which the entry is stored
    * @param path       access path for the read
    * @param tx         the current transaction
    * @param serializer the serializer to use for the value
    * @tparam A         the type of value stored
    * @return           the most recent value found, or `None` if a value cannot be found for the given path,
    *                   neither in the cache nor in the persistent store.
    */
  final def getCacheNonTxn[A](key: K, path: S#Acc)
                             (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Option[A] =
    getCacheOnly(key, path).orElse(store.get[A](key, path))

  //   final protected def isFresh( key: K, path: S#Acc )( implicit tx: S#Tx ) : Boolean =
  //      cacheContains( key, path ) || {
  //         store.getWithSuffix()
  //      }

  final def removeCache(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean = {
    removeCacheOnly(key, path) || store.remove(key, path)
  }
}

object InMemoryCacheMapImpl {
  private type Store[S <: Sys[S], K] = InMemoryConfluentMap[S, K]

  private final class Entry[S <: Sys[S], /* @spec(KeySpec) */ K, /* @spec(ValueSpec) */ A]
  (val path: S#Acc, val value: A)
    extends CacheMapImpl.Entry[S, K, Store[S, K]] {
    override def toString = s"Entry($value)"

    def flush(key: K, outTerm: Long, store: Store[S, K])(implicit tx: S#Tx): Unit = {
      val pathOut = path.addTerm(outTerm)
      log(s"txn flush write $value for ${pathOut.mkString(s"<$key @ ", ",", ">")}")
      store.put(key, pathOut, value)
    }
  }
}

trait InMemoryCacheMapImpl[S <: Sys[S], /* @spec(KeySpec) */ K]
  extends CacheMap.InMemory[S, K, InMemoryConfluentMap[S, K]]
  with CacheMapImpl[S, K, InMemoryConfluentMap[S, K]] {

  import InMemoryCacheMapImpl._

  final def putCache[/* @spec(ValueSpec) */ A](key: K, path: S#Acc, value: A)(implicit tx: S#Tx): Unit =
    putCacheOnly(key, new Entry(path, value))

  final def getCache[A](key: K, path: S#Acc)(implicit tx: S#Tx): Option[A] =
    getCacheOnly(key, path).orElse(store.get[A](key, path))

  final def removeCache(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean = {
    removeCacheOnly(key, path) || store.remove(key, path)
  }
}

// ---------------------------------------------------------------------

object PartialCacheMapImpl {
  import CacheMapImpl.Entry

  private type Store[S <: Sys[S], K] = DurablePersistentMap[S, K]

  def newIntCache[S <: Sys[S]](map: Store[S, Int]): CacheMap.Partial[S, Int, Store[S, Int]] =
    new PartialCacheMapImpl[S, Int] {

      // final protected def emptyCache: Map[Int, Any] = Map.empty
      final val store: DurablePersistentMap[S, Int] = map
    }

  private final class PartialEntry[S <: Sys[S], /* @spec(KeySpec) */ K, /* @spec(ValueSpec) */ A]
  (val fullPath: S#Acc, val value: A)(implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
    extends Entry[S, K, Store[S, K]] {

    override def toString = s"PartialEntry($value)"

    val path: S#Acc = fullPath.partial

    def flush(key: K, outTerm: Long, store: Store[S, K])(implicit tx: S#Tx): Unit = {
      val pathOut = fullPath.addTerm(outTerm)
      log(s"txn flush write $value for ${pathOut.mkString(s"<$key @ ", ",", ">")}")
      val out     = DataOutput()
      serializer.write(value, out)
      val arr     = out.toByteArray
      store.put(key, pathOut, arr)(tx, ByteArraySerializer)
    }
  }
}

trait PartialCacheMapImpl[S <: Sys[S], /* @spec(KeySpec) */ K]
  extends CacheMap.Partial[S, K, DurablePersistentMap[S, K]]
  with CacheMapImpl       [S, K, DurablePersistentMap[S, K]] {

  import PartialCacheMapImpl._

  final def putPartial[/* @spec(ValueSpec) */ A](key: K, path: S#Acc, value: A)
                                          (implicit tx: S#Tx, serializer: serial.Serializer[S#Tx, S#Acc, A]): Unit =
    putCacheOnly(key, new PartialEntry[S, K, A](path, value))

  final def getPartial[A](key: K, path: S#Acc)(implicit tx: S#Tx,
                                               serializer: serial.Serializer[S#Tx, S#Acc, A]): Option[A] =
    getCacheOnly(key, path.partial).orElse {
      store.getWithSuffix[Array[Byte]](key, path)(tx, ByteArraySerializer).map { tup =>
          val access  = tup._1
          val arr     = tup._2
          val in      = DataInput(arr)
          serializer.read(in, access)
      }
    }

  final def removeCache(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean =
    removeCacheOnly(key, path) || store.remove(key, path)
}