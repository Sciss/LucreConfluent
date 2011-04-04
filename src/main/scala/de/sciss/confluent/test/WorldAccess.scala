package de.sciss.confluent.test

object WorldAccess {
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

//      r1.set( s2 )
//      r2.set( s1 )
   }
}