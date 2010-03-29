package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, _ }

class IntervalProxy( val fi: FVal[ IntervalLike ], sp: Path )
extends IntervalExprLike {
   import VersionManagement._

   def start: PeriodLike   = access.start
   def stop: PeriodLike    = access.stop
//   def +( p: PeriodLike ): IntervalLike
//   def -( p: PeriodLike ): IntervalLike

   def fixed: IntervalLike = access.fixed
   @inline private def access: IntervalLike = get( fi, sp ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.

   override def toString = try { access.toString } catch { case _ => super.toString }
}
