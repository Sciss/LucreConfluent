/*
 *  HashedStore.scala
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

import collection.immutable.LongMap

object HashedStoreFactory {
   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class HashedStore[ K, V ]( map: Map[ Long, Value[ V ]]) extends Store[ K, V ] {
      def inspect = { println( "HashedStore.inspect -- nothin here" )}

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Path ) : Option[ V ] = Hashing.maxPrefixValue( key, map ).map {
         case ValueHolder( v )   => v
         case ValueProxy( h )    => map( h ).asInstanceOf[ ValueHolder[ V ]].v
      }

      def put( key: Path, value: V ) : Store[ K, V ] = {
         val hash       = key.sum
         lazy val proxy = ValueProxy( hash )
         new HashedStore( Hashing.add( key, map, { s: Path =>
            if( s.sum == hash ) ValueHolder( value ) else proxy
         }))
      }
   }

   private sealed trait Value[ +V ]
   private case class ValueProxy( hash: Long ) extends Value[ Nothing ]
   private case class ValueHolder[ V ]( v:  V ) extends Value[ V ]
}

class HashedStoreFactory[ K ] extends StoreFactory[ K ] {
   import HashedStoreFactory._
   def empty[ V ] : Store[ K, V ] = new HashedStore[ K, V ]( LongMap.empty[ Value[ V ]])
}
