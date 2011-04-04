package de.sciss.confluent.test

// screw the type safety. doesn't seem to be possible with serialization etc. anyway
object ThreadedAccess {
   trait System[ A <: Access[ _ ]] {
      def in[ T ]( v: Version )( fun: A => T ) : T
   }

   trait Access[ Repr ] {
      def version : Version
      def meld[ R[ _ ]]( v: Version )( fun: Repr => Ref[ _, R ]) : R[ this.type ]
   }

   trait Version

   trait Ref[ A, Repr[ _ ]] {
      def sub[ B ]( b: B ) : Repr[ B ]
   }

   object MyRef {
      def apply[ A <: MyAccess ]( implicit a: A ) : MyRef[ A ] = new Impl[ A ]( a )

      private class Impl[ A /* <: MyAccess */ ]( a: A ) extends MyRef[ A ] {
         def sub[ B ]( b: B ) = new Impl[ B ]( b )
         def schnuppi( implicit ev: A <:< MyAccess ) = a.gagaism
      }
   }
   trait MyRef[ A ] extends Ref[ A, MyRef ] {
      def schnuppi( implicit ev: A <:< MyAccess ) : Int
   }

   trait MyAccess extends Access[ MyAccess ] {
      def head : MyRef[ this.type ]
      def head_=( r: MyRef[ this.type ] ) : Unit
      def tail : MyRef[ this.type ]
      def tail_=( r : MyRef[ this.type ] ) : Unit

      def gagaism : Int
   }

   def test( sys: System[ MyAccess ], v0: Version, v1: Version ) {
      val v2 = sys.in( v0 ) { a =>
         val x = a.meld( v1 )( _.head )
         a.tail = x
         println( x.schnuppi )
         a.version
      }
      val a3 = sys.in( v2 ) { a => a }
      val (v4, a4) = sys.in( v1 ) { a =>
         a.head = a.head
         println( a.head.schnuppi )
         (a.version, a)
      }
      // a3.head = a4.head // forbidden
   }
}