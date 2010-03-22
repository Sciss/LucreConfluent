/**
 *  Version
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

package de.sciss.confluent

import collection.immutable.{ Queue, Seq => ISeq }

/**
 *    Note: this is a sub-_tree_,
 *    not the whole DAG
 */
trait VersionTree {
   val level: Int
   val order: TotalOrder[ Version ]
}

trait Version {
   val id:     Int
   val vertex: VersionVertex
   def level:  Int
   val tree:   VersionTree
}

object Version {
   private var idValCnt = 0

   val init: Version = newFrom()

//   val init: Version = {
//      val tree = new VersionTreeImpl( 0 )
//      val idVal = idValCnt; idValCnt += 1
//      new VersionImpl( idVal, tree )
//   }

   def newFrom( vs: Version* ) : Version = {
      // the new version's level is the maximum of the levels of
      // the ancestor versions, unless there is more than one
      // ancestor with that maximum level in which case that
      // level is incremented by 1.
      // note: my interpretation of p. 21 is that for the
      // second case, each time a new tree is created, although
      // we might then have several trees with the same level.
      // this is indicated by figure 5 (p. 22)
      val (level, predO) = vs.foldLeft[ Tuple2[ Int, Option[ Version ]]]( (0, None) )( (tup, v) => {
         val (level, predO) = tup
         predO.map( pred => {
            if( v.level == level ) {
               (level + 1, None)
            } else if( v.level > level ) {
               (v.level, Some( v ))
            } else {
               tup
            }
         }) getOrElse (v.level, Some( v ))
      })

      val idVal = idValCnt; idValCnt += 1
      val (tree, pred) = predO.map( pred => {
         pred.tree -> pred.vertex
      }) getOrElse {
         val tree = new VersionTreeImpl( level )
         tree -> tree.order.Base
      }
      new VersionImpl( idVal, tree, pred )
   }

   private class VersionImpl( val id: Int, val tree: VersionTree, pred: TotalOrder.Record )
   extends Version {
      val vertex = tree.order.insert( pred, this )
      def level: Int = tree.level

      override def toString = "v" + id
   }

   private class VersionTreeImpl( val level: Int )
   extends VersionTree {
      val order = new TotalOrder[ Version ]
   }

   object AncestorOrdering extends Ordering[ Version ] {
      def compare( x: Version, y: Version ) = {
         require( x.tree == y.tree )
         x.tree.order.compare( x.vertex, y.vertex )
      }
   }

   object IDOrdering extends Ordering[ Version ] {
      def compare( x: Version, y: Version ) = x.id - y.id
   }
}

trait VersionPath {
   val version: Version
   def path: CompressedPath

   def newBranch : VersionPath

   // XXX might need to be vps: VersionPath* ???
//   def newBranchWith( vs: Version* ) : VersionPath
}

object VersionPath {
   val init: VersionPath = {
      val vinit = Version.init 
      new VersionPathImpl( vinit, Queue( vinit, vinit ))
   }

   private case class VersionPathImpl( version: Version, path: CompressedPath )
   extends VersionPath {
      def newBranch : VersionPath = {
         val newVersion = Version.newFrom( version )
         val newPath = // if( newVersion.level == version.level ) {
            path.dropRight( 1 ) :+ newVersion
//       } else {
//          path :+ newVersion.id :+ newVersion.id
//       }
         VersionPathImpl( newVersion, newPath )
      }

      override def toString = path.mkString( "<", ", ", ">" )
   }
}
