/*
 *  FatField
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
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
 *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

/**
 *    @version 0.12, 28-Oct-10
 */
class FatValue[ @specialized V ] private( lexi: LexiTrie[ OracleMap[ V ]]) {
//   protected val lexi = new FatFieldMap[ V ]
//   protected val lexi = LexiTrie.empty[ OracleMap[ V ]] // ()( Version.IdOrdering )

   def assign( version: Path, value: V ) : FatValue[ V ] = {
      val idx        = version.dropRight( 1 )
      val newEntry   = (version.last -> value)
      new FatValue( lexi.map( idx, _ + newEntry, {
         // create a new oracle with new entry and
         // tree-entrance-entry (if found),
         // then insert oracle into the lexi
         access( version ).map( lastValue => {
            OracleMap( idx.last -> lastValue, newEntry )
         }) getOrElse OracleMap( newEntry )
      }))
   }

   def access( version: Path ) : Option[ V ] = {
      val (oracleO, off) = lexi.multiFindMaxPrefix( version )
      // map the not-found-offset to the last-in-oracle-index
      // ; e.g. 1 -> 1, 2 -> 1, 3 -> 3, 4 -> 3, 5 -> 5 etc.
      val idx = off - 1 + (off & 1)
      oracleO.map( _.query( version( idx ))) getOrElse None
	}

   override def toString = "FVal#" + hashCode

   def inspect {
      lexi.inspect
   }
}

object FatValue {
   def empty[ V ] = new FatValue( LexiTrie.empty[ OracleMap[ V ]])
}

///**
// * 	@version	   0.13, 22-Mar-09
// *    @todo       use Vector.last once it is properly implemented
// *    @todo       Vector.++ is inefficient i guess, should use a catenable Deque instead!
// */
//class FatValue[ V ] extends FatField[ V ] {
//	def access( version: Path ) : Option[ V ] = accessPlain( version )
//
//   override def toString = "FVal#" + hashCode
//}

//class FatRef[ V ] extends FatField[ V ] {
//   def access( version: Path ) : Option[ V ] = accessPlain( version )
//
//   override def toString = "FRef#" + hashCode
//}

///**
// *    @warning    not maintained
// */
//class FatPointer[ V ] extends FatField[ FatIdentifier[ V ]] {
//   type I = FatIdentifier[ V ]
//
//	def access( version: Path ) : Option[ I ] = {
//      val (idOption, off) = lexi.multiFindMaxPrefix( version )
//      idOption.map( _.query( version.last ).map( _.substitute( version, off ))) getOrElse None
//	}
//
//   override def toString = "FPtr#" + hashCode
//}

case class FatIdentifier[ @specialized V ]( path: Path, value: V ) {
   type I = FatIdentifier[ V ] 

	def setValue( v: V ) : I = FatIdentifier( path, v )

	def substitute( accessPath: Path, off: Int ) : I = {
	  	// ++ XXX inefficient, should use a catenable Deque instead!
      // i suspect that the code should be
      // path.dropRight( 1 ) ++ accessPath.drop( off )
	  	val sub = path.dropRight( 1 ) ++ accessPath.drop( off )
	  	FatIdentifier( sub, value )
	}

   override def toString = path.mkString( "FId( <", ",", ">, " + value + " )" )
}