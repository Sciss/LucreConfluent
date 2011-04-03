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
   def list( implicit c: KCtx[ V ]) : CList[ V, Int ]
   def list_=( l: CList[ V, Int ])( implicit c: KCtx[ V ]) : Unit
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
   def empty[ V <: VersionPath, A ]( implicit c: KCtx[ V ]) : CList[ V, A ] = new CNilImpl[ V ]()
   def apply[ V <: VersionPath, A ]( elems: A* )( implicit c: KCtx[ V ], sys: KSystem ) : CList[ V, A ] = {
//      elems.reverseIterator.foldRight( new CNilImpl[ V ])( (a, tail) => {
//         new CConsImpl[ V, A ]( c.path, sys.v( a ), sys.v( StoreFactory. )
//      })
      error( "No functiona" )
   }

   private class CNilImpl[ V <: VersionPath ] extends CNil[ V ]

   private class CConsImpl[ V <: VersionPath, A ]( val path: VersionPath, val headRef: KSystem.Var[ A ], val tailRef: KSystem.Var[ Store[ Version, CList[ _, A ]]])
   extends CCons[ V, A ] {
      def head( implicit c: KCtx[ V ]) : A = headRef.get( c )
      def head_=( a: A )( implicit c: KCtx[ V ]) : Unit = headRef.set( a )
      def tail( implicit c: KCtx[ V ]) : CList[ V, A ] = {
         val store   = tailRef.get
         val p       = path.path
         store.getWithPrefix( p ) match {
            case Some( (vu: CConsImpl[ _, _ ], pre) ) =>
               val v    = vu.asInstanceOf[ CConsImpl[ _, A ]]
               val ap   = v.path.path
               new CConsImpl[ V, A ]( VersionPath.wrap( ap.++( p.drop( pre ))), v.headRef, v.tailRef )
            case _ => new CNilImpl[ V ]
         }
      }
      def tail_=( l: CList[ V, A ])( implicit c: KCtx[ V ]) : Unit = {
         tailRef.transform( _.put( c.path.path, l.asInstanceOf[ CList[ VersionPath, A ]]))
      }

//      def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ] = {
//         val spath =
//         CConsImpl( spath, headRef, tailRef )
//      }
   }
}

sealed trait CList[ V <: VersionPath, +A ] {
}
trait CNil[ V <: VersionPath ] extends CList[ V, Nothing ] {
}
trait CCons[ V <: VersionPath, A ] extends CList[ V, A ] {
//   def substitute[ V1 <: Version ]( implicit c: KCtx[ V1 ]) : CCons[ V1, A ]

   def head( implicit c: KCtx[ V ]) : A
   def head_=( a: A )( implicit c: KCtx[ V ]) : Unit
   def tail( implicit c: KCtx[ V ]) : CList[ V, A ]
   def tail_=( l: CList[ V, A ])( implicit c: KCtx[ V ]) : Unit
}

class WorldTest {
   val sys  = Factory.ksystem
   val csr  = sys.t( sys.kProjector.cursorIn( VersionPath.init )( _ ))
   csr.t { implicit c =>
      val l0 = CList.empty

   }
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
