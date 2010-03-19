package de.sciss

import scala.collection.immutable.{ IntMap, Seq => ISeq, SortedSet => ISortedSet }

package object confluent {
   type VersionID       = Int
// XXX this doesn't go well with the lexi tree
//   type CompressedPath  = ISeq[ Tuple2[ VersionID, VersionID ]]
   type CompressedPath  = ISeq[ VersionID ]
//   type DSSTEntry[ V ]  = Tuple2[ VersionID, V ] 
   type TotalOrder[ V ] = IntMap[ V ]
   val TotalOrder       = IntMap    // XXX this is obviously not the DSST structure with O(1) lookup
//   val DSSTEntry        = Tuple2

//   implicit def DSSTEntryOrder[ V ] = new Ordering[ DSSTEntry[ V ]] {
//       def compare( x: DSSTEntry[ V ], y: DSSTEntry[ V ]) : Int = x._1 - y._1
//   }
}