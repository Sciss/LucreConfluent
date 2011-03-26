package de.sciss.confluent

import collection.immutable.LongMap

object HashedStoreFactory {
   private class HashedStore[ K, @specialized V ]( map: Map[ Long, V ]) extends Store[ K, V ] {
      def inspect = { println( "HashedStore.inspect -- nothin here" )}

      /**
       * Warning: multiplicities are currently _not_ supported,
       * we will need to enrich the Path type to account for that
       */
      def get( key: Path ) : Option[ V ] = Hashing.maxPrefixValue( key, map )

      def put( key: Path, value: V ) : Store[ K, V ] = {
         error( "NOT YET IMPLEMENTED" )
      }
   }
}

class HashedStoreFactory[ K ] extends StoreFactory[ K ] {
   import HashedStoreFactory._
   def empty[ V ] : Store[ K, V ] = new HashedStore[ K, V ]( LongMap.empty[ V ])
}
