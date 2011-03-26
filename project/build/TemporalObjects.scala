import sbt._

class TemporalObjectsProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val fingerTree = "de.sciss" %% "fingertree" % "0.11"
   val scalaSTM   = "org.scala-tools" %% "scala-stm" % "0.3-SNAPSHOT"

   val scalaToolsSnapshots = "Scala-Tools Snapshot Repository" at "http://scala-tools.org/repo-snapshots"
}
