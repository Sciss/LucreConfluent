package de.sciss.confluent

object LexiTrieStoreFactory extends StoreFactory[ Version ] {
   def empty[ V ] : Store[ Version, V ] = new LexiTrieStore[ V ]( LexiTrie.empty[ OracleMap[ V ]])

   private class LexiTrieStore[ @specialized V ]( lexi: LexiTrie[ OracleMap[ V ]]) extends Store[ Version, V ] {
      def inspect = lexi.inspect

      def get( key: Path ) : Option[ V ] = {
         val (oracleO, off) = lexi.multiFindMaxPrefix( key )
         // map the not-found-offset to the last-in-oracle-index
         // ; e.g. 1 -> 1, 2 -> 1, 3 -> 3, 4 -> 3, 5 -> 5 etc.
         val idx = off - 1 + (off & 1)
         oracleO.map( _.query( key( idx ))) getOrElse None
      }

      def put( key: Path, value: V) : Store[ Version, V ] = {
         val idx        = key.dropRight( 1 )
         val last       = key.last
         val newEntry   = (last -> value)
         new LexiTrieStore( lexi.map( idx, _ + newEntry, {
            // create a new oracle with new entry and
            // tree-entrance-entry (if found),
            // then insert oracle into the lexi
            val idxLast = idx.last
            if( idxLast != last ) {
               get( key ).map( lastValue =>
                  OracleMap.singleton( idx.last -> lastValue ) + newEntry
               ) getOrElse OracleMap.singleton( newEntry )
            } else OracleMap.singleton( newEntry )
         }))
      }
   }
}