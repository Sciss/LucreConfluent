package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree
import concurrent.stm.{InTxn, TxnExecutor}

object World {
//   def apply[ C1 <: KSystem.Ctx, A ]( implicit c: C1, sys: KSystem ) : World[ C1, A ] =
//      new Impl( sys.refVar[ C1, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]( CList.empty[ C1, A ]))
//
//   private class Impl[ C1 <: KSystem.Ctx, A ]( listRef: KSystem.RefVar[ ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]) extends World[ C1, A ] {
//      def list[ C1 <: KSystem.Ctx ]( implicit c: C1 ) : CList[ C1, A ] = listRef.get
//      def list_=[ C1 <: KSystem.Ctx ]( l: CList[ C1, A ])( implicit c: C1 ) : Unit = listRef.set( l )
//
//      def access[ C <: KSystem.Ctx ]( post: Path ) : World[ C, A ] = new Impl[ C, A ]( listRef )
//   }
}
trait World[ P ] extends Mutable[ P, World[ P ]] {
//   def head( implicit c: KCtx ) : CList
   // KSystem.Var[ Option[ CList[ KSystem.Ctx, KSystem.Var, Int ]]] // = None
   def list : CList[ P, Int ]
   def list_=( l: CList[ P, Int ]) : Unit
}

//object WorldUtil {
//   def getAndSubstitute[ T ]( s: Store[ Version, Option[ (Path, T )]], p: Path ) : Option[ (Path, T )] = {
//      s.getWithPrefix( p ).flatMap {
//         case (Some( (ap, v) ), pre) =>
////            println( "getAndSubstitute p = " + p.toList + " -> pre is " + pre + ", ap = " + ap.toList )
//            Some( ap.++( p.drop( pre )) -> v )
//         case _ => None
//      }
//   }
//}

//trait Access[ P ] { def seminalPath: P }

object CList {
//   var DEBUG_PRINT = true

   def empty[ C, T ]( implicit ctx: C, sys: System[ _, C, _ ]) : CList[ C, T ] = new CNilImpl[ C, T ]( sys.newMutable )
   def apply[ C <: Ct, T ]( elems: T* )( implicit path: C, sys: System[ _, C, _ ], mf: ClassManifest[ T ]) : CList[ C, T ] = {
//      val p = c.writePath.seminalPath
      elems.iterator.foldRight[ CList[ C, T ]]( new CNilImpl[ C, T ]( path ))( (v, tail) => {
         val headRef = sys.emptyVal[ T ]
         headRef.set( v )
         val tailRef = sys.emptyRef[ CList[ C, T ]]
         tailRef.set( tail )
         new CConsImpl[ C, T ]( path, sys, headRef, tailRef )
      })
//      error( "No functiona" )
   }

   private class CNilImpl[ C, T ]( val path: C ) extends CNil[ C, T ] {
      def substitute( path: C ) = new CNilImpl[ C, T ]( path )
//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = new CNilImpl[ C, A ]
      def inspect { println( "CNil[ " + path + ", ? ]")}

      override def toString = "CNil[" + path + "]"
   }

//   private type ListHolder[ A ] = KSystem.RefVar[ CList[ _ <: KSystem.Ctx, A ]]

   private class CConsImpl[ C, T ]( val path: C, sys: System[ _, C, _ ], val headRef: Val[ C, T ], tailRef: Ref[ C, CList[ C, T ]])
   extends CCons[ C, T ] {
      def head : T = headRef.get( path ) // error( "NO FUNCTIONA" ) // headRef.get( c )
      def head_=( a: T ) : Unit = headRef.set( a )( path ) // error( "NO FUNCTIONA" ) // headRef.set( a )
      def tail : CList[ C, T ] = tailRef.get( path ) // error( "NO FUNCTIONA" ) // tailRef.get[ C1 ]
      def tail_=( l: CList[ C, T ]) : Unit = tailRef.set( l )( path ) // error( "NO FUNCTIONA" ) // tailRef.set( l )

//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = {
//         new CConsImpl[ C, A ]( path ++ post, headRef, tailRef )
//      }

      def inspect {
         println( ":::::::: CCons[ " + path + "] ::::::::" )
         println( "  -head:" )
         headRef.inspect( path )
         println( "  -tail:" )
         tailRef.inspect( path )
      }

      def substitute( path: C ) = new CConsImpl[ C, T ]( path, sys, headRef, tailRef )

//      def substitute( path: P ) : CCons[ P, T ] = new CConsImpl( a, sys[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ] = {
//         val spath =
//         CConsImpl( spath, headRef, tailRef )
//      }

//      override def toString : String = if( DEBUG_PRINT )
      override def toString = "CCons[" + path + "]"

      def reverse : CList[ C, T ] = {
         var succ       = CList.empty[ C, T ]( sys.newMutable( path ), sys )
         var keepGoin   = true
         var pred: CCons[ C, T ] = this
         while( keepGoin ) {
            val next       = pred.tail
            pred.tail      = succ
            next match {
               case cns: CCons[ _, _ ] =>
                  succ  = pred
                  pred  = cns.asInstanceOf[ CCons[ C, T ]]
               case _ => keepGoin = false
            }
         }
         pred
      }
   }
}
// Partial2U[ KSystem.Ctx, CList, A ]#Apply
//extends Access[ KSystem.Ctx, Path, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]
sealed trait CList[ C, T] extends Mutable[ C, CList[ C, T ]] {
   def headOption : Option[ CCons[ C, T ]]
   def lastOption : Option[ CCons[ C, T ]]
   def drop( n: Int ) : CList[ C, T ]
   def reverse : CList[ C, T ]
   def toList : List[ T ]
   def iterator : Iterator[ CCons[ C, T ]]
   def inspect : Unit
}
trait CNil[ C, T ] extends CList[ C, T ] {
   def headOption : Option[ CCons[ C, T ]] = None
   def lastOption : Option[ CCons[ C, T ]] = None
   def drop( n: Int ) : CList[ C, T ] = this
   def reverse : CList[ C, T ] = this
   def toList : List[ T ] = Nil
   def iterator : Iterator[ CCons[ C, T ]] = Iterator.empty
}
trait CCons[ C, T ] extends CList[ C, T ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head : T
   def head_=( h: T ) : Unit
   def tail : CList[ C, T ]
   def tail_=( l: CList[ C, T ]) : Unit

   def headOption : Option[ CCons[ C, T ]] = Some( this )
   def lastOption : Option[ CCons[ C, T ]] = Some( last )

   def iterator : Iterator[ CCons[ C, T ]] = error( "NO FUNCTIONA" )

   def last : CCons[ C, T ] = {
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         res.tail match {
            case cns: CCons[ _, _ ] => res = cns.asInstanceOf[ CCons[ C, T ]]
            case _ => keepGoin = false
         }
      }
      res
   }

   def drop( n: Int ) : CList[ C, T ] = {
      var res        = this
      var keepGoin   = n
      while( keepGoin > 0 ) {
         keepGoin -= 1
         res.tail match {
            case cns: CCons[ _, _ ] => res = cns.asInstanceOf[ CCons[ C, T ]]
            case nil: CNil[ _, _ ]  => return nil.asInstanceOf[ CNil[ C, T ]]
         }
      }
      res
   }

   def reverse : CList[ C, T ]

   def toList : List[ T ] = {
      val b          = List.newBuilder[ T ]
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         b += res.head
         res.tail.headOption match {
            case Some( head ) => res = head
            case None => keepGoin = false
         }
      }
      b.result
   }
}

object WorldTest {
   def main( args: Array[ String ]) { new WorldTest }
}

object WorldFactory { def apply[ P ] = new WorldFactory[ P ]}
class WorldFactory[ P ] extends AccessProvider[ P, World[ P ]] {
   def init( f: RefFactory[ P ])( implicit path: P ) : World[ P ] = {
      val listRef = f.emptyRef[ CList[ P, Int ]]
//      listRef.set
      new WorldImpl( path, listRef )
   }
   def access( w: World[ P ]) : World[ P ] = error( "NO FUNCTIONA" )

   private class WorldImpl[ P ]( val path: P, listRef: Ref[ P, CList[ P, Int ]]) extends World[ P ] {
      def list : CList[ P, Int ] = listRef.get( path )
      def list_=( l: CList[ P, Int ]) : Unit = listRef.set( l )( path )

      def substitute( path: P ) : World[ P ] = new WorldImpl[ P ]( path, listRef )

      override def toString = "World[" + path + "]"
   }
}

class WorldTest {
   Hashing.verbose               = false
   FingerTree.TOSTRING_RESOLVE   = true

   implicit val sys = Factory.ksystem( WorldFactory[ KCtx ])

//   val proj = sys.keProjector
//   proj.in( VersionPath.init ) { implicit w =>
//      w.list = CList( 2, 1 )
//   }

   val kproj   = sys.kProjector
   val keproj  = sys.keProjector
   val csr  = sys.t( kproj.cursorIn( VersionPath.init.path )( _ ))

//   csr.t { implicit w =>
//
//   }

//   implicit def worldPath[ P ]( implicit w: World[ P ]) : P = w.path
//   implicit def worldPath[ P <: Ct ]( implicit w: World[ P ]) : P = w.path
   implicit def unwrapWorld( implicit w: World[ KCtx ]) : KCtx = w.path

   val v0 = csr.t { implicit w =>
//      implicit val path = w.path
      w.list = CList( 2, 1 )

      val l = w.list.toList
      assert( l == List( 2, 1 ), l.toString )
   }

   val v1 = csr.t { implicit w =>
//      implicit val path = w.path
      w.list = w.list.reverse

      val l = w.list.toList
      assert( l == List( 1, 2 ), l.toString )
   }

   val v2 = keproj.in( v0 ).t { implicit w =>
//      implicit val path = w.path
      w.list = w.list.drop( 1 )
      w.list.lastOption.foreach( _.tail = CList( 4 ))

      val l = w.list.toList
      assert( l == List( 1, 4 ), l.toString )
   }

//   println( "v1 = " + v1 )
//   println( "v2 = " + v2 )

   val v3 = csr.t { implicit w =>
//      implicit val path = w.path
      val ro = keproj.in( v2 ).meld( _.list.headOption )
      val r = ro.getOrElse( CList.empty[ KCtx, Int /* FUCKING BITCHES */ ]) // ( path, sys ))
// iterator not yet implemented
//      r.iterator.foreach( _.head +=  2 )
      def inc( l: CList[ KCtx, Int]) : Unit = l match {
         case cons0: CCons[ _, _ ] =>
            val cons = cons0.asInstanceOf[ CCons[ KCtx, Int ]]
            cons.head += 2
            inc( cons.tail )
         case _ =>
      }
      inc( r )

      w.list.lastOption match {
         case Some( head ) => head.tail = r
         case None => w.list = r
      }

      val l = w.list.toList
      assert( l == List( 1, 2, 3, 6 ), l.toString )
   }

   val v4 = csr.t { implicit w =>
//      implicit val path = w.path
      val ro = keproj.in( v2 ).meld( _.list.headOption )
      val r = ro.getOrElse( CList.empty[ KCtx, Int /* FUCKING BITCHES */ ]) // ( path, sys ))
      w.list.lastOption match {
         case Some( head ) => head.tail = r
         case None => w.list = r
      }

      val l = w.list.toList
      assert( l == List( 1, 2, 3, 6, 1, 4 ), l.toString )
   }

//   def t0[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//// hmmm... this would have been nice
////      println( "v0 : " + a0.list.toList )
//      val l = a0.access( emptyPath ).list.toList
//      println( "v0 : " + l )
//      assert( l == List( 2, 1 ))
//   }
//   proj.projectIn( v0 ).t( t0( _ ))
//
//   def t1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val l = a1.access( emptyPath ).list.toList
//      println( "v1 : " + l )
//      assert( l == List( 1, 2 ))
//   }
//   proj.projectIn( v1 ).t( t1( _ ))
//
//   def t2[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val l = a2.access( emptyPath ).list.toList
//      println( "v2 : " + l )
//      assert( l == List( 1, 4 ))
//   }
//   proj.projectIn( v2 ).t( t2( _ ))
}

class WorldTest1 {
   val sys  = Factory.ksystem( WorldFactory[ KCtx ])
//   val csr  = sys.t( sys.kProjector.cursorIn( VersionPath.init )( _ ))
////   val l0   = csr.t( implicit c => CList.empty[ KSystem.Ctx, String ])
//
//   def a1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val world = World.apply[ C1, String ]
//      world.list_=( CList.apply( "A", "B", "C" ))
//      world
//   }
//
//   val w1 = csr.t( a1( _ ))
//
//   def a2[ C1 <: KSystem.Ctx ]( w1: World[ _, String ] )( implicit c: C1 ) = {
//      val world   = w1.access( c.path.seminalPath )
//      val l1      = world.list
//      (l1.headOption, l1.lastOption) match {
//         case (Some( head ), Some( tail )) => tail.tail = head
//         case _ =>
//      }
//      world
//   }
//
//   val w2 = csr.t( implicit c => a2( w1 ))
//
//   def a3[ C1 <: KSystem.Ctx ]( w2: World[ _, String ] )( implicit c: C1 ) = {
//      val world   = w2.access( c.path.seminalPath )
//      val l2      = world.list
//      var lo      = l2.headOption
//// infinite loop... ouch!
////      while( lo.isDefined ) {
////         val le   = lo.get
////         println( le.head )
////         lo       = le.tailOption
////      }
//   }
//
//   csr.t( implicit c => a3( w2 ))

//   csr.t( implicit c => (l1.headOption, l1.tailOption) match {
//      case (Some( head ), Some( tail )) => tail.tail = head
//   })

//   class MutVar[ V <: VersionPath ]( val v: KSystem.Var[ String ])
//   class MutTest extends KMutVar[ KCtx, MutVar ] {
//      def get[ V <: VersionPath ]( implicit c: KCtx[ V ]) : MutVar[ V ] = error( "NO" )
//      def set[ V <: VersionPath ]( v: MutVar[ V ])( implicit c: KCtx[ V ]) : Unit = error( "NO" )
//   }
}
