/*
 *  HashedStore.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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
 *	 You should have received a copy of the GNU General Public
 *	 License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

import collection.immutable.LongMap
import concurrent.stm.{TxnLocal, InTxn, Ref => STMRef}
import de.sciss.fingertree.FingerTree

object HashedTxnStore {
   type Path[ V ] = FingerTree.IndexedSummed[ V, Long ]

//   private case class Compound[ V ]( perm: Map[ Long, Value[ V ]], temp: Map[ Long, Value[ V ]])

   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class StoreImpl[ X, V ]( cacheMgr: Cache[ Path[ X ]])
   extends TxnStore[ Path[ X ], V ] {
      type Pth = Path[ X ]

      val ref     = STMRef.make[ Map[ Long, Value[ V ]]]
      val cache   = TxnLocal( Map.empty[ Long, V ])

      def inspect( implicit txn: InTxn ) = {
         println( "INSPECT" )
         println( "  perm = " + ref.get )
         println( "  temp = " + cache.get )
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit txn: InTxn ) : Option[ V ] = cache.get.get( key.sum ).orElse {
         val map = ref.get
         Hashing.maxPrefixValue( key, map ).flatMap {
            case ValueFull( v )        => Some( v )
            case ValuePre( /* len, */ hash ) => Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v )
            case ValueNone             => None // : Option[ V ]
         }
      }

      def getWithPrefix( key: Pth )( implicit txn: InTxn ) : Option[ (V, Int) ] =
         cache.get.get( key.sum ).map( v => (v, key.size) ).orElse {
            val map = ref.get
            Hashing.getWithPrefix( key, map ).flatMap {
               case (ValueFull( v ), sz)        => Some( v -> sz )
               case (ValuePre( /* len, */ hash ), sz) => {
   //               assert( sz == len )
                  Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v -> sz /* len */)
               }
               case (ValueNone, _)              => None // : Option[ V ]
            }
         }

      def put( key: Pth, value: V )( implicit txn: InTxn ) {
         cache.transform { map =>
            cacheMgr.add( key, this )
            map + (key.sum, value)
         }
      }

//      def flush( pairs: Traversable[ (Pth, V) ])( implicit txn: InTxn ) : Unit = {
//         ref.transform { map =>
//            pairs.foldLeft( map ) { case (map, (key, value)) =>
//               val hash    = key.sum
////               if( map.isEmpty ) rec.addDirty( hash, this )
//               Hashing.add( key, map, { s: Pth =>
//                  if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
//               })
//            }
//         }
//      }

//      def commit( txn: InTxn, suffix: Int ) {
//         cRef.transform( c => {
//            val map = c.temp
//            Compound( c.perm ++ map.map( tup => (tup._1 + suffix, tup._2) ), map.empty )
//         })( txn )
//      }
   }

   private sealed trait Value[ +V ]
   private case object ValueNone extends Value[ Nothing ]
   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
   private case class ValueFull[ V ]( v:  V ) extends Value[ V ]

   def factory[ X ]( cache: Cache[ Path[ X ]] = HashedTxnStore.cache[ X ]) : TxnStoreFactory[ Path[ X ]] =
      new FactoryImpl[ X ]( cache )

   def cache[ X ] : Cache[ Path[ X ]] = new CacheMgrImpl[ X ]

//   def cacheGroup : TxnCacheGroup = new CacheGroupImpl

//   trait Committer {
//      def commit( txn: InTxn, suffix: Int ) : Unit
//   }
//
//   trait Recorder {
//      def addDirty( hash: Long, com: Committer )( implicit txn: InTxn )
//   }

   trait Cache[ X ] {
//      private val cacheSet = TxnLocal( Set.empty[ TxnCacheLike ]) // , beforeCommit = persistAll( _ )
//      private val hashSet  = TxnLocal( Set.empty[ Long ])
//
//      def add( cache: TxnCacheLike )( implicit txn: InTxn ) : Unit = cacheSet.transform( _ + cache )
//      def add( cache: TxnCacheLike )( implicit txn: InTxn ) : Unit

      private def flush( implicit txn: InTxn ) {
//         val suffix = Hashing.nextUnique( hashSet.get )
//         storeSet.get.foreach( _.commit( txn, suffix ))
         error( "TODO" )
      }
   }

   private class CacheMgrImpl[ X ] extends Cache[ X ] {
      def flush( implicit txn: InTxn ) : Unit = error( "TODO" )
//      def empty[ V ]( store: => TxnStore[ Path[ X ], V ]) : TxnStoreCache[ Path[ X ], V ] = {
////         new CacheImpl[ X, V ]( TxnLocal( LongMap.empty[ Value[ V ]]), store, group )
//         error( "TODO" )
//      }
   }

   private class FactoryImpl[ X ]( cache: Cache[ X ]) extends TxnStoreFactory[ Path[ X ]] {
      def empty[ V ] : TxnStore[ Path[ X ], V ] = {
         new StoreImpl[ X, V ]( STMRef( LongMap.empty[ Value[ V ]]), cache )
      }
   }
}
