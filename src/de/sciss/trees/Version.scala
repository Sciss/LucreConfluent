/**
 * 	Version
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

import _root_.scala.collection.mutable.ListBuffer

import _root_.edu.uci.ics.jung.graph.{ DirectedGraph, DirectedSparseGraph, ObservableGraph }

/**
 * 	@version	0.12, 02-Dec-09
 * 	@author		Hanns Holger Rutz
 */
case class Version( path: Seq[ Int ]) {
	override def toString = path.last.toString // XXX last is O(N)
}

object Version {
	// LexiTreeMap doesn't allow overwrite at the moment XXX
//	private val current = Version( List( 0 )) // XXX
	private var uniqueID = 0
//	private var testChain: List[ Int ] = List( 0 )
	private val testChain = new ListBuffer[ Int ]()
    private val listeners = new ListBuffer[ (Symbol) => Unit ]()

   val graph: DirectedGraph[ Int, Long ] = new DirectedSparseGraph()
   val ograph = new ObservableGraph( graph )

	def currentInc : Version = {
        val from = testChain.last
        ograph.addVertex( uniqueID )
        ograph.addEdge( (from.toLong << 32) | (uniqueID.toLong & 0xFFFFFF), from, uniqueID )
		testChain += uniqueID
//println( "VERSION INC -> " + testChain )
		uniqueID += 1
		current
	}

	// ---- initializer ----
	{
        ograph.addVertex( uniqueID )
		testChain += uniqueID
        uniqueID += 1
	}

	def current : Version = {
//		testChain ::= uniqueID
//		uniqueID += 1
		Version( testChain.toList ) // XXX ; toList is important, otherwise we create a mutable Version!!
	}

    def addListener( l: (Symbol) => Unit ) {
        listeners += l
    }

    def removeListener( l: (Symbol) => Unit ) {
        listeners -= l
    }

    def makeCurrent( v: Version ) {
//        testChain = v.path
        testChain.clear()    // chee-
        testChain ++= v.path // sy...
        listeners.foreach(_.apply('current))
    }


	implicit def implCurrent: Version = current
	implicit def fatValueGet[ T ]( fat: FatValue[ T ]) : T = fat.get()( current )
	implicit def fatValueCreate[ T ]( v: T ) : FatValue[ T ] = {
		val fat = new FatValue[ T ]()
		fat.set( v ) // ( currentInc )
		fat
	}

	implicit def fatPointerGet[ T ]( fat: FatPointer[ T ]) : T = {
		val id = fat.get()( current )
		if( id != null ) id.value else null.asInstanceOf[ T ]
	}

	implicit def fatPointerCreate[ T ]( v: T ) : FatPointer[ T ] = {
		val fat = new FatPointer[ T ]()
		fat.set( v ) // FatIdentifier( current.path, v )
		fat
	}

//	def ident[T]( a: T, b: T ) : Boolean = {
//		println( "Comparing a = " + a + "; with b = " + b )
//		a.equals( b )
//	}
}