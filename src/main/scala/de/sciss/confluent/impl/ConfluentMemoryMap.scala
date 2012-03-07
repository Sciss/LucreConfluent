package de.sciss.confluent
package impl

import collection.immutable.{LongMap, IntMap}
import concurrent.stm.{TxnLocal, InTxn}

object ConfluentMemoryMap {
   def apply[ A ]() : ConfluentTxMap[ A ] = new Impl[ A ]

   private val emptyLongMapVal   = LongMap.empty[ Any ]
   private def emptyLongMap[ T ] = emptyLongMapVal.asInstanceOf[ LongMap[ T ]]

   private final class Impl[ A ] extends ConfluentTxMap[ A ] {
      private val idMapRef = TxnLocal[ IntMap[ LongMap[ Value[ A ]]]]( IntMap.empty )

      def put( id: Int, path: PathLike, value: A )( implicit tx: InTxn ) {
         idMapRef.transform { idMap =>
            val mapOld  = idMap.getOrElse( id, emptyLongMap[ Value[ A ]])
            var mapNew  = mapOld
            Hashing.prefixIterator( path, mapOld.contains ).foreach {
               case (key, preSum) =>
                  mapNew += ((key, if( preSum == 0L ) ValueNone else ValuePre( preSum )))
            }
            mapNew += ((path.sum, ValueFull( value )))
            idMap + ((id, mapNew))
         }
      }

      def get( id: Int, path: PathLike )( implicit tx: InTxn ) : A = {
         sys.error( "TODO" )
      }

      private sealed trait Value[ +A ]
      private case object ValueNone extends Value[ Nothing ]
      private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
      private case class ValueFull[ A ]( v: A ) extends Value[ A ]
   }
}
