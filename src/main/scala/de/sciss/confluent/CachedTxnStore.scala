/*
 *  CachedStore.scala
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

object CachedTxnStore {
   type Path[ V ] = FingerTree.IndexedSummed[ V, Long ]

   private class CacheImpl[ X, V ]( store: TxnStore[ Path[ X ], V ], rec: TxnCacheGroup[ Long, Path[ X ], V ])
   extends TxnStore[ Path[ X ], V ] with TxnCacheLike[ Path[ X ], V ] {
      type Pth = Path[ X ]

      val ref = TxnLocal( LongMap.empty[ (Pth, V) ])

      def inspect( implicit txn: InTxn ) = {
         println( "INSPECT CACHE" )
         println( ref.get )
         println( "INSPECT STORE" )
         store.inspect
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit txn: InTxn ) : Option[ V ] = ref.get.get( key.sum ).map( _._2 ).orElse( store.get( key ))

      def getWithPrefix( key: Pth )( implicit txn: InTxn ) : Option[ (V, Int) ] =
         ref.get.get( key.sum ).map( tup => (tup._2, key.size) ).orElse( store.getWithPrefix( key ))

      def put( key: Pth, value: V )( implicit txn: InTxn ) {
         ref.transform { map =>
//            if( map.isEmpty ) rec.addDirty( this )
            val hash = key.sum
            rec.addDirty( this, hash )
            map + (hash -> (key, value))
         }
      }

      def putAll( elems: Iterable[ (Pth, V) ])( implicit txn: InTxn ) {
         if( elems.isEmpty ) return
         ref.transform { map =>
//            if( map.isEmpty ) rec.addDirty( this )
//            val (keys, values) = elems.unzip
            val hashed = elems.map( tup => tup._1.sum )
//
//            val hashed = keys.map( _.sum ) // elems.view.map( tup => (tup._1.sum, tup._2) )
            rec.addAllDirty( this, hashed )
            map ++ hashed.zip(elems)
         }
      }

      def flush( trns: ((Pth, V)) => (Pth, V) )( implicit txn: InTxn ) {
         store.putAll( ref.get.values.map( trns ))
      }
   }

   def factory[ X, Up ]( storeFactory: TxnStoreFactory[ Path[ X ], Up ]) : TxnStoreFactory[ Path[ X ], Up ] =
      new FactoryImpl[ X, Up ]( storeFactory )

   private class FactoryImpl[ X, Up ]( storeFactory: TxnStoreFactory[ Path[ X ], Up ])
   extends TxnStoreFactory[ Path[ X ], Up ] {
      def empty[ V <: Up ] : TxnStore[ Path[ X ], V ] = error( "TODO" ) // new CacheImpl[ X, V ]( storeFactory.empty[ V ])
   }
}