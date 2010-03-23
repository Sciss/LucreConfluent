package de.sciss.temporal

import de.sciss.confluent.{ VersionPath, FatIdentifier => FId, FatPointer => FPtr, FatValue => FVal, _ }

object VersionManagement {
   private var currentPathVar = VersionPath.init

   def currentVersion: VersionPath   = currentPathVar
   def currentAccess: CompressedPath = currentPathVar.path

   def get[ V ]( fval: FVal[ V ]) = {
      fval.access( currentAccess ).get
   }

   def set[ V ]( fval: FVal[ V ], value: V ) {
      fval.assign( currentAccess, value )
   }
}

//class IntervalAccess( val path: CompressedPath, val fptr: FPtr[ IntervalLike ])
//extends IntervalExprLike {
//   import VersionManagement._
//
//   def start: PeriodLike = detach.start
//   def stop: PeriodLike = detach.stop
////   def +( p: PeriodLike ): IntervalLike
////   def -( p: PeriodLike ): IntervalLike
//
//   def eval: IntervalConst = detach.eval
//   def detach: IntervalLike = get( fptr ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.
////   def set( i: IntervalLike ) = set( fptr, i )
//}

class IntervalProxy( fi: FVal[ IntervalLike ])
extends IntervalExprLike {
   import VersionManagement._

   def start: PeriodLike   = access.start
   def stop: PeriodLike    = access.stop
//   def +( p: PeriodLike ): IntervalLike
//   def -( p: PeriodLike ): IntervalLike

   def fixed: IntervalLike = access.fixed
   @inline private def access: IntervalLike = get( fi ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.
}

object Region {
   import VersionManagement._

   def apply( name: String, i: IntervalLike ) = {
      val r = new Region( name )
      r.interval = i
      r
   }
}

// note: eventually 'name' should also be confluent
class Region private ( val name: String ) {
   import VersionManagement._

   private val fi = new FVal[ IntervalLike ]
//   def interval: IntervalLike = new IntervalAccess( currentAccess, intervalPtr )
   def interval: IntervalLike = new IntervalProxy( fi )
   def interval_=( i: IntervalLike ) = {
       set( fi, i )
   }
}