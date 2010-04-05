/*
 *  IntervalProxy.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
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
 *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, FatRef => FRef, _ }

class IntervalProxy( protected val ref: FVal[ IntervalLike ], protected val readPath: Path /*, protected val writePath: Path*/ )
extends IntervalExprLike with NodeProxy[ IntervalLike ] {
   import VersionManagement._

//   def start: PeriodLike   = access.start
//   def start: PeriodLike   = StartProxy // new PeriodProxy( fi, sp )
//   def stop: PeriodLike    = access.stop
//   def +( p: PeriodLike ): IntervalLike
//   def -( p: PeriodLike ): IntervalLike

   def fixed: IntervalLike = access.fixed
//   @inline private def access: IntervalLike = get( i, readPath, writePath ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.

//   def access( rp: Path, wp: Path ) = new IntervalProxy( i, rp, wp )
//      new IntervalProxy( i, NodeID.substitute( readPath, rp ), NodeID.substitute( writePath, wp ))

   override def toString = try { access.toString } catch { case _ => super.toString }

   object start extends PeriodExpr {
      def inf: PeriodConst = access.start.inf
      def sup: PeriodConst = access.start.sup
      def fixed: PeriodLike = access.start.fixed

      override def toString = try { access.start.toString } catch { case _ => super.toString }
   }

   object dur extends PeriodExpr {
      def inf: PeriodConst = access.dur.inf
      def sup: PeriodConst = access.dur.sup
      def fixed: PeriodLike = access.dur.fixed

      override def toString = try { access.dur.toString } catch { case _ => super.toString }
   }
}

//abstract class PeriodProxy( val fi: FVal[ IntervalLike ], sp: Path )