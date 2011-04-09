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

trait Projection[ A ] {
//   def isApplicable( implicit a: A ) : Boolean
}

object Cursor {
   sealed trait Update
   case object Moved extends Update
}
trait Cursor[ A ] extends Projection[ A ] with Model[ ECtx, Cursor.Update ] {
   def dispose( implicit C: ECtx ) : Unit
}

/**
 * A projection onto the ephemeral, basic transactional, level
 */
trait EProjection[ A ] extends Projection[ A ] {
   def t[ R ]( fun: A => R ) : R
}

/**
 * A projection onto the ephemeral along with cursor (model) functionality.
 */
trait ECursor[ A ] extends EProjection[ A ] with Cursor[ A ]

object Projector {
   sealed trait Update[ A, +Csr <: Cursor[ A ]]
   case class CursorAdded[ A, Csr <: Cursor[ A ]]( cursor: Csr ) extends Update[ A, Csr ]
   case class CursorRemoved[ A, Csr <: Cursor[ A ]]( cursor: Csr ) extends Update[ A, Csr ]
}

/**
 * A projector manages cursors
 */
trait Projector[ A, +Csr <: Cursor[ A ]] /* extends Model[ ECtx, Projector.Update[ A, Csr ]] */ {
   def cursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

/**
 * A K projector manages cursors, and can create them from a k-time access path
 */
trait KProjector[ A, +Proj, +Csr <: Cursor[ A ]] extends Projector[ A, Csr ] {
   def cursorIn( v: Path )( implicit c: ECtx ) : Csr
//   def projectIn( v: VersionPath ) : Proj
//   def kCursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

trait KProjection[ A ] extends Projection[ A ] {
   def path( implicit c: CtxLike ) : Path // VersionPath
}

/**
 * A KE projector is a K projector that projects onto the ephemeral level
 */
trait KEProjector[ A ]
extends KProjector[ A, EProjection[ A ] with KProjection[ A ], ECursor[ A ] with KProjection[ A ]] {
   def in[ R ]( v: Path )( fun: A => R ) : R
//   def range[ T ]( vr: V[ T ], interval: (VersionPath, VersionPath) )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ]
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
