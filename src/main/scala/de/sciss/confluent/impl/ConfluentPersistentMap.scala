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

import de.sciss.lucre.stm.{Serializer, TxnSerializer, PersistentStore}
import annotation.switch

object ConfluentPersistentMap {
   def apply[ S <: KSys[ S ], A ]( store: PersistentStore ) : ConfluentTxnMap[ S#Tx, S#Acc ] =
      new Impl[ S ]( store )

//   private val emptyLongMapVal   = LongMap.empty[ Any ]
//   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ S <: KSys[ S ]]( store: PersistentStore )
   extends ConfluentTxnMap[ S#Tx, S#Acc ] {
      def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
         val (index, term) = path.splitIndex
         // first we need to see if anything has already been written to the index of the write path
         store.flatGet { out =>
            out.writeInt( id )
            out.writeLong( index.sum )
         } { in =>
            (in.readUnsignedByte(): @switch) match {
               case 1 =>
                  // a single 'root' value is found. extract it for successive re-write.
                  val term2   = in.readLong()
                  val prev    = ser.read( in )
                  Some( EntrySingle( term2, prev ))
               case 2 =>
                  // there is already a map found
                  val m = tx.readIndexMap[ A ]( in, index )
                  Some( EntryMap( m ))
               case _ => None  // this would be a partial hash which we don't use
            }
         } match { // with the previous entry read, react as follows:
            // if there is a single entry, construct a new ancestor.map with the
            // entry's value taken as root value
            case Some( EntrySingle( prevTerm, prevValue )) =>
               putFullMap[ A ]( id, index, term, value, prevTerm, prevValue )
            // if there is an existing map, simply add the new value to it
            case Some( EntryMap( m )) =>
               m.add( term, value )
            // if there is no previous entry...
            case _ =>
               // we may write a single entry if and only if the value can be seen
               // as a root value (the write path corresponds to the construction
               // of the entity, e.g. path == <term, term>; or the entity was
               // re-written in the tree root, hence path.suffix == <term, term>)
               val indexTerm = index.term
               if( term == indexTerm ) {
                  putPartials( id, index )
                  putFullSingle[ A ]( id, index, term, value )
               // otherwise, we must read the root value for the entity, and then
               // construct a new map containing that root value along with the
               // new value
               } else {
                  val prevValue = get[ A ]( id, path ).getOrElse(
                     sys.error( path.mkString( "Expected previous value not found for <" + id + " @ ", ",", ">" ))
                  )
                  putPartials( id, index )
                  putFullMap[ A ]( id, index, term, value, indexTerm, prevValue )
               }
         }
      }

      private def putFullMap[ A ]( id: Int, index: S#Acc, term: Long, value: A, prevTerm: Long, prevValue: A )
                                 ( implicit tx: S#Tx, ser: Serializer[ A ]) {
//         require( prevTerm != term, "Duplicate flush within same transaction? " + term.toInt )
//         require( prevTerm == index.term, "Expected initial assignment term " + index.term.toInt + ", but found " + prevTerm.toInt )
         // create new map with previous value
sys.error( "TODO: prevTerm" )
         val m = tx.newIndexMap[ A ]( index, prevValue )
         // store the full value at the full hash (path.sum)
         store.put { out =>
            out.writeInt( id )
            out.writeLong( index.sum )
         } { out =>
            out.writeUnsignedByte( 2 )    // aka map entry
            m.write( out )
         }
         // then add the new value
         m.add( term, value )
      }

      // stores the prefixes
      private def putPartials( id: Int, index: S#Acc )( implicit tx: S#Tx ) {
         Hashing.foreachPrefix( index, key => store.contains { out =>
            out.writeInt( id )
            out.writeLong( key )
         }) {
            // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
            case (key, preSum) => store.put { out =>
               out.writeInt(  id )
               out.writeLong( key )
            } { out =>
               out.writeUnsignedByte( 0 ) // aka entry pre
               out.writeLong( preSum )
            }
         }
      }

      // store the full value at the full hash (path.sum)
      private def putFullSingle[ A ]( id: Int, index: S#Acc, term: Long, value: A )
                                    ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
         store.put { out =>
            out.writeInt( id )
            out.writeLong( index.sum )
         } { out =>
            out.writeUnsignedByte( 1 )    // aka entry single
            out.writeLong( term )
            ser.write( value, out )
         }
      }

      def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ A ] = {
         val (maxIndex, maxTerm) = path.splitIndex
         getWithPrefixLen[ A, A ]( id, maxIndex, maxTerm )( (_, _, value) => value )
      }

      def getWithSuffix[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ (S#Acc, A) ] = {
         val (maxIndex, maxTerm) = path.splitIndex
         getWithPrefixLen[ A, (S#Acc, A) ]( id, maxIndex, maxTerm )( (preLen, term, value) =>
            (path.dropAndReplaceHead( preLen, term ), value)
         )
      }

      private def getWithPrefixLen[ A, B ]( id: Int, maxIndex: S#Acc, maxTerm: Long )
                                          ( fun: (Int, Long, A) => B )
                                          ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ B ] = {
         val preLen = Hashing.maxPrefixLength( maxIndex, key => store.contains { out =>
            out.writeInt( id )
            out.writeLong( key )
         })
         val (index, term) = if( preLen == maxIndex.size ) {   // maximum prefix lies in last tree
            (maxIndex, maxTerm)
         } else {                                              // prefix lies in other tree
            maxIndex.splitAtIndex( preLen )
         }
         val preSum  = index.sum
         store.flatGet { out =>
            out.writeInt( id )
            out.writeLong( preSum )
         } { in =>
            (in.readUnsignedByte(): @switch) match {
               case 0 =>   // partial hash
                  val hash = in.readLong()
//                  EntryPre[ S ]( hash )
                  val (fullIndex, fullTerm) = maxIndex.splitAtSum( hash )
                  getWithPrefixLen( id, fullIndex, fullTerm )( fun )

               case 1 =>
                  // --- THOUGHT: This assertion is wrong. We need to replace store.get by store.flatGet.
                  // if the terms match, we have Some result. If not, we need to ask the index tree if
                  // term2 is ancestor of term. If so, we have Some result, if not we have None.
//                  assert( term == term2, "Accessed version " + term.toInt + " but found " + term2.toInt )

                  // --- ADDENDUM: I believe we do not need to store `term2` at all, it simply doesn't
                  // matter. Given a correct variable system, there is no notion of uninitialised values.
                  // Therefore, we cannot end up in this case without the previous stored value being
                  // correctly the nearest ancestor of the search term. For example, say the index tree
                  // is v0, and the variable was created in v2. Then there is no way that we try to
                  // read that variable with v0. The value stored here is always the initialisation.
                  // If there was a second assignment for the same index tree, we'd have found an
                  // entry map, and we can safely _coerce_ the previous value to be the map's
                  // _root_ value.

                  val term2 = in.readLong()
                  val value = ser.read( in )
//                  EntrySingle[ S, A ]( term2, value )
                  Some( fun( preLen, term2, value ))

               case 2 =>
                  val m = tx.readIndexMap[ A ]( in, index )
//                  EntryMap[ S, A ]( m )
                  val (term2, value) = m.nearest( term )
                  Some( fun( preLen, term2, value ))
            }
         }
      }
   }

   private sealed trait Entry[ S <: KSys[ S ], +A ]
   private final case class EntryPre[    S <: KSys[ S ]](     hash: Long )          extends Entry[ S, Nothing ]
   private final case class EntrySingle[ S <: KSys[ S ], A ]( term: Long, v: A )    extends Entry[ S, A ]
   private final case class EntryMap[    S <: KSys[ S ], A ]( m: IndexMap[ S, A ])  extends Entry[ S, A ]
}
