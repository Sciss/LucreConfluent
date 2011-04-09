/*
 *  PackageObject.scala
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

package de.sciss

import collection.immutable.{ Vector }
import fingertree.FingerTree

/**
 *    @version 0.11, 11-Apr-10
 */
package object confluent {
//   type Path  = Vector[ Version ]
   type Path = FingerTree.IndexedSummed[ Version, Long ]

   def Path( vs: Version* ) : Path =
      FingerTree.IndexedSummed.applyWithView( vs: _* )( math.Numeric.LongIsIntegral, _.rid.toLong )

   type VersionTreeOrder = (PreOrder.Record[ Version ], PostOrder.Record[ Version ])

   type Ct                                            = CtxLike
   type Vr[ C, T ]                                    = EVar[ C, T ]
//   type RVr[ A, C, T[ _ <: C ] <: Access[ C, A, T ]]  = ERefVar[ A, C, T ]

   // http://stackoverflow.com/questions/5527684/problem-partially-applying-type-parameters/5527759
//   type Partial2U[ Up, T[ _ <: Up, _ ], Second ] = { type Apply[ First <: Up ] = T[ First, Second ]}
}