/*
 *  Cursor.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2013 Hanns Holger Rutz. All rights reserved.
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

import stm.Disposable

//object Cursor {
//   implicit def serializer[ S <: Sys[ S ]] : stm.Serializer[ S#Tx, S#Acc, Cursor[ S ]] = sys.error( "TODO" )
//}
trait Cursor[ S <: Sys[ S ]] extends stm.Cursor[ S ] with Disposable[ S#Tx ] with Writable {
//   // this should be removed. It is only used in one of the
//   // tests right now, due to lack for proper `stepBack` or `stepFrom` functionality.
//   private[confluent] def position_=( path: S#Acc )( implicit tx: S#Tx ) : Unit

   def stepFrom[ A ]( path: S#Acc )( fun: S#Tx => A ) : A
}