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

import collection.immutable.{ Vector }

/**
 *    Note: this is a sub-_tree_,
 *    not the whole DAG
 */
trait VersionTree {
   val level: Int
   val preOrder:  PreOrder[ Version ]
   val postOrder: PostOrder[ Version ]

   def insertRoot( version: Version ) : VersionVertex
   def insertChild( parent: Version )( child: Version ) : VersionVertex
   def insertRetroParent( child: Version )( parent: Version ) : VersionVertex
   def insertRetroChild( parent: Version )( child: Version ) : VersionVertex

   def inspect {
      println( "--PRE--")
      preOrder.inspect
      println( "--POST--")
      postOrder.inspect
   }
}

trait Version {
   val id:     Int
   val vertex: VersionVertex
   def level:  Int
   val tree:   VersionTree
}

case class VersionVertex( preRec: PreOrder.Record[ Version ], postRec: PostOrder.Record[ Version ])

object Version {
   private var idValCnt = 0

   val init: Version = {
      val tree = new VersionTreeImpl( 0 )
      new VersionImpl( tree, tree.insertRoot )
   }

//   val init: Version = {
//      val tree = new VersionTreeImpl( 0 )
//      val idVal = idValCnt; idValCnt += 1
//      new VersionImpl( idVal, tree )
//   }

   def newFrom( v: Version, vs: Version* ) : Version = {

      if( vs.nonEmpty ) println( "WARNING: melding not yet implemented!!!" )

      // the new version's level is the maximum of the levels of
      // the ancestor versions, unless there is more than one
      // ancestor with that maximum level in which case that
      // level is incremented by 1.
      // note: my interpretation of p. 21 is that for the
      // second case, each time a new tree is created, although
      // we might then have several trees with the same level.
      // this is indicated by figure 5 (p. 22)
      val allV = v :: vs.toList
      val level = allV.foldLeft[ Int ]( v.level )( (level, v) => {
         if( v.level == level ) level + 1 else math.max( v.level, level )
      })

      val tree = v.tree
      new VersionImpl( tree, tree.insertChild( v ))
   }

   def newRetroParent( child: Version ) : Version = {
      val tree = child.tree
      new VersionImpl( tree, tree.insertRetroParent( child ))
   }

   def newRetroChild( parent: Version ) : Version = {
      val tree = parent.tree
      new VersionImpl( tree, tree.insertRetroChild( parent ))
   }

   private def nextID = { val idVal = idValCnt; idValCnt += 1; idVal }

   private class VersionImpl( val tree: VersionTree, insertionFun: (Version) => VersionVertex )
   extends Version {
      val id: Int    = nextID
      val vertex     = insertionFun( this )
      def level: Int = tree.level

      override def toString = "v" + id
   }

   private class VersionTreeImpl( val level: Int )
   extends VersionTree {
      val preOrder   = new PreOrder[ Version ]
      val postOrder  = new PostOrder[ Version ]

      def insertRoot( version: Version ) : VersionVertex = {
         val preRec  = preOrder.insertRoot( version )
         val postRec = postOrder.insertRoot( version )
//println( "---> first vertex" )
//inspect
         VersionVertex( preRec, postRec )
      }

      def insertChild( parent: Version )( child: Version ) : VersionVertex = {
         val preRec  = preOrder.insertChild( parent.vertex.preRec, child )
         val postRec = postOrder.insertChild( parent.vertex.postRec, child )
//println( "---> new vertex" )
//inspect
         VersionVertex( preRec, postRec )
      }

      def insertRetroParent( child: Version )( parent: Version ) : VersionVertex = {
         val preRec  = preOrder.insertRetroParent( child.vertex.preRec, parent )
         val postRec = postOrder.insertRetroParent( child.vertex.postRec, parent )
         VersionVertex( preRec, postRec )
      }

      def insertRetroChild( parent: Version )( child: Version ) : VersionVertex = {
         val preRec  = preOrder.insertRetroChild( parent.vertex.preRec, child )
         val postRec = postOrder.insertRetroChild( parent.vertex.postRec, child )
         VersionVertex( preRec, postRec )
      }
   }

//   // not used any more
//   object AncestorOrdering extends Ordering[ Version ] {
//      def compare( x: Version, y: Version ) = {
//         require( x.tree == y.tree )
//         x.tree.preOrder.compare( x.vertex.preRec, y.vertex.preRec )
//      }
//   }

   object IdOrdering extends Ordering[ Version ] {
      def compare( x: Version, y: Version ) = x.id - y.id
   }
}

trait VersionPath {
   val version: Version
   def path: CompressedPath

   def newBranch : VersionPath
   def newRetroParent : VersionPath
   def newRetroChild : VersionPath

   // XXX might need to be vps: VersionPath* ???
//   def newBranchWith( vs: Version* ) : VersionPath

   def use[ T ]( thunk: => T ) =
      VersionManagement.use( this )( thunk )
}

object VersionPath {
   val init: VersionPath = {
      val vinit = Version.init 
      new VersionPathImpl( vinit, Vector( vinit, vinit ))
   }

   private case class VersionPathImpl( version: Version, path: CompressedPath )
   extends VersionPath {
      def newBranch : VersionPath =
         newTail( Version.newFrom( version ))
            
      def newRetroParent : VersionPath =
         newTail( Version.newRetroParent( version ))

      def newRetroChild : VersionPath =
         newTail( Version.newRetroChild( version ))

      private def newTail( tailVersion: Version ) : VersionPath = {
         val newPath = // if( newVersion.level == version.level ) {
            path.dropRight( 1 ) :+ tailVersion
//       } else {
//          path :+ newVersion.id :+ newVersion.id
//       }
         VersionPathImpl( tailVersion, newPath )
      }

      override def toString = path.mkString( "<", ", ", ">" )

//      override def equals( o: Any ) = o match {
//         case vp: VersionPath => (vp.version == version) && (vp.path == path)
//         case _ => super.equals( o )
//      }
   }
}
