/*
 *  System.scala
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
import Double.{PositiveInfinity => dinf}
import reflect.OptManifest

trait System[ P,  // access path type
              C <: Ct,  // context
              A // access
//              V[ ~ ] <: Vr[ C, ~ ], // variable to immutable value
//              RV[ ~[ _ <: C ] <: Access[ C, A, ~ ]] <: RVr[ A, C, ~ ]
] extends RefFactory[ A ] // variable to mutable (reference) value
{
   def t[ R ]( fun: ECtx => R ) : R // any system can initiate an ephemeral transaction
//   def v[ T ]( init: T )( implicit m: OptManifest[ T ], c: C ) : V[ T ]
//   def refVar[ C1 <: C, T[ _ <: C ] <: Access[ C, A, T ]]( init: T[ C1 ])( implicit m: OptManifest[ T[ _ ]], c: C ) : RV[ T ]
//   def modelVar[ T ]( init: T )( implicit m: OptManifest[ T ], c: C ) : V[ T ] with Model[ C, T ]
//   def userVar[ T ]( init: T )( user: (C, T) => Unit )( implicit m: OptManifest[ T ], c: C ) : V[ T ]

//   def refFactory( implicit a: A ) : RefFactory[ A ]

//   def storeFactory : StoreFactory[ ]

//   def newMutable( implicit access: A ) : P
   def newMutable( implicit access: A ) : A
}

object ESystem {
   type Var[ ~ ]                                            = EVar[ ECtx, ~ ]
//   type RefVar[ ~[ _ <: ECtx ] <: Access[ ECtx, Unit, ~ ]]  = ERefVar[ Unit, ECtx, ~ ]
}
trait ESystem[ A ] extends System[ Unit, ECtx, A ]
/* with Cursor[ ESystem, ECtx, ESystem.Var ] with CursorProvider[ ESystem ] */ {
//   type Var[ T ] = EVar[ Ctx, T ]
//   type Ctx = ECtx
//   def t[ R ]( fun: ECtx => R ) : R
}

///////////////////////////////////////////////////////////////////////////////

object KSystemLike {
   /* sealed */ trait Update // [ C <: Ct, Csr <: KProjection[ C ] with Cursor[ C ]]

//   case class NewBranch[ C <: Ct, Csr <: KProjection[ C ] with Cursor[ C ]]( oldPath: VersionPath, newPath: VersionPath )
   case class NewBranch( oldPath: VersionPath, newPath: VersionPath )
   extends Update // [ C, Csr ]
//   case class CursorAdded[ C <: Ct, Csr <: KProjection[ C ] with Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
//   case class CursorRemoved[ C <: Ct, Csr <: KProjection[ C ] with Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
}

trait KSystemLike[ C <: Ct, A,
                   Proj <: KProjection[ A ], Csr <: KProjection[ A ] with Cursor[ A ]]
extends System[ Path, C, A ] with Model[ ECtx, KSystemLike.Update ] {
//   def in[ R ]( v: VersionPath )( fun: C => R ) : R

   def kProjector : KProjector[ A, Proj, Csr ]
   def keProjector : KEProjector[ A ]

//   def newBranch( v: VersionPath )( implicit c: ECtx ) : VersionPath
//   def dag( implicit c: CtxLike ) : LexiTrie[ OracleMap[ VersionPath ]]
//   def dag( implicit c: CtxLike ) : Store[ Version, VersionPath ]

//   def addKCursor( implicit c: C ) : KCursor[ C, V ]
//   def removeKCursor( cursor: KCursor[ C, V ])( implicit c: C ) : Unit
//   def kcursors( implicit c: ECtx ) : ISet[ KCursor[ C, V ]]
}

object KSystem {
   type Ctx                                              = KCtx // [ _ <: VersionPath ]
   type Var[ ~ ]                                         = KVar[ Ctx, ~ ]
//   type RefVar[ ~[ _ <: Ctx ] <: Access[ Ctx, Path, ~ ]] = ERefVar[ Path, Ctx, ~ ]

   type Projection[ A ]                                  = EProjection[ Path, A ] with KProjection[ A ]
//   type Cursor                                           = ECursor[ Ctx ] with KProjection[ Ctx ]
   type Cursor[ A ] = ECursor[ Path, A ] with KProjection[ A ]
//   sealed trait Update extends KSystemLike.Update[ KCtx, Var ]
}

trait KSystem[ A ]
extends KSystemLike[ KSystem.Ctx, A, KSystem.Projection[ A ], KSystem.Cursor[ A ]] {
//   def kProjector : KEProjector[ A ]
}
// with KEProjector[ KCtx, KSystem.Var ]

///////////////////////////////////////////////////////////////////////////////

//object PSystemLike {
////   /* sealed */ trait Update[ C <: Ct, Csr <: PProjection[ C ] with Cursor[ C ]]
////
////   case class CursorAdded[ C <: Ct, Csr <: PProjection[ C ] with Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
////   case class CursorRemoved[ C <: Ct, Csr <: PProjection[ C ] with Cursor[ C ]]( cursor: Csr ) extends Update[ C, Csr ]
//}
//
//trait PSystemLike[ C <: Ct, V[ ~ ] <: PVar[ C, ~ ], Proj <: PProjection[ C ], Csr <: PProjection[ C ] with Cursor[ C ]]
//extends System[ C, V ] /* with Model[ ECtx, PSystemLike.Update[ C, Csr ]] */ {
//   def pProjector : PProjector[ C, Proj, Csr ]
//}
//
//object PSystem {
//   type Var[ ~ ]     = PVar[ PCtx, ~ ]
//   type Projection   = EProjection[ PCtx ] with PProjection[ PCtx ]
//   type Cursor       = ECursor[ PCtx ] with PProjection[ PCtx ]
//}
//
//trait PSystem extends PSystemLike[ PCtx, PSystem.Var, PSystem.Projection, PSystem.Cursor ] {
//   def pProjector : PEProjector[ PCtx, PSystem.Var ]
//}

///////////////////////////////////////////////////////////////////////////////

//object BSystem {
//   type Var[ ~ ]     = BVar[ BCtx, ~ ]
//   type KProj        = KProjection[ BCtx ]// Projection   = EProjection[ PCtx ] with PProjection[ PCtx ]
//   type PProj        = PProjection[ BCtx ]
//   type KCursor      = Cursor[ BCtx ] with KProjection[ BCtx ]
//   type PCursor      = Cursor[ BCtx ] with PProjection[ BCtx ]
////   type Cursor       = ECursor[ PCtx ] with PProjection[ PCtx ]
//
////   trait Update extends KSystemLike.Update[ BCtx, C <: Ct, Csr <: PProjection[ C ] with Cursor[ C ]]
//}
////trait BSystem
////extends /* KSystemLike[ BCtx, BSystem.Var, KSystem.Projection, KSystem.Cursor ]
////with    */ KEProjector[ BCtx, BSystem.Var ]
////with    /* PSystemLike[ BCtx, BSystem.Var, PSystem.Projection, PSystem.Cursor ]
////with    */ PEProjector[ BCtx, BSystem.Var ]
//
//trait BSystem
//extends KSystemLike[ BCtx, BSystem.Var, BSystem.KProj, BSystem.KCursor ]
//with    PSystemLike[ BCtx, BSystem.Var, BSystem.PProj, BSystem.PCursor ] {
//
//}
