package de.sciss.confluent.test

object TypeSafePointerTest {

   trait Version {
      type Step <: Version
      def step: Step
   }

//   trait Version[ Next ] {
//      def step: Next
//   }

   trait Ctx[ V <: Version ] { def v: V; def step: Ctx[ V#Step ]}

//   type AnyCtx     = Ctx[ Version ]
   type AnyRf[ T ] = Ref[ _, T ]

//   object Ref {
////      implicit def access[ C <: Ctx, R, T ]( r: R )( implicit c: C, view: R => Ref[ C#V, T ]) : T = view( r ).access( c )
//      implicit def access2[ C <: Ctx, T ]( r: Ref[ C#V, T ])( implicit c: C ) : T = r.access( c )
////      implicit def access3[ V1 <: Version, C <: Ctx { type V = V1 }, T ]( r: Ref[ V1, T ])( implicit c: C ) : T = r.access( c )
//      implicit def substitute[ C <: Ctx, T ]( r: AnyRf[ T ])( implicit c: C ) : Ref[ C#V, T ] = r.substitute( c )
//   }

   trait Ref[ V <: Version, T ] {
//      type V <: Version

      def access( implicit c: Ctx[ V ]) : T
//      def access[ C <: Ctx { type V = V1 }]( implicit c: C ) : T
      def substitute[ V1 <: Version ]( implicit c: Ctx[ V1 ]) : Ref[ V1, T ]
   }

   trait Factory {
      def makeVar[ V1 <: Version, T ]( init: T )( implicit c: Ctx[ V1 ]) : Ref[ V1, T ]
   }

//   def shouldCompile1( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r

//   def shouldCompile2( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = {
//      val r1 = r.substitute( c )
//      r1.access( c )
//   }

   def shouldCompile2b[ V1 <: Version ]( r: AnyRf[ String ])( implicit c: Ctx[ V1 ]) : String = {
      val r1 = r.substitute( c )
      r1.access( c )
   }

   def shouldCompile3[ V1 <: Version ]( r: Ref[ V1, String ])( implicit c: Ctx[ V1 ]) : String = r.access( c )

   def shouldCompile4[ V1 <: Version ]( f: Factory )( implicit c: Ctx[ V1 ]) : String = {
      val r = f.makeVar( "Hallo" )
      r.access( c )
   }

//   def versionStep[ V1 ]( c: Ctx[ V1 ]) : Ctx[ _ ] = c // no importa

//   def shouldFail3[ V1 ]( f: Factory, c: Ctx[ V1 ]) : String = {
//      val r    = f.makeVar( "Hallo" )( c )
//      val c2   = versionStep( c )
//      r.access( c2 )
//   }

//   def shouldFail1( r: AnyRf[ String ])( implicit c: AnyCtx ) : String = r.access( c )

   def shouldCompile5[ V1 <: Version ]( r: Ref[ V1, String ])( implicit c: Ctx[ V1 ]) : String = r.access( c )

//   def shouldFail2[ V1, V2 <: V1 ]( r: Ref[ V1, String ], c: Ctx[ V2 ]) {
//      shouldCompile5( r )( c )
//   }

   def versionStep[ V1 <: Version ]( c: Ctx[ V1 ]) : Ctx[ V1#Step ] = c.step // = new CtxImpl( c.v.step )

//   def shouldFail4[ V1 <: Version ]( f: Factory, c: Ctx[ V1 ]) : String = {
//      val r    = f.makeVar( "Hallo" )( c )
//      val c2   = versionStep( c )
//      r.access( c2 )
//   }

   case class CtxImpl[ V <: Version ]( v: V ) extends Ctx[ V ] {
      def step: Ctx[ V#Step ] = CtxImpl( v.step )
   }

   case class VersionImpl( cnt: Int ) extends Version {
      type Step = Version
      def step : Version = VersionImpl( cnt + 1 )
   }

   val FirstVersion : Version = VersionImpl( 0 )

   def shouldCompile6( f: Factory ) {
      val c0   = new CtxImpl( FirstVersion )
      val r0   = f.makeVar( "Hallo" )( c0 )
      val c1   = versionStep( c0 )
      val r1   = f.makeVar( "Welt" )( c1 )
      r0.access( c0 )
      r1.access( c1 )
      val r0s  = r0.substitute( c1 )
      r0s.access( c1 )
   }

   object FactoryImpl extends Factory {
      def makeVar[ V1 <: Version, T ]( init: T )( implicit c: Ctx[ V1 ]) : Ref[ V1, T ] = RefImpl[ V1, T ]( init )
   }

   case class RefImpl[ V <: Version, T ]( t: T ) extends Ref[ V, T ] {
      def access( implicit c: Ctx[ V ]) : T = t
      def substitute[ V1 <: Version ]( implicit c: Ctx[ V1 ]) : Ref[ V1, T ] = RefImpl[ V1, T ]( t )
   }

//   def shouldFail6a( f: Factory ) {
//      val c0   = new CtxImpl( FirstVersion )
//      val r0   = f.makeVar( "Hallo" )( c0 )
//      val c1   = versionStep( c0 )
//      r0.access( c1 )
//   }
//
//   def shouldFail6b( f: Factory ) {
//      val c0   = new CtxImpl( FirstVersion )
//      val c1   = versionStep( c0 )
//      val r1   = f.makeVar( "Welt" )( c1 )
//      r1.access( c0 )
//   }


//   trait Var {
//      def set[ C <: Ctx ]( r: Ref[ C#V, String ])( implicit c: C ) : Unit
//      def get[ C <: Ctx ]( implicit c: C ) : Ref[ C#V, String ]
//   }


//   def shouldSucceed1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = Ref.access( r )
//   def shouldFail1[ C <: Ctx ]( r: Ref[ _ <: Version, String ])( implicit c: C ) : String = r.access
}