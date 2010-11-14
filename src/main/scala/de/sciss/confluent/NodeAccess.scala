///*
// *  NodeAccess.scala
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
//package de.sciss.confluent
//
//import VersionManagement._
//
///**
// *    @version 0.11, 11-Apr-10
// */
//trait NodeAccess[ T ] {
//   def access( readPath: Path, writePath: Path ) : T
//}
//
//object NodeID {
//   def substitute( storedPath: Path, accessPath: Path ) : Path = {
//      val common  = storedPath( storedPath.length - 2 )
//      val pd      = accessPath.dropWhile( _ != common )
//      storedPath.dropRight( 2 ) ++ pd
//   }
//}
//
//trait NodeID[ T ] {
//   protected def readPath: Path
//   protected def nodeAccess: NodeAccess[ T ]
//
//   def substitute( readAccess: Path, writeAccess: Path ): T = {
//      val p    = readPath
//      val rpn  = NodeID.substitute( p, readAccess )
//      val wpn  = NodeID.substitute( p, writeAccess )
//      nodeAccess.access( rpn, wpn )
//   }
//}
//
//trait NodeProxy[ T ] {
//   protected def readPath: Path
//   protected def ref: FatValue[ T ]
//
//   @inline protected def access: T =
//      get( ref, NodeID.substitute( readPath, readAccess ))
//}
//
//case class Handle[ T ]( protected val nodeAccess: NodeAccess[ T ], seminalPath: Path )
//extends NodeID[ T ] {
//   protected def readPath = seminalPath
//}