package de.sciss.confluent

object InMemoryConfluentMap {
//   def newIntMap[ S <: KSys[ S ]] : InMemoryConfluentMap[ S, Int ] =
//      new IntMapImpl[ S ]
//
//   def newLongMap[ S <: KSys[ S ]] : InMemoryConfluentMap[ S, Long ] =
//      new LongMapImpl[ S ]
}
trait InMemoryConfluentMap[ S <: KSys[ S ], @specialized( Int, Long) K ] {
   def put[ @specialized A ]( key: K, path: S#Acc, value: A )( implicit tx: S#Tx ) : Unit

   /**
    * Finds the most recent value for an entity `id` with respect to version `path`.
    *
    * @param key        the identifier for the object
    * @param path       the path through which the object has been accessed (the version at which it is read)
    * @param tx         the transaction within which the access is performed
    * @tparam A         the type of values stored with the entity
    * @return           `None` if no value was found, otherwise a `Some` of that value.
    */
   def get[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ A ]

   /**
    * Finds the most recent value for an entity `id` with respect to version `path`. If a value is found,
    * it is return along with a suffix suitable for identifier path actualisation.
    *
    * @param key        the identifier for the object
    * @param path       the path through which the object has been accessed (the version at which it is read)
    * @param tx         the transaction within which the access is performed
    * @tparam A         the type of values stored with the entity
    * @return           `None` if no value was found, otherwise a `Some` of the tuple consisting of the
    *                   suffix and the value. The suffix is the access path minus the prefix at which the
    *                   value was found. However, the suffix overlaps the prefix in that it begins with the
    *                   tree entering/exiting tuple at which the value was found.
    */
   def getWithSuffix[ A ]( key: K, path: S#Acc )( implicit tx: S#Tx ) : Option[ (S#Acc, A) ]
}
