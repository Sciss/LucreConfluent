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
import confluent.{ VersionPath, FatRef => FRef, FatValue => FVal, _ }
package confluent {

/**
 *    @version 0.11, 25-Mar-10
 */
object VersionManagement {
   private var currentPathVar = VersionPath.init
   private var readPathVar = VersionPath.init  

//   def currentVersion: VersionPath   = currentPathVar
//   def currentAccess: Path = currentPathVar.path

   def currentVersion: VersionPath = currentPathVar
   def readVersion: VersionPath = readPathVar
   def writeVersion: VersionPath = currentPathVar
   def readAccess:  Path = readPathVar.path
   def writeAccess: Path = currentPathVar.path
   def seminalPath: Path = currentPathVar.tail

//   def get[ V ]( fval: FVal[ V ]) = {
//      fval.access( readAccess ).get
//   }

   def get[ V ]( fval: FVal[ V ], path: Path ) = {
      fval.access( path ).get
   }

   def get[ V ]( fref: FRef[ V ], readPath: Path, writePath: Path ) : V = {
      fref.access( readPath ).map {
         case nid: NodeID[ _ ] => nid.access( readPath, writePath ).asInstanceOf[ V ]
         case x => x
      } get
   }
   
//   def get[ V ]( fval: FVal[ V ], seminalPath: Path, access: Path ) = {
//      fval.access( substitute( access, seminalPath )).get
//   }

   def getO[ V ]( fval: FVal[ V ], path: Path ) = {
      fval.access( path )
   }

   def getO[ V ]( fref: FRef[ V ], readPath: Path, writePath: Path ) : Option[ V ] = {
      fref.access( readPath ).map {
         case nid: NodeID[ _ ] => nid.access( readPath, writePath ).asInstanceOf[ V ]
         case v => v
      }
   }

   def resolve[ V ]( readPath: Path, writePath: Path, v: V ) : V = v match {
      case nid: NodeID[ _ ] => nid.access( readPath, writePath ).asInstanceOf[ V ]
      case _ => v
   }

//   def getO[ V ]( fval: FVal[ V ], seminalPath: Path, access: Path ) = {
//      fval.access( substitute( access, seminalPath ))
//   }

//   def set[ V ]( fval: FVal[ V ], value: V ) {
//      fval.assign( writeAccess, value )
//   }

   def set[ V ]( fval: FVal[ V ], path: Path, value: V ) {
      fval.assign( path, value )
   }

   def set[ V ]( fref: FRef[ V ], path: Path, value: V ) {
      fref.assign( path, value )
   }

   // substitutes an access path to become an assignment pedigree
   def substitute( access: Path, seminalPath: Path ) : Path = {
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
      val sh = seminalPath.head 
      val off = access.indexOf( sh )
      val accessLen = access.length
      // XXX THIS IS WRONG - WE JUST KEEP IT HERE TILL THE REAL
      // SOLUTION IS IMPLEMENTED ; THIS WORKS ONLY UP TO ONE
      // LEFT OF MELDING XXX
      if( off + 2 == accessLen ) {
         if( off == 0 ) access else access.drop( off )
      } else {
         val res = sh +: seminalPath( 1 ) +: access.drop( off + 2 )
         println( "RES = " + res )
         res
      }

//      while(
//
//      if( (off + 2) == seminalPath.length == seminalPath( 1 ).level ) { //
//         if( off == 0 ) access else access.drop( off )
//      } else { // access = <v0, v0, v2, v2> ; s = <v0, v1> --> <v0, v1, v2, v2>
//
//      }

      // access = <v0, v0, v2, v2> ; s = <v0, v1, v2, v2>
   }

//   // avec lazy access
//   def set[ V ]( fval: FVal[ V ], value: V, seminalPath: Path ) {
////      val pedigree = substitute( currentAccess, seminalVersion )
////      println( "pedigree = " + pedigree )
//      fval.assign( substitute( writeAccess, seminalPath ), value )
//   }

   def read[ T ]( v: VersionPath )( thunk: => T ) = {
      val oldV = readPathVar
      try {
         readPathVar = v
         thunk
      } finally {
         readPathVar = oldV
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
      readPathVar    = version
   }

   def makeWrite( version: VersionPath ) {
      currentPathVar = version
   }

   def makeRead( version: VersionPath ) {
      readPathVar = version
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