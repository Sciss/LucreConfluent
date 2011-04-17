/*
 *  Version.scala
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
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

import collection.immutable.{IntMap, Set => ISet}
import concurrent.stm.{InTxn, Ref => STMRef}

trait Version {
   def id: Int               // we don't really need this anymore, but it might be nice for inspection
   def rid: Int              // randomized ID
   def tree: VersionTree     // might be able to get rid of this!
   def preRec: PreOrder.Record[ Version ]
   def postRec: PostOrder.Record[ Version ]

   def level: Int = tree.level
   
   // ---- multiplicities support ----
   def appendLevel:  Int
   def fallBack:     Version
}

//case class VersionVertex( preRec: PreOrder.Record[ Version ], postRec: PostOrder.Record[ Version ])

object Version {
//   private val idSync = new AnyRef
//   private var idValCnt = 0
////   private val idRnd = new util.Random()
////   private val idRndSet = IntMap.empty[ Unit ]
//
//   private val idsTaken    = MSet( 0 ) // .empty[ Int ]
//   private val sumsTaken   = MSet.empty[ Long ]
   val FREEZE_SEED = true

   private val idRnd       = {
      if( FREEZE_SEED ) new util.Random( -1 ) else new util.Random()
   }

   private case class IDGen( cnt: Int, idsTaken: ISet[ Int ], sumsTaken: ISet[ Long ])
   private val idRef = STMRef( IDGen( 1 /* 0 */, ISet( 0 ), ISet.empty ))

   val init: Version = {
      val tree       = VersionTree.empty( 0 )
//      val (id, rid)  = nextID( 0L )
      new VersionImpl( 0, 0, tree, tree.insertRoot )
   }

//   def testWrapXXX( suffix: Int )( implicit txn: InTxn ) : Version = {
//      val pv      = init // parent.version
//      val tree    = pv.tree
//      val insFun  = tree.insertChild( pv ) _
//
//      val id = idRef.get
//      idRef.set( id.copy( cnt = id.cnt + 1 ))
//
////      val (id, rid) = (0, suffix) // nextID( parent.path.sum )( txn )
//      new VersionImpl( id.cnt, suffix, tree, insFun )
//   }

//   def newFrom( v: Version, vs: Version* ) : Version = {
   def newFrom( parent: VersionPath )( implicit txn: InTxn ) : Version = {
//      val (tree, insFun) = prepareNewFrom( v, vs: _ * )
      val pv      = parent.version
      val tree    = pv.tree
      val insFun  = tree.insertChild( pv ) _

      val (id, rid) = nextID( parent.path.sum :: Nil )
      new VersionImpl( id, rid, tree, insFun )
   }

   def newFrom( preSums: Traversable[ Long ])( implicit txn: InTxn ) : Version = {
      val pv      = init // parent.version
      val tree    = pv.tree
      val insFun  = tree.insertChild( pv ) _

      val (id, rid) = nextID( preSums )
      new VersionImpl( id, rid, tree, insFun )
   }

   def testWrapXXX( id: Int, rid: Int ) : Version = {
      val pv      = init
      val tree    = pv.tree
      val insFun  = tree.insertChild( pv ) _
      new VersionImpl( id, rid, tree, insFun )
   }

//   def newMultiFrom( v: Version, vs: Version* ) : Version = {
//      val (tree, insFun) = prepareNewFrom( v, vs: _ * )
//      new MultiNeutralVersionImpl( tree, insFun )
//   }
//
//   def newMultiVariant( v: Version ) : Version = {
////      require( v.isMulti )
//      val tree = v.tree
//      new MultiVariantVersionImpl( v, tree, tree.insertChild( v ))
//   }

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
         val tree = VersionTree.empty( level )
         (tree, tree.insertRoot)
      }
   }

//   def newRetroParent( child: Version ) : Version = {
//      val tree = child.tree
//      new VersionImpl( tree, tree.insertRetroParent( child ))
//   }
//
//   def newRetroChild( parent: Version ) : Version = {
//      val tree = parent.tree
//      new VersionImpl( tree, tree.insertRetroChild( parent ))
//   }

//   private def nextID: (Int, Int) = idSync.synchronized {
//      val id         = idValCnt
//      idValCnt      += 1
//      var rid: Int   = 0
////      var failed    = false
////      do {
//         rid  = idRnd.nextInt( 0x7FFFFFFF )
//         // XXX check unique sum condition
//         // failed = ...
////      } while( failed )
//      (id, rid)
//   }

//   private def nextID( from: Long )( implicit txn: InTxn ) : (Int, Int) = {
//      val IDGen( cnt, idsTaken, sumsTaken ) = idRef.get( txn )
//      while( true ) {
//         val rid = idRnd.nextInt() & 0x7FFFFFFF
//         if( !idsTaken.contains( rid )) {   // unique vertices
////            val res = s :+ rid
//            val sum = from + rid
//            if( !sumsTaken.contains( sum )) {  // unique sums
//               idRef.set( IDGen( cnt + 1, idsTaken + rid, sumsTaken + sum ))( txn )
//               return (cnt, rid)
//            }
//         }
//      }
//      error( "Never here" )
//   }

   private def nextID( preSums: Traversable[ Long ])( implicit txn: InTxn ) : (Int, Int) = {
      val IDGen( cnt, idsTaken, sumsTaken ) = idRef.get( txn )
      val view = preSums // .view
      while( true ) {
         val rid = idRnd.nextInt() & 0x7FFFFFFF
         if( !idsTaken.contains( rid )) {   // unique vertices
            val sums = preSums.map( _ + rid )
            if( sums.forall( !sumsTaken.contains( _ ))) {
               idRef.set( IDGen( cnt + 1, idsTaken + rid, sumsTaken ++ sums ))( txn )
               return (cnt, rid)
            }
         }
      }
      error( "Never here" )
   }

   private abstract class AbstractVersionImpl( val id: Int, val rid: Int, val tree: VersionTree, insertionFun: (Version) => VersionTreeOrder )
   extends Version {
//      val (id: Int, rid: Int) = nextID( parentSum )
      val (preRec, postRec)   = insertionFun( this )

      override def toString = "v" + id
   }

   private class VersionImpl( id: Int, rid: Int, t: VersionTree, insFun: (Version) => VersionTreeOrder )
   extends AbstractVersionImpl( id, rid, t, insFun ) {
      def appendLevel = level
      def fallBack = this
   }

//   private class MultiNeutralVersionImpl( t: VersionTree, insFun: (Version) => VersionTreeOrder )
//   extends AbstractVersionImpl( t, insFun ) {
//      def appendLevel = level + 1
//      def fallBack = this
//   }
//
//   private class MultiVariantVersionImpl( n: Version, t: VersionTree, insFun: (Version) => VersionTreeOrder )
//   extends AbstractVersionImpl( t, insFun ) {
//      def appendLevel = level + 1
//      def fallBack = n
//   }

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
