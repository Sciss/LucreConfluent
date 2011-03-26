package de.sciss.confluent
package test

object World {
   def apply( implicit c: KCtx, sys: KSystem ) : World = new Impl( sys.v( Option.empty ))

   private class Impl( var head: KSystem.Var[ Option[ CList[ KCtx, KSystem.Var, Int ]]]) extends World
}
trait World {
//   def head( implicit c: KCtx ) : CList
   var head: KSystem.Var[ Option[ CList[ KCtx, KSystem.Var, Int ]]] // = None
}

object CList {
//   def empty[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ], A ] : CList[ C, V, A ] = new CList[ A ]()
}

final class CList[ C <: Ct, V[ ~ ] <: Vr[ C, ~ ], A ]( val elem: V[ Int ], val next: V[ Option[ CList[ C, V, A ]]]) {
   private type This = CList[ C, V, A ]

//   var elem: A = _
//   var next: This = _
//
//   def isEmpty : Boolean = next eq this

//   def length: Int = if (isEmpty) 0 else next.length + 1
//
//   def head: A = elem
//
//   def tail: This = {
//      require( nonEmpty, "tail of empty list" )
//      next
//   }
//
//   def nonEmpty : Boolean = !isEmpty
}

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
