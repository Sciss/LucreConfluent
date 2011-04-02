package de.sciss.confluent.test

object TypeSafePointerTest {

   trait Version

   trait Ctx {
      type V <: Version
   }

//   type AnyCtx     = Ctx[ _ <: Version ]
   type AnyRf[ T ] = Ref[ _ <: Version, T ]

   object Ref {
//      implicit def access[ C <: AnyCtx, R, T ]( r: R )( implicit c: C, view: R => Ref[ C#V, T ]) : T = view( r ).access( c )
//      implicit def access2[ C <: Ctx, T ]( r: Ref[ C#V, T ])( implicit c: C ) : T = r.access( c )
      implicit def access3[ V1 <: Version, C <: Ctx { type V = V1 }, T ]( r: Ref[ V1, T ])( implicit c: C ) : T = r.access( c )
      implicit def substitute[ C <: Ctx, T ]( r: AnyRf[ T ])( implicit c: C ) : Ref[ C#V, T ] = r.substitute( c )
   }
   trait Ref[ V1 <: Version, T ] {
//      type V <: Version

//      def access( implicit c: Ctx[ V1 ]) : T
      def access[ C <: Ctx { type V = V1 }]( implicit c: C ) : T
      def substitute[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, T ]
   }

   trait Factory {
//      def makeVar[ C <: Ctx, T ]( init: T )( implicit c: C ) : Ref[ C#V, T ]
      def makeVar[ V1 <: Version, C <: Ctx { type V = V1 }, T ]( init: T )( implicit c: C ) : Ref[ V1, T ]
   }

//   def shouldCompile1( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r

   def shouldCompile2( r: AnyRf[ String ])( implicit c: Ctx ) : String = {
      val r1 = Ref.substitute( r )
      r1.access( c )
   }

//   def shouldCompile3[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : String = r.access( c )
   def shouldCompile3b[ V1 <: Version, C <: Ctx { type V = V1 }]( r: Ref[ V1, String ])( implicit c: C ) : String = r.access( c )

   def shouldCompile4[ C <: Ctx ]( f: Factory )( implicit c: C ) : String = {
      val r = f.makeVar[ C#V, C, String ]( "Hallo" )
      r.access( c )
   }

//   def shouldCompile4b[ V1 <: Version, C <: Ctx { type V = V1 }]( f: Factory )( implicit c: C ) : String = {
//      val r = f.makeVar( "Hallo" )
//      r.access( c )
//   }

   def versionStep( c: Ctx ) : Ctx = c // no importa

//   def shouldFail3[ C <: Ctx ]( f: Factory, c: C ) : String = {
//      val r    = f.makeVar( "Hallo" )( c )
//      val c2   = versionStep( c )
//      r.access( c2 )
//   }

//   def shouldFail1( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r.access( c )

//   def test[ V <: Version ]( r: Ref[ V, String ])( implicit c: Ctx[ V ]) : String = r.access( c )

//   def shouldFail2( r: AnyRf[ String ], c: AnyCtx ) {
//      test( r )( c )
//   }

//   trait Var {
//      def set[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : Unit
//      def get[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, String ]
//   }


//   def shouldSucceed1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = Ref.access( r )
//   def shouldFail1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = r.access
}