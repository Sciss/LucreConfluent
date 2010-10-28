/*
 *  BinaryTreeMap
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

import collection.SortedMapLike
import collection.immutable.{ SortedMap, MapLike, RedBlack }
import collection.mutable.Builder
import collection.generic.ImmutableSortedMapFactory
import annotation.tailrec

object BinaryTreeMap extends ImmutableSortedMapFactory[ BinaryTreeMap ] {
   def empty[ A, B ]( implicit ord: Ordering[ A ]) = new BinaryTreeMap[A, B]()( ord )

   private def make[ A, B ]( s: Int, t: RedBlack[ A ]#Tree[ B ])( implicit ord: Ordering[ A ]) =
      new BinaryTreeMap[ A, B ]( s, t )( ord )
}

/**
 *    Basically like Scala's immutable TreeMap, but with add functionality
 *    of method 'getClosestLessOrEqualTo'. Unfortunately, tree is
 *    not accessible in TreeMap, so we need to copy all the crap over.
 *    Hopefully this survives some updates of scala.collection in the future...
 */
class BinaryTreeMap[ A, B ]( override val size: Int, t: RedBlack[ A ]#Tree[ B ] )( implicit val ordering: Ordering[ A ])
extends RedBlack[ A ]
   with SortedMap[ A, B ]
   with SortedMapLike[ A, B, BinaryTreeMap[ A, B ]]
   with MapLike[ A, B, BinaryTreeMap[ A, B ]] {

   /**
    *    Returns the value of the element with a key
    *    being the closest to a given key, but no greater
    *    than a given key
    *
    *    @param   key    upper bound (inclusive)
    */
   def getClosestLessOrEqualTo( key: A ) : Option[ B ] =
      getClosestLessOrEqualTo( key, tree, None )

   @tailrec
   private def getClosestLessOrEqualTo( key: A, t: RedBlack[ A ]#Tree[ B ],
                                        best: Option[ RedBlack[ A ]#NonEmpty[ B ]]) : Option[ B ] = {
      val (next, nextBest) = t match {
         case leaf_bla: RedBlack[ _ ]#NonEmpty[ _ ] => {
            val leaf = leaf_bla.asInstanceOf[ RedBlack[ A ]#NonEmpty[ B ]] // XXX yukk
            val cmp = ordering.compare( key, leaf.key )
            if( cmp == 0 ) return Some( leaf.value )
            if( cmp < 0 ) {
               (leaf.left, best)
            } else {
               val better = best.map( bestLeaf => {
                  if( ordering.gt( leaf.key, bestLeaf.key )) leaf else bestLeaf
               }) orElse Some( leaf )
               (leaf.right, better)
            }
         }
         case _ => return best.map( _.value )
      }
      getClosestLessOrEqualTo( key, next, nextBest )
   }

   // ---- all the rest is necessary to get this class compiled, mostly taken from TreeMap ----

   def isSmaller( x: A, y: A ) = ordering.lt( x, y )
   
   override protected[ this ] def newBuilder : Builder[ (A, B), BinaryTreeMap[ A, B ]] = 
     BinaryTreeMap.newBuilder[ A, B ]

   def this()( implicit ordering: Ordering[ A ]) = this( 0, null )( ordering )

   protected val tree: RedBlack[ A ]#Tree[ B ] = if( size == 0 ) Empty else t

   override def rangeImpl( from : Option[ A ], until : Option[ A ]): BinaryTreeMap[ A, B ] = {
     val ntree = tree.range( from, until )
     new BinaryTreeMap[ A, B ]( ntree.count, ntree )
   }

   override def empty: BinaryTreeMap[ A, B ] = BinaryTreeMap.empty[ A, B ]( ordering )

   override def updated[ B1 >: B ]( key: A, value: B1 ): BinaryTreeMap[ A, B1 ] = {
     val newSize = if( tree.lookup( key ).isEmpty ) size + 1 else size
     BinaryTreeMap.make( newSize, tree.update( key, value ))
   }

   override def +[ B1 >: B ]( kv: (A, B1) ): BinaryTreeMap[ A, B1 ] = updated( kv._1, kv._2 )

   def -( key: A ): BinaryTreeMap[ A, B ] =
      if( tree.lookup( key ).isEmpty ) {
         this
      } else {
         BinaryTreeMap.make( size - 1, tree.delete( key ))
      }

   override def get( key: A ): Option[ B ] = tree.lookup( key ) match {
     case n: NonEmpty[ _ ] => Some( n.value )
     case _ => None
   }

   def iterator: Iterator[( A, B )] = tree.toStream.iterator
}