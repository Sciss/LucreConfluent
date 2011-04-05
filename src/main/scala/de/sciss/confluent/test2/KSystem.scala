package de.sciss.confluent.test2

import de.sciss.confluent.{HashedStoreFactory, StoreFactory}

object Path {
   def init: Path = new Impl

   private class Impl extends Path
}
trait Path

trait Ref[ A, Repr[ _ ]] {
   def sub[ B ]( b: B ) : Repr[ B ]
}

trait Access[ Repr ] {
   def path : Path
   def meld[ R[ _ ]]( p: Path )( fun: Repr => Ref[ _, R ]) : R[ this.type ]
}

//trait AccessFactory[ A ] {
//
//}

//object KSystem {
//   def apply[ A /* <: Access[ _ ] */ ] : KSystem[ A ] = new Impl
//
//   private class Impl[ A ] extends KSystem[ A ] {
//      def in[ T ]( v: Path )( fun: A => T ) : T = {
//         error( "NOT YET" )
//      }
//   }
//}
trait KSystem[ A /* <: Access[ _ ] */ ] {
   type Vertex

   def storeFactory : StoreFactory[ Vertex ]

   def in[ T ]( v: Path )( fun: A => T ) : T
}

trait KSystemImpl[ A ] extends KSystem[ A ] {
   def in[ T ]( v: Path )( fun: A => T ) : T = {
      error( "TODO" )
   }
}

object Test {
   trait MyAccess

   val sys = new KSystemImpl[ MyAccess ] {
      type Vertex = Int
      val storeFactory = new HashedStoreFactory[ Vertex ]
   }
}
