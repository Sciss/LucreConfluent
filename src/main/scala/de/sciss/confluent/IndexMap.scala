package de.sciss.confluent

import de.sciss.lucre.stm.Writer

//trait IndexTree[ S <: KSys[ S ]] {
//
//}

trait IndexMap[ S <: KSys[ S ], A ] extends Writer {
   def add( term: Long, value: A )( implicit tx: S#Tx ) : Unit
   def nearest( term: Long )( implicit tx: S#Tx ) : A
}