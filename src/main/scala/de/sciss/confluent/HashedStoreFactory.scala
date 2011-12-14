/*
 *  HashedStoreFactory.scala
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
 */

package de.sciss.confluent

import collection.immutable.LongMap

object HashedStoreFactory {
   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class HashedStore[ K, V ]( map: Map[ Long, Value[ V ]]) extends Store[ PathLike[ K ], V ] {
      type Pth = PathLike[ K ]

      def inspect() {
//         println( "HashedStore.inspect -- nothin here" )
         println( "INSPECT" )
         println( "  " + map )
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Pth ) : Option[ V ] = Hashing.maxPrefixValue( key, map ).flatMap {
         case ValueFull( v )        => Some( v )
         case ValuePre( /* len, */ hash ) => Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v )
         case ValueNone             => None // : Option[ V ]
      }

      def getWithPrefix( key: Pth ) : Option[ (V, Int) ] = {
         Hashing.getWithPrefix( key, map ).flatMap {
            case (ValueFull( v ), sz)        => Some( v -> sz )
            case (ValuePre( /* len, */ hash ), sz) => {
//               assert( sz == len )
               Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v -> sz /* len */)
            }
            case (ValueNone, _)              => None // : Option[ V ]
         }
      }

      def put( key: Pth, value: V ) : Store[ Pth, V ] = {
         val hash       = key.sum
//         lazy val proxy = ValueProxy( hash )
         new HashedStore[ K, V ]( Hashing.add[ K, Value[ V ]]( key, map, { s: Pth =>
//            if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( s.size, hash )
            if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
         }))
      }
   }

   private sealed trait Value[ +V ]
   private case object ValueNone extends Value[ Nothing ]
   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
   private case class ValueFull[ V ]( v:  V ) extends Value[ V ]
}

class HashedStoreFactory[ K ] extends StoreFactory[ PathLike[ K ]] {
   import HashedStoreFactory._
   def empty[ V ] : Store[ PathLike[ K ], V ] = new HashedStore[ K, V ]( LongMap.empty[ Value[ V ]])
}
