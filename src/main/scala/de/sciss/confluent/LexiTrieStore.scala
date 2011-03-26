/*
 *  LexiTrieStore.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

object LexiTrieStoreFactory extends StoreFactory[ Version ] {
   def empty[ V ] : Store[ Version, V ] = new LexiTrieStore[ V ]( LexiTrie.empty[ OracleMap[ V ]])

   private class LexiTrieStore[ @specialized V ]( lexi: LexiTrie[ OracleMap[ V ]]) extends Store[ Version, V ] {
      def inspect = lexi.inspect

      def get( key: Path ) : Option[ V ] = {
         val (oracleO, off) = lexi.multiFindMaxPrefix( key )
         // map the not-found-offset to the last-in-oracle-index
         // ; e.g. 1 -> 1, 2 -> 1, 3 -> 3, 4 -> 3, 5 -> 5 etc.
         val idx = off - 1 + (off & 1)
         oracleO.map( _.query( key( idx ))) getOrElse None
      }

      def getWithPrefix( key: Path ) : Option[ (V, Int) ] = {
         val (oracleO, off) = lexi.multiFindMaxPrefix( key )
         // map the not-found-offset to the last-in-oracle-index
         // ; e.g. 1 -> 1, 2 -> 1, 3 -> 3, 4 -> 3, 5 -> 5 etc.
         val idx = off - 1 + (off & 1)
         oracleO.map( _.query( key( idx )).map( v => v -> off /* ??? XXX */ )) getOrElse None
      }

      def put( key: Path, value: V) : Store[ Version, V ] = {
         val idx        = key.dropRight( 1 )
         val last       = key.last
         val newEntry   = (last -> value)
         new LexiTrieStore( lexi.map( idx, _ + newEntry, {
            // create a new oracle with new entry and
            // tree-entrance-entry (if found),
            // then insert oracle into the lexi
            val idxLast = idx.last
            if( idxLast != last ) {
               get( key ).map( lastValue =>
                  OracleMap.singleton( idx.last -> lastValue ) + newEntry
               ) getOrElse OracleMap.singleton( newEntry )
            } else OracleMap.singleton( newEntry )
         }))
      }
   }
}