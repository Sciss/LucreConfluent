name := "TemporalObjects"

version := "0.32"

organization := "de.sciss"

description := "Confluent persistence and quasi-retroactive / fluent references for Scala"

homepage := Some( url( "https://github.com/Sciss/TemporalObjects" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.20",
   "de.sciss" %% "lucredata-txn" % "0.24",
   "org.scalatest" %% "scalatest" % "1.7.2" % "test",
   "de.sciss" %% "lucredata-txn-views" % "0.24" % "test",
   "de.sciss" %% "lucreexpr" % "0.10" % "test"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution /* in Test */ := false

initialCommands in console := """import de.sciss.confluent._
"""

// publishArtifact in (Compile, packageDoc) := false   // disable doc generation during development cycles

