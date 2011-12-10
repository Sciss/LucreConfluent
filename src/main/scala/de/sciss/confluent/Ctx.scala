/*
 *  Ctx.scala
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

import concurrent.stm.InTxn
import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}

trait CtxLike[ Repr ] /* extends RefFactory[ Repr ] */ {
   /**
    * Logical system time when this context has been created.
    * Milliseconds since January 1, 1970, 00:00:00 GMT
    */
   def time: Long

   /**
    * A commit comment, or empty string.
    */
   def comment: String

   /**
    * The STM transaction corresponding to this context.
    */
   def txn: InTxn

   /**
    * Constructs an ephemeral 'downgrade' of this context.
    */
   def eph: ECtx
//   def seminal: (NodeID, Repr)
//   def newNode[ T ]( fun: (Repr with NodeFactory[ Repr ]) => T ) : T

   /**
    * Initiates a node creation process.
    */
   def newNode[ T ]( fun: NodeFactory[ Repr ] => T ) : T

   /**
    * Initiates a node re-creation process. This is used in de-serialization.
    */
   def oldNode[ T ]( id: Int )( fun: NodeFactory[ Repr ] => T ) : T

   /**
    * Serialization support.
    */
   def writeObject( out: TupleOutput ) : Unit

   /**
    * Serialization support.
    */
   def readObject( in: TupleInput ) : Repr
}

trait NodeFactory[ C ] /* extends RefFactory[ C ] */ {
   def id: NodeID
   def path: C

   def emptyVal[ T ]( implicit s: SerializerOld[ C, T ]) : Val[ C, T ]
   def emptyRef[ T <: Node[ C, T ]]( implicit s: SerializerOld[ C, T ]) : Ref[ C, T ]
}

trait ECtx extends CtxLike[ ECtx ] // { def seminal = this }

trait KCtxLike[ Repr ] extends CtxLike[ Repr ] { // [ Path ]
//   def path : VersionPath // V
//   private[confluent] def writePath : VersionPath
}

trait KCtx extends MutableOld[ Path, KCtx ] with KCtxLike[ KCtx ] {
   def addInEdge( v: Version ) : Unit
}

//trait PCtxLike extends CtxLike {
//   def period : Period
//   def interval : Interval
//   private[proc] def interval_=( i: Interval ) : Unit
//}
//
//trait PCtx extends PCtxLike
//
//trait BCtx extends KCtxLike with PCtxLike
