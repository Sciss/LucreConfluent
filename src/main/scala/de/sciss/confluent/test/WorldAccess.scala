package de.sciss.confluent.test

object WorldAccess1 {
   trait World {
//      type C
      type Next <: World
      def next : Next
   }

   trait StateFactory { def make[ W <: World ]( w: W ) : State[ W ]}
   trait State[ C ]

   trait RefFactory { def make[ W <: World ]( w: W ) : Ref[ W ]}
   trait Ref[ C ] { def set( state: State[ C ])}

   def test( sf: StateFactory, rf: RefFactory, w1: World ) {
      val s1 = sf.make( w1 )
      val r1 = rf.make( w1 )
      r1.set( s1 )

      val w2 = w1.next
      val s2 = sf.make( w2 )
      val r2 = rf.make( w2 )
      r2.set( s2 )

      val w3 = w2.next
      val s3 = sf.make( w3 )
      val r3 = rf.make( w3 )
      r3.set( s3 )

//      r1.set( s2 )
//      r2.set( s1 )
   }
}

object WorldAccess2 {
   trait World {
//      type C
      type Next <: World
      def next : Next
   }

   trait StateFactory { def make[ W <: World ]( w: W ) : State[ W ]}
   trait State[ C ]

   trait RefFactory { def make[ W <: World ]( w: W ) : Ref[ W ]}
   trait Ref[ C ] { def set( state: State[ C ])}

   trait Cursor[ W <: World ] {
      def t[ T ]( fun: W => T )
   }

   def test( sf: StateFactory, rf: RefFactory, csr: Cursor[ _ <: World ]) {
      csr.t { w1 =>
         val s1 = sf.make( w1 )
         val r1 = rf.make( w1 )
         r1.set( s1 )

         val w2 = w1.next
         val s2 = sf.make( w2 )
         val r2 = rf.make( w2 )
         r2.set( s2 )

         val w3 = w2.next
         val s3 = sf.make( w3 )
         val r3 = rf.make( w3 )
         r3.set( s3 )

//      r1.set( s2 )
//      r2.set( s1 )
      }
   }
}

//object WorldAccess3 {
//   trait World[ Repr ] {
////      type C
//
//      type Next <: Repr
//      def next : Next // Next
//   }
//
////   trait StateFactory[ W <: World[ W ]] { def make[ W1 <: W ]( w: W1 ) : State[ W1 ]}
//   trait State[ W1 ]
//
////   trait RefFactory[ W <: World[ W ]] { def make[ W1 <: W ]( w: W1 ) : Ref[ W1 ]}
//   trait Ref[ W1 ] { def set( state: State[ W1 ])}
//
//   trait System[ W <: World[ W ]] {
//      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
//      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
////      def makeCursor : Cursor[ W ]
//      def t[ T ]( fun: W => T )
//   }
//
//   trait CursorFactory[ W <: World[ W ]] { def make : Cursor[ W ]}
//
//   trait Cursor[ W1 ] {
//      def t[ T ]( fun: W1 => T )
//   }
//
////   def test[ W <: World[ W ]]( sf: StateFactory[ W ], rf: RefFactory[ W ], cf: CursorFactory[ W ]) {
//   def test[ W <: World[ W ]]( sys: System[ W ]) {
////      val csr = cf.make
////      val csr = sys.makeCursor
//      sys.t { w1 =>
////         val s1 = sf.make( w1 )
////         val r1 = rf.make( w1 )
//         val s1 = sys.makeState( w1 )
//         val r1 = sys.makeRef( w1 )
//         r1.set( s1 )
//
//         val w2 = w1.next
////         val s2 = sf.make( w2 )
////         val r2 = rf.make( w2 )
//         val s2 = sys.makeState( w2 )
//         val r2 = sys.makeRef( w2 )
//         r2.set( s2 )
//
//         val w3 = w2.next
////         val s3 = sf.make( w3 )
////         val r3 = rf.make( w3 )
//         val s3 = sys.makeState( w3 )
//         val r3 = sys.makeRef( w3 )
//         r3.set( s3 )
//
////         r1.set( s2 )
////         r2.set( s1 )
//      }
//   }
//}

object WorldAccess4 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
      def next : Next
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
      def init[ T ] : W
   }

   trait CursorFactory[ W <: World[ W ]] { def make : Cursor[ W ]}

   trait Cursor[ W1 ] {
      def t[ T ]( fun: W1 => T )
   }

   def test[ W <: World[ W ]]( sys: System[ W ]) {
      val w1 = sys.init
      val s1 = sys.makeState( w1 )
      val r1 = sys.makeRef( w1 )
      r1.set( s1 )   // ok

      val w2 = w1.next
      val s2 = sys.makeState( w2 )
      val r2 = sys.makeRef( w2 )
      r2.set( s2 )   // ok

//         r1.set( s2 ) // ok, it's forbidden!
//         r2.set( s1 ) // ok, it's forbidden!

      val w3 = w2.next
      val s3 = sys.makeState( w3 )  // ok
      val r3 = sys.makeRef( w3 )    // ok
      r3.set( s3 )

      val w4 = sys.init
      val r4 = sys.makeRef( w4 )
      r4.set( s1 )   // ouch! should be forbidden
   }
}

object WorldAccess5 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
      def next : Next
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
      def t[ T ]( fun: W => T ) : T
   }

   trait CursorFactory[ W <: World[ W ]] { def make : Cursor[ W ]}

   trait Cursor[ W1 ] {
      def t[ T ]( fun: W1 => T )
   }

   def test[ W <: World[ W ]]( sys: System[ W ]) {
      val s1 = sys.t { w1 =>
         val s1 = sys.makeState( w1 )
         val r1 = sys.makeRef( w1 )
         r1.set( s1 )   // ok

         val w2 = w1.next
         val s2 = sys.makeState( w2 )
         val r2 = sys.makeRef( w2 )
         r2.set( s2 )   // ok

//         r1.set( s2 ) // ok, it's forbidden!
//         r2.set( s1 ) // ok, it's forbidden!

         val w3 = w2.next
         val s3 = sys.makeState( w3 )  // ok
         val r3 = sys.makeRef( w3 )    // ok
         r3.set( s3 )

         s1
      }

      sys.t { w4 =>
         val r4 = sys.makeRef( w4 )
         r4.set( s1 )   // ouch! should be forbidden
      }
   }
}

object WorldAccess6 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
      def t[ W1 <: W, T ]( pred: W1 )( fun: W1#Next => T ) : T
   }

   def test[ W <: World[ W ]]( sys: System[ W ], w0: W ) {
      val (s1, w1) = sys.t( w0 ) { implicit w1 =>
         val s1 = sys.makeState
         val r1 = sys.makeRef
         r1.set( s1 )   // ok
         (s1, w1)
      }

      val w2 = sys.t( w1 ) { implicit w2 =>
         val s2 = sys.makeState
         val r2 = sys.makeRef
         r2.set( s2 )   // ok
//         r1.set( s2 ) // yes, forbidden!
//         r2.set( s1 ) // yes, forbidden!
         w2
      }

      val w3 = sys.t( w2 ) { w3 =>
         val s3 = sys.makeState( w3 )  // ok
         val r3 = sys.makeRef( w3 )    // ok
         r3.set( s3 )
//         r3.set( s1 )   // still forbidden :)
         w3
      }

      sys.t( w3 ) { w4 =>
         val r4 = sys.makeRef( w4 )
//         r4.set( s1 )   // ok, it's forbidden!
      }
   }
}

//object WorldAccess7 {
//   trait World[ Repr <: World[ _ ]] {
//      type Next <: Repr
//   }
//
//   trait State[ W1 ]
//   trait Ref[ W1 ] { def set( state: State[ W1 ])}
//
//   trait System[ W <: World[ W ]] {
////      type Hmmm = _ <: W
//      var hmmm : W
//
//      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
//      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
////      def oracle : Hmmm
//      def t[ T ]( fun: hmmm.type => T ) : T
//   }
//
//   def test[ W <: World[ W ]]( sys: System[ W ], w0: W ) {
//      val s1 = sys.t { implicit w1 =>
//         val s1 = sys.makeState
//         val r1 = sys.makeRef
//         r1.set( s1 )   // ok
//         s1
//      }
//
//      sys.t { implicit w2 =>
//         val s2 = sys.makeState
//         val r2 = sys.makeRef
//         r2.set( s2 )   // ok
////         r1.set( s2 ) // yes, forbidden!
//         r2.set( s1 ) // yes, forbidden!
//      }
//
//      sys.t { w3 =>
//         val s3 = sys.makeState( w3 )  // ok
//         val r3 = sys.makeRef( w3 )    // ok
//         r3.set( s3 )
//      }
//
//      sys.t { w4 =>
//         val r4 = sys.makeRef( w4 )
//         r4.set( s1 )   // ok, it's forbidden!
//      }
//   }
//}

object WorldAccess8 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
      def next: Next
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
      def pred : W
      def t[ W1 <: W, T ]( pr: W1#Next = pred.next )( fun: W1 => T ) : T
   }

   def test[ W <: World[ W ]]( sys: System[ W ]) {
      val (r1, s1) = sys.t() { implicit w1 =>
         val s1 = sys.makeState
         val r1 = sys.makeRef
         r1.set( s1 )   // ok
         (r1, s1)
      }

      sys.t() { implicit w2 =>
         val s2 = sys.makeState
         val r2 = sys.makeRef
         r2.set( s2 )   // ok
         r1.set( s2 ) // yes, forbidden!
         r2.set( s1 ) // yes, forbidden!
      }

      sys.t() { w3 =>
         val s3 = sys.makeState( w3 )  // ok
         val r3 = sys.makeRef( w3 )    // ok
         r3.set( s3 )
//         r3.set( s1 )
         w3
      }

      sys.t() { w4 =>
         val r4 = sys.makeRef( w4 )
//         r4.set( s1 )   // ok, it's forbidden!
      }
   }
}

//object WorldAccess9 {
//   trait World[ Repr <: World[ _ ]] {
//      type Next <: Repr
//   }
//
//   trait State[ W1 ]
//   trait Ref[ W1 ] { def set( state: State[ W1 ])}
//
//   trait System[ W <: World[ W ]] {
//      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
//      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
//      def pred : W
//      def t[ W1 <: W, T ]( fun: W1#Next => T )( pr: W1 = pred ) : T
//   }
//
//   def test[ W <: World[ W ]]( sys: System[ W ]) {
//      val s1 = sys.t { implicit w1 =>
//         val s1 = sys.makeState
//         val r1 = sys.makeRef
//         r1.set( s1 )   // ok
//         (s1, w1)
//      }
//
//      sys.t { implicit w2 =>
//         val s2 = sys.makeState
//         val r2 = sys.makeRef
//         r2.set( s2 )   // ok
////         r1.set( s2 ) // yes, forbidden!
////         r2.set( s1 ) // yes, forbidden!
//      }
//
//      sys.t { w3 =>
//         val s3 = sys.makeState( w3 )  // ok
//         val r3 = sys.makeRef( w3 )    // ok
//         r3.set( s3 )
////         r3.set( s1 )
//         w3
//      }
//
//      sys.t( sys.pred ) { w4 =>
//         val r4 = sys.makeRef( w4 )
////         r4.set( s1 )   // ok, it's forbidden!
//      }
//   }
//}

object WorldAccess10 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def t[ T ]( fun: W => T ) : T
   }

   trait SystemRef[ W <: World[ W ]] {
//      def get : System[ _ <: W ]
      def iterator : Iterator[ System[ W ]]
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[ W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
   }

   def test[ W <: World[ W ]]( sr: SystemRef[ W ]) {
      val iter = sr.iterator
      val (s1, r1) =
      iter.next.t { implicit w1 =>
         val s1 = sr.makeState( w1 )
         val r1 = sr.makeRef( w1 )
         r1.set( s1 )   // ok
         (s1, r1)
      }

      iter.next.t { implicit w2 =>
         val s2 = sr.makeState
         val r2 = sr.makeRef
         r2.set( s2 )   // ok
         r1.set( s2 ) // yes, forbidden!
//         r2.set( s1 ) // yes, forbidden!
         w2
      }
//
//      val sys3 = sr.get
//      sys3.t( w2 ) { implicit w3 =>
//         val s3 = sys3.makeState
//         val r3 = sys3.makeRef
//         r3.set( s3 )
////         r3.set( s1 )   // still forbidden :)
//         w3
//      }
//
//      val sys4 = sr.get
//      sys4.t { implicit w4 =>
//         val r4 = sys4.makeRef
////         r4.set( s1 )   // ok, it's forbidden!
//      }
   }
}

object WorldAccess11 {
   trait World[ Repr <: World[ _ ]] {
      type Next <: Repr
   }

   trait State[ W1 ]
   trait Ref[ W1 ] { def set( state: State[ W1 ])}

   trait System[ W <: World[ W ]] {
      def makeState[ W1 <: W ]( implicit w: W1 ) : State[ W1 ]
      def makeRef[   W1 <: W ]( implicit w: W1 ) : Ref[ W1 ]
      def t[ W1 <: W ]( implicit pred: W1 ) : Applicator[ W1 ]
   }

   trait Applicator[ W <: World[ _ ]] {
      def apply[ T ]( fun: W#Next => T ) : T
   }

   def test[ W <: World[ W ]]( sys: System[ W ], w0: W ) {
      var pred: W = w0 // null.asInstanceOf[ W ]
      implicit def gimme: W = pred

      val (s1, r1, w1) = sys.t.apply { implicit w1 =>
         val s1 = sys.makeState
         val r1 = sys.makeRef
         r1.set( s1 )   // ok
         (s1, r1, w1)
      }
      pred = w1

      pred = sys.t.apply { implicit w2 =>
         val s2 = sys.makeState
         val r2 = sys.makeRef
         r2.set( s2 )   // ok
         r1.set( s2 ) // yes, forbidden!
         r2.set( s1 ) // yes, forbidden!
         w2
      }

//      val w3 = sys.t( w2 ) { w3 =>
//         val s3 = sys.makeState( w3 )  // ok
//         val r3 = sys.makeRef( w3 )    // ok
//         r3.set( s3 )
////         r3.set( s1 )   // still forbidden :)
//         w3
//      }
//
//      sys.t( w3 ) { w4 =>
//         val r4 = sys.makeRef( w4 )
////         r4.set( s1 )   // ok, it's forbidden!
//      }
   }
}
