package de.sciss.confluent.test

object TypeSafePointerTest {

   trait Version

   trait Ctx[ +V1 <: Version ] {
      type V = V1 // <: Version
   }

   type AnyCtx     = Ctx[ _ <: Version ]
   type AnyRf[ T ] = Ref[ _ <: Version, T ]

   object Ref {
      implicit def access[ C <: AnyCtx, R, T ]( r: R )( implicit c: C, view: R => Ref[ C#V, T ]) : T = view( r ).access( c )
      implicit def substitute[ C <: AnyCtx, T ]( r: AnyRf[ T ])( implicit c: C ) : Ref[ C#V, T ] = r.substitute( c )
   }
   trait Ref[ V1 <: Version, T ] {
      def access( implicit c: Ctx[ V1 ]) : T
      def substitute[ C <: AnyCtx ]( implicit c: C ) : Ref[ C#V, T ]
   }

   trait Factory {
      def makeVar[ C <: AnyCtx, T ]( init: T )( implicit c: C ) : Ref[ C#V, T ]
   }

//   def shouldCompile1( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r

   def shouldCompile2( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = {
      val r1 = Ref.substitute( r )
      r1.access( c )
   }

//   def shouldFail( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r.access( c )

//   trait Var {
//      def set[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : Unit
//      def get[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, String ]
//   }


//   def shouldSucceed1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = Ref.access( r )
//   def shouldFail1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = r.access
}