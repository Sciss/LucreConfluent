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
import java.lang.ref.{SoftReference => JSoftReference}

object HashedTxnDBStore {
   import TxnStore._

//   private case class Compound[ V ]( perm: Map[ Long, Value[ V ]], temp: Map[ Long, Value[ V ]])

   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class StoreImpl[ C <: Ct[ C ], X, V ]( dbStore: TxnStore[ C, Long, Value[ V ]])
   extends TxnStore[ C, PathLike[ X ], V ] {
      type Pth = PathLike[ X ]

      val ref     = STMRef[ Map[ Long, SoftValue[ V ]]]( LongMap.empty[ SoftValue[ V ]])
//      val cache   = TxnLocal( Map.empty[ Long, V ])

      def inspect( implicit access: C ) = {
         println( "INSPECT STORE" )
         println( ref.get( access.txn ))
//         println( "  perm = " + ref.get )
//         println( "  temp = " + cache.get )
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth )( implicit access: C ) : Option[ V ] = {
         implicit val txn  = access.txn
         val map           = ref.get
         val dbView        = dbStore.mapView
         val tup           = dbGetAndResolve( key, map, dbView )
         if( tup._1 ) { // ref needs update
            ref.set( tup._2 )
         }
         tup._4
      }

      def getWithPrefix( key: Pth )( implicit access: C ) : Option[ (V, Int) ] = {
         implicit val txn  = access.txn
         val map           = ref.get
         val dbView        = dbStore.mapView
         val tup           = dbGetAndResolve( key, map, dbView )
         if( tup._1 ) { // ref needs update
            ref.set( tup._2 )
         }
         tup._4.map( v => (v, tup._3) )
      }

      private def dbHarden( vf: SoftValueFull[ V ], key: Long, map: Map[ Long, SoftValue[ V ]],
                            dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], V) = {
         val v0 = vf.get()
         if( v0 == null ) {
            val vfh  = dbView.get( key ).getOrElse( error( "Missing entry in store (" + key + ")" )).asInstanceOf[ ValueFull[ V ]]
            val map1 = map + (key -> vfh.soften)
            (true, map1, vfh.v)
         } else {
            (false, map, v0)
         }
      }

      /**
       * Resolves a key through a double lookup: It first examines the in-memory map, and if the key is
       * not found it performs search on the underlying database. In this latter case, entries found in
       * the database (whether they are `ValueNone`, `ValuePre` or `ValueFull`) will be regenerated in
       * the in-memory map (a `ValueFull` is translated into a `SoftValueFull` of course).
       *
       * @return  the search result, consisting of three parts: The first tuple entry specifies whether
       *          the map was modified during the search (due to in-memory refresh). If so, the second
       *          tuple entry corresponds to the refreshed map which the caller should thus write back
       *          to the `Ref`.
       *          Finally, the last tuple entry specifies the value lookup result: This is either
       *          of `SearchNoKey` (the key was not found in the map),
       *          `SearchNoValue` (the key was found but it was pointing to `ValueNone`) or
       *          `SearchValue` (the key was found and either it pointed directly or indirectly through
       *          `ValuePre` to the stored value)
       */
      private def dbGetRefresh( key: Long, map: Map[ Long, SoftValue[ V ]],
                                dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], Search[ V ]) = {
         map.get( key ) match {
            case Some( vp @ ValuePre( fullKey ) )  => dbGetRefresh( fullKey, map, dbView ) // retry with full key
            case Some( vn @ ValueNone )            => (false, map, SearchNoValue)
            case Some( vf: SoftValueFull[ _ ])     =>
                  val tup = dbHarden( vf.asInstanceOf[ SoftValueFull[ V ]], key, map, dbView )  // refresh soft reference and unwrap full value
                  (tup._1, tup._2, SearchValue( tup._3 ))       // lift full value to option
            case None => dbView.get( key ) match {
               case Some( vp @ ValuePre( fullKey )) =>
                  val map1 = map + (key -> vp)  // first update the input map
                  val tup  = dbGetRefresh( fullKey, map1, dbView )   // the retry with full key
                  (true, tup._2, tup._3)        // true here is shorthand for (true | tup._1), since we know the map has been modified
               case Some( vn @ ValueNone )      => (true, map + (key -> vn), SearchNoValue)
               case Some( vfh @ ValueFull( v )) => (true, map + (key -> vfh.soften), SearchValue( v ))
               case None                        => (false, map, SearchNoKey)
            }
         }
      }

      private def dbContainsRefresh( key: Long, map: Map[ Long, SoftValue[ V ]],
                                     dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], Boolean) = {
         if( map.contains( key )) {
            (false, map, true)
         } else {
            dbView.get( key ) match {
               case Some( vh )   => (true, map + (key -> vh.soften), true)
               case None         => (false, map, false)
            }
         }
      }

//      private def dbGetAndResolve( key: Pth, map: Map[ Long, SoftValue[ V ]],
//                                   dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], Option[ V ]) = {
//         val tup1    = dbMaxPrefix( key, map, dbView )
//         val mod1    = tup1._1
//         val map1    = tup1._2
//         val pre1    = tup1._3
//         val pre1Sz  = pre1.size
//         val pre1Sum = pre1.sum
//         if( pre1Sz == 0 ) {
//            (mod1, map1, None)
//         } else {
//            val tup2 = dbGetRefresh( pre1Sum, map1, dbView )
//            val mod2 = mod1 | tup2._1
//            val map2 = tup2._2
//            tup2._3 match {
//               case SearchNoKey => if( pre1Sz == 1 ) {
//                  (mod2, map2, None)
//               } else {
//                  val pre2Sum = pre1.init.sum
//                  val tup3    = dbGetRefresh( pre2Sum, map2, dbView )
//                  val mod3    = mod2 | tup3._1
//                  val map3    = tup3._2
//                  (mod3, map3, tup3._3.valueOption)
//               }
//               case s => (mod2, map2, s.valueOption)
//            }
//         }
//      }

      private def dbGetAndResolve( key: Pth, map: Map[ Long, SoftValue[ V ]],
                                   dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], Int, Option[ V ]) = {
         val tup1    = dbMaxPrefix( key, map, dbView )
         val mod1    = tup1._1
         val map1    = tup1._2
         val pre1    = tup1._3
         val pre1Sz  = pre1.size
         val pre1Sum = pre1.sum
         if( pre1Sz == 0 ) {
            (mod1, map1, pre1Sz, None)
         } else {
            val tup2 = dbGetRefresh( pre1Sum, map1, dbView )
            val mod2 = mod1 | tup2._1
            val map2 = tup2._2
            tup2._3 match {
               case SearchNoKey => if( pre1Sz == 1 ) {
                  (mod2, map2, pre1Sz, None)
               } else {
                  val pre2Sz  = pre1Sz - 1
                  val pre2Sum = pre1.init.sum
                  val tup3    = dbGetRefresh( pre2Sum, map2, dbView )
                  val mod3    = mod2 | tup3._1
                  val map3    = tup3._2
                  (mod3, map3, pre2Sz, tup3._3.valueOption)
               }
               case s => (mod2, map2, pre1Sz, s.valueOption)
            }
         }
      }

      private def dbMaxPrefix( key: Pth, map: Map[ Long, SoftValue[ V ]],
                               dbView: MapView[ Long, Value[ V ]]) : (Boolean, Map[ Long, SoftValue[ V ]], Pth) = {
         var mod     = false
         var mmap    = map

         val sz      = key.size
         val m       = Hashing.bitCount( sz )
         // "We search for the minimum j, 1 <= j <= m(r), such that sum(p_i_j(r)) is not stored in the hash table H"
         var allFound= true
         var ij      = 0
         var ijm     = 0

         {
            var succ    = 0
            var i = 1; while( i <= m ) {
               val pre  = succ
               succ     = Hashing.prefix( sz, i, m )
               val tup  = dbContainsRefresh( key.take( succ ).sum, mmap, dbView )
               if( tup._1 ) {
                  mod   = true
                  mmap  = tup._2
               }
               if( !tup._3 && allFound ) {
                  allFound = false
                  ij       = succ
                  ijm      = pre
               }
            i += 1 }
         }

//         val is      = Array.tabulate( m )( i => i -> Hashing.prefix( sz, i + 1, m ))
//         val noPres  = is.filter( tup => !map.contains( key.take( tup._2 ).sum ))
         // "If there is no such j then sum(r) itself is stored in the hash table H so r' = r"
//         if( noPres.isEmpty ) return (mod, mmap, key)
         if( allFound ) return (mod, mmap, key)

//         val (j, ij) = noPres.min      // j - 1 actually
//         val ijm     = if( j == 0 ) 0 else is( j - 1 )._2

         val twopk   = ij - ijm
         var d       = twopk >> 1
         var twoprho = d
         while( twoprho >= 2 ) {
            twoprho >>= 1
            val pre  = key.take( ijm + d )
            val tup  = dbContainsRefresh( pre.sum, mmap, dbView )
            if( tup._1 ) {
               mod   = true
               mmap  = tup._2
            }
            d        = if( tup._3 ) d + twoprho else d - twoprho
         }

         (mod, mmap, key.take( ijm + d ))
      }

//      private def dbGet( map: Map[ Long, SoftValue[ V ]], hash: Long, vf: SoftValueFull[ V ])( implicit access: C ) : V = {
//         val v0 = vf.get()
//         if( v0 == null ) {
//            val vfh  = dbStore.get( hash ).getOrElse( error( "Missing entry in store (" + hash + ")" )).asInstanceOf[ ValueFull[ V ]]
//            ref.set( map + (hash -> vfh.soften))( access.txn )  // refresh SoftReference
//            vfh.v
//         } else v0
//      }

      def put( key: Pth, value: V )( implicit access: C ) {
         ref.transform( map => {
            val fullHash = key.sum
            val list = Hashing.collect( key, map, { s: Pth =>
               if( s.isEmpty ) ValueNone else if( s.sum == fullHash ) ValueFull( value ) else new ValuePre( s.sum )
            })
            dbStore.putAll( list )
            val soft = list.map( tup => (tup._1, tup._2.soften) )
            map ++ soft
         })( access.txn )
      }

      def putAll( elems: Iterable[ (Pth, V) ])( implicit access: C ) {
// since we use the cache now, let's just skip this check
//         if( elems.isEmpty ) return
         ref.transform( map => {
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
         })( access.txn )
      }
   }

   private sealed trait Search[ +V ] {
      def valueOption: Option[ V ]
   }
   private case object SearchNoKey extends Search[ Nothing ] {
      def valueOption = None
   }
   private case object SearchNoValue extends Search[ Nothing ] {
      def valueOption = None
   }
   private final case class SearchValue[ V ]( v: V ) extends Search[ V ] {
      def valueOption = Some( v )
   }

   private[HashedTxnDBStore] sealed trait SoftValue[ +V ]

   sealed trait Value[ +V ] {
      private[HashedTxnDBStore] def soften: SoftValue[ V ]
   }
   case object ValueNone extends Value[ Nothing ] with SoftValue[ Nothing ] {
      private[HashedTxnDBStore] def soften: SoftValue[ Nothing ] = this
   }
   final case class ValuePre( hash: Long ) extends Value[ Nothing ] with SoftValue[ Nothing ] {
      private[HashedTxnDBStore] def soften: SoftValue[ Nothing ] = this
   }
   final case class ValueFull[ V ]( v: V ) extends Value[ V ] {
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

   def valFactory[ C <: Ct[ C ], X, Up ] : TxnDelegateValStoreFactory2[ C, PathLike[ X ], Up, Long, Value ] =
      new ValFactoryImpl[ C, X, Up ]

   def refFactory[ C <: Ct[ C ], X, Up[ _ ]] : TxnDelegateRefStoreFactory2[ C, PathLike[ X ], Up, Long, Value ] =
      new RefFactoryImpl[ C, X, Up ]

   private class ValFactoryImpl[ C <: Ct[ C ], X, Up ]
   extends TxnDelegateValStoreFactory2[ C, PathLike[ X ], Up, Long, Value ] {
      def emptyVal[ V <: Up ]( del: TxnStore[ C, Long, Value[ V ]])( implicit access: C ): TxnStore[ C, PathLike[ X ], V ] =
         new StoreImpl[ C, X, V ]( del )
   }

   private class RefFactoryImpl[ C <: Ct[ C ], X, Up[ _ ]]
   extends TxnDelegateRefStoreFactory2[ C, PathLike[ X ], Up, Long, Value ] {
      def emptyRef[ V <: Up[ _ ]]( del: TxnStore[ C, Long, Value[ V ]])( implicit access: C ): TxnStore[ C, PathLike[ X ], V ] =
         new StoreImpl[ C, X, V ]( del )
   }
}