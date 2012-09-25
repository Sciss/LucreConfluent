package de.sciss.lucre
package confluent

trait KEntry[ S <: KSys[ S ], A ] extends stm.Var[ S#Tx, A ] {
   def meld( from: S#Acc )( implicit tx: S#Tx ) : A
}
