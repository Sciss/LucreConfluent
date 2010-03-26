package de.sciss.temporal

trait ContainerLike[ +Repr ] {
   def add( c: ContainerLike[ _ ]) : Repr
}

object Container {
   private var currentVar: ContainerLike[ _ ] = _

   def current = currentVar

   def use( c: ContainerLike[ _ ], thunk: => Unit ) {
      val oldC = currentVar
      currentVar = c
      try {
         thunk
      } finally {
         currentVar = oldC
      }
   }
}

class Container extends ContainerLike[ Container ] {
   def use( thunk: => Unit ) {
      Container.use( this, thunk )
   }

   def add( c: ContainerLike[ _ ]) : Container = {
      error( "Not yet implemented" )
   }
}