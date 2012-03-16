/*
 *  ConfluentCacheMap.scala
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

import collection.immutable.LongMap
import concurrent.stm.TMap
import de.sciss.lucre.stm.{Serializer, Sys}

object ConfluentCacheMap {
   def apply[ S <: KSys[ S ], A ]( persistent: ConfluentTxnMap[ S#Tx, S#Acc ]) : ConfluentCacheMap[ S ] =
      new Impl[ S ]( persistent )

   private val emptyLongMapVal   = LongMap.empty[ Any ]
   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ S <: KSys[ S ]]( persistent: ConfluentTxnMap[ S#Tx, S#Acc ])
   extends ConfluentCacheMap[ S ] {
      private val idMapRef = TMap.empty[ Int, LongMap[ Write[ S#Acc, _ ]]]
      @volatile private var dirtyVar = false

      def isDirty : Boolean = dirtyVar

      def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
         implicit val itx = tx.peer
         val mapOld  = idMapRef.get( id ).getOrElse( emptyLongMap[ Write[ S#Acc, _ ]])
         val mapNew  = mapOld + ((path.sum, Write[ S#Acc, A ]( path, value, ser )))
         idMapRef.put( id, mapNew )
         dirtyVar = true
      }

      def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ A ] = {
         idMapRef.get( id )( tx.peer ).flatMap( _.get( path.sum ).map( _.value )).asInstanceOf[ Option[ A ]]
      }

      def flush( suffix: Traversable[ Int ])( implicit tx: S#Tx ) {
         implicit val itx = tx.peer
         if( dirtyVar ) idMapRef.foreach { tup1 =>
            val id   = tup1._1
            val map  = tup1._2
            map.foreach {
               case (_, Write( p, value, writer )) =>
                  var path = p.index
                  suffix.foreach( path :+= _ )
                  persistent.put( id, path, value )( tx, writer )
            }
         }
      }

      def flush( suffix: Int )( implicit tx: S#Tx ) {
         implicit val itx = tx.peer
         if( dirtyVar ) idMapRef.foreach { tup1 =>
            val id   = tup1._1
            val map  = tup1._2
            map.foreach {
               case (_, Write( p, value, writer )) =>
                  val path    = p.index :-| suffix
                  persistent.put( id, path, value )( tx, writer )
            }
         }
      }
   }

   private final case class Write[ Acc, A ]( path: Acc, value: A, serializer: Serializer[ A ]) {
//      type A1 = A
   }
}
sealed trait ConfluentCacheMap[ S <: Sys[ S ]] extends ConfluentTxnMap[ S#Tx, S#Acc ] {
//   def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, writer: TxnWriter[ A ]) : Unit
//   def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, reader: TxnReader[ S#Tx, S#Acc, A ]) : Option[ A ]

   def isDirty : Boolean
   def flush( suffix: Int )( implicit tx: S#Tx ) : Unit
   def flush( suffix: Traversable[ Int ])( implicit tx: S#Tx ) : Unit
}