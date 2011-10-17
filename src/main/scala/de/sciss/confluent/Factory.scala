/*
 *  Factory.scala
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

import impl._

object Factory {
//   def proc[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ]]( name: String )( implicit sys: System[ C, V ], c: C ) : Proc[ C, V ] =
//      ProcImpl[ C, V ]( name )
//
//   def group[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ]]( name: String )( implicit sys: System[ C, V ], c: C ) : ProcGroup[ C, V ] =
//      ProcGroupImpl[ C, V ]( name )

//   private val hsf = new HashedStoreFactory[ Version ]
//   private val hsf = HashedTxnStore.factory[ Version ]( HashedTxnStore.cache )

//   def esystem[ W[ _ <: ECtx ] <: Access[ ECtx, Unit, W ]]( init: W[ _ ])( implicit m: OptManifest[ W[ _ ]]) : ESystem[ W ] =
//      ESystemImpl[ W ]( init )

   def ksystem[ A <: Node[ KCtx, A ]]( ap: AccessProvider[ KCtx, A ]) : KSystem[ A ] = KSystemImpl[ A ]( ap ) // ( hsf )

//   def psystem : PSystem = error( "NOT YET IMPLEMENTED" ) // PSystemImpl()
//   def bsystem : BSystem = error( "NOT YET IMPLEMENTED" ) // BSystemImpl()
}