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

object HashedTxnStore {
   private class StoreImpl[ C <: Ct[ C ], X, V ] extends TxnStore[ C, PathLike[ X ], V ] {
      type Pth = PathLike[ X ]

      val ref = STMRef[ Map[ Long, Value[ V ]]]( LongMap.empty[ Value[ V ]])

      def inspect( implicit access: C ) = {
         println( "INSPECT STORE" )
         println( ref.get( access.txn ))
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit access: C ) : Option[ V ] = {
         val map = ref.get( access.txn )
         Hashing.maxPrefixValue( key, map ).flatMap {
            case ValueFull( v )        => Some( v )
            case ValuePre( /* len, */ hash ) => Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v )
            case ValueNone             => None // : Option[ V ]
         }
      }

      def getWithPrefix( key: Pth )( implicit access: C ) : Option[ (V, Int) ] = {
         val map = ref.get( access.txn )
         Hashing.getWithPrefix( key, map ).flatMap {
            case (ValueFull( v ), sz)        => Some( v -> sz )
            case (ValuePre( /* len, */ hash ), sz) => {
//               assert( sz == len )
               Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v -> sz /* len */)
            }
            case (ValueNone, _)              => None // : Option[ V ]
         }
      }

      /**
       * Note: This store is supposed to be used with a cache in front, thus
       * we only support bulk writing via `putAll`. This method throws
       * a runtime exception.
       */
      def put( key: Pth, value: V )( implicit access: C ) {
         ref.transform( map => {
            val hash    = key.sum
//          if( map.isEmpty ) rec.addDirty( hash, this )
            Hashing.add( key, map, { s: Pth =>
               if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
            })
         })( access.txn )
      }

      def putAll( elems: Iterable[ (Pth, V) ])( implicit access: C ) {
// since we use the cache now, let's just skip this check
//         if( elems.isEmpty ) return
         ref.transform( map => {
            elems.foldLeft( map ) { case (map, (key, value)) =>
               val hash    = key.sum
               Hashing.add( key, map, { s: Pth =>
                  if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
               })
            }
         })( access.txn )
      }
   }

   private sealed trait Value[ +V ]
   private case object ValueNone extends Value[ Nothing ]
   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
   private case class ValueFull[ V ]( v:  V ) extends Value[ V ]

   def valFactory[ C <: Ct[ C ], X, Up ] : TxnValStoreFactory[ C, PathLike[ X ], Up ] = new ValFactoryImpl[ C, X, Up ]
   def refFactory[ C <: Ct[ C ], X, Up[ _ ]] : TxnRefStoreFactory[ C, PathLike[ X ], Up ] = new RefFactoryImpl[ C, X, Up ]

   private class ValFactoryImpl[ C <: Ct[ C ], X, Up ] extends TxnValStoreFactory[ C, PathLike[ X ], Up ] {
      def emptyVal[ V <: Up ]( implicit access: C ): TxnStore[ C, PathLike[ X ], V ] = new StoreImpl[ C, X, V ]
   }

   private class RefFactoryImpl[ C <: Ct[ C ], X, Up[ _ ]] extends TxnRefStoreFactory[ C, PathLike[ X ], Up ] {
      def emptyRef[ V <: Up[ _ ]]( implicit access: C ): TxnStore[ C, PathLike[ X ], V ] = new StoreImpl[ C, X, V ]
   }
}
