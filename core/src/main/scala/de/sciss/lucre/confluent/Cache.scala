package de.sciss.lucre
package confluent

trait Cache[ -Tx ] {
   def flushCache( term: Long )( implicit tx: Tx ) : Unit
}
