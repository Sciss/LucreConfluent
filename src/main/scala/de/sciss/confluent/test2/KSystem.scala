package de.sciss.confluent.test2

import de.sciss.confluent.{HashedStoreFactory, PathLike, StoreFactory}

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
   def meld[ R[ _ ]]( p: Path )( fun: Repr => Ref[ _, R ]) : R[ Repr ]
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

   def storeFactory : StoreFactory[ PathLike[ Vertex ]]

   def in[ T ]( v: Path )( fun: A => T ) : T
}

//trait AccessPrepare[ A <: AnyRef ] {
//   val a: A
//   def apply[ T ]( fun: a.type => T ) : T
//}

trait KSystemImpl[ A ] extends KSystem[ A ] {
   def in[ T ]( v: Path )( fun: A => T ) : T = {
      sys.error( "TODO" )
   }
}

object Test {
   trait MyAccess <: Access[ MyAccess ] {
      def head : CLinkOption[ MyAccess, Int ]
      def head_=( r: CLinkOption[ MyAccess, Int ] ) : Unit
   }

   object CLinkOption {
      def empty[ A <: Access[ _ ], V ]( implicit a: A ) : CNoLink[ A, V ] = sys.error( "TODO" )
      def single[ A <: Access[ _ ], V ]( init: V )( implicit a: A ) : CLink[ A, V ] = sys.error( "TODO" )
//      def single[ A, V ]( init: V )( implicit a: A /*, ev: A <:< Access[ _ ]*/) : CLink[ A, V ] = error( "TODO" )
   }
   trait Funk[ A ]
   sealed trait CLinkOption[ A, V ] extends Ref[ A, ({type λ[α] = CLinkOption[ α, V ]})#λ ] {
      def lift: Option[ CLink[ A, V ]]
      def reverse: CLinkOption[ A, V ]
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

      val v0 = sys.in( Path.init ) { implicit a =>
//         implicit val fuckyou: a.type = a
         val w0   = CLinkOption.single( 2 )
         val w1   = CLinkOption.single( 1 )
         w0.next  = w1
         a.head   = w0 // CLinkOption.empty
//         a.head   = CLinkOption.empty[ a.type, Int ]( a ) // works, too
//         a.head
         a.path
      }

      val v1 = sys.in( v0 ) { implicit a =>
         a.head   = a.head.reverse
         a.path
      }
   }
}
