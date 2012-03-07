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
import de.sciss.lucre.stm.{TxnReader, TxnWriter, Sys}
import concurrent.stm.TMap

object ConfluentCacheMap {
   def apply[ S <: Sys[ S ], A ]() : ConfluentTxMap[ S#Tx, S#Acc, A ] = new Impl[ S, A ]

   private val emptyLongMapVal   = LongMap.empty[ Any ]
   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ S <: Sys[ S ], A ] extends ConfluentCacheMap[ S, A ] {
      private val idMapRef = TMap.empty[ Int, LongMap[ Write[ PathLike with S#Acc, A ]]]
      @volatile private var dirtyVar = false

      def isDirty : Boolean = dirtyVar

      def put( id: Int, path: PathLike with S#Acc, value: A )( implicit tx: S#Tx, writer: TxnWriter[ A ]) {
         implicit val itx = tx.peer
         val mapOld  = idMapRef.get( id ).getOrElse( emptyLongMap[ Write[ PathLike with S#Acc, A ]])
         val mapNew  = mapOld + ((path.sum, Write( path, value, writer )))
         idMapRef.put( id, mapNew )
         dirtyVar = true
      }

      def get( id: Int, path: PathLike with S#Acc )( implicit tx: S#Tx, reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
         idMapRef.get( id )( tx.peer ).flatMap( _.get( path.sum ).map( _.value )).getOrElse( sys.error( "TODO" ))
      }

      def flush()( implicit tx: S#Tx ) {
         sys.error( "TODO" )
      }
   }

   private final case class Write[ Acc, A ]( path: Acc, value: A, writer: TxnWriter[ A ])
}
sealed trait ConfluentCacheMap[ S <: Sys[ S ], A ] extends ConfluentTxMap[ S#Tx, S#Acc, A ] {
   def isDirty : Boolean
   def flush()( implicit tx: S#Tx ) : Unit
}