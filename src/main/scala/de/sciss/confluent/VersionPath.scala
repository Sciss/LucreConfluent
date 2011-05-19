/*
 *  VersionPath.scala
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

import concurrent.stm.InTxn

trait VersionPath {
   type Child <: VersionPath
   val version: Version
   def path: Path
   def seminalPath : Path = path.takeRight( 1 )

//   def newBranch( implicit txn: InTxn ) : Child // VersionPath

//   def meldWith( v: Version ) : VersionPath
//   def newRetroParent : VersionPath
//   def newRetroChild : VersionPath
//   def newMultiBranch : Multiplicity

//   def tail: Path = path.takeRight( 2 )

//   def use: VersionPath = { VersionManagement.use( this ); this }
//
//   def read[ T ]( thunk: => T ) =
//      VersionManagement.read( this )( thunk )

//   protected[ confluent ] def useVariant( nv: Version, vv: Version, vvs: Set[ Version ]) : VersionPath

//   protected[ confluent ] def updateVersion( idx: Int, v: Version ) : VersionPath
}

object VersionPath {
   val init: VersionPath = {
      val vinit = Version.init
//      new VersionPathImpl( vinit, Path( vinit, vinit ))
      new VersionPathImpl( vinit, Path( vinit )) // XXX full path
   }

   def wrap( path: Path ) : VersionPath = VersionPathImpl( path.last, path )

   private case class VersionPathImpl( version: Version, path: Path )
   extends VersionPath {
      type Child = VersionPathImpl

//      def newBranch( implicit txn: InTxn ) : Child =
////         newTail( Version.newFrom( version ))
//         newTail( Version.newFrom( this ))

//      def meldWith( v: Version ) : VersionPath = {
//         newTail( Version.newFrom( version, v ))
//      }

//      def newRetroParent : VersionPath =
//         newTail( Version.newRetroParent( version ))
//
//      def newRetroChild : VersionPath =
//         newTail( Version.newRetroChild( version ))
//
//      def newMultiBranch : Multiplicity = new MultiplicityImpl( this )

      // simply full path for now XXX
      protected def newTail( tailVersion: Version ) : Child = VersionPathImpl( tailVersion, path :+ tailVersion )

//      protected def newTail( tailVersion: Version ) : VersionPath = {
//         val newPath = if( tailVersion.level == version.level ) {
//            path.dropRight( 1 ) :+ tailVersion
//         } else {
//            path :+ tailVersion :+ tailVersion
//         }
//         VersionPathImpl( tailVersion, newPath )
//      }

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

//      protected[ confluent ] def updateVersion( idx: Int, v: Version ) : VersionPath = {
//         val newPath = path.updated( idx, v )
//         VersionPathImpl( newPath( newPath.size - 1 ), newPath )
//      }

//      override def toString = path.mkString( "<", ", ", ">" )
   }

//   // [FIXED] TO-DO : parent should not be an argument, as
//   // it might change with a retroc command. instead
//   // we should introduce edges and query the parent from the tree.
//   // --> FIX : parent is only used to create the neutral path. so no problems with later
//   // changes of parent
//   private class MultiplicityImpl( parent: VersionPath ) extends Multiplicity {
//      private val neutralVersionPath = {
//         val tailVersion = Version.newMultiFrom( parent.version )
//         val newPath = if( tailVersion.level == parent.version.level ) {
//            parent.path.dropRight( 1 ) :+ tailVersion
//         } else {
//            parent.path :+ tailVersion :+ tailVersion
//         }
//         new VersionPathImpl( tailVersion, newPath )
//      }
//
//      private var currentVariantVar = neutralVersionPath.version  // XXX atomic
//      private var lastVariantVar = neutralVersionPath.version     // XXX atomic
//      private var variantVersions = Set.empty[ Version         ]  // XXX atomic
//
//      def currentVariant = currentVariantVar
//      def lastVariant = lastVariantVar
//      def neutralVersion = neutralVersionPath
//
////      def useVariant( v: Version ) : Multiplicity = {
////         import VersionManagement._
////
////         val npz = neutralVersionPath.path.size
////         val vp = // if( (npz & 1) == 0 ) {
////            currentVersion.updateVersion( npz - 1, v )
//////         } else {
//////            currentVersion.updateVersion( npz - 1, v )
//////         }
////         currentVariantVar = v
////         vp.use
////         this
////      }
////
////      def useNeutral : Multiplicity = useVariant( neutralVersionPath.version )
//
//      private def createVariantPath : VersionPath = {
//         val tailVersion = Version.newMultiVariant( neutralVersionPath.version )
//         val newPath = neutralVersionPath.path.dropRight( 1 ) :+ tailVersion
//         new VersionPathImpl( tailVersion, newPath )
//      }
//
////      def variant[ T ]( thunk: => T ) : T = {
////         import VersionManagement._
////
////         val write = createVariantPath
////         variantVersions += write.version
////         makeRead( neutralVersionPath )
////         makeWrite( write )
////         try {
////            thunk
////         } finally {
////            lastVariantVar = write.version
////            neutralVersionPath.use
////         }
////      }
//   }
}