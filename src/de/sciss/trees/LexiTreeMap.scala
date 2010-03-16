/**
 * 	LexiTreeMap
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
 *		07-May-09	created
 */
package de.sciss.trees

//import _root_.scala.collection.SortedSet
//import _root_.scala.collection.mutable.Set

import _root_.de.sciss.trees.{ LexiMapNode => N, LexiValueNode => VN, LexiTreeMapSource => TS } // thank you scala...

trait LexiTreeMapSource[ T, V ] { // XXX this trait name is definitely ugly
	def middle_=( m: N[T,V] ) : Unit
	def middle: N[T,V]
	def terminal: Boolean = false
	def value: V = null.asInstanceOf[ V ]
}

protected class LexiMapNode[ T, V ]( val item: T, var left: N[T,V], var right: N[T,V] )
extends TS[ T, V ] {
	private var mid: N[T,V]	= null
//	private var term		= false

	def middle_=( m: N[T,V] ) {
		mid = m
	}

	def middle = mid

//	def terminal_=( b: Boolean ) { term = b }
}

protected class LexiValueNode[ T, V ]( item2: T, override val value: V, left2: N[T,V], right2: N[T,V] )
extends LexiMapNode[ T, V ]( item2, left2, right2 ) {
	override def terminal = true
}

/**
 * 	A lexicographic search tree, as described by Sleator and Tarjan
 * 	in "Self-Adjusting Binary Search Trees" (1985). The splay code
 * 	is the simple top-down version as described in the paper, and
 * 	was based on Sleator's public domain java code published on
 *	http://www.link.cs.cmu.edu/splay/
 *
 * 	Note: the code is currently not thread-safe.
 *
 * 	@version	0.10, 10-May-09
 * 	@author		Danny Sleator
 * 	@author		Hanns Holger Rutz
 */
class LexiTreeMap[ T, V ]()( implicit view : (T) => Ordered[ T ])
extends TS[ T, V ]
// extends Set[ T ] with SortedSet[ T ]
{
 	private var root: N[T,V] = null
	private val header = new N[T,V]( null.asInstanceOf[ T ], null, null ) // For splay

	def middle_=( m: N[T,V] ) {
		root = m
	}
	def middle = root

	def clear {
		root = null
	}

    def insert( path: Seq[T], value: V ) {
		if( path.size == 0 ) return
    	var sup : TS[T,V] = this
    	val iter = path.iterator
    	while( iter.hasNext ) {
    		val i = iter.next
    		if( iter.hasNext ) {
    			insert( i, sup )
    		} else {
    			insert( i, sup, value )
    		}
      		sup = sup.middle
    	}
 	}

 	private def insert( i: T, m: TS[T,V] ) : Unit = {
    	if( m.middle == null ) {
    		m.middle = new N( i, null, null )
    		return
    	}
    	splay( i, m )
    	val rNew	= m.middle
    	val c		= i.compare( rNew.item )
    	if( c < 0 ) {
    		val n		= new N( i, rNew.left, rNew )
    		rNew.left	= null
    		m.middle	= n
    	} else if( c > 0 ){
    		val n		= new N( i, rNew, rNew.right )
    		rNew.right	= null
    		m.middle	= n
//    	} else {
//    		// throw new DuplicateItemException(x.toString());
//    		rNew
    	}
    }

   	private def insert( i: T, m: TS[T,V], value: V ) : Unit = {
    	if( m.middle == null ) {
    		m.middle = new VN( i, value, null, null )
    		return
    	}
    	splay( i, m )
    	val rNew	= m.middle
    	val c		= i.compare( rNew.item )
    	if( c < 0 ) {
    		val n		= new VN( i, value, rNew.left, rNew )
    		rNew.left	= null
    		m.middle	= n
    	} else if( c > 0 ){
    		val n		= new VN( i, value, rNew, rNew.right )
    		rNew.right	= null
    		m.middle	= n
    	} else {
////    		// throw new DuplicateItemException(x.toString());
////    		rNew
//		    // better throw an exception here because
//		    // we don't yet replace the node by VN with
//		    // the appropriate 'value'
//		    throw new IllegalArgumentException( "Trying to overwrite subpath " + i )
    		val n		= new VN( i, value, rNew.left, rNew.right )
    		m.middle	= n
    	}
    }

//    def -=( i: T ) {
//    	if( isEmpty ) return
//    	root = splay( i )
//    	if( i.compareTo( root.item ) != 0 ) {
//    		// throw new ItemNotFoundException(x.toString());
//    		return
//    	}
//    	// Now delete the root
//    	if( root.left == null ) {
//    		root = root.right
//    	} else {
//    		val n = root.right
//    		root = root.left
//    		root = splay( i )
//    		root.right = n
//    	}
//    }

//    // as required by the scala.collection.Range trait
//    def compare( item1: T, item2: T ) : Int = item1.compare( item2 )

//    /**
//     * Find the smallest item in the tree.
//     */
//    /* override */ def firstKey: T = {
//      	if( root == null ) throw new NoSuchElementException
//        var n = root; while( n.left != null ) n = n.left
//        root = splay( n.item )
//        n.item
//    }

//    /**
//     * Find the largest item in the tree.
//     */
//    /* override */ def lastKey: T = {
//      if( root == null ) throw new NoSuchElementException
//        var n = root; while( n.right != null ) n = n.right
//        root = splay( n.item )
//        n.item
//    }

    /**
     *  Find an item in the tree.
     */
    def find( path: Seq[T] ) : V = {
      	// separately checked since we enter the do loop with
        // assumption that iter.next works
      	if( path.size == 0 ) return null.asInstanceOf[ V ]

    	var sup : TS[T,V]	= this
    	var r :   N[T,V]	= null

    	path.foreach( i => {
    		if( sup.middle == null ) return null.asInstanceOf[ V ]
      		splay( i, sup )
      		r		= sup.middle
      		if( i.compare( r.item ) != 0 ) return null.asInstanceOf[ V ]
      		sup		= r // .middle
    	})
        // extra condition: final node must be terminal
        r.value
    }

	def findMaxPrefix( path: Seq[T] ) : V = {
	  	var v				= null.asInstanceOf[ V ]
    	var sup : TS[T,V]	= this

    	path.foreach( i => {
    		if( sup.middle == null ) return v
      		splay( i, sup )
    		val r	= sup.middle
      		if( i.compare( r.item ) != 0 ) return v
      		if( r.terminal ) v = r.value
      		sup		= r
    	})
        v
    }

	def findMaxPrefix2( path: Seq[T] ) : Tuple2[ V, Int ] = {
	  	var v				= null.asInstanceOf[ V ]
    	var sup : TS[T,V]	= this
    	var cnt				= 0

    	path.foreach( i => {
    		if( sup.middle == null ) return Tuple2( v, cnt )
      		splay( i, sup )
    		val r	= sup.middle
      		if( i.compare( r.item ) != 0 ) return Tuple2( v, cnt )
      		if( r.terminal ) v = r.value
      		sup		= r
      		cnt    += 1
    	})
        Tuple2( v, cnt )
    }

   // EXPERIMENTAL ONE, SKIPPING UNKNOWN INTERMEDIATE HIGHER VERSIONS
   def findMaxPrefix3( path: Seq[T] ) : Tuple2[ V, Int ] = {
        var v				= null.asInstanceOf[ V ]
       var sup : TS[T,V]	= this
       var cnt				= 0
       var checka : T   = null.asInstanceOf[ T ]
       var doChecka     = false

       path.foreach( i => {
          if( doChecka && i.compare( checka ) > 0 ) return Tuple2( v, cnt - 1 )
          if( sup.middle == null ) return Tuple2( v, cnt )
          splay( i, sup )
          val r = sup.middle
//          if( i.compare( r.item ) != 0 ) return Tuple2( v, cnt )
          if( i.compare( r.item ) != 0 ) {
             checka  = i
             doChecka = true
          } else {
             if( r.terminal ) v = r.value
             doChecka = false
             sup		= r
          }
          cnt    += 1
       })
       Tuple2( v, cnt )
    }

// 	def findMaxPrefix2( path: Seq[T] ) : Tuple2[ V, Int ] = {
//      	// separately checked since we enter the do loop with
//        // assumption that iter.next works
//      	if( path.size == 0) {
//      		return Tuple2( null.asInstanceOf[ V ], 0 )
//        }
//
//    	var sup : TS[T,V]	= this
//    	var r :   N[T,V]	= null
//    	var i :   T			= null.asInstanceOf[ T ]
//    	val iter			= path.elements
//    	var cnt				= 0
//
//    	do {
//    		if( sup.middle == null ) {
//    			return Tuple2( if( r == null ) null.asInstanceOf[ V ] else r.value, cnt )
////    			return Tuple2( null.asInstanceOf[ V ], cnt )
//    		}
//      		i		= iter.next
//      		splay( i, sup )
//      		if( i.compare( sup.middle.item ) != 0 ) {
//    			return Tuple2( if( r == null ) null.asInstanceOf[ V ] else r.value, cnt )
//      		}
//      		cnt	   += 1
//      		r		= sup.middle
//      		sup		= r // .middle
//    	} while( iter.hasNext )
//        // extra condition: final node must be terminal
//        Tuple2( r.value, cnt )
//    }

//	private def find( i: T, r: N[T] ) : N[T] = {
//    	if( r == null ) return null
//    	val r2	= splay( i )
//    	val c	= i.compare( r2.item )
//    	if( c < 0 ) {
//    		val n = new N( i, r2.left, r2 )
//    		r2.left = null
//    		n
//    	} else if( c > 0 ){
//    		val n = new N( i, r2, r2.right )
//    		r2.right = null
//    		n
//    	} else {
//    		// throw new DuplicateItemException(x.toString());
//    		r2
//    	}
//    }

	def contains( path: Seq[T] ) : Boolean = find( path ) != null

//    /*
//     *	@returns	a tuple where _1 is the new root, and _2 is the
//     * 				last element of the path (for convenience)
//     */
//    private def splay( path: Seq[T] ) : Tuple2[ N[T], T ] = {
//    	var sup : TS[T]	= this
//    	var r :   N[T]			= null
//    	var i :   T				= null.asInstanceOf[ T ]
//    	val iter				= path.elements
//    	while( iter.hasNext ) {
//      		i			= iter.next
//      		r			= insert( i, sup.middle )
//      		sup.middle	= r
//      		sup			= r
//      	}
//      	(r, i)
//    }

//     	private def insert( i: T, r: N[T] ) : N[T] = {
//    	if( r == null ) {
//    		return new N( i, null, null )
//    	}
//    	val r2	= splay( i )
//    	val c	= i.compare( r2.item )
//    	if( c < 0 ) {
//    		val n = new N( i, r2.left, r2 )
//    		r2.left = null
//    		n
//    	} else if( c > 0 ){
//    		val n = new N( i, r2, r2.right )
//    		r2.right = null
//    		n
//    	} else {
//    		// throw new DuplicateItemException(x.toString());
//    		r2
//    	}
//    }

//    /**
//     * Find an item in the tree.
//     */
//    def find( i: T ) : Option[ T ] = {
//		if( root == null ) return None
//		root = splay( i )
//        if( root.item.compare( i ) == 0 ) Some( root.item ) else None
//    }
//
//    def contains( i: T ) : Boolean = find( i ).isDefined

//    def isEmpty = root == null

    /*
     *	Internal method to perform a top-down splay.
     *
     *  splay( item ) does the splay operation on the given item.
     *  If item is in the tree, then the LexiMapNode containing
     *  that item becomes the root. If item is not in the tree,
     *  then after the splay, item.root is either the greatest item
     *  < item in the tree, or the least item > item in the tree.
     *
     *  This means, among other things, that if you splay with
     *  a item that's larger than any in the tree, the rightmost
     *  node of the tree becomes the root.  This property is used
     *  in the -=() method.
     *
     *	@param m	the subtree provider. note that m.middle must not be null
     * 				as this is not checked!
     */
     private def splay( i: T, m: TS[T,V] ) {
		var l			= header
		var r			= header
		var t			= m.middle
		header.left		= null
		header.right	= null
		while( true ) {
			val c = i.compare( t.item )
			if( c < 0 ) {
				if( t.left == null ) {
					return assemble( l, r, t, m )
				}
				if( i.compare( t.left.item ) < 0 ) {
					val y	= t.left						// rotate right
					t.left	= y.right
					y.right	= t
					t		= y
					if( t.left == null ) {
						return assemble( l, r, t, m )
					}
				}
				r.left	= t									// link right
				r		= t
				t		= t.left
			} else if( c > 0 ) {
				if( t.right == null ) {
					return assemble( l, r, t, m )
				}
				if( i.compare( t.right.item ) > 0 ) {
					val y	= t.right						// rotate left
					t.right	= y.left
					y.left	= t
					t		= y
					if( t.right == null ) {
						return assemble( l, r, t, m )
					}
				}
				l.right	= t 								// link left
				l		= t
				t		= t.right
			} else {
				return assemble( l, r, t, m )
			}
		}
		throw new IllegalStateException( "Should never be here" ) // satisfy scalac...
     }

     @inline
     private def assemble( l: N[T,V], r: N[T,V], t: N[T,V], m: TS[T,V] ) {
    	 l.right	= t.left
    	 r.left		= t.right
    	 t.left		= header.right
    	 t.right	= header.left
    	 m.middle	= t
     }
}
