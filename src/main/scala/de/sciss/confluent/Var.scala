/*
 *  Var.scala
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

trait EVar[ C, T ] {
   def get( implicit c: C ) : T
   def set( v: T )( implicit c: C ) : Unit
   def transform( f: T => T )(implicit c: C ) : Unit
}

trait KVar[ C, T ] extends EVar[ C, T ]  {
   def kRange( vStart: VersionPath, vStop: VersionPath )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ]
}

//trait PVar[ C, T ] extends EVar[ C, T ]  {
//   def pRange( r: Interval )( implicit c: CtxLike ) : Traversable[ (Period, T) ]
//}
//
//trait BVar[ C, T ] extends KVar[ C, T ] with PVar[ C, T ]

//trait ERefVar[ C, T ] {
//   def get( implicit c: C ) : T
//   def set( v: T )( implicit c: C ) : Unit
//}
//
//trait Ref[ C, T ]

// trait Acc

trait Mutable[ A, Repr ] /* extends Acc */ {
//   def accessPath: V
   def path : A
   def substitute( path: A ) : Repr
}

//trait Access[ Up, V, Res[ _ <: Up ]] {
////   def accessPath: V
//   def access[ C <: Up ]( post: V ) : Res[ C ]
//}

//trait ERefVar[ V, C, T[ _ <: C ] <: Access[ C, V, T ]] {
//   def get[ C1 <: C ]( implicit c: C1 ) : T[ C1 ]
//   def set[ C1 <: C ]( v: T[ C1 ])( implicit c: C1 ) : Unit
////   def transform[ C1 <: C ]( f: T[ C1 ] => T[ C1 ])(implicit c: C1 ) : Unit
//}

trait AccessProvider[ P, A ] {
   def init( f: RefFactory[ A ], path: P ) : A
//   def access( a: A, p: P ) : A
}

trait Ref[ A, T <: Mutable[ A, T ]] {
   def get( implicit access: A ) : T
   def set( v: T )( implicit access: A ) : Unit
//   def transform[ C1 <: C ]( f: T[ C1 ] => T[ C1 ])(implicit c: C1 ) : Unit
}

trait RefFactory[ A ] {
   def emptyRef[ T <: Mutable[ A, T ]] : Ref[ A, T ]
   def emptyVal[ T ] : Val[ A, T ]
//   def apply[ V ]( v: V ) : RefVar[ A, V ]
}

trait Val[ A, T ] {
   def get( implicit access: A ) : T
   def set( v: T )( implicit access: A ) : Unit
//   def transform[ C1 <: C ]( f: T[ C1 ] => T[ C1 ])(implicit c: C1 ) : Unit
}

//trait ValFactory[ P ] {
//   def emptyVal[ T ] : Val[ T ]
////   def apply[ V ]( v: V ) : RefVar[ A, V ]
//}