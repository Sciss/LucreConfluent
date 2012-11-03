package de.sciss.lucre
package confluent

import stm.Disposable

//object Cursor {
//   implicit def serializer[ S <: Sys[ S ]] : stm.Serializer[ S#Tx, S#Acc, Cursor[ S ]] = sys.error( "TODO" )
//}
trait Cursor[ S <: Sys[ S ]] extends stm.Cursor[ S ] with Disposable[ S#Tx ] with Writable {
//   // this should be removed. It is only used in one of the
//   // tests right now, due to lack for proper `stepBack` or `stepFrom` functionality.
//   private[confluent] def position_=( path: S#Acc )( implicit tx: S#Tx ) : Unit

   def stepFrom[ A ]( path: S#Acc )( fun: S#Tx => A ) : A
}