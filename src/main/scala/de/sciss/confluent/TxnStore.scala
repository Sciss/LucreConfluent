/*
 *  TxnStore.scala
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

trait TxnStoreLike[ C, K, @specialized V, Repr ] {
   def put( key: K, value: V )( implicit access: C ) : Unit
   def get( key: K )( implicit access: C ) : Option[ V ]
   def getOrElse( key: K, default: => V )( implicit access: C ) : V = get( key ).getOrElse( default )

   /**
    *    Finds the value which is the nearest
    *    ancestor in the trie. It returns a tuple
    *    composed of this value as an Option
    *    (None if no ancestor assignment found),
    *    along with an offset Int which is the
    *    offset into path for the first key
    *    element _not_ found in the trie.
    *
    *    Like findMaxPrefixOffset, but with support
    *    for multiplicities
    */
   def getWithPrefix( key: K )( implicit access: C ) : Option[ (V, Int) ]

   def inspect( implicit access: C ) : Unit

   def putAll( elems: Iterable[ (K, V) ])( implicit access: C ) : Unit

   def mapView( implicit access: C ): TxnStore.MapView[ K, V ] = TxnStore.MapView[ C, K, V ]( this )
}

//trait TxnCachedStore[ K, V ] extends TxnStoreLike[ K, V, TxnCachedStore[ K, V ]] {
//   def flush( pairs: (K, V)* )( implicit txn: InTxn ) : Unit
//}

object TxnStore {
   trait MapView[ K, V ] {
      def put( key: K, value: V ) : Unit
      def get( key: K ) : Option[ V ]
   }

   def MapView[ C, K, V ]( underlying: TxnStoreLike[ C, K, V, _ ])( implicit access: C ) : MapView[ K, V ] = new MapView[ K, V ] {
      def put( key: K, value: V ) : Unit = underlying.put( key, value )
      def get( key: K ) : Option[ V ] = underlying.get( key )
//      def getWithPrefix( key: K ) : Option[ (V, Int) ] = underlying.getWithPrefix( key )
//      def inspect : Unit = underlying.inspect
   }
}
trait TxnStore[ C, K, V ] extends TxnStoreLike[ C, K, V, TxnStore[ C, K, V ]] {
//   def flush( pairs: (K, V)* )( implicit txn: InTxn ) : Unit
}

//trait TxnCacheLike[ K, V ] {
//   def flush( trns: ((K, V)) => (K, V) )( implicit txn: InTxn ) : Unit
//}

trait TxnCacheLike[ C, K ] {
   def flush( trns: K => K )( implicit access: C ) : Unit
}

//
//trait TxnStoreCache[ K, V ] extends TxnStoreLike[ K, V, TxnStore[ K, V ]] with TxnCacheLike

//object TxnStore {
//   type Path[ K ] = FingerTree.IndexedSummed[ K, Long ]
//}

trait TxnValStoreFactory[ C, K, Up ] {
   def emptyVal[ V <: Up ]( implicit access: C ): TxnStore[ C, K, V ]
//   def emptyVal[ V ]: TxnStore[ K, V ]
//   def emptyRef[ V <: Ref ]: TxnStore[ K, V ]
}

trait TxnDelegateValStoreFactory[ C, K, Up ] {
   def emptyVal[ V <: Up ]( del: TxnStore[ C, K, V ])( implicit access: C ): TxnStore[ C, K, V ]
}

trait TxnDelegateValStoreFactory2[ C, K, Up, KD, Del[ _ ]] {
   def emptyVal[ V <: Up ]( del: TxnStore[ C, KD, Del[ V ]])( implicit access: C ): TxnStore[ C, K, V ]
}

trait TxnRefStoreFactory[ C, K, Up[ _ ]] {
   def emptyRef[ V <: Up[ V ]]( implicit access: C ): TxnStore[ C, K, V ]
//   def emptyVal[ V ]: TxnStore[ K, V ]
//   def emptyRef[ V <: Ref ]: TxnStore[ K, V ]
}

trait TxnDelegateRefStoreFactory[ C, K, Up[ _ ]] {
   def emptyRef[ V <: Up[ V ]]( del: TxnStore[ C, K, V ])( implicit access: C ): TxnStore[ C, K, V ]
}

trait TxnDelegateRefStoreFactory2[ C, K, Up[ _ ], KD, Del[ _ ]] {
   def emptyRef[ V <: Up[ V ]]( del: TxnStore[ C, KD, Del[ V ]])( implicit access: C ): TxnStore[ C, K, V ]
}

//trait TxnStoreCacheFactory[ K ] {
//   def empty[ V ]( store: => TxnStore[ K, V ]): TxnStoreCache[ K, V ]
////   def emptyVal[ V ]: TxnStore[ K, V ]
////   def emptyRef[ V ]: TxnStore[ K, V ]
//}
//

trait TxnCacheGroup[ C, H, K ] {
//   def addDirty( cache: TxnCacheLike[ K, V ])( implicit txn: InTxn ) : Unit
   def addDirty( cache: TxnCacheLike[ C, K ], hash: H )( implicit access: C ) : Unit
   def addAllDirty( cache: TxnCacheLike[ C, K ], hashes: Traversable[ H ])( implicit access: C ) : Unit
}

//trait TxnStoreCommitter[ K ] {
//   def commit( txn: InTxn, keyTrns: KeyTransformer[ K ]) : Unit
//}
//

//trait KeyValueTransformer[ K, Up ] {
//   def transform[ V <: Up ]( entry: (K, V) ) : (K, V)
//}

//class TestTrans[ Up <: Mutable[ Int, Up ]] extends KeyValueTransformer[ Int, Up ] {
//   def transform[ V <: Up ]( entry: (Int, V) ) : (Int, V) = {
//      val (k, v) = entry
//      val v2 = v.substitute( 33 )
//      (k, v2)
//   }
//}

//trait TxnDirtyRecorder[ K ] {
//   def addDirty( key: K, com: TxnStoreCommitter[ K ])
//}

trait TxnDBStoreFactory[ I, C, K ] {
   def emptyVal[ V ]( i: I )( implicit access: C, serializer: Serializer[ C, V ]): TxnStore[ C, K, V ]
}
