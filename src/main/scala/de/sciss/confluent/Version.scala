/*
 *  Version
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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

import collection.immutable.{IntMap, Vector}

/**
 *    Note: this is a sub-_tree_,
 *    not the whole DAG
 */
trait VersionTree {
   def level: Int
   def preOrder:  PreOrder[ Version ]  // XXX atomic
   def postOrder: PostOrder[ Version ] // XXX atomic

   def insertRoot( version: Version ) : VersionTreeOrder
   def insertChild( parent: Version )( child: Version ) : VersionTreeOrder
   def insertRetroParent( child: Version )( parent: Version ) : VersionTreeOrder
   def insertRetroChild( parent: Version )( child: Version ) : VersionTreeOrder

   def inspect {
      println( "--PRE--")
      preOrder.inspect
      println( "--POST--")
      postOrder.inspect
   }
}

trait Version {
   def id:           Int
   def rid:          Int               // randomized ID
//   def vertex:       VersionVertex
   def tree:         VersionTree       // might be able to get rid of this!
   def preRec: PreOrder.Record[ Version ]
   def postRec: PostOrder.Record[ Version ]

   def level: Int = tree.level
   
   // ---- multiplicities support ----
   def appendLevel:  Int
   def fallBack:     Version
}

//case class VersionVertex( preRec: PreOrder.Record[ Version ], postRec: PostOrder.Record[ Version ])

object Version {
   private val idSync = new AnyRef
   private var idValCnt = 0
   private val idRnd = new util.Random()
//   private val idRndSet = IntMap.empty[ Unit ]

   val init: Version = {
      val tree = new VersionTreeImpl( 0 )
      new VersionImpl( tree, tree.insertRoot )
   }

   def newFrom( v: Version, vs: Version* ) : Version = {
      val (tree, insFun) = prepareNewFrom( v, vs: _ * )
      new VersionImpl( tree, insFun )
   }

   def newMultiFrom( v: Version, vs: Version* ) : Version = {
      val (tree, insFun) = prepareNewFrom( v, vs: _ * )
      new MultiNeutralVersionImpl( tree, insFun )
   }

   def newMultiVariant( v: Version ) : Version = {
//      require( v.isMulti )
      val tree = v.tree
      new MultiVariantVersionImpl( v, tree, tree.insertChild( v ))
   }

   private def prepareNewFrom( v: Version, vs: Version* ) : Tuple2[ VersionTree, (Version) => VersionTreeOrder ] = {

      // the new version's level is the maximum of the levels of
      // the ancestor versions, unless there is more than one
      // ancestor with that maximum level in which case that
      // level is incremented by 1.
      // note: my interpretation of p. 21 is that for the
      // second case, each time a new tree is created, although
      // we might then have several trees with the same level.
      // this is indicated by figure 5 (p. 22)
      val level = vs.foldLeft[ Int ]( v.appendLevel )( (level, vi) => {
         if( vi.appendLevel == level ) level + 1 else math.max( vi.appendLevel, level )
      })

      if( level == v.level ) {
         val tree = v.tree
         (tree, tree.insertChild( v ))
      } else {
         val tree = new VersionTreeImpl( level )
         (tree, tree.insertRoot)
      }
   }

   def newRetroParent( child: Version ) : Version = {
      val tree = child.tree
      new VersionImpl( tree, tree.insertRetroParent( child ))
   }

   def newRetroChild( parent: Version ) : Version = {
      val tree = parent.tree
      new VersionImpl( tree, tree.insertRetroChild( parent ))
   }

   private def nextID: (Int, Int) = idSync.synchronized {
      val id         = idValCnt
      idValCnt      += 1
      var rid: Int   = 0
//      var failed    = false
//      do {
         rid  = idRnd.nextInt( 0x7FFFFFFF )
         // XXX check unique sum condition
         // failed = ...
//      } while( failed )
      (id, rid)
   }

   private abstract class AbstractVersionImpl( val tree: VersionTree, insertionFun: (Version) => VersionTreeOrder )
   extends Version {
      val (id: Int, rid: Int) = nextID
      val (preRec, postRec)   = insertionFun( this )

      override def toString = "v" + id
   }

   private class VersionImpl( t: VersionTree, insFun: (Version) => VersionTreeOrder )
   extends AbstractVersionImpl( t, insFun ) {
      def appendLevel = level
      def fallBack = this
   }

   private class MultiNeutralVersionImpl( t: VersionTree, insFun: (Version) => VersionTreeOrder )
   extends AbstractVersionImpl( t, insFun ) {
      def appendLevel = level + 1
      def fallBack = this
   }

   private class MultiVariantVersionImpl( n: Version, t: VersionTree, insFun: (Version) => VersionTreeOrder )
   extends AbstractVersionImpl( t, insFun ) {
      def appendLevel = level + 1
      def fallBack = n
   }

   private class VersionTreeImpl( val level: Int )
   extends VersionTree {
      val preOrder   = new PreOrder[ Version ]
      val postOrder  = new PostOrder[ Version ]

      def insertRoot( version: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRoot( version )
         val postRec = postOrder.insertRoot( version )
         (preRec, postRec)
      }

      def insertChild( parent: Version )( child: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertChild( parent.preRec, child )
         val postRec = postOrder.insertChild( parent.postRec, child )
         (preRec, postRec)
      }

      def insertRetroParent( child: Version )( parent: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRetroParent( child.preRec, parent )
         val postRec = postOrder.insertRetroParent( child.postRec, parent )
         (preRec, postRec)
      }

      def insertRetroChild( parent: Version )( child: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRetroChild( parent.preRec, child )
         val postRec = postOrder.insertRetroChild( parent.postRec, child )
         (preRec, postRec)
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
   def path: Path

   def newBranch : VersionPath
   def meldWith( v: Version ) : VersionPath
   def newRetroParent : VersionPath
   def newRetroChild : VersionPath
   def newMultiBranch : Multiplicity
   def tail: Path = path.takeRight( 2 )

//   def use: VersionPath = { VersionManagement.use( this ); this }
//
//   def read[ T ]( thunk: => T ) =
//      VersionManagement.read( this )( thunk )

//   protected[ confluent ] def useVariant( nv: Version, vv: Version, vvs: Set[ Version ]) : VersionPath
   protected[ confluent ] def updateVersion( idx: Int, v: Version ) : VersionPath
}

trait Multiplicity {
   def lastVariant : Version
   def currentVariant : Version
//   def useVariant( v: Version ) : Multiplicity
//   def useNeutral : Multiplicity
//   def variant[ T ]( thunk: => T ) : T
   def neutralVersion: VersionPath
}

object VersionPath {
   val init: VersionPath = {
      val vinit = Version.init 
      new VersionPathImpl( vinit, Path( vinit, vinit ))
   }

   def wrap( path: Path ) : VersionPath = VersionPathImpl( path.last, path )

   private case class VersionPathImpl( version: Version, path: Path )
   extends VersionPath {
      def newBranch : VersionPath =
         newTail( Version.newFrom( version ))

      def meldWith( v: Version ) : VersionPath = {
         newTail( Version.newFrom( version, v ))
      }
            
      def newRetroParent : VersionPath =
         newTail( Version.newRetroParent( version ))

      def newRetroChild : VersionPath =
         newTail( Version.newRetroChild( version ))

      def newMultiBranch : Multiplicity = new MultiplicityImpl( this )

      protected def newTail( tailVersion: Version ) : VersionPath = {
         val newPath = if( tailVersion.level == version.level ) {
            path.dropRight( 1 ) :+ tailVersion
         } else {
            path :+ tailVersion :+ tailVersion
         }
         VersionPathImpl( tailVersion, newPath )
      }

//      protected[ confluent ] def useVariant( nv: Version, vv: Version, vvs: Set[ Version ]) : VersionPath = {
//         // XXX scala 2.8 beta 1 : indexOf is broken for last element!!!
////         val idx = path.indexOf( nv )
//         val psz = path.size
//         val idx = if( path( psz - 1 ) == nv ) psz - 1 else path.indexOf( nv )   // XXX scala 2.8 beta 1 : indexOf is broken for last element!!!
//         if( idx == -1 ) return this // error( "Invalid path" )
//         val idx2 = (idx + 2) & ~1 // variant subtree start
//         val newPath = path.patch( idx2,
//            if( vv != nv ) List( vv, vv ) else Nil,
//            if( (psz > idx2) && vvs.contains( path( idx2 ))) 2 else 0 )
//         val newVersion = newPath( newPath.size - 1 ) // if( i != path.size - 1 ) version else v
//         VersionPathImpl( newVersion, newPath )
//      }

      protected[ confluent ] def updateVersion( idx: Int, v: Version ) : VersionPath = {
         val newPath = path.updated( idx, v )
         VersionPathImpl( newPath( newPath.size - 1 ), newPath )
      }

//      override def toString = path.mkString( "<", ", ", ">" )
   }

   // [FIXED] TO-DO : parent should not be an argument, as
   // it might change with a retroc command. instead
   // we should introduce edges and query the parent from the tree.
   // --> FIX : parent is only used to create the neutral path. so no problems with later
   // changes of parent
   private class MultiplicityImpl( parent: VersionPath ) extends Multiplicity {
      private val neutralVersionPath = {
         val tailVersion = Version.newMultiFrom( parent.version )
         val newPath = if( tailVersion.level == parent.version.level ) {
            parent.path.dropRight( 1 ) :+ tailVersion
         } else {
            parent.path :+ tailVersion :+ tailVersion
         }
         new VersionPathImpl( tailVersion, newPath )
      }

      private var currentVariantVar = neutralVersionPath.version  // XXX atomic
      private var lastVariantVar = neutralVersionPath.version     // XXX atomic
      private var variantVersions = Set.empty[ Version         ]  // XXX atomic

      def currentVariant = currentVariantVar
      def lastVariant = lastVariantVar
      def neutralVersion = neutralVersionPath 

//      def useVariant( v: Version ) : Multiplicity = {
//         import VersionManagement._
//
//         val npz = neutralVersionPath.path.size
//         val vp = // if( (npz & 1) == 0 ) {
//            currentVersion.updateVersion( npz - 1, v )
////         } else {
////            currentVersion.updateVersion( npz - 1, v )
////         }
//         currentVariantVar = v
//         vp.use
//         this
//      }
//
//      def useNeutral : Multiplicity = useVariant( neutralVersionPath.version )

      private def createVariantPath : VersionPath = {
         val tailVersion = Version.newMultiVariant( neutralVersionPath.version )
         val newPath = neutralVersionPath.path.dropRight( 1 ) :+ tailVersion
         new VersionPathImpl( tailVersion, newPath )
      }

//      def variant[ T ]( thunk: => T ) : T = {
//         import VersionManagement._
//
//         val write = createVariantPath
//         variantVersions += write.version
//         makeRead( neutralVersionPath )
//         makeWrite( write )
//         try {
//            thunk
//         } finally {
//            lastVariantVar = write.version
//            neutralVersionPath.use
//         }
//      }
   }
}
