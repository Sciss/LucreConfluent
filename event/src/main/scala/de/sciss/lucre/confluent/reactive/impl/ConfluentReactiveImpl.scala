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
import confluent.Sys

object ConfluentReactiveImpl {
//   def ??? : Nothing = sys.error( "TODO" )

   private type S = ConfluentReactive

   def apply( storeFactory: DataStoreFactory[ DataStore ]) : S = {
      // tricky: before `durable` was a `val` in `System`, this caused
      // a NPE with `Mixin` initialising `global`.
      // (http://stackoverflow.com/questions/12647326/avoiding-npe-in-trait-initialization-without-using-lazy-vals)
      val durable = stm.Durable( storeFactory )
      new System( storeFactory, durable )
   }

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

//   private sealed trait IsFresh[ S <: ConfluentReactiveLike[ S ]] {
//      final def isFresh( implicit tx: S#Tx ) : Boolean = true
//   }
//
//   private sealed trait IsRead[ S <: ConfluentReactiveLike[ S ]] {
//      protected def id: S#ID
//
//      final def isFresh( implicit tx: S#Tx ) : Boolean  = tx.isFresh( id )
//   }

   private final class EventVarTxImpl[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID )
                                                         ( implicit protected val ser: stm.Serializer[ S#Tx, S#Acc, A ])
   extends BasicEventVar[ S, A ] {
//      implicit protected def ser: stm.Serializer[ S#Tx, S#Acc, A ]

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

//   private final class EventVarTxNew[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID )
//                                                         ( implicit protected val ser: stm.Serializer[ S#Tx, S#Acc, A ])
//   extends EventVarTxImpl[ S, A ] with IsFresh[ S ]
//
//   private final class EventVarTxRead[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID )
//                                                         ( implicit protected val ser: stm.Serializer[ S#Tx, S#Acc, A ])
//   extends EventVarTxImpl[ S, A ] with IsRead[ S ]

   private final class EventVarImpl[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID,
                                                                           protected val ser: ImmutableSerializer[ A ])
   extends BasicEventVar[ S, A ] {
//      implicit protected def ser: ImmutableSerializer[ A ]

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

//   private final class EventVarNew[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID,
//                                                                           protected val ser: ImmutableSerializer[ A ])
//   extends EventVarImpl[ S, A ] with IsFresh[ S ]
//
//   private final class EventVarRead[ S <: ConfluentReactiveLike[ S ], A ]( protected val id: S#ID,
//                                                                           protected val ser: ImmutableSerializer[ A ])
//   extends EventVarImpl[ S, A ] with IsRead[ S ]

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

//   private final class IntEventVarNew[ S <: ConfluentReactiveLike[ S ]]( protected val id: S#ID )
//   extends IntEventVar[ S ] with IsFresh[ S ]
//
//   private final class IntEventVarRead[ S <: ConfluentReactiveLike[ S ]]( protected val id: S#ID )
//   extends IntEventVar[ S ] with IsRead[ S ]

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
         // OBSOLETE: note: eventCache.getCacheTxn will call store.get if the value is not in cache.
         // that assumes that the value has been written, and id.path.splitIndex is called.
         // for a fresh variable this fails obviously because the path is empty. therefore,
         // we decompose the call at this site.
         eventCache.getCacheTxn[ A ]( id.id, id.path )( this, ser )
//         val idi  = id.id
//         val path = id.path
//         if( path.isEmpty ) {
//            eventCache.getCacheOnly[ A ]( idi, path )( this )
//         } else {
//            eventCache.getCacheTxn[  A ]( idi, path )( this, ser )
//         }
      }
      private[reactive] def getEventNonTxn[ A ]( id: S#ID )( implicit ser: ImmutableSerializer[ A ]) : Option[ A ] = {
         // OBSOLETE: see comment in getEventTxn
         eventCache.getCacheNonTxn[ A ]( id.id, id.path )( this, ser )
//         val idi  = id.id
//         val path = id.path
//         if( path.isEmpty ) {
//            eventCache.getCacheOnly[   A ]( idi, path )( this )
//         } else {
//            eventCache.getCacheNonTxn[ A ]( idi, path )( this, ser )
//         }
      }

//      @inline private def allocEvent( pid: S#ID ) : S#ID = // new ConfluentID( system.newIDValue()( this ), pid.path )

      private def makeEventVar[ A ]( id: S#ID )( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         serializer match {
            case plain: ImmutableSerializer[ _ ] =>
               new EventVarImpl[ S, A ]( id, plain.asInstanceOf[ ImmutableSerializer[ A ]])
            case _ =>
               new EventVarTxImpl[ S, A ]( id )
         }
      }

      final def newEventVar[ A ]( pid: S#ID )
                                ( implicit serializer: stm.Serializer[ S#Tx, S#Acc, A ]) : evt.Var[ S, A ] = {
         val res = makeEventVar[ A ]( pid )
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
//      final lazy val inMemory: evt.InMemory#Tx = system.inMemory.wrap( peer )
      final lazy val inMemory: stm.InMemory#Tx = system.inMemory.wrap( peer )
   }

   private final class RegularTxn( val system: S, val durable: stm.Durable#Tx,
                                   val inputAccess: S#Acc, val cursorCache: Cache[ S#Tx ])
   extends confluent.impl.ConfluentImpl.RegularTxnMixin[ S, stm.Durable ] with TxnImpl {
      lazy val peer = durable.peer
   }

   private final class RootTxn( val system: S, val peer: InTxn )
   extends confluent.impl.ConfluentImpl.RootTxnMixin[ S, stm.Durable ] with TxnImpl {
      lazy val durable: stm.Durable#Tx = {
         log( "txn durable" )
         system.durable.wrap( peer )
      }
   }

   private final class System( protected val storeFactory: DataStoreFactory[ DataStore ], val durable: stm.Durable )
   extends confluent.impl.ConfluentImpl.Mixin[ S ]
   with evt.impl.ReactionMapImpl.Mixin[ S ]
   with ConfluentReactive {
      def inMemory               = durable.inMemory
      def durableTx(  tx: S#Tx ) = tx.durable
      def inMemoryTx( tx: S#Tx ) = tx.inMemory

      private val eventStore  = storeFactory.open( "event", overwrite = true )
      private val eventVarMap = DurablePersistentMap.newConfluentIntMap[ S ]( eventStore, this, isOblivious = true )
      val eventCache : CacheMap.Durable[ S, Int, DurablePersistentMap[ S, Int ]] = DurableCacheMapImpl.newIntCache( eventVarMap )

      protected def wrapRegular( dtx: stm.Durable#Tx, inputAccess: S#Acc, cursorCache: Cache[ S#Tx ]) =
         new RegularTxn( this, dtx, inputAccess, cursorCache )

      protected def wrapRoot( peer: InTxn ) = new RootTxn( this, peer )
   }
}
