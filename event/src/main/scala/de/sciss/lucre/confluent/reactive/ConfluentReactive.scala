/*
 *  ConfluentReactive.scala
 *  (ConfluentReactive)
 *
 *  Copyright (c) 2012 Hanns Holger Rutz. All rights reserved.
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
package reactive

import de.sciss.lucre.{event => evt}
import stm.{DataStoreFactory, DataStore, ImmutableSerializer}
import stm.impl.BerkeleyDB
import java.io.File
import impl.{ConfluentReactiveImpl => Impl}

object ConfluentReactive {
   private type S = ConfluentReactive

   def apply( storeFactory: DataStoreFactory[ DataStore ]) : S = Impl( storeFactory )

   def tmp() : S = {
      val dir = File.createTempFile( "confluent_", "db" )
      dir.delete()
//      dir.mkdir()
      apply( BerkeleyDB.factory( dir ))
   }

   trait Txn extends ConfluentReactiveLike.Txn[ S ] {
      private[confluent] def durable : stm.Durable#Tx
      private[confluent] def inMemory : stm.InMemory#Tx
   }

   implicit def inMemory( tx: S#Tx ) : stm.InMemory#Tx = tx.inMemory
}
object ConfluentReactiveLike {
   trait Txn[ S <: ConfluentReactiveLike[ S ]] extends confluent.Sys.Txn[ S ] with evt.Txn[ S ] {
      private[reactive] def putEventTxn[    A ]( id: S#ID, value: A )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Unit
      private[reactive] def putEventNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ])         : Unit
      private[reactive] def getEventTxn[    A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Option[ A ]
      private[reactive] def getEventNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ])         : Option[ A ]
   }
}
trait ConfluentReactiveLike[ S <: ConfluentReactiveLike[ S ]] extends confluent.Sys[ S ] with evt.Sys[ S ] {
   type Tx <: ConfluentReactiveLike.Txn[ S ]
   private[reactive] def eventCache : CacheMap.Durable[ S, Int, DurablePersistentMap[ S, Int ]]
}
// ought to remove cursor eventually in favour of a proper branch management...
trait ConfluentReactive extends ConfluentReactiveLike[ ConfluentReactive ] {
   final protected type S  = ConfluentReactive
   final type D            = stm.Durable
   final type I            = stm.InMemory
   final type Tx           = ConfluentReactive.Txn

//   private[confluent] def reactionMap: evt.ReactionMap[ S ]
}