name                         := "LucreConfluent"

version         in ThisBuild := "2.7.0-SNAPSHOT"

organization    in ThisBuild := "de.sciss"

description     in ThisBuild := "Confluently persistent references for Scala"

homepage        in ThisBuild := Some(url("https://github.com/Sciss/LucreConfluent"))

licenses        in ThisBuild := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

scalaVersion    in ThisBuild := "2.10.3"

resolvers       in ThisBuild += "Oracle Repository" at "http://download.oracle.com/maven"  // required for sleepycat

// retrieveManaged in ThisBuild := true

scalacOptions   in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions   in ThisBuild ++= Seq("-Xelide-below", "INFO") // elide debug logging!

scalacOptions   in ThisBuild += "-no-specialization" // mother*******

testOptions in Test += Tests.Argument("-oDF")   // ScalaTest: durations and full stack traces

parallelExecution in ThisBuild /* in Test */ := false

// ---- publishing ----

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("confluent", "persistence")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

