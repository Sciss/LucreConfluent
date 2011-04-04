package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree

object World {
   def apply[ C1 <: KSystem.Ctx, A ]( implicit c: C1, sys: KSystem ) : World[ C1, A ] =
      new Impl( sys.refVar[ C1, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]( CList.empty[ C1, A ]))

   private class Impl[ C1 <: KSystem.Ctx, A ]( listRef: KSystem.RefVar[ ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]) extends World[ C1, A ] {
      def list[ C1 <: KSystem.Ctx ]( implicit c: C1 ) : CList[ C1, A ] = listRef.get
      def list_=[ C1 <: KSystem.Ctx ]( l: CList[ C1, A ])( implicit c: C1 ) : Unit = listRef.set( l )

      def access[ C <: KSystem.Ctx ]( post: Path ) : World[ C, A ] = new Impl[ C, A ]( listRef )
   }
}
trait World[ C1 <: KSystem.Ctx, A ] extends Access[ KSystem.Ctx, Path, ({type λ[ α <: KSystem.Ctx ] = World[ α, A ]})#λ ] {
//   def head( implicit c: KCtx ) : CList
   // KSystem.Var[ Option[ CList[ KSystem.Ctx, KSystem.Var, Int ]]] // = None
   def list[ C1 <: KSystem.Ctx ]( implicit c: C1 ) : CList[ C1, A ]
   def list_=[ C1 <: KSystem.Ctx ]( l: CList[ C1, A ])( implicit c: C1 ) : Unit
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
   def empty[ C1 <: KSystem.Ctx, A ]( implicit c: C1 ) : CList[ C1, A ] = new CNilImpl[ C1, A ]()
   def apply[ C1 <: KSystem.Ctx, A ]( elems: A* )( implicit c: C1, sys: KSystem, mf: ClassManifest[ A ]) : CList[ C1, A ] = {
      val p = c.writePath.seminalPath
      elems.iterator.foldRight[ CList[ C1, A ]]( new CNilImpl[ C1, A ])( (a, tail) => {
         new CConsImpl[ C1, A ]( p, sys.v( a ), sys.refVar[ C1, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ]( tail ))
      })
//      error( "No functiona" )
   }

   private class CNilImpl[ C1 <: KSystem.Ctx, A ] extends CNil[ C1, A ] {
      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = new CNilImpl[ C, A ]
   }

//   private type ListHolder[ A ] = KSystem.RefVar[ CList[ _ <: KSystem.Ctx, A ]]

   private class CConsImpl[ C1 <: KSystem.Ctx, A ]( val path: Path, val headRef: KSystem.Var[ A ],
                                                    val tailRef: KSystem.RefVar[ ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ])
   extends CCons[ C1, A ] {
      def head( implicit c: C1 ) : A = headRef.get( c )
      def head_=( a: A )( implicit c: C1 ) : Unit = headRef.set( a )
      def tail( implicit c: C1 ) : CList[ C1, A ] = tailRef.get[ C1 ]
      def tail_=( l: CList[ C1, A ])( implicit c: C1 ) : Unit = tailRef.set( l )

      def access[ C <: KSystem.Ctx ]( post: Path ) : CList[ C, A ] = {
         new CConsImpl[ C, A ]( path ++ post, headRef, tailRef )
      }

//      def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ] = {
//         val spath =
//         CConsImpl( spath, headRef, tailRef )
//      }
   }
}
// Partial2U[ KSystem.Ctx, CList, A ]#Apply
sealed trait CList[ C1 <: KSystem.Ctx, A ] extends Access[ KSystem.Ctx, Path, ({type λ[ α <: KSystem.Ctx ] = CList[ α, A ]})#λ ] {
   def headOption( implicit c: C1 ) : Option[ CCons[ C1, A ]]
   def lastOption( implicit c: C1 ) : Option[ CCons[ C1, A ]]
   def drop( n: Int )( implicit c: C1 ) : CList[ C1, A ]
   def reverse( implicit c: C1 ) : CList[ C1, A ]
   def toList( implicit c: C1 ) : List[ A ]
}
trait CNil[ C1 <: KSystem.Ctx, A ] extends CList[ C1, A ] {
   def headOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = None
   def lastOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = None
   def drop( n: Int )( implicit c: C1 ) : CList[ C1, A ] = this
   def reverse( implicit c: C1 ) : CList[ C1, A ] = this
   def toList( implicit c: C1 ) : List[ A ] = Nil
}
trait CCons[ C1 <: KSystem.Ctx, A ] extends CList[ C1, A ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head( implicit c: C1 ) : A
   def head_=( a: A )( implicit c: C1 ) : Unit
   def tail( implicit c: C1 ) : CList[ C1, A ]
   def tail_=( l: CList[ C1, A ])( implicit c: C1 ) : Unit

   def headOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = Some( this )
   def lastOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = Some( last )

   def last( implicit c: C1 ) : CCons[ C1, A ] = {
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         res.tail match {
            case cns: CCons[ C1, A ] => res = cns
            case _ => keepGoin = false
         }
      }
      res
   }

   def drop( n: Int )( implicit c: C1 ) : CList[ C1, A ] = {
      var res        = this
      var keepGoin   = n
      while( keepGoin > 0 ) {
         keepGoin -= 1
         res.tail match {
            case cns: CCons[ C1, A ] => res = cns
            case nil: CNil[ C1, A ]  => return nil
         }
      }
      res
   }

   def reverse( implicit c: C1 ) : CList[ C1, A ] = {
      var succ       = CList.empty[ C1, A ]
      var keepGoin   = true
      var pred       = this
      while( keepGoin ) {
         val next       = pred.tail
         pred.tail      = succ
         next match {
            case cns: CCons[ C1, A ] =>
               succ  = pred
               pred  = cns
            case _ => keepGoin = false
         }
      }
      pred
   }

   def toList( implicit c: C1 ) : List[ A ] = {
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

class WorldTest {
   Hashing.verbose               = false
   FingerTree.TOSTRING_RESOLVE   = true

   implicit val sys  = Factory.ksystem
   val proj = sys.kProjector
   val csr  = sys.t( proj.cursorIn( VersionPath.init )( _ ))

   def p0[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val a    = World[ C1, Int ]
      a.list_=( CList( 2, 1 ))
      (a, c.path)
   }
   val (a0, v0) = csr.t( p0( _ ))

   def p1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val a    = a0.access( c.path.seminalPath )
      a.list_=( a.list.reverse )
      (a, c.path)
   }
   val (a1, v1) = csr.t( p1( _ ))

   val csr2 = sys.t( proj.cursorIn( v0 )( _ ))

   def p2[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val a    = a0.access( c.path.seminalPath )
      a.list_=( a.list.drop( 1 ))
      a.list.lastOption.foreach( _.tail = CList( 4 ))
      (a, c.path)
   }
   val (a2, v2) = csr2.t( p2( _ ))
// println( "jo chuck " + v0.path.toList + " : " + v1.path.toList + " : " + v2.path.toList )

   sys.t( csr2.dispose( _ ))

   val emptyPath = Path()

   def t0[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
// hmmm... this would have been nice
//      println( "v0 : " + a0.list.toList )
      val l = a0.access( emptyPath ).list.toList
      println( "v0 : " + l )
      assert( l == List( 2, 1 ))
   }
   proj.projectIn( v0 ).t( t0( _ ))

   def t1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val l = a1.access( emptyPath ).list.toList
      println( "v1 : " + l )
      assert( l == List( 1, 2 ))
   }
   proj.projectIn( v1 ).t( t1( _ ))

   def t2[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val l = a2.access( emptyPath ).list.toList
      println( "v2 : " + l )
      assert( l == List( 1, 4 ))
   }
   proj.projectIn( v2 ).t( t2( _ ))
}

class WorldTest1 {
   implicit val sys  = Factory.ksystem
   val csr  = sys.t( sys.kProjector.cursorIn( VersionPath.init )( _ ))
//   val l0   = csr.t( implicit c => CList.empty[ KSystem.Ctx, String ])

   def a1[ C1 <: KSystem.Ctx ]( implicit c: C1 ) = {
      val world = World.apply[ C1, String ]
      world.list_=( CList.apply( "A", "B", "C" ))
      world
   }

   val w1 = csr.t( a1( _ ))

   def a2[ C1 <: KSystem.Ctx ]( w1: World[ _, String ] )( implicit c: C1 ) = {
      val world   = w1.access( c.path.seminalPath )
      val l1      = world.list
      (l1.headOption, l1.lastOption) match {
         case (Some( head ), Some( tail )) => tail.tail = head
         case _ =>
      }
      world
   }

   val w2 = csr.t( implicit c => a2( w1 ))

   def a3[ C1 <: KSystem.Ctx ]( w2: World[ _, String ] )( implicit c: C1 ) = {
      val world   = w2.access( c.path.seminalPath )
      val l2      = world.list
      var lo      = l2.headOption
// infinite loop... ouch!
//      while( lo.isDefined ) {
//         val le   = lo.get
//         println( le.head )
//         lo       = le.tailOption
//      }
   }

   csr.t( implicit c => a3( w2 ))

//   csr.t( implicit c => (l1.headOption, l1.tailOption) match {
//      case (Some( head ), Some( tail )) => tail.tail = head
//   })

//   class MutVar[ V <: VersionPath ]( val v: KSystem.Var[ String ])
//   class MutTest extends KMutVar[ KCtx, MutVar ] {
//      def get[ V <: VersionPath ]( implicit c: KCtx[ V ]) : MutVar[ V ] = error( "NO" )
//      def set[ V <: VersionPath ]( v: MutVar[ V ])( implicit c: KCtx[ V ]) : Unit = error( "NO" )
//   }
}
