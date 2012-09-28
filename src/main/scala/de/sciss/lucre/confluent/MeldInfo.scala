package de.sciss.lucre
package confluent

object MeldInfo {
   def empty[ S <: Sys[ S ]] : MeldInfo[ S ] = anyMeldInfo.asInstanceOf[ MeldInfo[ S ]]
   private val anyMeldInfo = MeldInfo[ Confluent ]( -1, Set.empty )
}
final case class MeldInfo[ S <: Sys[ S ]]( highestLevel: Int, highestTrees: Set[ S#Acc ]) {
   def requiresNewTree : Boolean = highestTrees.size > 1
   def outputLevel : Int = if( requiresNewTree ) highestLevel + 1 else highestLevel

   /**
    * An input tree is relevant if its level is higher than the currently observed
    * highest level, or if it has the same level but was not recorded in the set
    * of highest trees.
    */
   def isRelevant( level: Int, seminal: S#Acc ) : Boolean = {
      level > highestLevel || (level == highestLevel && !highestTrees.contains( seminal ))
   }

   def add( level: Int, seminal: S#Acc ) : MeldInfo[ S ] = {
      if( isRelevant( level, seminal )) MeldInfo( level, highestTrees + seminal ) else this
   }

   def isEmpty : Boolean = highestLevel < 0
}
