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

import concurrent.stm.TMap
import de.sciss.lucre.stm.{Serializer, Sys}
import collection.immutable.{IntMap, LongMap}

object ConfluentCacheMap {
//   def apply[ S <: KSys[ S ], A ]( persistent: ConfluentTxnMap[ S#Tx, S#Acc ]) : ConfluentCacheMap[ S ] =
//      new Impl[ S ]( persistent )

   private val emptyLongMapVal      = LongMap.empty[ Any ]
   private def emptyLongMap[ T ]    = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]
   private val emptyIntMapVal       = IntMap.empty[ Any ]
   private def emptyIntMap[ T ]     = emptyIntMapVal.asInstanceOf[ IntMap[ T ]]

   private final case class Write[ Acc, A ]( path: Acc, value: A, serializer: Serializer[ A ]) {
//      type A1 = A
   }
}
trait ConfluentCacheMap[ S <: KSys[ S ]] extends ConfluentTxnMap[ S#Tx, S#Acc ] {
   import ConfluentCacheMap._

   protected def persistent: ConfluentTxnMap[ S#Tx, S#Acc ]

   private var idMapRef = emptyIntMap[ LongMap[ Write[ S#Acc, _ ]]]

   protected def clear() {
      idMapRef = emptyIntMap
   }

   final def put[ A ]( id: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
      val mapOld  = idMapRef.getOrElse( id, emptyLongMap[ Write[ S#Acc, _ ]])
      val mapNew  = mapOld + ((path.sum, Write[ S#Acc, A ]( path, value, ser )))
      idMapRef += ((id, mapNew))
//      dirtyVar = true
   }

   final def get[ A ]( id: Int, path: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ A ] = {
      idMapRef.get( id ).flatMap( _.get( path.sum ).map( _.value )).asInstanceOf[ Option[ A ]].orElse(
         persistent.get[ A ]( id, path )
      )
   }

   private def flush( implicit tx: S#Tx ) {
//      implicit val itx = tx.peer
//      idMapRef.foreach { tup1 =>
//         val id   = tup1._1
//         val map  = tup1._2
//         map.foreach {
//            case (_, Write( p, value, writer )) =>
//               var path = p.index
//               suffix.foreach( path :+= _ )
//               persistent.put( id, path, value )( tx, writer )
//         }
//      }
   }
}