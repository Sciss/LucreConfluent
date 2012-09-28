name := "ConfluentReactive"

version := "1.3.0-SNAPSHOT"

organization := "de.sciss"

description := "Confluent persistence and quasi-retroactive / fluent references for Scala"

homepage := Some( url( "https://github.com/Sciss/ConfluentReactive" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

// crossScalaVersions in ThisBuild := Seq( "2.10.0-M6", "2.9.2" )

libraryDependencies ++= Seq(
   "de.sciss" %% "lucreevent" % "1.3.0-SNAPSHOT",
   "de.sciss" %% "lucreconfluent" % "1.3.0-SNAPSHOT"
)

libraryDependencies <+= scalaVersion { sv =>
   val v = sv match {
      case "2.10.0-M7" => "1.9-2.10.0-M7-B1"
      case _ => "1.8"
   }
   "org.scalatest" %% "scalatest" % v % "test"
}

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

scalacOptions += "-no-specialization" // mother*******

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution /* in Test */ := false

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
   BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
   BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.confluent.event"

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
  <url>git@github.com:Sciss/ConfluentReactive.git</url>
  <connection>scm:git:git@github.com:Sciss/ConfluentReactive.git</connection>
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

(LsKeys.ghRepo in LsKeys.lsync) := Some( "ConfluentReactive" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
