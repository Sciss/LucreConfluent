package de.sciss.temporal

import de.sciss.confluent.{ FatValue => FVal, _ }

class IntervalProxy( val fi: FVal[ IntervalLike ], sp: Path )
extends IntervalExprLike {
   import VersionManagement._

//   def start: PeriodLike   = access.start
//   def start: PeriodLike   = StartProxy // new PeriodProxy( fi, sp )
//   def stop: PeriodLike    = access.stop
//   def +( p: PeriodLike ): IntervalLike
//   def -( p: PeriodLike ): IntervalLike

   def fixed: IntervalLike = access.fixed
   @inline private def access: IntervalLike = get( fi, sp ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.

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