package de.sciss.confluent

//trait IndexTree[ S <: KSys[ S ]] {
//
//}

trait IndexMap[ S <: KSys[ S ], A ] {
   def add( term: Long, value: A )( implicit tx: S#Tx ) : Unit
   def nearest( term: Long )( implicit tx: S#Tx ) : A
}