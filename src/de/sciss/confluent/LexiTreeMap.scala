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
 *    19-Mar-10   big cleanup and enhancement
 */

package de.sciss.confluent

import scala.collection.immutable.{ Queue }

/**
 * 	A lexicographic search tree, as described by Sleator and Tarjan
 * 	in "Self-Adjusting Binary Search Trees" (1985). The splay code
 * 	is the simple top-down version as described in the paper, and
 * 	was based on Sleator's public domain java code published on
 *    http://www.link.cs.cmu.edu/splay/
 *
 *    "If we traverse any path in the tree down from the root,
 *    concatenating all the symbols in the nodes from which we
 *    leave by a middle edge (an edge to a middle child), then
 *    we obtain a prefix [...]"
 *
 * 	Note: the code is currently not thread-safe.
 *
 * 	@version    0.12, 19-Mar-10
 * 	@author		Danny Sleator
 * 	@author		Hanns Holger Rutz
 */
@specialized
class LexiTreeMap[ T, V ]()( implicit ordering: Ordering[ T ])
{
   import ordering._

   type Path = Seq[ T ]

   private var root: Node     = null
   private val header: Node   = new Index( null.asInstanceOf[ T ], null, null ) // For splay

	def clear {
		root = null
	}

   def inspect {
      println( "LEXI" )
      if( root != null ) root.inspect( Queue.Empty )
   }

   def insert( path: Path, value: V ) {
		if( path.size == 0 ) return
    	var sup: Access = RootAccess
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

//   def insertOrReplace( path: Path, f: (Option[ V ]) => V ) {
//      if( path.size == 0 ) return
//       var sup: Access = RootAccess
//       val iter = path.iterator
//       while( iter.hasNext ) {
//          val i = iter.next
//          if( iter.hasNext ) {
//             insert( i, sup )
//          } else {
//             insert( i, sup, f )
//          }
//         sup = sup.middle
//       }
//   }

 	private def insert( key: T, m: Access ) : Unit = {
    	if( m.middle == null ) {
    		m.middle = new Index( key, null, null )
    		return
    	}
    	splay( key, m )
      val rNew = m.middle
      val c = compare( key, rNew.key )
      if( c < 0 ) {
         val n		   = new Index( key, rNew.left, rNew )
         rNew.left	= null
         m.middle    = n
      } else if( c > 0 ){
         val n		   = new Index( key, rNew, rNew.right )
         rNew.right	= null
         m.middle	   = n
//    	} else {
//    		// throw new DuplicateItemException(x.toString());
//    		rNew
      }
    }

   private def insert( key: T, m: Access, value: V ) : Unit = {
    	if( m.middle == null ) {
    		m.middle = new Leaf( key, value, null, null )
    		return
    	}
    	splay( key, m )
    	val rNew = m.middle
      val c		= compare( key, rNew.key )
      if( c < 0 ) {
         val n		   = new Leaf( key, value, rNew.left, rNew )
         rNew.left	= null
         m.middle	   = n
      } else if( c > 0 ){
         val n		   = new Leaf( key, value, rNew, rNew.right )
         rNew.right	= null
         m.middle	   = n
      } else {
////    		// throw new DuplicateItemException(x.toString());
////    		rNew
//		    // better throw an exception here because
//		    // we don't yet replace the node by VN with
//		    // the appropriate 'value'
//		    throw new IllegalArgumentException( "Trying to overwrite subpath " + i )
         val n		= new Leaf( key, value, rNew.left, rNew.right )
         m.middle	= n
      }
   }
    
//    def -=( i: T ) {
//    	if( isEmpty ) return
//    	root = splay( i )
//    	if( i.compareTo( root.key ) != 0 ) {
//    		// throw new ItemNotFoundException(x.toString());
//    		return
//    	}
//    	// Now delete the root
//    	if( root.left.isEmpty ) {
//    		root = root.right
//    	} else {
//    		val n = root.right
//    		root = root.left
//    		root = splay( i )
//    		root.right = n
//    	}
//    }

//    // as required by the scala.collection.Range trait
//    def compare( key1: T, key2: T ) : Int = key1.compare( key2 )

//    /**
//     * Find the smallest key in the tree.
//     */
//    /* override */ def firstKey: T = {
//      	if( root.isEmpty ) throw new NoSuchElementException
//        var n = root; while( n.left.isDefined ) n = n.left
//        root = splay( n.key )
//        n.key
//    }

//    /**
//     * Find the largest key in the tree.
//     */
//    /* override */ def lastKey: T = {
//      if( root.isEmpty ) throw new NoSuchElementException
//        var n = root; while( n.right.isDefined ) n = n.right
//        root = splay( n.key )
//        n.key
//    }

   /**
    *  Find an key in the tree.
    */
   def find( path: Path ) : Option[ V ] = {
      if( path.isEmpty ) return None

      var sup: Access = RootAccess
      val iter = path.iterator
      while( sup.middle != null ) {
         val key = iter.next
         splay( key, sup )
         val n = sup.middle
         if( (n == null) || (compare( key, n.key ) == 0) ) return None
         sup = n
         if( !iter.hasNext ) {
            return if( n.terminal ) Some( n.value ) else None
         }
      }
      None
   }

	def findMaxPrefix( path: Path ) : Option[ V ] = {
    	var sup: Access = RootAccess
      var l: Node = null

      val iter = path.iterator
      var cond = true
    	while( cond && iter.hasNext && (sup.middle != null) ) {
         val key = iter.next
         splay( key, sup )
    		val n	= sup.middle
         if( compare( key, n.key ) != 0 ) {
            cond = false
         } else {
      	   if( n.terminal ) l = n
            sup = n
         }
      }
      if( l != null ) Some( l.value ) else None
   }

	def findMaxPrefix2( path: Path ) : Tuple2[ Option[ V ], Int ] = {
      var sup: Access = RootAccess
      var l: Node = null
    	var cnt = 0

      val iter = path.iterator
      var cond = true
      while( cond && iter.hasNext && sup.middle != null ) {
         val key = iter.next
         splay( key, sup )
    		val n	= sup.middle
      	if( compare( key, n.key ) != 0 ) {
            cond = false
         } else {
            if( n.terminal ) l = n
            sup	= n
            cnt  += 1
         }
    	}
      (if( l != null ) Some( l.value ) else None, cnt)
   }

/*
   // EXPERIMENTAL ONE, SKIPPING UNKNOWN INTERMEDIATE HIGHER VERSIONS
   def findMaxPrefix3( path: Path ) : Tuple2[ Option[ V ], Int ] = {
      var v : Option[ V ]  = None
      var sup : Access     = RootAccess // : Access  = this
      var cnt				   = 0
      var checka : T       = null.asInstanceOf[ T ]
      var doChecka         = false

      path.foreach( i => {
         if( doChecka && i.compare( checka ) > 0 ) return Tuple2( v, cnt - 1 )
         if( sup.middle == null ) return Tuple2( v, cnt )
         splay( i, sup )
         val r = sup.middle
         if( i.compare( r.key ) != 0 ) {
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
*/

// 	def findMaxPrefix2( path: Path ) : Tuple2[ V, Int ] = {
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
//      		if( i.compare( sup.middle.key ) != 0 ) {
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
//    	val c	= i.compare( r2.key )
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

	def contains( path: Path ) : Boolean = find( path ).isDefined

//    /*
//     *	@returns	a tuple where _1 is the new root, and _2 is the
//     * 				last element of the path (for convenience)
//     */
//    private def splay( path: Path ) : Tuple2[ N[T], T ] = {
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
//    	val c	= i.compare( r2.key )
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
//     * Find an key in the tree.
//     */
//    def find( i: T ) : Option[ T ] = {
//		if( root == null ) return None
//		root = splay( i )
//        if( root.key.compare( i ) == 0 ) Some( root.key ) else None
//    }
//
//    def contains( i: T ) : Boolean = find( i ).isDefined

//    def isEmpty = root == null

   /*
    *	Internal method to perform a top-down splay.
    *
    *  splay( key ) does the splay operation on the given key.
    *  If key is in the tree, then the LexiMapNode containing
    *  that key becomes the root. If key is not in the tree,
    *  then after the splay, key.root is either the greatest key
    *  < key in the tree, or the least key > key in the tree.
    *
    *  This means, among other things, that if you splay with
    *  a key that's larger than any in the tree, the rightmost
    *  node of the tree becomes the root.  This property is used
    *  in the -=() method.
    *
    *	@param m	the subtree provider. note that m.middle must not be null
    * 				as this is not checked!
    */
   private def splay( key: T, m: Access ) {
		var l          = header
		var r          = header
		var t			   = m.middle
		header.left		= null
		header.right	= null
		while( true ) {
			val c = compare( key, t.key )
			if( c < 0 ) {
				if( t.left == null ) {
					return assemble( l, r, t, m )
				}
				if( compare( key, t.left.key ) < 0 ) {
					val y    = t.left						// rotate right
					t.left	= y.right
					y.right	= t
					t		   = y
					if( t.left == null ) {
						return assemble( l, r, t, m )
					}
				}
				r.left	= t								// link right
				r		   = t
				t		   = t.left
			} else if( c > 0 ) {
				if( t.right == null ) {
					return assemble( l, r, t, m )
				}
				if( compare( key, t.right.key ) > 0 ) {
					val y	= t.right						// rotate left
					t.right	= y.left
					y.left	= t
					t		   = y
					if( t.right == null ) {
						return assemble( l, r, t, m )
					}
				}
				l.right	= t 								// link left
				l		   = t
				t		   = t.right
			} else {
				return assemble( l, r, t, m )
			}
		}
		error( "Should never be here" ) // satisfy scalac...
   }

   @inline
   private def assemble( l: Node, r: Node, t: Node, m: Access ) {
      l.right	= t.left
      r.left	= t.right
      t.left	= header.right
      t.right	= header.left
      m.middle	= t
   }

   // ---- Internal classes ----
   trait Access {
      def middle: Node
      def middle_=( m: Node ) : Unit
   }

   protected object RootAccess
   extends Access {
      def middle = root
      def middle_=( m: Node ) {
         root = m
      }
   }

   protected trait Node
   extends Access {
      val key: T
      def left: Node
      def left_=( l: Node )
      def right: Node
      def right_=( r: Node)

      private var mid: Node = null

      def middle = mid
      def middle_=( m: Node ) {
         mid = m
      }

      def value: V      
      def terminal: Boolean

      def inspect( pre: Queue[ T ]) {
         if( left != null )   left.inspect( pre )
         if( middle != null ) middle.inspect( pre enqueue key )
         if( right != null )  right.inspect( pre )
      }
   }

   protected class Index( val key: T, var left: Node, var right: Node )
   extends Node {
      def terminal = false
      def value : V = error( "Index does not have a value" )
   }

   protected class Leaf( val key: T, val value: V, var left: Node, var right: Node )
   extends Node {
      def terminal = true
      
      override def inspect( pre: Queue[ T ]) {
         val succ = pre enqueue key
         if( left != null )   left.inspect( pre )
         println( succ.mkString( "<", ",", ">: " ) + value /* orNull */)
         if( middle != null ) middle.inspect( succ )
         if( right != null )  right.inspect( pre )
      }
   }
}