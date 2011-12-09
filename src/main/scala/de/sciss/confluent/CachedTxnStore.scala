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
import concurrent.stm.TxnLocal

object CachedTxnStore {
//   type Path[ V ] = FingerTree.IndexedSummed[ V, Long ]

   private trait CacheLike[ C <: Ct[ C ], X, V ] // ( store: TxnStore[ Path[ X ], V ], group: TxnCacheGroup[ Long, Path[ X ], V ])
   extends TxnStore[ C, PathLike[ X ], V ] /* with TxnCacheLike[ Path[ X ], V ] */ {
      type Pth = PathLike[ X ]

      protected def store: TxnStore[ C, PathLike[ X ], V ]
//      val group: TxnCacheGroup[ Long, Path[ X ]]

      protected val ref = TxnLocal( LongMap.empty[ (Pth, V) ])

      def inspect( implicit access: C ) {
         println( "INSPECT CACHE" )
         println( ref.get( access.txn ))
         store.inspect
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit access: C ) : Option[ V ] =
         ref.get( access.txn ).get( key.sum ).map( _._2 ).orElse( store.get( key ))

      def getWithPrefix( key: Pth )( implicit access: C ) : Option[ (V, Int) ] =
         ref.get( access.txn ).get( key.sum ).map( tup => (tup._2, key.size) ).orElse( store.getWithPrefix( key ))

      protected def addDirty( hash: Long )( implicit access: C ) : Unit
      protected def addAllDirty( hashes: Traversable[ Long ])( implicit access: C ) : Unit

      def put( key: Pth, value: V )( implicit access: C ) {
         ref.transform( map => {
//            if( map.isEmpty ) rec.addDirty( this )
            val hash = key.sum
            addDirty( hash ) // group.addDirty( this, hash )
            map + (hash -> (key, value))
         })( access.txn )
      }

      def putAll( elems: Iterable[ (Pth, V) ])( implicit access: C ) {
         if( elems.isEmpty ) return
         ref.transform( map => {
//            if( map.isEmpty ) rec.addDirty( this )
//            val (keys, values) = elems.unzip
            val hashed = elems.map( tup => tup._1.sum )
//
//            val hashed = keys.map( _.sum ) // elems.view.map( tup => (tup._1.sum, tup._2) )
            addAllDirty( hashed ) // group.addAllDirty( this, hashed )
            map ++ hashed.zip(elems)
         })( access.txn )
      }

//      def flush( trns: ((Pth, V)) => (Pth, V) )( implicit txn: InTxn ) {
//         store.putAll( ref.get.values.map( trns ))
//      }
   }

   private class ValCache[ C <: Ct[ C ], X, V ]( protected val store: TxnStore[ C, PathLike[ X ], V ],
                                                 group: TxnCacheGroup[ C, Long, PathLike[ X ]])
   extends CacheLike[ C, X, V ] with TxnCacheLike[ C, PathLike[ X ]] {
      protected def addDirty( hash: Long )( implicit access: C ) {
         group.addDirty( this, hash )
      }

      protected def addAllDirty( hashes: Traversable[ Long ])( implicit access: C ) {
         group.addAllDirty( this, hashes )
      }

      def flush( trns: Pth => Pth )( implicit access: C ) {
         store.putAll( ref.get( access.txn ).map( tup => {
            val tup2 = tup._2
            (trns( tup2._1 ), tup2._2)
         }))
      }
   }

   private class RefCache[ C <: Ct[ C ], X, A, V <: MutableOld[ A, V ]]( protected val store: TxnStore[ C, PathLike[ X ], V ],
                                                                      group: TxnCacheGroup[ C, Long, (PathLike[ X ], A) ])
   extends CacheLike[ C, X, V ] with TxnCacheLike[ C, (PathLike[ X ], A) ] {
      protected def addDirty( hash: Long )( implicit access: C ) {
         group.addDirty( this, hash )
      }

      protected def addAllDirty( hashes: Traversable[ Long ])( implicit access: C ) {
         group.addAllDirty( this, hashes )
      }

      def flush( trns: ((Pth, A)) => (Pth, A) )( implicit access: C ) {
         store.putAll( ref.get( access.txn ).map( tup => {
            val tup2 = tup._2
            val v    = tup2._2
            val tupm = trns( (tup2._1, v.path) )
            (tupm._1, v.substitute( tupm._2 ))
         }))
      }
   }

//   def valFactory[ X ]( storeFactory: TxnValStoreFactory[ Path[ X ], Any ], group: TxnCacheGroup[ Long, Path[ X ]]) : TxnValStoreFactory[ Path[ X ], Any ] =
//      new ValFactoryImpl[ X ]( storeFactory, group )
//
//   def refFactory[ X, A ]( storeFactory: TxnValStoreFactory[ Path[ X ], Any ], group: TxnCacheGroup[ Long, (Path[ X ], A) ]) : TxnRefStoreFactory[ Path[ X ], ({type λ[α] = Mutable[A,α]})#λ ] =
//      new RefFactoryImpl[ X, A ]( storeFactory, group )
//
//   private class ValFactoryImpl[ X ]( storeFactory: TxnValStoreFactory[ Path[ X ], Any ], group: TxnCacheGroup[ Long, Path[ X ]])
//   extends TxnValStoreFactory[ Path[ X ], Any ] {
//      def emptyVal[ V ]( implicit txn: InTxn ) : TxnStore[ Path[ X ], V ] = new ValCache[ X, V ]( storeFactory.emptyVal[ V ], group )
//   }
//
//   private class RefFactoryImpl[ X, A ]( storeFactory: TxnValStoreFactory[ Path[ X ], Any ], group: TxnCacheGroup[ Long, (Path[ X ], A) ])
//   extends TxnRefStoreFactory[ Path[ X ], ({type λ[α] = Mutable[A,α]})#λ ] {
//      def emptyRef[ V <: Mutable[ A, V ]]( implicit txn: InTxn ) : TxnStore[ Path[ X ], V ] = new RefCache[ X, A, V ]( storeFactory.emptyVal[ V ], group )
//   }

   def valFactory[ C <: Ct[ C ], X ]( group: TxnCacheGroup[ C, Long, PathLike[ X ]]) : TxnDelegateValStoreFactory[ C, PathLike[ X ], Any ] =
      new ValFactoryImpl[ C, X ]( group )

   def refFactory[ C <: Ct[ C ], X, A ]( group: TxnCacheGroup[ C, Long, (PathLike[ X ], A) ]) : TxnDelegateRefStoreFactory[ C, PathLike[ X ],
      ({type λ[α] = MutableOld[A,α]})#λ ] = new RefFactoryImpl[ C, X, A ]( group )

   private class ValFactoryImpl[ C <: Ct[ C ], X ]( group: TxnCacheGroup[ C, Long, PathLike[ X ]])
   extends TxnDelegateValStoreFactory[ C, PathLike[ X ], Any ] {
      def emptyVal[ V ]( del: TxnStore[ C, PathLike[ X ], V ])( implicit access: C ) : TxnStore[ C, PathLike[ X ], V ] =
         new ValCache[ C, X, V ]( del, group )
   }

   private class RefFactoryImpl[ C <: Ct[ C ], X, A ]( group: TxnCacheGroup[ C, Long, (PathLike[ X ], A) ])
   extends TxnDelegateRefStoreFactory[ C, PathLike[ X ], ({type λ[α] = MutableOld[A,α]})#λ ] {
      def emptyRef[ V <: MutableOld[ A, V ]]( del: TxnStore[ C, PathLike[ X ], V ])( implicit access: C ) : TxnStore[ C, PathLike[ X ], V ] =
         new RefCache[ C, X, A, V ]( del, group )
   }
}