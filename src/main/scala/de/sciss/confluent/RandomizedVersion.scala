package de.sciss.confluent

object RandomizedVersion {
   var FREEZE_SEED = true
}
trait RandomizedVersion extends Version {
//   def rid: Int  // randomized ID
}