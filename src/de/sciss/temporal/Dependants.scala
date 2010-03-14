package de.sciss.temporal

import scala.collection.mutable.{ WeakHashMap }

trait MutableModelDependant[ T ] {
   def modelReplaced( oldModel: T, newModel: T ) : Unit
}

trait MutableModel[ T ] {
   def addDependant( l: MutableModelDependant[ T ]) : Unit
   def removeDependant( l: MutableModelDependant[ T ]) : Unit
//   def replacedBy( newModel: T ) : Unit
}

trait MutableModelImpl[ T ] extends MutableModel[ T ] {
   model: T =>
   
   private var dependants = new WeakHashMap[ MutableModelDependant[ T ], Int ]() // maps to use count

   def replacedBy( newModel: T ) {
      dependants.keysIterator.foreach( _.modelReplaced( model, newModel ))
   }

   def addDependant( l: MutableModelDependant[ T ]) {
      dependants += l -> (dependants.getOrElse( l, 0 ) + 1)
   }

   def removeDependant( l: MutableModelDependant[ T ]) {
      dependants.remove( l ).foreach( cnt => {
         if( cnt > 1 ) {
            dependants += l -> (cnt - 1)
         }
      })
   }
}