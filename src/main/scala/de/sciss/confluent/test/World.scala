package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree
import concurrent.stm.TxnExecutor

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
   def list : CList[ World[ P ], Int ]
   def list_=( l: CList[ World[ P ], Int ]) : Unit
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
   def empty[ A, T ]( implicit a: A, sys: System[ _, _, A ]) : CList[ A, T ] = new CNilImpl[ A, T ]( sys.newMutable )
   def apply[ A, T ]( elems: T* )( implicit a: A, sys: System[ _, _, A ], mf: ClassManifest[ T ]) : CList[ A, T ] = {
//      val p = c.writePath.seminalPath
      elems.iterator.foldRight[ CList[ A, T ]]( new CNilImpl[ A, T ]( a ))( (v, tail) => {
         val headRef = sys.emptyVal[ T ]
         headRef.set( v )
         new CConsImpl[ A, T ]( a, sys, headRef )
      })
//      error( "No functiona" )
   }

   private class CNilImpl[ A, T ]( val path: A ) extends CNil[ A, T ] {
      def substitute( path: A ) = new CNilImpl[ A, T ]( path )
//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = new CNilImpl[ C, A ]
   }

//   private type ListHolder[ A ] = KSystem.RefVar[ CList[ _ <: KSystem.Ctx, A ]]

   private class CConsImpl[ A, T ]( val path: A, sys: System[ _, _, A ], val headRef: Val[ A, T ] /* KSystem.Var[ T ] */)
   extends CCons[ A, T ] {
      def head : T = error( "NO FUNCTIONA" ) // headRef.get( c )
      def head_=( a: T ) : Unit = error( "NO FUNCTIONA" ) // headRef.set( a )
      def tail : CList[ A, T ] = error( "NO FUNCTIONA" ) // tailRef.get[ C1 ]
      def tail_=( l: CList[ A, T ]) : Unit = error( "NO FUNCTIONA" ) // tailRef.set( l )

//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = {
//         new CConsImpl[ C, A ]( path ++ post, headRef, tailRef )
//      }

      def substitute( path: A ) = new CConsImpl[ A, T ]( path, sys, headRef )

//      def substitute( path: P ) : CCons[ P, T ] = new CConsImpl( a, sys[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ] = {
//         val spath =
//         CConsImpl( spath, headRef, tailRef )
//      }

      def reverse : CList[ A, T ] = {
         var succ       = CList.empty[ A, T ]( sys.newMutable( path ), sys )
         var keepGoin   = true
         var pred: CCons[ A, T ] = this
         while( keepGoin ) {
            val next       = pred.tail
            pred.tail      = succ
            next match {
               case cns: CCons[ A, T ] =>
                  succ  = pred
                  pred  = cns
               case _ => keepGoin = false
            }
         }
         pred
      }
   }
}
// Partial2U[ KSystem.Ctx, CList, A ]#Apply
//extends Access[ KSystem.Ctx, Path, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]
sealed trait CList[ A, T] extends Mutable[ A, CList[ A, T ]] {
   def headOption : Option[ CCons[ A, T ]]
   def lastOption : Option[ CCons[ A, T ]]
   def drop( n: Int ) : CList[ A, T ]
   def reverse : CList[ A, T ]
   def toList : List[ T ]
   def iterator : Iterator[ CCons[ A, T ]]
}
trait CNil[ A, T ] extends CList[ A, T ] {
   def headOption : Option[ CCons[ A, T ]] = None
   def lastOption : Option[ CCons[ A, T ]] = None
   def drop( n: Int ) : CList[ A, T ] = this
   def reverse : CList[ A, T ] = this
   def toList : List[ T ] = Nil
   def iterator : Iterator[ CCons[ A, T ]] = Iterator.empty
}
trait CCons[ A, T ] extends CList[ A, T ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head : T
   def head_=( h: T ) : Unit
   def tail : CList[ A, T ]
   def tail_=( l: CList[ A, T ]) : Unit

   def headOption : Option[ CCons[ A, T ]] = Some( this )
   def lastOption : Option[ CCons[ A, T ]] = Some( last )

   def iterator : Iterator[ CCons[ A, T ]] = error( "NO FUNCTIONA" )

   def last : CCons[ A, T ] = {
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         res.tail match {
            case cns: CCons[ A, T ] => res = cns
            case _ => keepGoin = false
         }
      }
      res
   }

   def drop( n: Int ) : CList[ A, T ] = {
      var res        = this
      var keepGoin   = n
      while( keepGoin > 0 ) {
         keepGoin -= 1
         res.tail match {
            case cns: CCons[ A, T ] => res = cns
            case nil: CNil[ A, T ]  => return nil
         }
      }
      res
   }

   def reverse : CList[ A, T ]

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
   def init( f: RefFactory[ World[ P ]], path: P ) : World[ P ] = {
      val listRef = f.emptyRef[ CList[ World[ P ], Int ]]
//      listRef.set
      new WorldImpl( path, listRef )
   }
   def access( w: World[ P ]) : World[ P ] = error( "NO FUNCTIONA" )

   private class WorldImpl[ P ]( val path: P, listRef: Ref[ World[ P ], CList[ World[ P ], Int ]]) extends World[ P ] {
      def list : CList[ World[ P ], Int ] = listRef.get( this )
      def list_=( l: CList[ World[ P ], Int ]) : Unit = listRef.set( l )( this )

      def substitute( path: P ) : World[ P ] = new WorldImpl[ P ]( path, listRef )
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

   val v0 = csr.t { implicit w =>
      w.list = CList( 2, 1 )
      w.path.path // XXX CONTINUE HERE -- OBVIOUSLY WE NEED A POST-COMMIT HOOK TO GRAB THE NEW CURSOR POSITION
   }

   val v1 = csr.t { implicit w =>
      w.list = w.list.reverse
      w.path.path
   }

//   val csr2 = sys.t( proj.cursorIn( v0 )( _ ))

   val v2 = keproj.in( v0 ) { implicit w =>
      w.list = w.list.drop( 1 )
      w.list.lastOption.foreach( _.tail = CList( 4 ))
      w.path.path
   }

   val v3 = csr.t { implicit w =>
      val ro = keproj.in( v2 )( _.list.headOption )
      val r = ro.getOrElse( CList.empty[ World[ KCtx ], Int /* FUCKING BITCHES */ ]( w, sys ))
      r.iterator.foreach( _.head +=  2 )
      w.list.headOption match {
         case Some( head ) => head.tail = r
         case None => w.list = r
      }
      w.path.path
   }

   val v4 = csr.t { implicit w =>
      val ro = keproj.in( v2 )( _.list.headOption )
      val r = ro.getOrElse( CList.empty[ World[ KCtx ], Int /* FUCKING BITCHES */ ]( w, sys ))
      w.list.headOption match {
         case Some( head ) => head.tail = r
         case None => w.list = r
      }
      w.path.path
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
