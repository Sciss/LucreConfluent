package de.sciss.confluent

import de.sciss.lucre.Writable

trait IndexMap[ S <: KSys[ S ], A ] extends Writable {
   def add( term: Long, value: A )( implicit tx: S#Tx ) : Unit
   def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A)
}