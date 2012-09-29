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
import confluent.impl.DurableCacheMapImpl

object ConfluentReactiveImpl {
//   def ??? : Nothing = sys.error( "TODO" )

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

      override def toString = "evt.Var(" + id + ")"
   }

   private final class EventVarTxImpl[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID )
                                                         ( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ])
   extends BasicEventVar[ S, A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " set " + v )
         tx.putEventTxn( id, v )
      }

      def get( implicit tx: S#Tx ) : Option[ A ] = {
         log( this.toString + " get" )
         tx.getEventTxn( id )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get
   }

   private final class EventVarImpl[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID,
                                                                           protected val ser: ImmutableSerializer[ A ])
   extends BasicEventVar[ S, A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         log( this.toString + " set " + v )
         tx.putEventNonTxn( id, v )( ser )
      }

      def get( implicit tx: S#Tx ) : Option[ A ] = {
         log( this.toString + " get" )
         tx.getEventNonTxn[ A ]( id )( ser )
      }

//      def getFresh( implicit tx: S#Tx ) : A = get
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

      override def toString = "evt.Var[Int](" + id + ")"

      // ---- Serializer ----
      def write( v: Int, out: DataOutput ) { out.writeInt( v )}
      def read( in: DataInput ) : Int = in.readInt()
   }

   trait TxnMixin[ S <: ConfluentReactiveLike[ S ]]
   extends confluent.impl.ConfluentImpl.TxnMixin[ S ] // gimme `alloc` and `readSource`
   with ConfluentReactiveLike.Txn[ S ] {
      _: S#Tx =>

      final def reactionMap : ReactionMap[ S ] = system.reactionMap

      private val eventCache: CacheMap.Durable[ S, Int, DurablePersistentMap[ S, Int ]] = system.eventCache

      private var markDirtyFlag = false

//      def isDirty = markDirtyFlag.get( peer )

      final private def markEventDirty() {
         if( !markDirtyFlag ) {
            markDirtyFlag = true
            addDirtyCache( eventCache )
         }
      }

      private[reactive] def putEventTxn[ A ]( id: S#ID, value: A )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) {
         eventCache.putCacheTxn[ A ]( id.id, id.path, value )( this, ser )
         markEventDirty()
      }
      private[reactive] def putEventNonTxn[ A ]( id: S#ID, value: A )( implicit ser: ImmutableSerializer[ A ]) {
         eventCache.putCacheNonTxn( id.id, id.path, value )( this, ser )
         markEventDirty()
      }
      private[reactive] def getEventTxn[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : Option[ A ] = {
         eventCache.getCacheTxn[ A ]( id.id, id.path )( this, ser )
      }
      private[reactive] def getEventNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : Option[ A ] = {
         eventCache.getCacheNonTxn[ A ]( id.id, id.path )( this, ser )
      }

//      @inline private def allocEvent( pid: S#ID ) : S#ID = // new ConfluentID( system.newIDValue()( this ), pid.path )

      private def makeEventVar[ A ]( id: S#ID )( implicit ser: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         ser match {
            case plain: ImmutableSerializer[ _ ] =>
               new EventVarImpl[ S, A ]( id, plain.asInstanceOf[ ImmutableSerializer[ A ]])
            case _ =>
               new EventVarTxImpl[ S, A ]( id )
         }
      }

      final def newEventVar[ A ]( pid: S#ID )
                                ( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         val res = makeEventVar[ A ]( alloc( pid ))
         log( "new evt var " + res )
         res
      }

      final def newEventIntVar[ A ]( pid: S#ID ) : evt.Var[ S, Int ] = {
         val id   = alloc( pid )
         val res  = new IntEventVar( id )
         log( "new evt var " + res )
         res
      }

      final def readEventVar[ A ]( pid: S#ID, in: DataInput )
                                 ( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         val res = makeEventVar[ A ]( readSource( in, pid ))
         log( "read evt " + res )
         res
      }

      final def readEventIntVar[ A ]( pid: S#ID, in: DataInput ) : evt.Var[ S, Int ] = {
         val res = new IntEventVar( readSource( in, pid ))
         log( "read evt " + res )
         res
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

      private val eventStore  = storeFactory.open( "event" )
      private val eventVarMap = DurablePersistentMap.newConfluentIntMap[ S ]( eventStore, this )
      val eventCache : CacheMap.Durable[ S, Int, DurablePersistentMap[ S, Int ]] = DurableCacheMapImpl.newIntCache( eventVarMap )

      protected def wrapRegular( dtx: evt.Durable#Tx, inputAccess: S#Acc ) = new RegularTxn( this, dtx, inputAccess )
      protected def wrapRoot( peer: InTxn ) = new RootTxn( this, peer )
   }
}
