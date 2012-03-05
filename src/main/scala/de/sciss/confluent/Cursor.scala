/*
 *  Cursor.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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

trait Projection[ C ] {
//   def isApplicable( implicit ctx: C ) : Boolean
}

object Cursor {
   sealed trait Update
   case object Moved extends Update
}
trait Cursor[ C ] extends Projection[ C ] with Model[ ECtx, Cursor.Update ] {
   def dispose( implicit C: ECtx ) : Unit
}

/**
 * A projection onto the ephemeral, basic transactional, level
 */
trait EProjection[ Res, A, C ] extends Projection[ C ] {
//   def t[ R ]( fun: A => R ) : R
   def t( fun: A => Unit ) : Res
   def meld[ R ]( fun: A => R )( implicit ctx: C ) : R
}

/**
 * A projection onto the ephemeral along with cursor (model) functionality.
 */
trait ECursor[ Res, A, C ] extends EProjection[ Res, A, C ] with Cursor[ C ]

object Projector {
   sealed trait Update[ C, +Csr <: Cursor[ C ]]
   case class CursorAdded[ C, Csr <: Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
   case class CursorRemoved[ C, Csr <: Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
}

/**
 * A projector manages cursors
 */
trait Projector[ C, +Csr <: Cursor[ C ]] /* extends Model[ ECtx, Projector.Update[ A, Csr ]] */ {
//   def cursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

/**
 * A K projector manages cursors, and can create them from a k-time access path
 */
trait KProjector[ C, +Proj, +Csr <: Cursor[ C ]] extends Projector[ C, Csr ] {
   def cursorInRoot( implicit c: ECtx ) : Csr
   def cursorIn( v: Path )( implicit c: ECtx ) : Csr
   def in( v: Path ) : Proj
//   def kCursors( implicit c: CtxLike ) : Iterable[ Csr ]  // Set doesn't work because of variance...
}

trait KProjection[ C ] extends Projection[ C ] {
//   def path( implicit c: CtxLike ) : Path // VersionPath
}

/**
 * A KE projector is a K projector that projects onto the ephemeral level
 */
trait KEProjector[ A, C ]
extends KProjector[ C, EProjection[ Path, A, C ] with KProjection[ C ], ECursor[ Path, A, C ] with KProjection[ C ]] {
//   def in[ R ]( v: Path )( fun: A => R ) : R
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
