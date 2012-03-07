///*
// *  ConfluentMemoryMapFullPath.scala
// *  (TemporalObjects)
// *
// *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
// *
// *	 This software is free software; you can redistribute it and/or
// *	 modify it under the terms of the GNU General Public License
// *	 as published by the Free Software Foundation; either
// *	 version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	 This software is distributed in the hope that it will be useful,
// *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	 General Public License for more details.
// *
// *  You should have received a copy of the GNU General Public
// *  License (gpl.txt) along with this software; if not, write to the Free Software
// *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
// *
// *
// *	 For further information, please contact Hanns Holger Rutz at
// *	 contact@sciss.de
// */
//
//package de.sciss.confluent
//package impl
//
//import collection.immutable.{LongMap, IntMap}
//import concurrent.stm._
//
//object ConfluentMemoryMapFullPath {
//   private type MapType[ A ] = IntMap[ LongMap[ Value[ A ]]]
//   private def EmptyMap[ A ] : MapType[ A ] = IntMap.empty
//
////   def apply[ A ]() : ConfluentTxMap[ A ] = new Impl[ A ]
//   def local[ A ]() : ConfluentTxMap[ InTxn, A ] = new Impl[ InTxnEnd, A ]( TxnLocal( EmptyMap[ A ]))
//   def ref[ A ]()   : ConfluentTxMap[ InTxn, A ] = new Impl[ InTxn, A ]( Ref( EmptyMap[ A ]))
//
//   private val emptyLongMapVal   = LongMap.empty[ Any ]
//   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]
//
//   private final class Impl[ -Txn <: InTxnEnd, A ]( idMapRef: RefLike[ MapType[ A ], Txn ]) extends ConfluentTxMap[ Txn, A ] {
////      private val idMapRef = TxnLocal[ MapType[ A ]]( IntMap.empty )
//
//      def put( id: Int, path: PathLike, value: A )( implicit tx: Txn ) {
//         idMapRef.transform { idMap =>
//            val mapOld  = idMap.getOrElse( id, emptyLongMap[ Value[ A ]])
//            var mapNew  = mapOld
//            Hashing.foreachPrefix( path, mapOld.contains ) {
//               case (key, preSum) =>
//                  mapNew += ((key, /* if( preSum == 0L ) ValueNone else */ ValuePre( preSum )))
//            }
//            mapNew += ((path.sum, ValueFull( value )))
//            idMap + ((id, mapNew))
//         }
//      }
//
//      def get( id: Int, path: PathLike )( implicit tx: Txn ) : A = {
//         val idMap   = idMapRef.get
//         val map     = idMap( id )
//         map( Hashing.maxPrefixKey( path, map.contains )) match {
//            case ValueFull( v )      => v
//            case ValuePre( hash )    => map( hash ) match {
//               case ValueFull( v )   => v
//               case _                => sys.error( "Orphaned partial prefix for id " + id + " and path " + path )
//            }
////            case ValueNone           => throw new NoSuchElementException( "path not found: " + path )
//         }
//      }
//   }
//
//   private sealed trait Value[ +A ]
////   private case object ValueNone extends Value[ Nothing ]
//   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
//   private case class ValueFull[ A ]( v: A ) extends Value[ A ]
//}
