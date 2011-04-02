package de.sciss.confluent.test

object TypeSafePointerTest {

   trait Version

   trait Ctx[ V1 <: Version ]

   type AnyCtx     = Ctx[ _ <: Version ]
   type AnyRf[ T ] = Ref[ _ <: Version, T ]

   object Ref {
      implicit def access[ V <: Version, R, T ]( r: R )( implicit c: Ctx[ V ], view: R => Ref[ V, T ]) : T = view( r ).access( c )
      implicit def substitute[ V <: Version, T ]( r: AnyRf[ T ])( implicit c: Ctx[ V ]) : Ref[ V, T ] = r.substitute( c )
   }
   trait Ref[ V1 <: Version, T ] {
      def access( implicit c: Ctx[ V1 ]) : T
      def substitute[ V <: Version ]( implicit c: Ctx[ V ]) : Ref[ V, T ]
   }

   trait Factory {
      def makeVar[ V <: Version, T ]( init: T )( implicit c: Ctx[ V ]) : Ref[ V, T ]
   }

   // def shouldCompile1[ V <: Version ]( r: AnyRf[ String ])( implicit c: Ctx[ V ]) : String = r

   def shouldCompile2[ V <: Version ]( r: AnyRf[ String ])( implicit c: Ctx[ V ]) : String = {
      val r1 = Ref.substitute( r )
      r1.access( c )
   }

   // def shouldFail[ V <: Version ]( r: AnyRf[ String ])( implicit c: Ctx[ V ]) : String = r.access( c )

//   trait Var {
//      def set[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : Unit
//      def get[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, String ]
//   }


//   def shouldSucceed1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = Ref.access( r )
//   def shouldFail1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = r.access
}