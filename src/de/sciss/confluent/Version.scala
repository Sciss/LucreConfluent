package de.sciss.confluent

import scala.collection.immutable.{ Queue, Seq => ISeq }

trait Version {
   val id:     VersionID
   val level:  Int
}

object Version {
   private var idCnt = 0

   val init: Version = newFrom()

   def newFrom( vs: Version* ) : Version = {
      var level   = 0
      var single  = true
      // the new version's level is the maximum of the levels of
      // the ancestor versions, unless there is more than one
      // ancestor with that maximum level in which case that
      // level is incremented by 1
      vs.foreach( v => {
         if( v.level > level ) {
            level    = v.level
            single   = true
         } else if( v.level == level ) {
            single   = false
         }
      })
      val id = idCnt; idCnt += 1
      new VersionImpl( id, if( single ) level else level + 1 )
   }

   private case class VersionImpl( id: Int, level: Int ) extends Version
}

trait VersionPath {
   val version: Version
   def path: CompressedPath

   def newBranch : VersionPath

   // XXX might need to be vps: VersionPath* ???
//   def newBranchWith( vs: Version* ) : VersionPath
}

object VersionPath {
   val init: VersionPath = new VersionPathImpl( Version.init, Queue( 0 ))

   private case class VersionPathImpl( version: Version, path: CompressedPath )
   extends VersionPath {
      def newBranch : VersionPath = {
         val newVersion = Version.newFrom( version )
         val newPath = // if( newVersion.level == version.level ) {
            path.dropRight( 1 ) :+ newVersion.id
//       } else {
//          path :+ newVersion.id :+ newVersion.id
//       }
         VersionPathImpl( newVersion, newPath )
      }
   }
}