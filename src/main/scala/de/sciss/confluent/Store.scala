/*
 *  Store.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.confluent

trait StoreLike[ K, @specialized V, Repr ] {
   def put( key: K, value: V ) : Repr
   def get( key: K ) : Option[ V ]
   def getOrElse( key: K, default: => V ) : V = get( key ).getOrElse( default )

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
   def getWithPrefix( key: K ) : Option[ (V, Int) ]

   def inspect() : Unit
}

trait Store[ K, V ] extends StoreLike[ K, V, Store[ K, V ]]

trait StoreFactory[ K ] {
   def empty[ V ] : Store[ K, V ]
}
