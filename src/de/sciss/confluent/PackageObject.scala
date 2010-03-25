/**
 *  PackageObject
 *  (de.sciss.confluent package)
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

package de.sciss

import collection.immutable.{ Vector }

package object confluent {
//   type VersionID       = Int
//   type VersionRecord      = TotalOrder.UserRecord[ Version ]
// XXX this doesn't go well with the lexi tree
//   type CompressedPath  = ISeq[ Tuple2[ VersionID, VersionID ]]
   type CompressedPath  = Vector[ Version ]
//   type DSSTEntry[ V ]  = Tuple2[ VersionID, V ] 
//   type TotalOrder[ V ] = IntMap[ V ]
//   val TotalOrder       = IntMap    // XXX this is obviously not the DSST structure with O(1) lookup
//   val DSSTEntry        = Tuple2

//   implicit def DSSTEntryOrder[ V ] = new Ordering[ DSSTEntry[ V ]] {
//       def compare( x: DSSTEntry[ V ], y: DSSTEntry[ V ]) : Int = x._1 - y._1
//   }

//   implicit val versionPlainOrdering = new Ordering[ VersionID ] {
//      def compare( x: VersionID, y: VersionID ) = x.elem - y.elem
//   }
}