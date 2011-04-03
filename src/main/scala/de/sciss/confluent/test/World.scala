package de.sciss.confluent
package test

object World {
//   def apply[ V1 <: VersionPath ]( implicit c: KCtx[ V1 ], sys: KSystem ) : World = new Impl( sys.v( Option.empty ))
//
//   private class Impl( var head: KSystem.Var[ Option[ CList[ KSystem.Ctx, KSystem.Var, Int ]]]) extends World
}
trait World[ V <: VersionPath ] {
//   def head( implicit c: KCtx ) : CList
   // KSystem.Var[ Option[ CList[ KSystem.Ctx, KSystem.Var, Int ]]] // = None
   def list[ C1 <: KSystem.Ctx ]( implicit c: C1 ) : CList[ C1, Int ]
   def list_=[ C1 <: KSystem.Ctx ]( l: CList[ C1, Int ])( implicit c: C1 ) : Unit
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
      elems.reverseIterator.foldRight[ CList[ C1, A ]]( new CNilImpl[ C1, A ])( (a, tail) => {
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
   def tailOption( implicit c: C1 ) : Option[ CCons[ C1, A ]]
}
trait CNil[ C1 <: KSystem.Ctx, A ] extends CList[ C1, A ] {
   def headOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = None
   def tailOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = None
}
trait CCons[ C1 <: KSystem.Ctx, A ] extends CList[ C1, A ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head( implicit c: C1 ) : A
   def head_=( a: A )( implicit c: C1 ) : Unit
   def tail( implicit c: C1 ) : CList[ C1, A ]
   def tail_=( l: CList[ C1, A ])( implicit c: C1 ) : Unit

   def headOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = Some( this )
   def tailOption( implicit c: C1 ) : Option[ CCons[ C1, A ]] = {
      var res        = this
      var keepGoin   = true
      while( keepGoin ) {
         res.tail match {
            case cns: CCons[ C1, A ] => res = cns
            case _ => keepGoin = false
         }
      }
      Some( res )
   }
}

class WorldTest {
   implicit val sys  = Factory.ksystem
   val csr  = sys.t( sys.kProjector.cursorIn( VersionPath.init )( _ ))
//   val l0   = csr.t( implicit c => CList.empty[ KSystem.Ctx, String ])
   val l1   = csr.t( implicit c => CList.apply( "A", "B", "C" ))
//   csr.t( implicit c => (l1.headOption, l1.tailOption) match {
//      case (Some( head ), Some( tail )) => tail.tail = head
//   })

//   class MutVar[ V <: VersionPath ]( val v: KSystem.Var[ String ])
//   class MutTest extends KMutVar[ KCtx, MutVar ] {
//      def get[ V <: VersionPath ]( implicit c: KCtx[ V ]) : MutVar[ V ] = error( "NO" )
//      def set[ V <: VersionPath ]( v: MutVar[ V ])( implicit c: KCtx[ V ]) : Unit = error( "NO" )
//   }
}

//final class CList[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ], A ]( val elem: V[ Int ], val next: V[ Option[ CList[ C, V, A ]]]) {
//   private type This = CList[ C, V, A ]
//
////   var elem: A = _
////   var next: This = _
////
////   def isEmpty : Boolean = next eq this
//
////   def length: Int = if (isEmpty) 0 else next.length + 1
////
////   def head: A = elem
////
////   def tail: This = {
////      require( nonEmpty, "tail of empty list" )
////      next
////   }
////
////   def nonEmpty : Boolean = !isEmpty
//}

// class GroupView[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ]]( sys: System[ C, V ], g: ProcGroup[ C, V ], csr: ECursor[ C ])

//case object CNil extends CList[ Nothing ] {
//   def head: Nothing = throw new NoSuchElementException( "head of empty list" )
//   def tail: CList[ Nothing ] = throw new UnsupportedOperationException( "tail of empty list" )
//   def isEmpty : Boolean = true
//}
//final case class CCons[ A ] {
//
//   def head : A = hd
//   def tail : CList[ A ] = tl
//   def isEmpty : Boolean = false
//}
