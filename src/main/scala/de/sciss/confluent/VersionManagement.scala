///*
// *  VersionManagement
// *  (TemporalObjects)
// *
// *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
// *
// *	 This software is free software; you can redistribute it and/or
// *	 modify it under the terms of the GNU General Public License
// *	 as published by the Free Software Foundation; either
// *	 version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	 This software is distributed in the hope that it will be useful,
// *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	 General Public License for more details.
// *
// *	 You should have received a copy of the GNU General Public
// *	 License (gpl.txt) along with this software; if not, write to the Free Software
// *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *	 For further information, please contact Hanns Holger Rutz at
// *	 contact@sciss.de
// *
// *
// *  Changelog:
// */
//
//package de.sciss {
//import confluent.{ FatValue => FVal, _ }
//package confluent {
//
///**
// *    @version 0.13, 11-Apr-10
// */
//object VersionManagement {
//   private var currentPathVar = VersionPath.init
//   private var readPathVar = VersionPath.init
//
//   def currentVersion: VersionPath = currentPathVar
//   def readVersion: VersionPath = readPathVar
//   def writeVersion: VersionPath = currentPathVar
//   def readAccess:  Path = readPathVar.path
//   def writeAccess: Path = currentPathVar.path
//   def seminalPath: Path = currentPathVar.tail
//
//   def get[ V ]( fval: FVal[ V ], path: Path ) = {
//      fval.access( path ).get
//   }
//
////   def get[ V ]( fref: FRef[ V ], readPath: Path, writePath: Path ) : V = {
////      fref.access( readPath ).map {
////         case nid: NodeID[ _ ] => nid.substitute( readPath, writePath ).asInstanceOf[ V ]
////         case x => x
////      } get
////   }
//
//   def getO[ V ]( fval: FVal[ V ], path: Path ) = {
//      fval.access( path )
//   }
//
////   def getO[ V ]( fref: FRef[ V ], readPath: Path, writePath: Path ) : Option[ V ] = {
////      fref.access( readPath ).map {
////         case nid: NodeID[ _ ] => nid.substitute( readPath, writePath ).asInstanceOf[ V ]
////         case v => v
////      }
////   }
//
//   def resolve[ V ]( readPath: Path, writePath: Path, v: V ) : V = v match {
//      case nid: NodeID[ _ ] => nid.substitute( readPath, writePath ).asInstanceOf[ V ]
//      case _ => v
//   }
//
//   def set[ V ]( fval: FVal[ V ], path: Path, value: V ) : FVal[ V ] = {
//      fval.assign( path, value )
//   }
//
////   def set[ V ]( fref: FRef[ V ], path: Path, value: V ) {
////      fref.assign( path, value )
////   }
//
//   def read[ T ]( v: VersionPath )( thunk: => T ) = {
//      val oldV = readPathVar
//      try {
//         readPathVar = v
//         thunk
//      } finally {
//         readPathVar = oldV
//      }
//   }
//
//   // ---- navigation ----
//
//   /**
//    *    Creates a new (non-melded) node
//    *    branching off from the current version.
//    *    It does so by adding the new node
//    *    _after_ the current node
//    *    (i.e. becoming the new children's tail)
//    *    in the pre-order list,
//    *    and _before_ the current node in
//    *    the post-order list
//    */
//   def versionStep : VersionPath = {
//      currentPathVar = currentPathVar.newBranch
//      currentPathVar
//   }
//
//   /**
//    *    Inserts a retroactive node
//    *    before the current version.
//    *    It does so by adding the new
//    *    node _before_ the current node
//    *    in the pre-order list, and
//    *    _after_ the current node in
//    *    the post-order list. Makes the
//    *    retroactive node the current node.
//    *
//    *    @return  the new retroactive node
//    */
//   def prependRetro : VersionPath = {
//      currentPathVar = currentPathVar.newRetroParent
//      currentPathVar
//   }
//
//   /**
//    *    Inserts a retroactive node
//    *    after the current version.
//    *    It does so by adding the new
//    *    node _right after_ the current node
//    *    (i.e. becoming the new children's head)
//    *    in the pre-order list,
//    *    and _before_ the current node in
//    *    the post-order list
//    *
//    *    @return  the new retroactive node
//    */
//   def appendRetro : VersionPath = {
//      currentPathVar = currentPathVar.newRetroChild
//      currentPathVar
//   }
//
//   def use( version: VersionPath ) {
//      currentPathVar = version
//      readPathVar    = version
//   }
//
//   def makeWrite( version: VersionPath ) {
//      currentPathVar = version
//   }
//
//   def makeRead( version: VersionPath ) {
//      readPathVar = version
//   }
//
//   // ---- transactions DSL ----
//
//   def t[ T ]( thunk: => T ) : T = {
//      val current = currentVersion
//      val write   = current.newBranch
//      makeRead( current )
//      makeWrite( write )
//      try {
//         thunk
//      } finally {
//         write.use
//      }
//   }
//
//   def retroc[ T ]( thunk: => T ) : T = {
//      val current = currentVersion
//      val write   = current.newRetroChild
//      makeRead( current )
//      makeWrite( write )
//      try {
//         thunk
//      } finally {
//         write.use
//      }
//   }
//
//   def multi : Multiplicity = {
//      val m = currentVersion.newMultiBranch
//      m.neutralVersion.use
//      m
//   }
//
//   def meld[ T ]( thunk: => T ) : Meld[ T ] = {
//      val current    = currentVersion
//      val write      = current.newBranch
//      makeRead( current )
//      makeWrite( write )
//      try {
//         new Meld( thunk, write )
//      } finally {
//         current.use
//      }
//   }
//}
//
//}}