/*
 *  LexiTrie.scala
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

import collection.immutable.IntMap


/**
 * An immutable lexicographic trie which can be used as a purely functional
 * replacement of the splay implementation (LexiTreeMap).
 *
 * It uses path-copying which might not be the most efficient approach,
 * and performance needs to undergo testing!
 *
 * @version 0.10, 13-Nov-10
 */
case class LexiTrie[ @specialized V ]( val value: Option[ V ], val sub: IntMap[ LexiTrie[ V ]]) {
   def +( entry: (Path, V) ) : LexiTrie[ V ] = {
      val (key, v) = entry
      key.headOption match {
         case None => copy( value = Some( v ))
         case Some( head ) => copy( sub = sub.updated( head.id,
             sub.getOrElse( head.id, LexiTrie.empty[ V ]) + (key.tail -> v) ))  // XXX tailrec
      }
   }

   def get( key: Path ) : Option[ V ] = key.headOption match {
       case None => value
       case Some( head ) => sub.get( head.id ).flatMap( _.get( key.tail ))    // XXX tailrec
   }

   def map( key: Path, some: V => V, none: => V ) : LexiTrie[ V ] = {
      key.headOption match {
         case None => copy( value = Some( value match {
            case Some( v ) => some( v )
            case None      => none
         }))
         case Some( head ) => copy( sub = sub.updated( head.id,
             sub.getOrElse( head.id, LexiTrie.empty[ V ]).map( key.tail, some, none )))  // XXX tailrec
      }
   }

   /**
    *    Finds the value which is the nearest
    *    ancestor in the trie. It returns a tuple
    *    composed of this value as an Option
    *    (None if no ancestor assignment found),
    *    along with an offset Int which is the
    *    offset into path for the first key
    *    element _not_ found in the trie.
    *
    *    Like findMaxPrefixOffset, but with support
    *    for multiplicities
    */
   def multiFindMaxPrefix( key: Path ) : (Option[ V ], Int) = multiFind( key, 0, None )

   private def multiFind( key: Path, cnt: Int, found: Option[ V ]) : (Option[ V ], Int ) = key.headOption match {
       case None => (value.orElse( found ), cnt)
       case Some( head ) => sub.get( head.id ) match {
          case Some( t ) => t.multiFind( key.tail, cnt + 1, t.value.orElse( found ))     // XXX tailrec
          case None if( head.fallBack != head ) => multiFind( head.fallBack +: key.tail, cnt, found )     // XXX tailrec
          case _ => (found, cnt)
       }
   }

   def inspect {
      println( "LexiTrie" )
      inspect0( "  " )
   }

   private def inspect0( indent: String ) {
      value.foreach( v => println( indent + "val = " + v ))
      sub.keys.toList.sorted.foreach { key =>
         println( indent + "key = " + key )
         sub.get( key ).foreach( _.inspect0( indent + "  " ))
      }
   }
}

object LexiTrie {
   def empty[ V ] = new LexiTrie[ V ]( None, IntMap.empty )
}