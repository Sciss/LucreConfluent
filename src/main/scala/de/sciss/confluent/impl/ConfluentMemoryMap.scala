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

      def put( key: Int, path: PathLike, value: A )( implicit tx: InTxn ) {
         val idMap   = idMapRef.get
         val map0    = idMap.getOrElse( key, emptyLongMap[ Value[ A ]])
//         val map1    = HashingOld.add( key, map, { s: Pth =>
//            if( s.isEmpty ) ValueNone else if( s.sum == hash ) ValueFull( value ) else new ValuePre( /* s.size, */ s.sum )
//         })


         sys.error( "TODO" )
      }

      def get( key: Int, path: PathLike )( implicit tx: InTxn ) : A = {
         sys.error( "TODO" )
      }

      private sealed trait Value[ +A ]
      private case object ValueNone extends Value[ Nothing ]
      private case class ValuePre( /* len: Int, */ hash: Long ) extends Value[ Nothing ]
      private case class ValueFull[ A ]( v: A ) extends Value[ A ]
   }
}
