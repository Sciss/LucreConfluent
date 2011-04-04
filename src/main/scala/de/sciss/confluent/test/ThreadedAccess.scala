package de.sciss.confluent.test

// screw the type safety. doesn't seem to be possible with serialization etc. anyway
object ThreadedAccess {
   trait World[ Repr ]

   trait Ctx {
      type Next <: Ctx
   }

//   trait State[ C1 ]
   trait Ref[ C1 <: Ctx ] {
      def set( link: Ref[ C1 ])( implicit c: C1 )
      def get( implicit c: C1 ): (C1#Next, Ref[ C1#Next ])
   }

//   trait System[ W <: World[ W ]]
   trait System {
//      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
//      def t[ W1 <: W, T ]( pred: W1 )( fun: W1#Next => T ) : T
      def in( v: Version ) : Executer[ Access ]
//      def inn[ V ]( v: V ) : Executer[ Access2[ V ]]
   }

   trait Executer[ W ] {
      def apply[ T ]( fun: W => T ) : T
   }

//   object Access {
//      implicit def self[ X <: Access2[ _ ]]( a: X ) = a
//   }
   trait Access {
      def head : Kuku[ this.type ]
      def head_=( r: Kuku[ this.type ] ) : Unit
      def tail : Kuku[ this.type ]
      def tail_=( r : Kuku[ this.type ] ) : Unit
      def version : Version

      def meld[ R[ _ ]]( v: Version )( fun: Access => RefX[ _, R ]) : R[ this.type ]
   }

//   def test[ C1 <: Ctx ]( r1: Ref[ C1 ])( implicit c1: C1 ) {
//      val (c2, r2) = r1.get
//      val (c3, r3) = r2.get
//      r1.set( r3 )
//   }

   object Version {
      def init: Version = error( "egal" )
   }

   trait Version

   trait RefX[ A, Repr[ _ ]] {
      def sub[ B ] : Repr[ B ]
   }

   trait Kuku[ A ] extends RefX[ A, Kuku ]

//   trait Access extends World[ Access ] {
//      def head : RefX
//      def head_=( r: RefX ) : Unit
//      def tail : RefX
//      def tail_=( r : RefX ) : Unit
//      def version : Version
//   }

   def gugu( sys: System, v1: Version ) {
      val v0 = Version.init
      val v2 = sys.in( v0 )( a => { a.tail = a.meld( v1 )( _.head ); a.version })
//      val a0 = sys.inn[ v0.type ]( v0 ) { a => a }
//      val a1 = sys.inn[ v1.type ]( v1 ) { a => a }
      val a0 = sys.in( v0 ) { a => a }
      val a1 = sys.in( v1 ) { implicit a =>
         a.head = a.head // ( Access2.self[ a.type ]( a ))
         a
      }
//      a1.head = a0.head
   }
}
