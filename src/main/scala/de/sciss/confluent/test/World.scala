package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree

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
trait World extends {
//   def head( implicit c: KCtx ) : CList
   // KSystem.Var[ Option[ CList[ KSystem.Ctx, KSystem.Var, Int ]]] // = None
   def list : CList[ Int ]
   def list_=( l: CList[ Int ]) : Unit
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

object CList {
   def empty[ A ]( implicit w: World ) : CList[ A ] = new CNilImpl[ A ]()
   def apply[ A ]( elems: A* )( implicit w: World, mf: ClassManifest[ A ]) : CList[ A ] = {
//      val p = c.writePath.seminalPath
//      elems.iterator.foldRight[ CList[ A ]]( new CNilImpl[ A ])( (a, tail) => {
//         new CConsImpl[ A ]( p, sys.v( a ), sys.refVar[ C1, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]( tail ))
//      })
      error( "No functiona" )
   }

   private class CNilImpl[ A ] extends CNil[ A ] {
//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = new CNilImpl[ C, A ]
   }

//   private type ListHolder[ A ] = KSystem.RefVar[ CList[ _ <: KSystem.Ctx, A ]]

   private class CConsImpl[ A ]( w: World, val path: Path, val headRef: KSystem.Var[ A ])
   extends CCons[ A ] {
      def head : A = error( "NO FUNCTIONA" ) // headRef.get( c )
      def head_=( a: A ) : Unit = error( "NO FUNCTIONA" ) // headRef.set( a )
      def tail : CList[ A ] = error( "NO FUNCTIONA" ) // tailRef.get[ C1 ]
      def tail_=( l: CList[ A ]) : Unit = error( "NO FUNCTIONA" ) // tailRef.set( l )

//      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = {
//         new CConsImpl[ C, A ]( path ++ post, headRef, tailRef )
//      }

//      def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ] = {
//         val spath =
//         CConsImpl( spath, headRef, tailRef )
//      }

      def reverse : CList[ A ] = {
         var succ       = CList.empty[ A ]( w )
         var keepGoin   = true
         var pred: CCons[ A ] = this
         while( keepGoin ) {
            val next       = pred.tail
            pred.tail      = succ
            next match {
               case cns: CCons[ A ] =>
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
sealed trait CList[ A ]  {
   def headOption : Option[ CCons[ A ]]
   def lastOption : Option[ CCons[ A ]]
   def drop( n: Int ) : CList[ A ]
   def reverse : CList[ A ]
   def toList : List[ A ]
}
trait CNil[ A ] extends CList[ A ] {
   def headOption : Option[ CCons[ A ]] = None
   def lastOption : Option[ CCons[ A ]] = None
   def drop( n: Int ) : CList[ A ] = this
   def reverse : CList[ A ] = this
   def toList : List[ A ] = Nil
}
trait CCons[ A ] extends CList[ A ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head : A
   def head_=( a: A ) : Unit
   def tail : CList[ A ]
   def tail_=( l: CList[ A ]) : Unit

   def headOption : Option[ CCons[ A ]] = Some( this )
   def lastOption : Option[ CCons[ A ]] = Some( last )

   def last : CCons[ A ] = {
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         res.tail match {
            case cns: CCons[ A ] => res = cns
            case _ => keepGoin = false
         }
      }
      res
   }

   def drop( n: Int ) : CList[ A ] = {
      var res        = this
      var keepGoin   = n
      while( keepGoin > 0 ) {
         keepGoin -= 1
         res.tail match {
            case cns: CCons[ A ] => res = cns
            case nil: CNil[ A ]  => return nil
         }
      }
      res
   }

   def reverse : CList[ A ]

   def toList : List[ A ] = {
      val b          = List.newBuilder[ A ]
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

object WorldFactory extends AccessProvider[ World, Path, KSystem[ World ]] {
   def init( sys: KSystem[ World ], p: Path ) : World = error( "NO FUNCTIONA" )
   def access( w: World ) : World = error( "NO FUNCTIONA" )
}

class WorldTest {
   Hashing.verbose               = false
   FingerTree.TOSTRING_RESOLVE   = true

   implicit val sys  = Factory.ksystem( WorldFactory )

//   val proj = sys.keProjector
//   proj.in( VersionPath.init ) { implicit w =>
//      w.list = CList( 2, 1 )
//   }

   val proj = sys.kProjector
   val csr  = sys.t( proj.cursorIn( VersionPath.init )( _ ))

//   def p0[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val a    = World // [ C1, Int ]
//      a.list_=( CList( 2, 1 ))
//      (a, c.path)
//   }
//   val (a0, v0) = csr.t( p0( _ ))

//   def p1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val a    = a0.access( c.path.seminalPath )
//      a.list_=( a.list.reverse )
//      (a, c.path)
//   }
//   val (a1, v1) = csr.t( p1( _ ))
//
//   val csr2 = sys.t( proj.cursorIn( v0 )( _ ))
//
//   def p2[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
//      val a    = a0.access( c.path.seminalPath )
//      a.list_=( a.list.drop( 1 ))
//      a.list.lastOption.foreach( _.tail = CList( 4 ))
//      (a, c.path)
//   }
//   val (a2, v2) = csr2.t( p2( _ ))
//// println( "jo chuck " + v0.path.toList + " : " + v1.path.toList + " : " + v2.path.toList )
//
//   sys.t( csr2.dispose( _ ))
//
//   val emptyPath = Path()
//
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
   val sys  = Factory.ksystem( WorldFactory )
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
