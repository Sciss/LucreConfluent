/*
 *  InMemoryConfluentMapImpl.scala
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

import concurrent.stm.TMap
import collection.immutable.LongMap
import annotation.switch

object InMemoryConfluentMapImpl {
   sealed trait Entry[ +A ]
   private final case class EntryPre( hash: Long ) extends Entry[ Nothing ]
   private final case class EntryFull[ A ]( term: Long, v: A ) extends Entry[ A ]
}
sealed trait InMemoryConfluentMapImpl[ S <: KSys[ S ], @specialized( Int, Long) K ] extends InMemoryConfluentMap[ S, K ] {
   import InMemoryConfluentMapImpl._

   final protected type Entries = Map[ Long, Entry[ _ ]]
   protected def store: TMap[ K, Entries ]

   override def toString = "InMemoryConfluentMap(" + store + ")"

   final def put[ @specialized A ]( key: K, path: S#Acc, value: A )( implicit tx: S#Tx ) {
      implicit val itx = tx.peer
      val (index, term) = path.splitIndex

      var entries  = store.get( key ).getOrElse( LongMap.empty )
      Hashing.foreachPrefix( index, entries.contains ) {
         // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
         case (hash, preSum) => entries += (hash -> EntryPre( preSum ))
      }
      // then store full value
      entries += (index.sum -> EntryFull( term, value ))
      store.put( key, entries )
   }

   final def get[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ A ] = {
      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[ A, A ]( key, maxIndex, maxTerm )( (_, _, value) => value )
   }

   final def getWithSuffix[A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ (S#Acc, A) ] = {
      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[ A, (S#Acc, A) ]( key, maxIndex, maxTerm )( (preLen, writeTerm, value) =>
      //            (path.dropAndReplaceHead( preLen, writeTerm ), value)
         (writeTerm +: path.drop( preLen ), value)
      )
   }

   private def getWithPrefixLen[ A, B ]( key: K, maxIndex: S#Acc, maxTerm: Long )
                                       ( fun: (Int, Long, A) => B )( implicit tx: S#Tx ) : Option[ B ] = {
      sys.error( "TODO" )
//      val preLen = Hashing.maxPrefixLength( maxIndex, hash => store.contains { out =>
//         writeKey( key, out ) // out.writeInt( key )
//         out.writeLong( hash )
//      })
//      val (index, term) = if( preLen == maxIndex.size ) {
//         // maximum prefix lies in last tree
//         (maxIndex, maxTerm)
//      } else {
//         // prefix lies in other tree
//         maxIndex.splitAtIndex( preLen )
//      }
//      val preSum = index.sum
//      store.flatGet { out =>
//         writeKey( key, out ) // out.writeInt( key )
//         out.writeLong( preSum )
//      } { in =>
//         (in.readUnsignedByte(): @switch) match {
//            case 0 => // partial hash
//               val hash = in.readLong()
//               //                  EntryPre[ S ]( hash )
//               val (fullIndex, fullTerm) = maxIndex.splitAtSum( hash )
//               getWithPrefixLen( key, fullIndex, fullTerm )( fun )
//
//            case 1 =>
//               val term2 = in.readLong()
//               val value = ser.read( in )
//               //                  EntrySingle[ S, A ]( term2, value )
//               Some( fun( preLen, term2, value ))
//
//            case 2 =>
//               val m = tx.readIndexMap[ A ]( in, index )
//               //                  EntryMap[ S, A ]( m )
//               val (term2, value) = m.nearest( term )
//               Some( fun( preLen, term2, value ))
//         }
//      }
   }
}
