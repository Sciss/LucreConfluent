/*
 *  KSys.scala
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

import de.sciss.lucre.stm.{Writer, Identifier, Sys, Txn => _Txn, Var => _Var}

object KSys {
//   private type S = KSys

   trait Txn[ S <: KSys[ S ]] extends _Txn[ S ]

   trait Var[ S <: KSys[ S ], A ] extends _Var[ S#Tx, A ]

   trait ID[ Txn, Acc ] extends Identifier[ Txn ] {
      def id: Int
      def path: Acc
   }

   trait Acc extends Writer {
      def mkString( prefix: String, sep: String, suffix: String ) : String
   }
}
trait KSys[ S <: KSys[ S ]] extends Sys[ S ] {
   type Var[ @specialized A ] <: KSys.Var[ S, A ]
   type Tx <: KSys.Txn[ S ]
   type ID <: KSys.ID[ S#Tx, S#Acc ]
   type Acc <: KSys.Acc
}
