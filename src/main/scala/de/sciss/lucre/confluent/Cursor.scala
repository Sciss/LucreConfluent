package de.sciss.lucre
package confluent

import stm.Disposable

//object Cursor {
//   implicit def serializer[ S <: Sys[ S ]] : stm.Serializer[ S#Tx, S#Acc, Cursor[ S ]] = sys.error( "TODO" )
//}
trait Cursor[ S <: Sys[ S ]] extends stm.Cursor[ S ] with Disposable[ S#Tx ] with Writable {
   private[confluent] def position_=( path: S#Acc )( implicit tx: S#Tx ) : Unit
}