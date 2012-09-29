/*
 *  ConfluentReactiveImpl.scala
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
package impl

import stm.{ImmutableSerializer, DataStoreFactory, DataStore}
import de.sciss.lucre.{event => evt}
import concurrent.stm.InTxn
import de.sciss.lucre.event.ReactionMap

object ConfluentReactiveImpl {
   def ??? : Nothing = sys.error( "TODO" )

   private type S = ConfluentReactive

   private sealed trait BasicEventVar[ S <: Sys[ S ], A ] extends evt.Var[ S, A ] {
      protected def id: S#ID

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
      }

      final def dispose()( implicit tx: S#Tx ) {
         tx.removeFromCache( id )
         id.dispose()
      }

      final def getOrElse( default: => A )( implicit tx: S#Tx ) : A = get.getOrElse( default )

      final def transform( default: => A )( f: A => A )( implicit tx: S#Tx ) { set( f( getOrElse( default )))}

      final def isFresh( implicit tx: S#Tx ) : Boolean  = tx.isFresh( id )
   }

   private final class IntEventVar[ S <: ConfluentReactiveLike[ S ]]( protected val id: S#ID )
   extends BasicEventVar[ S, Int ] with ImmutableSerializer[ Int ] {
      def get( implicit tx: S#Tx ) : Option[ Int ] = {
         log( this.toString + " get" )
         tx.getEventNonTxn[ Int ]( id )( this )
      }

//      def getFresh( implicit tx: S#Tx ) : Int = get

      def setInit( v: Int )( implicit tx: S#Tx ) {
         log( this.toString + " ini " + v )
         tx.putNonTxn( id, v )( this )
      }

      def set( v: Int )( implicit tx: S#Tx ) {
//         assertExists()
         log( this.toString + " set " + v )
         tx.putNonTxn( id, v )( this )
      }

      override def toString = "Var[Int](" + id + ")"

      // ---- Serializer ----
      def write( v: Int, out: DataOutput ) { out.writeInt( v )}
      def read( in: DataInput ) : Int = in.readInt()
   }

   trait TxnMixin[ S <: ConfluentReactiveLike[ S ]] extends ConfluentReactiveLike.Txn[ S ] {
      final def reactionMap : ReactionMap[ S ] = system.reactionMap

      private val eventMap: confluent.impl.DurableCacheMapImpl[ S, Int ] = ???

      private var markDirtyFlag = false

//      def isDirty = markDirtyFlag.get( peer )

      final private def markEventDirty() {
         if( !markDirtyFlag ) {
            markDirtyFlag = true
            addDirtyCache( eventMap )
         }
      }

      private[reactive] def putEventTxn[ A ]( id: S#ID, value: A )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) {
         eventMap.putCacheTxn[ A ]( id.id, id.path, value )( this, ser )
         markEventDirty()
      }
      private[reactive] def putEventNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) {
         ???
      }
      private[reactive] def getEventTxn[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : A = {
         ???
      }
      private[reactive] def getEventNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : A = {
         ???
      }

      final def newEventVar[ A ]( id: S#ID )
                                ( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
//         new VarImpl( Ref.make[ A ])
         ??? // new DurableVarImpl[ S, A ]( system.newEventIDValue()( this ), serializer )
      }

      final def newEventIntVar[ A ]( id: S#ID ) : evt.Var[ S, Int ] = {
         ??? // new DurableIntVar[ S ]( system.newEventIDValue()( this ))
      }

      final def readEventVar[ A ]( id: S#ID, in: DataInput )
                                 ( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         val id = in.readInt()
         ??? // new DurableVarImpl[ S, A ]( id, serializer )
      }

      final def readEventIntVar[ A ]( id: S#ID, in: DataInput ) : evt.Var[ S, Int ] = {
         val id = in.readInt()
         ??? // new DurableIntVar[ S ]( id )
      }
   }

   private sealed trait TxnImpl extends TxnMixin[ S ] with ConfluentReactive.Txn {
      final lazy val inMemory: evt.InMemory#Tx = system.inMemory.wrap( peer )
   }

   private final class RegularTxn( val system: S, val durable: evt.Durable#Tx,
                                   val inputAccess: S#Acc )
   extends confluent.impl.ConfluentImpl.RegularTxnMixin[ S, evt.Durable ] with TxnImpl {
      lazy val peer = durable.peer
   }

   private final class RootTxn( val system: S, val peer: InTxn )
   extends confluent.impl.ConfluentImpl.RootTxnMixin[ S, evt.Durable ] with TxnImpl {
      lazy val durable: evt.Durable#Tx = {
         log( "txn durable" )
         system.durable.wrap( peer )
      }
   }

   private final class System( protected val storeFactory: DataStoreFactory[ DataStore ], val durable: evt.Durable )
   extends confluent.impl.ConfluentImpl.Mixin[ S ]
   with evt.impl.ReactionMapImpl.Mixin[ S ]
   with ConfluentReactive {
      def inMemory               = durable.inMemory
      def durableTx(  tx: S#Tx ) = tx.durable
      def inMemoryTx( tx: S#Tx ) = tx.inMemory

      protected def wrapRegular( dtx: evt.Durable#Tx, inputAccess: S#Acc ) = new RegularTxn( this, dtx, inputAccess )
      protected def wrapRoot( peer: InTxn ) = new RootTxn( this, peer )
   }
}
