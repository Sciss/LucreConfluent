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

package de.sciss.confluent
package impl

import de.sciss.lucre.stm.{TxnSerializer, Serializer}
import collection.immutable.LongMap
import concurrent.stm.{TxnLocal, Txn => ScalaTxn}
import TemporalObjects.logConfig
import de.sciss.lucre.{DataOutput}

object CacheMapImpl {
   /**
    * Instances of `Entry` are stored for each variable write in a transaction. They
    * are flushed at the commit to the persistent store. There are two sub types, a
    * transactional and a non-transactional one. A non-transactional cache entry can de-serialize
    * the value without transactional context, e.g. this is true for all primitive types.
    * A transactional entry is backed by a `TxnSerializer`. To be saved in the store which uses
    * a sub system (`Durable`), serialization is a two-step process, using an intermediate
    * binary representation.
    */
   private sealed trait Entry[ S <: KSys[ S ], @specialized( Int, Long ) K ] {
      def key: K
      def path: S#Acc
      def flush( outTerm: Long, store: PersistentMap[ S, K ])( implicit tx: S#Tx ) : Unit
      def value: Any
   }
   private final class NonTxnEntry[ S <: KSys[ S ], @specialized( Int, Long ) K, @specialized A ]
   ( val key: K, val path: S#Acc, val value: A )( implicit serializer: Serializer[ A ])
   extends Entry[ S, K ] {
      override def toString = "NonTxnEntry(" + key + ", " + value + ")"

      def flush( outTerm: Long, store: PersistentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = path.addTerm( outTerm )
         logConfig( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         store.put( key, pathOut, value )
      }
   }
   private final class TxnEntry[ S <: KSys[ S ], @specialized( Int, Long ) K, A ]
   ( val key: K, val path: S#Acc, val value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ])
   extends Entry[ S, K ] {
      override def toString = "NonTxnEntry(" + key + ", " + value + ")"

      def flush( outTerm: Long, store: PersistentMap[ S, K ])( implicit tx: S#Tx ) {
         val pathOut = path.addTerm( outTerm )
         logConfig( "txn flush write " + value + " for " + pathOut.mkString( "<" + key + " @ ", ",", ">" ))
         val out     = new DataOutput()
         serializer.write( value, out )
         val arr     = out.toByteArray
         store.put( key, pathOut, arr )( tx, ByteArraySerializer )
      }
   }

   private val emptyLongMapVal      = LongMap.empty[ Any ]
   private def emptyLongMap[ T ]    = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]
}

trait CacheMapImpl[ S <: KSys[ S ], @specialized( Int, Long ) K ] {
   import CacheMapImpl._

   private val markDirty   = TxnLocal( false )
   private val cache       = TxnLocal( emptyCache.asInstanceOf[ Map[ K, LongMap[ Entry[ S, K ]]]])

   // ---- abstract ----

   /**
    * This is called when the transaction is about to be committed and the cache is actually dirty.
    * Implementations will typically generate a term value and then invoke `flushCache( term )`
    * which takes care of performing all writes
    *
    * @param tx   the transaction which is about to be completed
    */
   protected def flushCache()( implicit tx: S#Tx ) : Unit

   /**
    * The persistent map to which the data is flushed or from which it is retrieved when not residing in cache.
    */
   protected def persistent : PersistentMap[ S, K ]

   /**
    * Implementations may provide a particular map implementation for the cache (e.g. `IntMap` or `LongMap`).
    * The value type of the returned map (which must be immutable and empty) is cast to the internal cache
    * entries.
    */
   protected def emptyCache : Map[ K, _ ] // LongMap[ Entry[ S, K ]]]

   // ---- implementation ----

   final protected def putCacheTxn[ A ]( key: K, path: S#Acc, value: A )
                                       ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
      putCache( new TxnEntry( key, path, value ))
   }

   final protected def putCacheNonTxn[ A ]( key: K, path: S#Acc, value: A )
                                          ( implicit tx: S#Tx, ser: Serializer[ A ]) {
      putCache( new NonTxnEntry( key, path, value ))
   }

   final protected def removeCache( key: K )( implicit tx: S#Tx ) {
      cache.transform( _ - key )( tx.peer )
   }
   
   private def putCache( e: Entry[ S, K ])( implicit tx: S#Tx ) {
      implicit val itx = tx.peer
      cache.transform( mapMap => {
         val mapOld  = mapMap.getOrElse( e.key, emptyLongMap[ Entry[ S, K ]])
         val mapNew  = mapOld + (e.path.sum -> e)
         mapMap + ((e.key, mapNew))
      })

      if( !markDirty.swap( true )) ScalaTxn.beforeCommit( _ => flushCache() )
   }

   final protected def flushCache( term: Long )( implicit tx: S#Tx ) {
      val p = persistent
      cache.get( tx.peer ).foreach { tup1 =>
         val map  = tup1._2
         map.foreach { tup2 =>
            val e    = tup2._2
            e.flush( term, p )
         }
      }
   }
}