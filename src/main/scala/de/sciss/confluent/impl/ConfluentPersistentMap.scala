/*
 *  ConfluentPersistentMap.scala
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

import collection.immutable.{LongMap, IntMap}
import concurrent.stm._
import de.sciss.collection.txn.Ancestor
import de.sciss.lucre.stm.{TxnReader, TxnWriter, Sys}

object ConfluentPersistentMap {
   private type MapType[ S <: Sys[ S ], ~ ] = IntMap[ LongMap[ Entry[ Marked[ S, ~ ]]]]
   private def EmptyMap[ S <: Sys[ S ], ~ ] : MapType[ S, ~ ] = IntMap.empty
   private type Marked[ S <: Sys[ S ], ~ ] = Ancestor.Map[ S, Int, ~ ]

//   def apply[ A ]() : ConfluentTxMap[ A ] = new Impl[ A ]
   def apply[ S <: Sys[ S ], A ]() : ConfluentTxMap[ S#Tx, S#Acc ] = new Impl[ S ]
//   def ref[ S <: Sys[ S ], A ]()   : ConfluentTxMap[ S#Tx, S#Acc, A ] = new Impl[ S, A ]( Ref( EmptyMap[ S, A ]))

   private val emptyLongMapVal   = LongMap.empty[ Any ]
   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ S <: Sys[ S ]] extends ConfluentTxMap[ S#Tx, S#Acc ] {
//      private val idMapRef = TxnLocal[ MapType[ A ]]( IntMap.empty )

      def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, writer: TxnWriter[ A ]) {
//         idMapRef.transform { idMap =>
//            val mapOld  = idMap.getOrElse( id, emptyLongMap[ Entry[ A ]])
//            var mapNew  = mapOld
//            Hashing.foreachPrefix( path, mapOld.contains ) {
//               case (key, preSum) =>
//                  mapNew += ((key, /* if( preSum == 0L ) ValueNone else */ EntryPre( preSum )))
//            }
//            mapNew += ((path.sum, EntryFull( value )))
//            idMap + ((id, mapNew))
//         }
         sys.error( "TODO" )
      }

      def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
//         val idMap   = idMapRef.get( tx.peer )
//         val map     = idMap( id )
sys.error( "TODO" )
//         map( Hashing.maxPrefixKey( path, map.contains )) match {
//            case EntryFull( v )      => v
//            case EntryPre( hash )    => map( hash ) match {
//               case EntryFull( v )   => v
//               case _                => sys.error( "Orphaned partial prefix for id " + id + " and path " + path )
//            }
////            case ValueNone           => throw new NoSuchElementException( "path not found: " + path )
//         }
      }
   }

   private sealed trait Entry[ +A ]
//   private case object ValueNone extends Value[ Nothing ]
   private case class EntryPre( /* len: Int, */ hash: Long ) extends Entry[ Nothing ]
   private case class EntryFull[ A ]( v: A ) extends Entry[ A ]
}
