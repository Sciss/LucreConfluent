package de.sciss.temporal

import de.sciss.confluent.{ VersionPath, FatIdentifier => FId, FatPointer => FPtr, _ }

object VersionManagement {
   private var currentPathVar = VersionPath.init

   def currentVersion: VersionPath   = currentPathVar
   def currentAccess: CompressedPath = currentPathVar.path

   def get[ V ]( fptr: FPtr[ V ]) = {
      val fid = fptr.access( currentAccess )
      fid.get.value
   }

//   def set[ V ]( fptr: FPtr[ V ], value: V ) {
//      fptr.assign( FId( currentAccess, value ))
//   }
}

class IntervalAccess( val path: CompressedPath, val fptr: FPtr[ IntervalLike ])
extends IntervalExprLike {
   import VersionManagement._

   def start: PeriodLike = detach.start
   def stop: PeriodLike = detach.stop
//   def +( p: PeriodLike ): IntervalLike
//   def -( p: PeriodLike ): IntervalLike

   def eval: IntervalConst = detach.eval
   def detach: IntervalLike = get( fptr ) // XXX needs to deal with side-branches, e.g. find common ancestor tree etc.
//   def set( i: IntervalLike ) = set( fptr, i )
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

   private val intervalPtr = new FPtr[ IntervalLike ]
   def interval: IntervalLike = new IntervalAccess( currentAccess, intervalPtr )
   def interval_=( i: IntervalLike ) = {
      // XXX should match here against IntervalAccess to prevent loops (e.g. r.interval = r.interval)
      val fid = i match {
         case ia: IntervalAccess => FId( ia.path, ia.detach )
         case _ => FId( currentAccess, i )
      }
      intervalPtr.assign( currentAccess, fid )

//      set( intervalPtr, i )
   }
}