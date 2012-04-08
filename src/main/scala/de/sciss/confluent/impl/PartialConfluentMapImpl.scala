package de.sciss.confluent
package impl

import de.sciss.lucre.stm.{Serializer, DataStore}
import TemporalObjects.logPartial

final class PartialConfluentMapImpl[ S <: KSys[ S ]]( store: DataStore ) extends DurableConfluentMap[ S, Int ] {
   def put[ @specialized A ]( key: Int, path: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
//      val (index, term) = path.splitIndex
      // first we need to see if anything has already been written to the index of the write path
      logPartial( "put( " + key + ", " + path + ")" )
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
      getWithPrefixLen[ A, A ]( key, path ) { (_, value) =>
         value
      }
   }

   def getWithSuffix[ @specialized A ]( key: Int, path: S#Acc )
                                      ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ (S#Acc, A) ] = {
//      getWithPrefixLen[ A, (S#Acc, A) ]( key, path ) { (preLen, writeTerm, value) =>
//         (writeTerm +: path.drop( preLen ), value)
//      }
      getWithPrefixLen[ A, (S#Acc, A) ]( key, path )( (writePath, value) => (writePath, value) )
   }

   private def getWithPrefixLen[ @specialized A, B ]( key: Int, path: S#Acc )
//                                                    ( fun: (Int, Long, A) => B )
                                                    ( fun: (S#Acc, A) => B )
                                                    ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ B ] = {
      store.get { out =>
         out.writeUnsignedByte( 2 )
         out.writeInt( key )
      } { in =>
         val writePath  = tx.readPath( in )
         val value      = ser.read( in )
//         val preLen     = path.maxPrefixLength( writePath.index )
//         val term2      = writePath( preLen )
//         logPartial( "get( " + key + ", " + path + " ) => writePath = " + writePath + ", preLen = " + preLen + ", writeTerm = " + term2.toInt )
//         fun( preLen, term2, value )
         logPartial( "get( " + key + ", " + path + " ) => writePath = " + writePath )
         fun( writePath, value )
      }
   }

   def isFresh( key: Int, path: S#Acc )( implicit tx: S#Tx ) : Boolean = sys.error( "TODO" )
}
