package de.sciss.temporal

import de.sciss.confluent.{ VersionPath, FatIdentifier => FId, FatPointer => FPtr, FatValue => FVal, _ }

object VersionManagement {
   private var currentPathVar = VersionPath.init

   def currentVersion: VersionPath   = currentPathVar
   def currentAccess: CompressedPath = currentPathVar.path

   def get[ V ]( fval: FVal[ V ]) = {
      fval.access( currentAccess ).get
   }

   // avec lazy access
   def get[ V ]( fval: FVal[ V ], seminalPath: CompressedPath ) = {
      fval.access( substitute( currentAccess, seminalPath )).get
   }

   def set[ V ]( fval: FVal[ V ], value: V ) {
      fval.assign( currentAccess, value )
   }

   // substitutes an access path to become an assignment pedigree
   private def substitute( access: CompressedPath, seminalPath: CompressedPath ) : CompressedPath = {
      // XXX this needs to be optimized one day
      // XXX bug in Vector (23-Mar-10) : indexOf returns -1 for the last element
//      val off = access.indexOf( seminalVersion ) match {
//         case -1 if( access( access.length - 1 ) == seminalVersion ) => access.length - 1
//         case x => x
//      }
//      // XXX correct?
//      if( (off & 1) == 0 ) {
//         access.drop( off )
//      } else {
//         seminalVersion +: access.drop( off )
//      }
      val off = access.indexOf( seminalPath.head )
      if( off == 0 ) access else access.drop( off )
   }

   // avec lazy access
   def set[ V ]( fval: FVal[ V ], value: V, seminalPath: CompressedPath ) {
//      val pedigree = substitute( currentAccess, seminalVersion )
//      println( "pedigree = " + pedigree )
      fval.assign( substitute( currentAccess, seminalPath ), value )
   }

//   def transaction( thunk: => Unit ) {
//      try {
//
//      } catch { case x => {
//
//         throw e
//      }}
//   }

   def versionStep : VersionPath = {
      currentPathVar = currentPathVar.newBranch
      currentPathVar
   }

   def makeCurrent( version: VersionPath ) {
      currentPathVar = version
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

class IntervalProxy( val fi: FVal[ IntervalLike ], sp: CompressedPath )
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

object Region {
   import VersionManagement._

   def apply( name: String, i: IntervalLike ) = {
      val r = new Region( name, currentVersion.path.takeRight( 2 ))
      r.interval = i
      r
   }
}

// note: eventually 'name' should also be confluent
class Region private ( val name: String, val sp: CompressedPath ) {
   import VersionManagement._

   private val fi = new FVal[ IntervalLike ]
//   def interval: IntervalLike = new IntervalAccess( currentAccess, intervalPtr )
   def interval: IntervalLike = new IntervalProxy( fi, sp )
   def interval_=( i: IntervalLike ) = {
       set( fi, i, sp )
   }

   override def toString = "Region( " + name + ", " + (try { get( fi, sp ).toString } catch { case _ => fi.toString }) + " )"

   def inspect {
      println( toString )
      fi.inspect
   }
}