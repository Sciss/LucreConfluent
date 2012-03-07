package de.sciss.confluent
package impl

import collection.immutable.{LongMap, IntMap}
import concurrent.stm._

object ConfluentMemoryMap {
   private type MapType[ A ] = IntMap[ LongMap[ Value[ A ]]]
   private def EmptyMap[ A ] : MapType[ A ] = IntMap.empty

//   def apply[ A ]() : ConfluentTxMap[ A ] = new Impl[ A ]
   def local[ A ]() : ConfluentTxMap[ InTxn, A ] = new Impl[ InTxnEnd, A ]( TxnLocal( EmptyMap[ A ]))
   def ref[ A ]()   : ConfluentTxMap[ InTxn, A ] = new Impl[ InTxn, A ]( Ref( EmptyMap[ A ]))

   private val emptyLongMapVal   = LongMap.empty[ Any ]
   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ -Txn <: InTxnEnd, A ]( idMapRef: RefLike[ MapType[ A ], Txn ]) extends ConfluentTxMap[ Txn, A ] {
//      private val idMapRef = TxnLocal[ MapType[ A ]]( IntMap.empty )

      def put( id: Int, path: PathLike, value: A )( implicit tx: Txn ) {
         idMapRef.transform { idMap =>
            val mapOld  = idMap.getOrElse( id, emptyLongMap[ Value[ A ]])
            var mapNew  = mapOld
            Hashing.foreachPrefix( path, mapOld.contains ) {
               case (key, preSum) =>
                  mapNew += ((key, /* if( preSum == 0L ) ValueNone else */ ValuePre( preSum )))
            }
            mapNew += ((path.sum, ValueFull( value )))
            idMap + ((id, mapNew))
         }
      }

      def get( id: Int, path: PathLike )( implicit tx: Txn ) : A = {
         val idMap   = idMapRef.get
         val map     = idMap( id )
         map( Hashing.maxPrefixKey( path, map.contains )) match {
            case ValueFull( v )      => v
            case ValuePre( hash )    => map( hash ) match {
               case ValueFull( v )   => v
               case _                => sys.error( "Orphaned partial prefix for id " + id + " and path " + path )
            }
//            case ValueNone           => throw new NoSuchElementException( "path not found: " + path )
         }
      }
   }

   private sealed trait Value[ +A ]
//   private case object ValueNone extends Value[ Nothing ]
   private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
   private case class ValueFull[ A ]( v: A ) extends Value[ A ]
}
