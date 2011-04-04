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
      def sub[ B ] : Repr[ B ]
   }

   trait MyRef[ A ] extends Ref[ A, MyRef ]

   trait MyAccess extends Access[ MyAccess ] {
      def head : MyRef[ this.type ]
      def head_=( r: MyRef[ this.type ] ) : Unit
      def tail : MyRef[ this.type ]
      def tail_=( r : MyRef[ this.type ] ) : Unit
   }

   def test( sys: System[ MyAccess ], v0: Version, v1: Version ) {
      val v2 = sys.in( v0 )( a => { a.tail = a.meld( v1 )( _.head ); a.version })
      val a3 = sys.in( v2 ) { a => a }
      val (v4, a4) = sys.in( v1 ) { a =>
         a.head = a.head
         (a.version, a)
      }
      // a3.head = a4.head // forbidden
   }
}