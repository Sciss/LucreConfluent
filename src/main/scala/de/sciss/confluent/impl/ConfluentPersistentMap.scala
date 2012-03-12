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
import de.sciss.collection.txn.Ancestor
import annotation.switch
import de.sciss.lucre.stm.{TxnSerializer, PersistentStore, TxnReader, TxnWriter, Sys}

object ConfluentPersistentMap {

   def apply[ S <: KSys[ S ], A ]( store: PersistentStore[ S#Tx ]) : ConfluentTxnMap[ S#Tx, S#Acc ] =
      new Impl[ S ]( store )

//   private val emptyLongMapVal   = LongMap.empty[ Any ]
//   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ S <: KSys[ S ]]( store: PersistentStore[ S#Tx ])
   extends ConfluentTxnMap[ S#Tx, S#Acc ] {

      def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         val (index, term) = path.splitIndex
         val indexSum      = index.sum
         // first, check if the index exists
//         if( store.contains { out =>
//            out.writeInt( id )
//            out.writeLong( indexSum )
//         })
         store.flatGet { out =>
            out.writeInt( id )
            out.writeLong( indexSum )
         } { in =>
            (in.readUnsignedByte(): @switch) match {
               case 1 =>
                  val term = in.readInt()
//                  val access : S#Acc = path.init :+ term
                  val prev = ser.read( in, path )
                  Some( EntrySingle( term, prev ))
               case 2 =>
//                  val full = tx.indexTree( index.term )
//                  val anc : Ancestor.Map[ S, Int, A ] = Ancestor.readMap[ S, Int, A ]( in, path, full )
                  val m = tx.readIndexMap( index )
                  Some( EntryMark( m ))
               case _ => None
            }
         } match {
            case Some( EntrySingle( prevTerm, prev )) =>
               putSecond[ A ]( id, index, term, value, prevTerm, prev )
            case Some( EntryMark( m )) =>
               putMap[ A ]( id, index, term, value, m )
            case _ =>
               putFirst[ A ]( id, index, term, value )
         }
      }

      private def putMap[ A ]( id: Int, index: S#Acc, term: Int, value: A, m: IndexMap[ S, A ])
                             ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         sys.error( "TODO" )
      }

      private def putSecond[ A ]( id: Int, index: S#Acc, term: Int, value: A, prevTerm: Int, prevValue: A )
                                ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         sys.error( "TODO" )
      }

      private def putFirst[ A ]( id: Int, index: S#Acc, term: Int, value: A )
                               ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         // store the prefixes
         Hashing.foreachPrefix( index, key => store.contains { out =>
            out.writeInt( id )
            out.writeLong( key )
         }) {
            // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
            case (key, preSum) => store.put { out =>
               out.writeInt(  id )
               out.writeLong( key )
            } { out =>
               out.writeUnsignedByte( 0 ) // aka EntryPre
               out.writeLong( preSum )
            }
         }
         // then store the full value at the full hash (path.sum)
         store.put { out =>
            out.writeInt( id )
            out.writeLong( index.sum )
         } { out =>
            out.writeUnsignedByte( 1 )    // aka EntrySingle
            out.writeInt( term )
            ser.write( value, out )
         }
      }

      def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, reader: TxnReader[ S#Tx, S#Acc, A ]) : Option[ A ] = {
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

   private sealed trait Entry[ S <: Sys[ S ], +A ]
   private final case class EntryPre[    S <: Sys[ S ]]( hash: Long ) extends Entry[ S, Nothing ]
   private final case class EntrySingle[ S <: Sys[ S ], A ]( term: Int, v: A ) extends Entry[ S, A ]
//   private final case class EntryMark[   S <: Sys[ S ], A ]( t: Ancestor.Map[ S, Int, A ]) extends Entry[ S, A ]
   private final case class EntryMark[   S <: Sys[ S ], A ]( m: IndexMap[ S, A ]) extends Entry[ S, A ]
}