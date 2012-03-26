///*
// *  VersionInfo.scala
// *  (TemporalObjects)
// *
// *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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
// *  You should have received a copy of the GNU General Public
// *  License (gpl.txt) along with this software; if not, write to the Free Software
// *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
// *
// *
// *	 For further information, please contact Hanns Holger Rutz at
// *	 contact@sciss.de
// */
//
//package de.sciss.confluent
//
//object VersionInfo {
//   def apply( id: Int, rid: Int, timeStamp: Long = System.currentTimeMillis(), comment: Option[ String ] = None ) : VersionInfo =
//      new Impl( id )( rid, timeStamp, comment )
//
//   private final case class Impl( id: Int )( val rid: Int, val timeStamp: Long, val comment: Option[ String ])
//   extends VersionInfo
//}
//trait VersionInfo {
//   /**
//    * Monotonically increase version identifier
//    */
//   def id: Int
//
//   /**
//    * Randomized identifier, used for certain mappings
//    */
//   def rid: Int
//
//   /**
//    * Stamp of the logical time at which the transaction was issued.
//    */
//   def timeStamp: Long
//
//   /**
//    * Option comment for the transaction, for example action performed in a GUI.
//    */
//   def comment: Option[ String ]
//}