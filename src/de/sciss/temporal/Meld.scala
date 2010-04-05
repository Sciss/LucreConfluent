package de.sciss.temporal

import de.sciss.confluent.{ VersionManagement, VersionPath }
import VersionManagement._

case class Meld[ V ]( input: V, inputVersion: VersionPath ) {
   def into[ T ]( fun: V => T ) : V = {
      val current    = currentVersion
      val write      = current.meldWith( inputVersion.version )
      makeRead( inputVersion )
      makeWrite( write )
      try {
         fun.apply( resolve( inputVersion.path, write.path, input ))
         input
      } finally {
         makeCurrent( write )
      }
   }
}