package de.sciss.confluent
package impl

import de.sciss.lucre.stm.{Serializer, DataStore}

final class PartialConfluentMapImpl[ S <: KSys[ S ]]( store: DataStore ) extends DurableConfluentMap[ S, Int ] {
   def put[ @specialized A ]( key: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
//      val (index, term) = path.splitIndex
      // first we need to see if anything has already been written to the index of the write path
      store.put { out =>
         out.writeUnsignedByte( 2 )
         out.writeInt( key )
//         out.writeLong( path.term )
      } { out =>
         path.write( out )
         ser.write( value, out )
      }
   }

   def get[ @specialized A ]( key: Int, path: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ A ] = {
//      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[ A, A ]( key, path )( (_, _, value) => value )
   }

   def getWithSuffix[ @specialized A ]( key: Int, path: S#Acc )
                                      ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ (S#Acc, A) ] = {
//      val (maxIndex, maxTerm) = path.splitIndex
      getWithPrefixLen[ A, (S#Acc, A) ]( key, path )( (preLen, writeTerm, value) =>
      //            (path.dropAndReplaceHead( preLen, writeTerm ), value)
         (writeTerm +: path.drop( preLen ), value)
      )
   }

   private def getWithPrefixLen[ @specialized A, B ]( key: Int, path: S#Acc )
                                                    ( fun: (Int, Long, A) => B )
                                                    ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ B ] = {
      store.get { out =>
         out.writeUnsignedByte( 2 )
         out.writeInt( key )
      } { in =>
         var preLen  = 0
         val sz      = in.readInt()
         val szm     = math.min( path.size - 1, sz - 1 )
         var term2   = 0L
         var same    = true
         while( same && preLen < szm ) {
            term2    = in.readLong()
            same     = term2 == path( preLen )
         preLen += 1 }
         if( same ) {
            term2    = in.readLong()
         } else {
            preLen  -= 1
         }
         in.skipFast( (sz - (preLen + 1)) << 3 )
         val value   = ser.read( in )
         fun( preLen, term2, value )
      }
   }

   def isFresh( key: Int, path: S#Acc )( implicit tx: S#Tx ) : Boolean = sys.error( "TODO" )
}
