package de.sciss.lucre
package confluent

trait IndexMap[ S <: Sys[ S ], A ] extends Writable {
   def add( term: Long, value: A )( implicit tx: S#Tx ) : Unit
   def nearest( term: Long )( implicit tx: S#Tx ) : (Long, A)
   def nearestOption( term: Long )( implicit tx: S#Tx ) : Option[ (Long, A) ]
   def nearestUntil( timeStamp: Long, term: Long )( implicit tx: S#Tx ) : Option[ (Long, A) ]
}