/*
 *  KSysImpl.scala
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

import util.MurmurHash
import de.sciss.lucre.stm.{Identifier, Txn}

object KSysImpl {
//   sealed trait ID extends Identifier[ Txn ] {
//      private[Confluent] def id: Int
//      private[Confluent] def path: KSys#Acc
//      final def shortString : String = path.mkString( "<", ",", ">" )
//
//      override def hashCode = {
//         import MurmurHash._
//         var h = startHash( 2 )
//         val c = startMagicA
//         val k = startMagicB
//         h = extendHash( h, id, c, k )
//         h = extendHash( h, path.##, nextMagicA( c ), nextMagicB( k ))
//         finalizeHash( h )
//      }
//
//      override def equals( that: Any ) : Boolean =
//         that.isInstanceOf[ ID ] && {
//            val b = that.asInstanceOf[ ID ]
//            id == b.id && path == b.path
//         }
//   }
}
class KSysImpl extends KSys {
   def atomic[ A ]( fun: KSys#Tx => A ) : A = sys.error( "TODO" )
}
