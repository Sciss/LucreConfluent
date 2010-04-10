/*
 *  OracleMap
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

/**
 *    This is a tiny mutable holder of a BinaryTreeMap, to store
 *    L(c~) as required by the compressed-path method. We use this
 *    wrapper as BinaryTreeMap itself is immutable and we save
 *    an extra traveral in the lexicographical map, omitting a re-insert.
 */
/*
object OracleMap {
//   def empty[ V ]: OracleMap[ V ] = new OracleMap( BinaryTreeMap.empty( Version.AncestorOrdering ))

   def apply[ V ]( entries: Tuple2[ Version, V ]* ): OracleMap[ V ] =
      new OracleMap( BinaryTreeMap( entries: _* )( Version.AncestorOrdering ))
}

class OracleMap[ V ] private ( private var tree: BinaryTreeMap[ Version, V ]) {
   private val queryFilter = (a: Version, b: Version) => {
      a.tree.postOrder.compare( a.postRec, b.postRec ) > 0
   }

   def +=( entry: Tuple2[ Version, V ]) {
      tree += entry
   }

//   def query( t: Version ) : Option[ V ] = tree.getClosestLessOrEqualTo( t )

   def query( t: Version ) : Option[ V ] = tree.getClosestLessOrEqualTo( t, queryFilter )

   override def toString = tree.toString

   def inspect = tree.inspect
}
*/

/**
 *    Note: this is a simple O(n) implementation.
 *    We didn't bother to optimize it, as the approach with
 *    TotalOrder would in any case require a two dimensional search.
 *    Eventually we should implement the algorithm described by
 *    Alstrup et al. in "Marked Ancestor Problems" (section 5 and 6)
 *
 *    A linearization rule has been added for retroactive vertices.
 *
 *    @version 0.11, 10-Apr-10 
 */
object OracleMap {
//   def empty[ V ]: OracleMap[ V ] = new OracleMap( BinaryTreeMap.empty( Version.AncestorOrdering ))

   def apply[ V ]( entries: Tuple2[ Version, V ]* ): OracleMap[ V ] = {
      val map = new OracleMap[ V ]
      entries.foreach( map += _ )
      map
   }
}

class OracleMap[ V ] private () {
   private var entries: List[ Tuple2[ Version, V ]] = Nil

   def +=( entry: Tuple2[ Version, V ]) {
      entries = if( entries.isEmpty || (entries.head._1 != entry._1) ) {
         entry :: entries
      } else {
         entry :: entries.tail
      }
   }

   def query( t: Version ) : Option[ V ] = {
      entries.foldLeft[ Option[ Tuple2[ Version, V ]]]( None )( (bestO, entry) => {
         val (key, value) = entry
         if( key == t ) return Some( value )
         // ---- linearization rule ----
         if( key.id < t.id ) {
            // ---- filter ----
            require( key.tree == t.tree )
            val isLeftPre   = key.tree.preOrder.compare(  key.vertex.preRec,  t.vertex.preRec ) < 0
            val isRightPost = key.tree.postOrder.compare( key.vertex.postRec, t.vertex.postRec ) > 0
            // ---- maxItem ----
            if( isLeftPre && isRightPost ) { // isAncestor ?
               bestO.map( best => {
                  val isRightPre = key.tree.preOrder.compare( key.vertex.preRec, best._1.vertex.preRec ) > 0
                  if( isRightPre ) {  // isNearestAncestor ?
                     entry
                  } else {
                     best
                  }
               }) orElse Some( entry )
            } else bestO
         } else bestO
      }).map( _._2 ) // ---- map ----
   }

   override def toString = entries.toString

   def inspect = {
      println( entries )
   }
}
