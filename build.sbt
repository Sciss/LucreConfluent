name := "TemporalObjects"

version := "0.32-SNAPSHOT"

organization := "de.sciss"

description := "Confluent persistence and quasi-retroactive / fluent references for Scala"

homepage := Some( url( "https://github.com/Sciss/TemporalObjects" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.20",
   "de.sciss" %% "lucredata-txn" % "0.23-SNAPSHOT",
   "org.scalatest" %% "scalatest" % "1.7.1" % "test",
   "de.sciss" %% "lucredata-txn-views" % "0.23-SNAPSHOT" % "test"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked", "-Xelide-below", "INFO" ) // elide debug logging!

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution in Test := false

initialCommands in console := """import de.sciss.confluent._
"""
