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

import stm.{Txn => _Txn, DataStore, Disposable, ImmutableSerializer, Identifier}
import data.Ancestor
import de.sciss.fingertree.FingerTree

object Sys {
   trait Entry[ S <: Sys[ S ], A ] extends stm.Var[ S#Tx, A ] {
      def meld( from: S#Acc )( implicit tx: S#Tx ) : A
   }

   trait Var[ S <: Sys[ S ], @specialized A ] extends stm.Var[ S#Tx, A ] {
      private[confluent] def setInit( value: A )( implicit tx: S#Tx ) : Unit
   }

   private[confluent] trait IndexTree[ D <: stm.DurableLike[ D ]] extends Writable with Disposable[ D#Tx ] {
      def tree: Ancestor.Tree[ D, Long ]
      def level: Int
      def term: Long
   }

   trait IndexMapHandler[ S <: Sys[ S ]] {
      def readIndexMap[ A ]( in: DataInput, index: S#Acc )
                           ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]
      def newIndexMap[ A ]( index: S#Acc, rootTerm: Long, rootValue: A )
                          ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]
   }

   trait PartialMapHandler[ S <: Sys[ S ]] {
      def getIndexTreeTerm( term: Long )( implicit tx: S#Tx ) : Long

      def readPartialMap[ A ]( access: S#Acc, in: DataInput )
                           ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]
      def newPartialMap[ A ]( access: S#Acc, rootTerm: Long, rootValue: A )
                          ( implicit tx: S#Tx, serializer: ImmutableSerializer[ A ]) : IndexMap[ S, A ]
   }

//   trait IndexTreeHandler[ D <: stm.DurableLike[ D ], -Index ] {
//      def writeTreeVertex( tree: IndexTree[ D ], v: Ancestor.Vertex[ D, Long ])( implicit tx: D#Tx ) : Unit
////      def readTreeVertexLevel( term: Long ) : Int
//      def readIndexTree( term: Long )( implicit tx: D#Tx ) : IndexTree[ D ]
//      def newIndexTree( term: Long, level: Int )( implicit tx: D#Tx ) : IndexTree[ D ]
//      def readTreeVertex( tree: Ancestor.Tree[ D, Long ], index: Index, term: Long )
//                        ( implicit tx: D#Tx ) : (Ancestor.Vertex[ D, Long ], Int)
//   }

   trait Txn[ S <: Sys[ S ]] extends _Txn[ S ] {
      def inputAccess: S#Acc

      def forceWrite() : Unit

      private[confluent] def readPath( in: DataInput ) : S#Acc

      // formerly Confluent.Txn

//      private[confluent] implicit def durable: D#Tx

//      private[confluent] def readTreeVertex( tree: Ancestor.Tree[ D, Long ], index: S#Acc,
//                                             term: Long ) : (Ancestor.Vertex[ D, Long ], Int)
//      private[confluent] def readPartialTreeVertex( index: S#Acc, term: Long ) : Ancestor.Vertex[ D, Long ]

//      private[confluent] def writeTreeVertex( tree: IndexTree[ D ], v: Ancestor.Vertex[ D, Long ]) : Unit
      private[confluent] def readTreeVertexLevel( term: Long ) : Int
//      private[confluent] def readIndexTree( term: Long ) : IndexTree[ D ]
//      private[confluent] def newIndexTree( term: Long, level: Int ) : IndexTree[ D ]

      private[confluent] def addInputVersion( path: S#Acc ) : Unit

      private[confluent] def putTxn[ A ]( id: S#ID, value: A )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Unit
      private[confluent] def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) : Unit
      private[confluent] def getTxn[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : A
      private[confluent] def getNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A
      private[confluent] def isFresh( id: S#ID ) : Boolean

      private[confluent] def putPartial[ A ]( id: S#ID, value: A )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Unit
      private[confluent] def getPartial[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : A

      private[confluent] def removeFromCache( id: S#ID ) : Unit
//      private[confluent] def addDirtyMap( map: CacheMapImpl[ S, _, _ ]) : Unit

      private[confluent] def makeVar[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Var[ S, A ] // BasicVar[ A ]

//      private[confluent] def inMemory : InMemory#Tx

   }

   trait ID[ S <: Sys[ S ]] extends Identifier[ S#Tx ] {
      def id: Int
      def path: S#Acc
   }

   trait Acc[ S <: Sys[ S ]] extends Writable with PathLike {
      def mkString( prefix: String, sep: String, suffix: String ) : String

//      // append element
//      private[confluent] def :+( suffix: Long ) : S#Acc

      // prepend element
      private[confluent] def +:( suffix: Long ) : S#Acc

      private[confluent] def :+( last: Long ) : S#Acc

      private[confluent] def index : S#Acc

      private[confluent] def tail : S#Acc

      private[confluent] def term: Long

      private[confluent] def indexSum: Long

      private[confluent] def apply( idx: Int ): Long

      private[confluent] def maxPrefixLength( that: S#Acc ) : Int

      private[confluent] def maxPrefixLength( that: Long ) : Int

      private[confluent] def seminal : S#Acc

      private[confluent] def partial: S#Acc

      private[confluent] def tree: FingerTree[ (Int, Long), Long ]

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

      private[confluent] def head : Long
      private[confluent] def last : Long
      private[confluent] def isEmpty : Boolean
      private[confluent] def nonEmpty : Boolean
   }
}

/**
 * This is analogous to a `ConfluentLike` trait. Since there is only one system in
 * `LucreConfluent`, it was decided to just name it `confluent.Sys`.
 *
 * @tparam S   the implementing system
 */
trait Sys[ S <: Sys[ S ]] extends stm.Sys[ S ] {
   type D <: stm.DurableLike[ D ]
   type I <: stm.InMemoryLike[ I ]

//   type Var[ @specialized A ] <: Sys.Var[ S, A ]
   type Tx                         <: Sys.Txn[ S ]
   final type ID                    = Sys.ID[ S ]
   final type Acc                   = Sys.Acc[ S ] // <: Sys.Acc[ S ]
   final type Var[ @specialized A ] = Sys.Var[ S, A ] // Confluent.Var[ A ]
   final type Entry[ A ]            = Sys.Entry[ S, A ] // with S#Var[ A ]

   def durable : D
   def inMemory : I

   private[lucre] def durableTx(  tx: S#Tx ) : D#Tx
   private[lucre] def inMemoryTx( tx: S#Tx ) : I#Tx

   private[confluent] def varMap : DurablePersistentMap[ S, Int ]
   private[confluent] def partialMap : impl.PartialCacheMapImpl[ S, Int ]
   private[confluent] def partialTree : Ancestor.Tree[ D, Long ]
   private[confluent] def newIDValue()( implicit tx: S#Tx ) : Int
   private[confluent] def newVersionID( implicit tx: S#Tx ) : Long
   private[confluent] def position_=( newPos: S#Acc )( implicit tx: S#Tx ) : Unit
   private[confluent] def store : DataStore

   private[confluent] def indexMap : Sys.IndexMapHandler[ S ]
//   private[confluent] def partialIndexMap : Sys.IndexMapHandler[ S ]

   // ---- formerly Txn ----
   private[confluent] def writePartialTreeVertex( v: Ancestor.Vertex[ D, Long ])( implicit tx: S#Tx )
//   private[confluent] def readTreeVertex( tree: Ancestor.Tree[ D, Long ], index: S#Acc,
//                                          term: Long )( implicit tx: S#Tx ) : (Ancestor.Vertex[ D, Long ], Int)
}