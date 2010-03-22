package de.sciss.confluent

/**
 *    This is a tiny mutable holder of a BinaryTreeMap, to store
 *    L(c~) as required by the compressed-path method. We use this
 *    wrapper as BinaryTreeMap itself is immutable and we save
 *    an extra traveral in the lexicographical map, omitting a re-insert.
 */
object OracleMap {
//   def empty[ V ]: OracleMap[ V ] = new OracleMap( BinaryTreeMap.empty( Version.AncestorOrdering ))

   def apply[ V ]( entries: Tuple2[ Version, V ]* ): OracleMap[ V ] =
      new OracleMap( BinaryTreeMap( entries: _* )( Version.AncestorOrdering ))
}

class OracleMap[ V ] private ( private var tree: BinaryTreeMap[ Version, V ]) {
   def +=( entry: Tuple2[ Version, V ]) {
      tree += entry
   }

   def query( t: Version ) : Option[ V ] = tree.getClosestLessOrEqualTo( t )

   override def toString = tree.toString
}