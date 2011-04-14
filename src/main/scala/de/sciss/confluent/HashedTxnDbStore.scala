/*
 *  HashedTxnDBStore.scala
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
import java.lang.ref.{SoftReference => JSoftReference}

object HashedTxnDBStore {
   type Path[ V ] = FingerTree.IndexedSummed[ V, Long ]

//   private case class Compound[ V ]( perm: Map[ Long, Value[ V ]], temp: Map[ Long, Value[ V ]])

   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class StoreImpl[ X, V ]( dbStore: TxnStore[ Long, Value[ V ]]) extends TxnStore[ Path[ X ], V ] {
      type Pth = Path[ X ]

      val ref     = STMRef[ Map[ Long, SoftValue[ V ]]]( LongMap.empty[ SoftValue[ V ]])
//      val cache   = TxnLocal( Map.empty[ Long, V ])

      def inspect( implicit txn: InTxn ) = {
         println( "INSPECT STORE" )
         println( ref.get )
//         println( "  perm = " + ref.get )
//         println( "  temp = " + cache.get )
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit txn: InTxn ) : Option[ V ] = {
         val map = ref.get
         Hashing.getWithHash( key, map ).flatMap {
            case (vf: SoftValueFull[ _ ], hash) =>
               Some( dbGet( map, hash, vf.asInstanceOf[ SoftValueFull[ V ]]))
            case (ValuePre( fullHash ), hash ) =>
               Some( dbGet( map, hash, map( fullHash ).asInstanceOf[ SoftValueFull[ V ]]))
            case (ValueNone, _) => None
         }
      }

      def getWithPrefix( key: Pth )( implicit txn: InTxn ) : Option[ (V, Int) ] = {
         val map = ref.get
         Hashing.getWithPrefixAndHash( key, map ).flatMap {
            case (vf: SoftValueFull[ _ ], sz, hash) =>
               Some( dbGet( map, hash, vf.asInstanceOf[ SoftValueFull[ V ]]), sz )
            case (ValuePre( fullHash ), sz, hash ) =>
               Some( dbGet( map, hash, map( fullHash ).asInstanceOf[ SoftValueFull[ V ]]), sz )
            case (ValueNone, _, _ ) => None
         }
      }

      private def dbGet( map: Map[ Long, SoftValue[ V ]], hash: Long, vf: SoftValueFull[ V ])( implicit txn: InTxn ) : V = {
         val v0 = vf.get()
         if( v0 == null ) {
            val vfh  = dbStore.get( hash ).getOrElse( error( "Missing entry in store (" + hash + ")" )).asInstanceOf[ ValueFull[ V ]]
            ref.set( map + (hash -> vfh.soften))  // refresh SoftReference
            vfh.v
         } else v0
      }

      def put( key: Pth, value: V )( implicit txn: InTxn ) {
         ref.transform { map =>
            val fullHash = key.sum
            val list = Hashing.collect( key, map, { s: Pth =>
               if( s.isEmpty ) ValueNone else if( s.sum == fullHash ) ValueFull( value ) else new ValuePre( s.sum )
            })
            dbStore.putAll( list )
            val soft = list.map( tup => (tup._1, tup._2.soften) )
            map ++ soft
         }
      }

      def putAll( elems: Iterable[ (Pth, V) ])( implicit txn: InTxn ) {
// since we use the cache now, let's just skip this check
//         if( elems.isEmpty ) return
         ref.transform { map =>
//            elems.foldLeft( map ) { case (map, (key, value)) =>
//               val hash    = key.sum
//               Hashing.add( key, map, { s: Pth =>
//                  if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
//               })
//            }
            val list = elems.flatMap { tup =>
               val key        = tup._1
               val value      = tup._2
               val fullHash   = key.sum
               Hashing.collect( key, map, { s: Pth =>
                  if( s.isEmpty ) ValueNone else if( s.sum == fullHash ) ValueFull( value ) else new ValuePre( s.sum )
               })
            }
            dbStore.putAll( list )
            val soft = list.map( tup => (tup._1, tup._2.soften) )
            map ++ soft
         }
      }
   }

   private[HashedTxnDBStore] sealed trait SoftValue[ +V ]

   sealed trait Value[ +V ] {
      private[HashedTxnDBStore] def soften: SoftValue[ V ]
   }
   case object ValueNone extends Value[ Nothing ] with SoftValue[ Nothing ] {
      private[HashedTxnDBStore] def soften: SoftValue[ Nothing ] = this
   }
   case class ValuePre( hash: Long ) extends Value[ Nothing ] with SoftValue[ Nothing ] {
      private[HashedTxnDBStore] def soften: SoftValue[ Nothing ] = this
   }
   case class ValueFull[ V ]( v: V ) extends Value[ V ] {
      private[HashedTxnDBStore] def soften: SoftValue[ V ] = SoftValueFull( v )
   }

   private object SoftValueFull {
      def apply[ V ]( v: V ) = new SoftValueFull( v )
      def unapply[ V ]( vf: SoftValueFull[ V ]) : Option[ V ] = {
         val v = vf.get()
         if( v == null ) None else Some( v )
      }
   }
   private class SoftValueFull[ V ]( v:  V ) extends JSoftReference[ V ]( v ) with SoftValue[ V ]

//   def valFactory[ X, Up ]( dbStoreFactory: TxnValStoreFactory[ Long, AnyRef ]) : TxnValStoreFactory[ Path[ X ], Up ] =
//      new ValFactoryImpl[ X, Up ]( dbStoreFactory )
//
//   def refFactory[ X, Up[ _ ]]( dbStoreFactory: TxnValStoreFactory[ Long, AnyRef ]) : TxnRefStoreFactory[ Path[ X ], Up ] =
//      new RefFactoryImpl[ X, Up ]( dbStoreFactory )
//
//   private class ValFactoryImpl[ X, Up ]( dbStoreFactory: TxnValStoreFactory[ Long, AnyRef ])
//   extends TxnValStoreFactory[ Path[ X ], Up ] {
//      def emptyVal[ V <: Up ]( implicit txn: InTxn ): TxnStore[ Path[ X ], V ] = new StoreImpl[ X, V ]( dbStoreFactory.emptyVal[ AnyRef ])
//   }
//
//   private class RefFactoryImpl[ X, Up[ _ ]]( dbStoreFactory: TxnValStoreFactory[ Long, AnyRef ])
//   extends TxnRefStoreFactory[ Path[ X ], Up ] {
//      def emptyRef[ V <: Up[ _ ]]( implicit txn: InTxn ): TxnStore[ Path[ X ], V ] = new StoreImpl[ X, V ]( dbStoreFactory.emptyVal[ AnyRef ])
//   }

   def valFactory[ X, Up ] : TxnDelegateValStoreFactory2[ Path[ X ], Up, Long, Value ] =
      new ValFactoryImpl[ X, Up ]

   def refFactory[ X, Up[ _ ]] : TxnDelegateRefStoreFactory2[ Path[ X ], Up, Long, Value ] =
      new RefFactoryImpl[ X, Up ]

   private class ValFactoryImpl[ X, Up ]
   extends TxnDelegateValStoreFactory2[ Path[ X ], Up, Long, Value ] {
      def emptyVal[ V <: Up ]( del: TxnStore[ Long, Value[ V ]])( implicit txn: InTxn ): TxnStore[ Path[ X ], V ] =
         new StoreImpl[ X, V ]( del )
   }

   private class RefFactoryImpl[ X, Up[ _ ]]
   extends TxnDelegateRefStoreFactory2[ Path[ X ], Up, Long, Value ] {
      def emptyRef[ V <: Up[ _ ]]( del: TxnStore[ Long, Value[ V ]])( implicit txn: InTxn ): TxnStore[ Path[ X ], V ] =
         new StoreImpl[ X, V ]( del )
   }
}