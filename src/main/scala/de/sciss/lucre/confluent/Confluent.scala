/*
 *  Confluent.scala
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

import impl.{PartialCacheMapImpl, InMemoryCacheMapImpl, DurableCacheMapImpl, CacheMapImpl}
import util.MurmurHash
import de.sciss.fingertree.{Measure, FingerTree, FingerTreeLike}
import data.Ancestor
import concurrent.stm.{TxnLocal, TxnExecutor, InTxn, Txn => ScalaTxn}
import stm.impl.{LocalVarImpl, BerkeleyDB}
import java.io.File
import stm.{Var => STMVar, InMemory, LocalVar, ImmutableSerializer, IdentifierMap, Cursor, Disposable, Durable, DataStoreFactory, DataStore, Serializer}
import collection.immutable.{IndexedSeq => IIdxSeq, IntMap, LongMap}

object Confluent {
   def ??? : Nothing = sys.error( "TODO" )

   def apply( storeFactory: DataStoreFactory[ DataStore ]) : Confluent = ??? // new System( storeFactory )

   def tmp() : Confluent = {
???
//      val dir = File.createTempFile( "confluent_", "db" )
//      dir.delete()
////      dir.mkdir()
//      new System( BerkeleyDB.factory( dir ))
   }

   trait Txn extends Sys.Txn[ Confluent, Durable ] {
//      private[Confluent] implicit def durable: Durable#Tx
//
//      private[Confluent] def readTreeVertex( tree: Ancestor.Tree[ Durable, Long ], index: S#Acc,
//                                             term: Long ) : (Ancestor.Vertex[ Durable, Long ], Int)
//      private[Confluent] def readPartialTreeVertex( index: S#Acc, term: Long ) : Ancestor.Vertex[ Durable, Long ]
//      private[Confluent] def writeTreeVertex( tree: IndexTree, v: Ancestor.Vertex[ Durable, Long ]) : Unit
//      private[Confluent] def readTreeVertexLevel( term: Long ) : Int
//      private[Confluent] def readIndexTree( term: Long ) : IndexTree
//      private[Confluent] def newIndexTree( term: Long, level: Int ) : IndexTree
//
//      private[Confluent] def addInputVersion( path: S#Acc ) : Unit
//
//      private[Confluent] def putTxn[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : Unit
//      private[Confluent] def putNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) : Unit
//      private[Confluent] def getTxn[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A
//      private[Confluent] def getNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A
//      private[Confluent] def isFresh( id: S#ID ) : Boolean
//
//      private[Confluent] def putPartial[ A ]( id: S#ID, value: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : Unit
//      private[Confluent] def getPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A
////      private[Confluent] def getFreshPartial[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : A
//
//      private[Confluent] def removeFromCache( id: S#ID ) : Unit
//      private[Confluent] def addDirtyMap( map: CacheMapImpl[ Confluent, _, _ ]) : Unit
//
//      private[Confluent] def makeVar[ A ]( id: S#ID )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : BasicVar[ A ]

      private[confluent] def inMemory : InMemory#Tx
   }

   implicit def inMemory( tx: Confluent#Tx ) : InMemory#Tx = tx.inMemory
}
trait Confluent extends Sys[ Confluent ] with Cursor[ Confluent ] {
   private type S = Confluent
   private type D = Durable
   private type I = InMemory

//   final type ID                    = Sys.ID[ S ] // Confluent.ID
   final type Tx                    = Confluent.Txn   // Sys.Txn[ S, D ]
//   final type Acc                   = Sys.Acc[ S ] // Confluent.Path
//   final type Var[ @specialized A ] = stm.Var[ S#Tx, A ] // Confluent.Var[ A ]
//   final type Entry[ A ]            = Sys.Entry[ S, A ]

   def inMemory : I

   private[confluent] def newVersionID( implicit tx: Tx ) : Long
}