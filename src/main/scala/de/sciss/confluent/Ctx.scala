/*
 *  Ctx.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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
 *	 You should have received a copy of the GNU General Public
 *	 License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

import concurrent.stm.InTxn

trait CtxLike {
   def txn: InTxn
   def eph : ECtx
}

trait ECtx extends CtxLike

trait KCtxLike[ V <: VersionPath ] extends CtxLike {
   def path : VersionPath // V
   private[confluent] def writePath : VersionPath
}

trait KCtx[ V <: VersionPath ] extends KCtxLike[ V ]

//trait PCtxLike extends CtxLike {
//   def period : Period
//   def interval : Interval
//   private[proc] def interval_=( i: Interval ) : Unit
//}
//
//trait PCtx extends PCtxLike
//
//trait BCtx extends KCtxLike with PCtxLike
