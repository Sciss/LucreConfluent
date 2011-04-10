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
import concurrent.stm.{InTxn, Ref => STMRef}

object HashedTxnStoreFactory {
   private case class Compound[ V ]( perm: Map[ Long, Value[ V ]], temp: Map[ Long, Value[ V ]])

   // XXX no specialization thanks to scalac 2.8.1 crashing
   private class HashedTxnStore[ K, V ]( cRef: STMRef[ Compound[ V ]])
   extends TxnStore[ K, V ] with TxnStoreCommitter {
      def inspect( implicit txn: InTxn ) = {
         println( "INSPECT" )
         val c = cRef.get
         println( "  perm = " + c.perm )
         println( "  temp = " + c.temp )
      }

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Path )( implicit txn: InTxn ) : Option[ V ] = {
         def get( map: Map[ Long, Value[ V ]]) = Hashing.maxPrefixValue( key, map ).flatMap {
            case ValueFull( v )        => Some( v )
            case ValuePre( /* len, */ hash ) => Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v )
            case ValueNone             => None // : Option[ V ]
         }
         val c = cRef.get
         get( c.temp ).orElse( get( c.perm ))
      }

      def getWithPrefix( key: Path )( implicit txn: InTxn ) : Option[ (V, Int) ] = {
         def get( map: Map[ Long, Value[ V ]]) = Hashing.getWithPrefix( key, map ).flatMap {
            case (ValueFull( v ), sz)        => Some( v -> sz )
            case (ValuePre( /* len, */ hash ), sz) => {
//               assert( sz == len )
               Some( map( hash ).asInstanceOf[ ValueFull[ V ]].v -> sz /* len */)
            }
            case (ValueNone, _)              => None // : Option[ V ]
         }
         val c = cRef.get
         get( c.temp ).orElse( get( c.perm ))
      }

      def put( key: Path, value: V )( implicit txn: InTxn, rec: TxnDirtyRecorder ) {
         val hash = key.sum
         cRef.transform { c =>
            val map = c.temp
            if( map.isEmpty ) rec.addDirty( hash, this )
            c.copy( temp = Hashing.add( key, map, { s: Path =>
               if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
            }))
         }
      }

      def commit( txn: InTxn, suffix: Int ) {
         cRef.transform( c => {
            val map = c.temp
            Compound( c.perm ++ map.map( tup => (tup._1 + suffix, tup._2) ), map.empty )
         })( txn )
      }
   }

   private sealed trait Value[ +V ]
   private case object ValueNone extends Value[ Nothing ]
   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
   private case class ValueFull[ V ]( v:  V ) extends Value[ V ]
}

class HashedTxnStoreFactory[ K ] extends TxnStoreFactory[ K ] {
   import HashedTxnStoreFactory._
   def empty[ V ] : TxnStore[ K, V ] = {
      val mapE = LongMap.empty[ Value[ V ]]
      new HashedTxnStore[ K, V ]( STMRef( Compound( mapE, mapE )))
   }
}
