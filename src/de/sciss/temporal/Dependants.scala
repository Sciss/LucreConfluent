package de.sciss.temporal

import scala.collection.mutable.{ WeakHashMap }

trait MutableModelDependant[ T ] {
   def modelReplaced( oldModel: T, newModel: T ) : Unit

   override def finalize {
      println( "Finalized {" + this + "}" )
   }
}

trait MutableModel[ T ] {
   def addDependant( l: MutableModelDependant[ T ]) : MutableModelDependant[ T ]
//   def removeDependant( l: MutableModelDependant[ T ]) : Unit
//   def replacedBy( newModel: T ) : Unit
   def printDependants : Unit
}

trait MutableModelImpl[ T ] extends MutableModel[ T ] {
   model: T =>
   
   private val dependants = new WeakHashMap[ MutableModelDependant[ T ], Int ]() // maps to use count

   def replacedBy( newModel: T ) {
println( "=== ITER START {" + this + "}" )
      dependants.keysIterator.foreach( _.modelReplaced( model, newModel ))
//      val iter = dependants.underlying.keySet.iterator
//      while( iter.hasNext ) {
//         val dep = iter.next
//         if( dep.modelReplaced( model, newModel )) iter.remove
//      }
println( "=== ITER STOP {" + this + "}" )
      dependants.clear
   }

   def addDependant( l: MutableModelDependant[ T ]) : MutableModelDependant[ T ] = {
println( "{"+this+"}.addDependant( " + l + " )" )
      dependants += l -> (dependants.getOrElse( l, 0 ) + 1)
//printDependants
      l
   }

//   def removeDependant( l: MutableModelDependant[ T ]) {
//println( "{"+this+"}.removeDependant( " + l + " )" )
//      dependants.remove( l ).foreach( cnt => {
//         if( cnt > 1 ) {
//            dependants += l -> (cnt - 1)
//         }
//      })
//   }

   def printDependants {
      println( "Dependants of {" + this + "}:" )
      dependants.keysIterator.foreach( println( _ ))
   }
}