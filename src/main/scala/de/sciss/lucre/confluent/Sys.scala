/*
 *  Sys.scala
 *  (LucreConfluent)
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

package de.sciss.lucre
package confluent

import stm.{Txn => _Txn, ImmutableSerializer, Identifier}

object Sys {
//   private type S = Sys

   trait Entry[ S <: Sys[ S ], A ] extends stm.Var[ S#Tx, A ] {
      def meld( from: S#Acc )( implicit tx: S#Tx ) : A
   }

   trait Txn[ S <: Sys[ S ]] extends _Txn[ S ] {
//      def indexTree( version: Int ) : IndexTree[ S ]

      private[confluent] def getIndexTreeTerm( term: Long ) : Long

      private[confluent] def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                                              ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]

      private[confluent] def readPartialMap[ A ]( access: S#Acc, in: DataInput )
                                                ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]

      private[confluent] def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )
                                             ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]

      private[confluent] def newPartialMap[ A ]( access: S#Acc, rootTerm: Long, rootValue: A )
                                               ( implicit serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]

      def inputAccess: S#Acc

      def forceWrite() : Unit

      private[confluent] def readPath( in: DataInput ) : S#Acc
   }

//   trait Var[ S <: Sys[ S ], A ] extends _Var[ S#Tx, A ]

   trait ID[ Txn, Acc ] extends Identifier[ Txn ] {
      def id: Int
      def path: Acc
   }

//   final class Vertex( val version: Int, val hash: Int, val level: Int ) {
//      override def hashCode : Int = hash
//      override def equals
//   }

   trait Acc[ S <: Sys[ S ]] extends Writable with PathLike {
      def mkString( prefix: String, sep: String, suffix: String ) : String

//      // append element
//      private[confluent] def :+( suffix: Long ) : S#Acc

      // prepend element
      private[confluent] def +:( suffix: Long ) : S#Acc

      private[confluent] def index : S#Acc

      private[confluent] def term: Long

      private[confluent] def indexSum: Long

      private[confluent] def apply( idx: Int ): Long

      private[confluent] def maxPrefixLength( that: S#Acc ) : Int

      private[confluent] def maxPrefixLength( that: Long ) : Int

      private[confluent] def partial: S#Acc

//      // replace last element
//      private[confluent] def :-|( suffix: Long ) : S#Acc

      // split off last term, return index (init) and that last term
      private[confluent] def splitIndex: (S#Acc, Long)

      // split an index and term at a given point. that is
      // return the `idx` first elements of the path, and the one
      // following (the one found when applying `idx`).
      // although not enforced, `idx` should be an odd number,
      // greater than zero and less than `size`.
      private[confluent] def splitAtIndex( idx: Int ): (S#Acc, Long)

      private[confluent] def splitAtSum( hash: Long ): (S#Acc, Long)

      private[confluent] def indexOfSum( hash: Long ): Int

      private[confluent] def dropAndReplaceHead( dropLen: Int, newHead: Long ) : S#Acc

      private[confluent] def addTerm( term: Long )( implicit tx: S#Tx ) : S#Acc

      // drop initial elements
      private[confluent] def drop( num: Int ): S#Acc

      private[confluent] def _take( num: Int ): S#Acc
   }
}
trait Sys[ S <: Sys[ S ]] extends stm.Sys[ S ] {
//   type Var[ @specialized A ] <: Sys.Var[ S, A ]
   type Tx <: Sys.Txn[ S ]
   type ID <: Sys.ID[ S#Tx, S#Acc ]
   type Acc <: Sys.Acc[ S ]
   type Entry[ A ] <: Sys.Entry[ S, A ] // with S#Var[ A ]
}
