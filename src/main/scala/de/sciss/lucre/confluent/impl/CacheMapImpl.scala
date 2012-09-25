/*
 *  CacheMapImpl.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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

import stm.{ImmutableSerializer, Serializer}
import concurrent.stm.TxnLocal
import TemporalObjects.logConfluent
import collection.immutable.{IntMap, LongMap}

object CacheMapImpl {
   /**
    * Instances of `Entry` are stored for each variable write in a transaction. They
    * are flushed at the commit to the persistent store. There are two sub types, a
    * transactional and a non-transactional one. A non-transactional cache entry can de-serialize
    * the value without transactional context, e.g. this is true for all primitive types.
    * A transactional entry is backed by a `Serializer`. To be saved in the store which uses
    * a sub system (`Durable`), serialization is a two-step process, using an intermediate
    * binary representation.
    */
   sealed trait Entry[ S <: KSys[ S ], @specialized( Int, Long ) +K, -Store ] {
      def key: K
      def path: S#Acc
      def flush( outTerm: Long, store: Store )( implicit tx: S#Tx ) : Unit
      def value: Any
   }

   val emptyIntMapVal       = IntMap.empty[ Any ]
   def emptyIntMap[ T ]     = emptyIntMapVal.asInstanceOf[ IntMap[ T ]]
   val emptyLongMapVal      = LongMap.empty[ Any ]
   def emptyLongMap[ T ]    = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]
}

/**
 * A cache map puts an in-memory transaction local cache in front of a persistent store. Entries written
 * during the transaction are held in this cache for fast retrieval. But the cache serves a second purpose:
 * In the confluent system, the write paths are incomplete during the transaction, as it is not known in
 * advance whether a meld forces a new index tree to be generated or not. In this case, the implementation
 * needs to gather this information during the transaction, and when the flush is performed, the new
 * terminal version is appended before writing the cached entries to the persistent store.
 *
 * @tparam S   the underlying system
 * @tparam K   the key type (typically `Int` for a variable map or `Long` for an identifier map)
 */
sealed trait CacheMapImpl[ S <: KSys[ S ], @specialized( Int, Long ) K, Store ] {
   import CacheMapImpl._

   private val cache = TxnLocal( emptyCache.asInstanceOf[ Map[ K, LongMap[ Entry[ S, K, Store ]]]])

   // ---- abstract ----

   /**
    * The persistent map to which the data is flushed or from which it is retrieved when not residing in cache.
    */
   protected def store : Store

   /**
    * Implementations may provide a particular map implementation for the cache (e.g. `IntMap` or `LongMap`).
    * The value type of the returned map (which must be immutable and empty) is cast to the internal cache
    * entries.
    */
   protected def emptyCache : Map[ K, _ ]

   // ---- implementation ----

   final protected def getCacheOnly[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ A ] =
      cache.get( tx.peer ).get( key ).flatMap( _.get( path.sum ).map { e =>
         e.value.asInstanceOf[ A ]
      })

   final protected def cacheContains( key: K, path: S#Acc )( implicit tx: S#Tx ) : Boolean =
      cache.get( tx.peer ).get( key ) match {
         case Some( map ) => map.contains( path.sum )
         case _           => false
      }

   /**
    * Removes an entry from the cache, and only the cache. This will not affect any
    * values also persisted to `persistent`! If the cache does not contain an entry
    * at the given `key`, this method simply returns.
    *
    * @param key        key at which the entry is stored
    * @param tx         the current transaction
    */
   final protected def removeCacheOnly( key: K )( implicit tx: S#Tx ) {
      cache.transform( _ - key )( tx.peer )
   }
   
   final protected def putCacheOnly( e: Entry[ S, K, Store ])( implicit tx: S#Tx ) {
      implicit val itx = tx.peer
      cache.transform( mapMap => {
         val mapOld  = mapMap.getOrElse( e.key, emptyLongMap[ Entry[ S, K, Store ]])
         val mapNew  = mapOld + (e.path.sum -> e)
         mapMap + ((e.key, mapNew))
      })
   }

   /**
    * This method should be invoked from the implementations flush hook after it has
    * determined the terminal version at which the entries in the cache are written
    * to the persistent store. If this method is not called, the cache will just
    * vanish and not be written out to the `persistent` store.
    *
    * @param term    the new version to append to the paths in the cache (using the `PathLike`'s `addTerm` method)
    * @param tx      the current transaction (should be in commit or right-before commit phase)
    */
   final def flushCache( term: Long )( implicit tx: S#Tx ) {
      val p = store
      cache.get( tx.peer ).foreach { tup1 =>
         val map  = tup1._2
         map.foreach { tup2 =>
            val e    = tup2._2
            e.flush( term, p )
         }
      }
   }
}
object DurableCacheMapImpl {
   import CacheMapImpl.Entry

   private final class NonTxnEntry[ S <: KSys[ S ], @specialized( Int, Long ) K, @specialized A ]
   ( val key: K, val path: S#Acc, val value: A )( implicit serializer: ImmutableSerializer[ A ])
   extends Entry[ S, K, DurablePersistentMap[ S, K ]] {
      override def toString = "NonTxnEntry(" + key + ", " + value + ")"

      def flush( outTerm: Long, store: DurablePersistentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = path.addTerm( outTerm )
         logConfluent( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         store.put( key, pathOut, value )
      }
   }
   private final class TxnEntry[ S <: KSys[ S ], @specialized( Int, Long ) K, A ]
   ( val key: K, val path: S#Acc, val value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ])
   extends Entry[ S, K, DurablePersistentMap[ S, K ]] {
      override def toString = "TxnEntry(" + key + ", " + value + ")"

      def flush( outTerm: Long, store: DurablePersistentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = path.addTerm( outTerm )
         logConfluent( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         val out     = new DataOutput()
         serializer.write( value, out )
         val arr     = out.toByteArray
         store.put( key, pathOut, arr )( tx, ByteArraySerializer )
      }
   }
}
trait DurableCacheMapImpl[ S <: KSys[ S ], @specialized( Int, Long ) K ]
extends CacheMapImpl[ S, K, DurablePersistentMap[ S, K ]] {
   import DurableCacheMapImpl._

   /**
    * Stores an entry in the cache for which 'only' a transactional serializer exists.
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
   final protected def putCacheTxn[ A ]( key: K, path: S#Acc, value: A )
                                       ( implicit tx: S#Tx, serializer: Serializer[ S#Tx, S#Acc, A ]) {
      putCacheOnly( new TxnEntry( key, path, value ))
   }

   /**
    * Stores an entry in the cache for which a non-transactional serializer exists.
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
   final protected def putCacheNonTxn[ A ]( key: K, path: S#Acc, value: A )
                                          ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) {
      putCacheOnly( new NonTxnEntry( key, path, value ))
   }

   /**
    * Retrieves a value from the cache _or_ the underlying store (if not found in the cache), where 'only'
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
   final protected def getCacheTxn[ A ]( key: K, path: S#Acc )
                                       ( implicit tx: S#Tx,
                                         serializer: Serializer[ S#Tx, S#Acc, A ]) : Option[ A ] =
      getCacheOnly( key, path ).orElse {
         store.getWithSuffix[ Array[ Byte ]]( key, path )( tx, ByteArraySerializer ).map { tup =>
            val access  = tup._1
            val arr     = tup._2
            val in      = new DataInput( arr )
            serializer.read( in, access )
         }
      }

   /**
    * Retrieves a value from the cache _or_ the underlying store (if not found in the cache), where a
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
   final protected def getCacheNonTxn[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx,
                                                                   serializer: ImmutableSerializer[ A ]) : Option[ A ] =
      getCacheOnly( key, path ).orElse( store.get[ A ]( key, path ))

//   final protected def isFresh( key: K, path: S#Acc )( implicit tx: S#Tx ) : Boolean =
//      cacheContains( key, path ) || {
//         store.getWithSuffix()
//      }
}
object InMemoryCacheMapImpl {
   private final class Entry[ S <: KSys[ S ], @specialized( Int, Long ) K, @specialized A ]
   ( val key: K, val path: S#Acc, val value: A )
   extends CacheMapImpl.Entry[ S, K, InMemoryConfluentMap[ S, K ]] {
      override def toString = "Entry(" + key + ", " + value + ")"

      def flush( outTerm: Long, store: InMemoryConfluentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = path.addTerm( outTerm )
         logConfluent( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         store.put( key, pathOut, value )
      }
   }
}
trait InMemoryCacheMapImpl[ S <: KSys[ S ], @specialized( Int, Long ) K ]
extends CacheMapImpl[ S, K, InMemoryConfluentMap[ S, K ]] {
   import InMemoryCacheMapImpl._

   final protected def putCache[ A ]( key: K, path: S#Acc, value: A )( implicit tx: S#Tx ) {
      putCacheOnly( new Entry( key, path, value ))
   }

   final protected def getCache[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ A ] =
      getCacheOnly( key, path ).orElse( store.get[ A ]( key, path ))
}

// ---------------------------------------------------------------------

object PartialCacheMapImpl {
   import CacheMapImpl.Entry

   def newIntCache[ S <: KSys[ S ]]( map: DurablePersistentMap[ S, Int ]) : PartialCacheMapImpl[ S, Int ] =
      new PartialCacheMapImpl[ S, Int ] {
         final protected def emptyCache : Map[ Int, _ ] = CacheMapImpl.emptyIntMapVal
         final protected val store : DurablePersistentMap[ S, Int ] = map
      }

   private final class PartialEntry[ S <: KSys[ S ], @specialized( Int, Long ) K, A ]
   ( val key: K, val fullPath: S#Acc, val value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ])
   extends Entry[ S, K, DurablePersistentMap[ S, K ]] {
      override def toString = "PartialEntry(" + key + ", " + value + ")"

      val path: S#Acc = fullPath.partial

      def flush( outTerm: Long, store: DurablePersistentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = fullPath.addTerm( outTerm )
         logConfluent( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         val out     = new DataOutput()
         serializer.write( value, out )
         val arr     = out.toByteArray
         store.put( key, pathOut, arr )( tx, ByteArraySerializer )
      }
   }
}
trait PartialCacheMapImpl[ S <: KSys[ S ], @specialized( Int, Long ) K ]
extends CacheMapImpl[ S, K, DurablePersistentMap[ S, K ]] {
   import PartialCacheMapImpl._

   final def putPartial[ A ]( key: K, path: S#Acc, value: A )
                            ( implicit tx: S#Tx, serializer: Serializer[ S#Tx, S#Acc, A ]) {
      putCacheOnly( new PartialEntry( key, path, value ))
   }

   final def getPartial[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx,
                                                     serializer: Serializer[ S#Tx, S#Acc, A ]) : Option[ A ] =
      getCacheOnly( key, path.partial ).orElse {
         store.getWithSuffix[ Array[ Byte ]]( key, path )( tx, ByteArraySerializer ).map { tup =>
            val access  = tup._1
            val arr     = tup._2
            val in      = new DataInput( arr )
            serializer.read( in, access )
         }
      }

//   final def getFreshPartial[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx,
//                                                          serializer: Serializer[ S#Tx, S#Acc, A ]) : Option[ A ] =
//      getCacheOnly( key, path.partial ).orElse {
//         store.getWithSuffix[ Array[ Byte ]]( key, path )( tx, ByteArraySerializer ).map { tup =>
//            val access  = tup._1
//            val arr     = tup._2
//            val in      = new DataInput( arr )
//            serializer.read( in, access )
//         }
//      }
}