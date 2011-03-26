/*
 *  Cursor.scala
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

import collection.immutable.{Set => ISet}

trait Projection[ C <: Ct ] {
   def isApplicable( implicit c: C ) : Boolean
}

object Cursor {
   sealed trait Update
   case object Moved extends Update
}
trait Cursor[ C <: Ct ] extends Projection[ C ] with Model[ ECtx, Cursor.Update ] {
   def dispose( implicit C: ECtx ) : Unit
}

trait EProjection[ C <: Ct ] extends Projection[ C ] {
   def t[ R ]( fun: C => R ) : R
}

trait ECursor[ C <: Ct ] extends EProjection[ C ] with Cursor[ C ]

object Projector {
   sealed trait Update[ C <: Ct, +Csr <: Cursor[ C ]]
   case class CursorAdded[ C <: Ct, Csr <: Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
   case class CursorRemoved[ C <: Ct, Csr <: Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
}

trait Projector[ C <: Ct, +Csr <: Cursor[ C ]] extends Model[ ECtx, Projector.Update[ C, Csr ]] {
   def cursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

trait KProjector[ C <: Ct, +Proj, +Csr <: Cursor[ C ]] extends Projector[ C, Csr ] {
   def cursorIn( v: VersionPath )( implicit c: ECtx ) : Csr
   def projectIn( v: VersionPath ) : Proj
//   def kCursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

trait KProjection[ C <: Ct ] extends Projection[ C ] {
   def path( implicit c: CtxLike ) : VersionPath
}

trait KEProjector[ C <: Ct, V[ ~ ] <: KVar[ C, ~ ]]
extends KProjector[ C, EProjection[ C ] with KProjection[ C ], ECursor[ C ] with KProjection[ C ]] {
   def in[ R ]( v: VersionPath )( fun: C => R ) : R
   def range[ T ]( vr: V[ T ], interval: (VersionPath, VersionPath) )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ]
}

//trait PProjector[ C <: Ct, +Proj, +Csr <: Cursor[ C ]] extends Projector[ C, Csr ] {
//   def cursorAt( p: Period )( implicit c: ECtx ) : Csr
//   def projectAt( p: Period ) : Proj
////   def pCursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
//}
//
//trait PProjection[ C <: Ct ] extends Projection[ C ] {
//   def period( implicit c: CtxLike ) : Period
////   def interval( implicit c: ECtx ) : Interval
//}
//
//trait PEProjector[ C <: Ct, V[ ~ ] <: PVar[ C, ~ ]]
//extends PProjector[ C, EProjection[ C ] with PProjection[ C ], ECursor[ C ] with PProjection[ C ]] {
//   def at[ R ]( p: Period )( fun: C => R ) : R
////   def during[ R ]( ival: Interval )( fun: C => R ) : R
//   def range[ T ]( vr: V[ T ], interval: Interval )( implicit c: CtxLike ) : Traversable[ (Period, T) ]
//}
