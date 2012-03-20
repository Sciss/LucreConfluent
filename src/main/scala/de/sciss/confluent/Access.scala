package de.sciss.confluent

import de.sciss.lucre.stm.{Var, Sys}

trait Access[ S <: Sys[ S ], A ] extends Var[ S#Tx, A ] {
   def meld( from: S#Acc )( implicit tx: S#Tx ) : A
}
