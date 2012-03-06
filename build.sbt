name := "TemporalObjects"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.20-SNAPSHOT",
   "de.sciss" %% "lucredata-txn" % "0.20-SNAPSHOT"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )
