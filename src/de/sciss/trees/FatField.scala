/**
 * 	FatField
 * 	(de.sciss.tree package)
 *
 *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 */
package de.sciss.trees

/**
 * 	@version	0.11, 07-Jun-09
 * 	@author		Hanns Holger Rutz
 */
class FatValue[ T ] {
	private val pa = new LexiTreeMap[ Int, T ]()

	def assign( version: Seq[ Int ], value: T ) {
		pa.insert( version, value )
	}

	def access( version: Seq[ Int ]) : T = {
		pa.findMaxPrefix( version )
	}

	def get()( implicit version: Version ) : T = {
		pa.findMaxPrefix( version.path )
	}

	def set( value: T )( implicit version: Version ) : Unit = {
// LexiTreeMap doesn't allow overwrite at the moment XXX
//val version = VersionImplicits.currentInc
		pa.insert( version.path, value )
	}
}

class FatPointer[ T ] {
	private val pa = new LexiTreeMap[ Int, FatIdentifier[ T ]]()

	def assign( path: Seq[ Int ], valuePath: Seq[ Int ], value: T ) {
		pa.insert( path, FatIdentifier( valuePath, value ))
	}

//	def assignNull( path: Seq[ Int ]) {
//		pa.insert( path, null )
//	}

	def assign( path: Seq[ Int ], id: FatIdentifier[ T ]) {
		pa.insert( path, id )
	}

//	def access( version: Seq[ Int ]) : T = {
//		pa.find( version )
//	}

	def access( version: Seq[ Int ]) : FatIdentifier[ T ] = {
	  	val (id, off) = pa.findMaxPrefix2( version )
	  	if( id == null ) {
//	  		return id
	  		return FatIdentifier( version, null.asInstanceOf[ T ])
	  	}
	  	id.substitute( version, off )
	}

	def get()( implicit version: Version ) : FatIdentifier[ T ] = {
		val (id, off) = pa.findMaxPrefix2( version.path )
	  	if( id == null ) return id
//	  	println( "id = " + id + "; off = " + off + "; version = " + version )
	  	id.substitute( version.path, off )
	}

	def set( id: FatIdentifier[ T ])( implicit version: Version ) : Unit = {
// LexiTreeMap doesn't allow overwrite at the moment XXX
//val version = VersionImplicits.currentInc
		pa.insert( version.path, id )
	}

	def set( v: T )( implicit version: Version ) : Unit = {
		pa.insert( version.path,
		           FatIdentifier( List( version.path.last ) /* XXX last is O(N) */, v ))
	}
}

case class FatIdentifier[ T ]( val path: Seq[ Int ], val value: T ) {
	def append( i: Int ) : FatIdentifier[ T ] = {
	  	FatIdentifier( path ++ List( i ), value ) // XXX inefficient
	}

	def setValue( v: T ) : FatIdentifier[ T ] = FatIdentifier( path, v )

	def substitute( accessPath: Seq[ Int ], off: Int ) : FatIdentifier[ T ] = {
	  	// XXX inefficient
	  	val sub = path ++ accessPath.drop( off )
	  	FatIdentifier( sub, value )
	}
}