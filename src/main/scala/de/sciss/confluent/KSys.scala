/*
 *  KSys.scala
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

import de.sciss.lucre.stm.{TxnSerializer, Serializer, Writer, Identifier, Sys, Txn => _Txn}
import de.sciss.lucre.DataInput

object KSys {
//   private type S = KSys

   trait Txn[ S <: KSys[ S ]] extends _Txn[ S ] {
//      def indexTree( version: Int ) : IndexTree[ S ]

      def readIndexMap[ A ]( in: DataInput, index: S#Acc )( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ]
      def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )( implicit serializer: Serializer[ A ]) : IndexMap[ S, A ]

      def inputAccess: S#Acc
   }

//   trait Var[ S <: KSys[ S ], A ] extends _Var[ S#Tx, A ]

   trait ID[ Txn, Acc ] extends Identifier[ Txn ] {
      def id: Int
      def path: Acc
   }

//   final class Vertex( val version: Int, val hash: Int, val level: Int ) {
//      override def hashCode : Int = hash
//      override def equals
//   }

   trait Acc[ S <: KSys[ S ]] extends Writer with PathLike {
      def mkString( prefix: String, sep: String, suffix: String ) : String

//      // append element
//      private[confluent] def :+( suffix: Long ) : S#Acc

      // prepend element
      private[confluent] def +:( suffix: Long ) : S#Acc

      private[confluent] def index : S#Acc

      private[confluent] def term: Long

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

      private[confluent] def dropAndReplaceHead( dropLen: Int, newHead: Long ) : S#Acc

      // drop initial elements
      private[confluent] def drop( num: Int ): S#Acc
   }
}
trait KSys[ S <: KSys[ S ]] extends Sys[ S ] {
//   type Var[ @specialized A ] <: KSys.Var[ S, A ]
   type Tx <: KSys.Txn[ S ]
   type ID <: KSys.ID[ S#Tx, S#Acc ]
   type Acc <: KSys.Acc[ S ]
   type Entry[ A ] <: KEntry[ S, A ] // with S#Var[ A ]
}
