name := "TemporalObjects"

version := "0.34-SNAPSHOT"

organization := "de.sciss"

description := "Confluent persistence and quasi-retroactive / fluent references for Scala"

homepage := Some( url( "https://github.com/Sciss/TemporalObjects" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

crossScalaVersions in ThisBuild := Seq( "2.10.0-M6", "2.9.2" )

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.21-SNAPSHOT",
   "de.sciss" %% "lucredata-core" % "0.34-SNAPSHOT",
   "de.sciss" %% "lucredata-views" % "0.34-SNAPSHOT" % "test",
   "de.sciss" %% "lucreexpr" % "0.34-SNAPSHOT" % "test"
)

libraryDependencies <+= scalaVersion { sv =>
   val v = sv match {
      case "2.10.0-M6" => "1.9-2.10.0-M6-B2"
      case _ => "1.8"
   }
   "org.scalatest" %% "scalatest" % v % "test"
}

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution /* in Test */ := false

initialCommands in console := """import de.sciss.confluent._
"""

// publishArtifact in (Compile, packageDoc) := false   // disable doc generation during development cycles

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/TemporalObjects.git</url>
  <connection>scm:git:git@github.com:Sciss/TemporalObjects.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "confluent", "persistence", "reactive", "event" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "TemporalObjects" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
