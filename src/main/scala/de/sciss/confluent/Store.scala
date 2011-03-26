package de.sciss.confluent

import de.sciss.fingertree.FingerTree

trait StoreLike[ K, @specialized V, Repr ] {
   type Path = FingerTree.IndexedSummed[ K, Long ]

   def put( key: Path, value: V ) : Repr

   /**
    *    Finds the value which is the nearest
    *    ancestor in the trie. It returns a tuple
    *    composed of this value as an Option
    *    (None if no ancestor assignment found),
    *    along with an offset Int which is the
    *    offset into path for the first key
    *    element _not_ found in the trie.
    *
    *    Like findMaxPrefixOffset, but with support
    *    for multiplicities
    */
   def get( key: Path ) : Option[ V ]

   def inspect : Unit
}

trait Store[ K, V ] extends StoreLike[ K, V, Store[ K, V ]]

trait StoreFactory[ K ] {
   def empty[ V ] : Store[ K, V ]
}
