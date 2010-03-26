package de.sciss.temporal

import java.net.URI
import collection.immutable.Queue

trait FileLocation {
   def uri: URI
   def name = uri.getPath
}

case class URIFileLocation( uri: URI ) extends FileLocation {
   override def toString = "Loc( \"" + uri + "\" )"
}

object FileLocations {
//   private var coll = Queue.empty[ FileLocation ]
   private var coll: List[ FileLocation ] = Nil  // we use prepend, so later additions have higher priority

   def iterator: Iterator[ FileLocation ] = coll.iterator
   def toList: List[ FileLocation ] = coll

   def add( loc: FileLocation ) {
//      coll = coll.enqueue( loc )
      coll ::= loc
   }
}