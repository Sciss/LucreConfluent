package de.sciss.confluent.test

object TypeSafePointerTest {
   trait Version

   trait Ctx {
      type V <: Version
   }

   object Ref {
      implicit def access[ C <: Ctx, R, T ]( r: R )( implicit c: C, view: R => Ref[ C#V, T ]) : T = view( r ).access( c )
      implicit def substitute[ C <: Ctx, T ]( r: Ref[ _ <: Version, T ])( implicit c: C ) : Ref[ C#V, T ] = r.substitute( c )
   }
   trait Ref[ V1 <: Version, T ] {
//      def access( implicit c: { type V = V1 }) : T
      def access( implicit c: { type V = V1 }) : T
      def substitute[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, T ]
   }

   trait Var {
      def set[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : Unit
      def get[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, String ]
   }

   trait Factory {
      def makeVar[ C <: Ctx, T ]( init: T )( implicit c: C ) : Ref[ C#V, T ]
   }

//   def shouldSucceed1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = Ref.access( r )
//   def shouldFail1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = r.access
}