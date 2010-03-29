/**
 *  VersionManagement
 *  (de.sciss.temporal package)
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

package de.sciss {
import confluent.{ VersionPath, FatIdentifier => FId, FatPointer => FPtr, FatValue => FVal, _ }
package confluent {

/**
 *    @version 0.11, 25-Mar-10
 */
object VersionManagement {
   private var currentPathVar = VersionPath.init

   def currentVersion: VersionPath   = currentPathVar
   def currentAccess: Path = currentPathVar.path

   def get[ V ]( fval: FVal[ V ]) = {
      fval.access( currentAccess ).get
   }

   // avec lazy access
   def get[ V ]( fval: FVal[ V ], seminalPath: Path ) = {
      fval.access( substitute( currentAccess, seminalPath )).get
   }

   def get[ V ]( fval: FVal[ V ], seminalPath: Path, access: Path ) = {
      fval.access( substitute( access, seminalPath )).get
   }

   def getO[ V ]( fval: FVal[ V ], seminalPath: Path ) = {
      fval.access( substitute( currentAccess, seminalPath ))
   }

   def getO[ V ]( fval: FVal[ V ], seminalPath: Path, access: Path ) = {
      fval.access( substitute( access, seminalPath ))
   }

   def set[ V ]( fval: FVal[ V ], value: V ) {
      fval.assign( currentAccess, value )
   }

   // substitutes an access path to become an assignment pedigree
   private def substitute( access: Path, seminalPath: Path ) : Path = {
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
   def set[ V ]( fval: FVal[ V ], value: V, seminalPath: Path ) {
//      val pedigree = substitute( currentAccess, seminalVersion )
//      println( "pedigree = " + pedigree )
      fval.assign( substitute( currentAccess, seminalPath ), value )
   }

   def use[ T ]( v: VersionPath )( thunk: => T ) = {
      val oldV = currentPathVar
      try {
         currentPathVar = v
         thunk
      } finally {
         currentPathVar = oldV
      }
   }

   // ---- navigation ----

   /**
    *    Creates a new (non-melded) node
    *    branching off from the current version.
    *    It does so by adding the new node
    *    _after_ the current node
    *    (i.e. becoming the new children's tail)
    *    in the pre-order list,
    *    and _before_ the current node in
    *    the post-order list
    */
   def versionStep : VersionPath = {
      currentPathVar = currentPathVar.newBranch
      currentPathVar
   }

   /**
    *    Inserts a retroactive node
    *    before the current version.
    *    It does so by adding the new
    *    node _before_ the current node
    *    in the pre-order list, and
    *    _after_ the current node in
    *    the post-order list. Makes the
    *    retroactive node the current node.
    * 
    *    @return  the new retroactive node
    */
   def prependRetro : VersionPath = {
      currentPathVar = currentPathVar.newRetroParent
      currentPathVar
   }

   /**
    *    Inserts a retroactive node
    *    after the current version.
    *    It does so by adding the new
    *    node _right after_ the current node
    *    (i.e. becoming the new children's head)
    *    in the pre-order list,
    *    and _before_ the current node in
    *    the post-order list
    *
    *    @return  the new retroactive node
    */
   def appendRetro : VersionPath = {
      currentPathVar = currentPathVar.newRetroChild
      currentPathVar
   }

   def makeCurrent( version: VersionPath ) {
      currentPathVar = version
   }
}

//class IntervalAccess( val path: Path, val fptr: FPtr[ IntervalLike ])
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

}}