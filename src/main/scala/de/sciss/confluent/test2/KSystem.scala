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
   trait MyAccess <: Access[ MyAccess ] {
      def head : CLinkOption[ this.type, Int ]
      def head_=( r: CLinkOption[ this.type, Int ] ) : Unit
   }

   object CLinkOption {
      def empty[ A <: Access[ _ ], V ]( implicit a: A ) : CNoLink[ A, V ] = error( "TODO" )
      def single[ A <: Access[ _ ], V ]( init: V )( implicit a: A ) : CLink[ A, V ] = error( "TODO" )
   }
   trait Funk[ A ]
   sealed trait CLinkOption[ A, V ] extends Ref[ A, ({type λ[α] = CLinkOption[ α, V ]})#λ ] {
      def lift: Option[ CLink[ A, V ]]
   }
   trait CLink[ A, V ] extends CLinkOption[ A, V ] {
      def value: V
      def value_=( v: V ) : Unit
      def next: CLinkOption[ A, V ]
      def next_=( l: CLinkOption[ A, V ]) : Unit
      def lift: Option[ CLink[ A, V ]] = Some( this )
   }
   trait CNoLink[ A, V ] extends CLinkOption[ A, V ] {
      def lift: Option[ CLink[ A, V ]] = None
   }

   def run {
      val sys = new KSystemImpl[ MyAccess ] {
         type Vertex       = Int
         val storeFactory  = new HashedStoreFactory[ Vertex ]
      }

      val v1 = sys.in( Path.init ) { implicit a =>
         val w0   = CLinkOption.single[ a.type, Int ]( 2 )( a )
         val w1   = CLinkOption.single[ a.type, Int ]( 1 )( a )
         w0.next  = w1
         a.head   = w0 // CLinkOption.empty
//         a.head   = CLinkOption.empty[ a.type, Int ]( a ) // works, too
//         a.head
         a.path
      }

      sys.in( Path.init ) { implicit a =>
//         a.head = l1 // forbidden
      }
   }
}
