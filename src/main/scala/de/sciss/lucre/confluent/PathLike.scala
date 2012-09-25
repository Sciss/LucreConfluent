package de.sciss.lucre
package confluent

trait PathLike {
   def sum: Long
   def size: Int
//   def take( n: Int ) : PathLike

   def sumUntil( n: Int ) : Long

//   /**
//    * Drops the last element of the path
//    */
//   def init : PathLike
}
