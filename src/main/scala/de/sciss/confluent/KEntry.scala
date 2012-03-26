package de.sciss.confluent

import de.sciss.lucre.stm.Var

trait KEntry[ S <: KSys[ S ], A ] extends Var[ S#Tx, A ] {
   def meld( from: S#Acc )( implicit tx: S#Tx ) : A
}
