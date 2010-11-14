import sbt._

/**
 *    @version 0.16, 29-Oct-10
 */
class TemporalObjectsProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val ccstm         = "edu.stanford.ppl" % "ccstm" % "0.2.2-for-scala-2.8.0-SNAPSHOT"
   val ccstmRepo     = "CCSTM Release Repository at PPL" at "http://ppl.stanford.edu/ccstm/repo-releases"
   val ccstmSnap     = "CCSTM Snapshot Repository at PPL" at "http://ppl.stanford.edu/ccstm/repo-snapshots"
}
