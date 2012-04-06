package de.sciss.confluent
package impl

import de.sciss.lucre.stm.{Serializer, DataStore}


final class PartialConfluentMapImpl[ S <: KSys[ S ]]( store: DataStore ) extends DurableConfluentMap[ S, Int ] {
   def put[ @specialized A ]( key: Int, path: S#Acc, value: A )( implicit tx: S#Tx, serializer: Serializer[ A ]) {
      sys.error( "TODO" )
   }

   def get[ @specialized  A ]( key: Int, path: S#Acc )( implicit tx: S#Tx, serializer: Serializer[ A ]) : Option[ A ] = {
      sys.error( "TODO" )
   }

   def getWithSuffix[ @specialized A ]( key: Int, path: S#Acc )( implicit tx: S#Tx, serializer: Serializer[ A ]) : Option[ (S#Acc, A) ] = {
      sys.error( "TODO" )
   }

   def isFresh( key: Int, path: S#Acc )( implicit tx: S#Tx ) : Boolean = sys.error( "TODO" )
}
