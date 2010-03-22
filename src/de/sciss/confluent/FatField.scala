/**
 *  FatField
 *  (de.sciss.confluent package)
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

trait FatField[ V ] {
//   protected val map = new LexiTreeMap[ Version, TotalOrder[ V ]]()
   /*protected */ val lexi = new LexiTreeMap[ Version, OracleMap[ V ]]()( Version.IDOrdering )

   def inspect {
      lexi.inspect
   }
}

/**
 * 	@version	   0.13, 22-Mar-09
 */
class FatValue[ V ] extends FatField[ V ] {
	def assign( version: CompressedPath, value: V ) {
      val idx        = version.dropRight( 1 )   // XXX this might not be optimal
      val newEntry   = (version.last -> value)
      lexi.find( idx ).map( oracle => {
         // note: we update the mutable oracle, no need to call lexi.insert
//         println( "BEFORE " + oracle )
         oracle += newEntry
//         println( "AFTER " + oracle )
      }) getOrElse {
         // create a new oracle with new entry and
         // tree-entrance-entry (if found),
         // then insert oracle into the lexi
         val oracle = access( version ).map( lastValue => {
            OracleMap( idx.last -> lastValue, newEntry )
         }) getOrElse OracleMap( newEntry )
         lexi.insert( idx, oracle )
      }
	}

	def access( version: CompressedPath ) : Option[ V ] = {
//      val idx  = version.dropRight( 1 )   // XXX this might not be optimal
//      val t    = version.last
		lexi.findMaxPrefix( version ).map( _.query( version.last )) getOrElse None
//
//         // XXX here is a mistake (and thus we cannot use IntMap): we need the DSST
//         // struct to find out the most recent ancestor of t instead of get( t )!!!
//         order.get( t ) orElse (idx.lastOption.map( order.get( _ )) getOrElse None)
//      }) getOrElse None
	}

//	def get()( implicit version: Version ) : T = {
//		pa.findMaxPrefix( version.path )
//	}

//	def set( value: T )( implicit version: Version ) : Unit = {
//// LexiTreeMap doesn't allow overwrite at the moment XXX
////val version = VersionImplicits.currentInc
//		pa.insert( version.path, value )
//	}

   override def toString = "FVal#" + hashCode
}

class FatPointer[ V ] extends FatField[ FatIdentifier[ V ]] {

//	def assign( path: CompressedPath, valuePath: CompressedPath, value: T ) {
//		pa.insert( path, FatIdentifier( valuePath, value ))
//	}

//	def assignNull( path: Seq[ Int ]) {
//		pa.insert( path, null )
//	}

//	def assign( path: CompressedPath, id: FatIdentifier[ T ]) {
//		map.insert( path, id )
//	}

//	def access( version: Seq[ Int ]) : T = {
//		pa.find( version )
//	}

//	def access( version: CompressedPath ) : FatIdentifier[ T ] = {
//	  	val (idOption, off) = map.findMaxPrefix2( version )
//      idOption.map( id => {
//         id.substitute( version, off )
//      }) getOrElse {
//         FatIdentifier( version, null.asInstanceOf[ T ])
//      }
//	}

//   // EXPERIMENTAL ONE, SKIPPING UNKNOWN INTERMEDIATE HIGHER VERSIONS
//   def get2()( implicit version: Version ) : FatIdentifier[ T ] = {
//      val (id, off) = pa.findMaxPrefix3( version.path )
//        if( id == null ) return id
////	  	println( "id = " + id + "; off = " + off + "; version = " + version )
//        id.substitute( version.path, off )
//   }
//
//	def get()( implicit version: Version ) : FatIdentifier[ T ] = {
//		val (id, off) = pa.findMaxPrefix2( version.path )
//	  	if( id == null ) return id
////	  	println( "id = " + id + "; off = " + off + "; version = " + version )
//	  	id.substitute( version.path, off )
//	}
//
//	def set( id: FatIdentifier[ T ])( implicit version: Version ) : Unit = {
//// LexiTreeMap doesn't allow overwrite at the moment XXX
////val version = VersionImplicits.currentInc
//		pa.insert( version.path, id )
//	}
//
//	def set( v: T )( implicit version: Version ) : Unit = {
//		pa.insert( version.path,
//		           FatIdentifier( List( version.path.last ) /* XXX last is O(N) */, v ))
//	}

   override def toString = "FPtr#" + hashCode
}

case class FatIdentifier[ V ]( path: CompressedPath, value: V ) {
//	def append( i: Int ) : FatIdentifier[ T ] = {
//	  	FatIdentifier( path ++ List( i ), value ) // XXX inefficient
//	}

	def setValue( v: V ) : FatIdentifier[ V ] = FatIdentifier( path, v )

//	def substitute( accessPath: Seq[ Int ], off: Int ) : FatIdentifier[ T ] = {
//	  	// XXX inefficient
//	  	val sub = path ++ accessPath.drop( off )
//	  	FatIdentifier( sub, value )
//	}

   override def toString = path.mkString( "FId( <", ",", ">, " + value + " )" )
}