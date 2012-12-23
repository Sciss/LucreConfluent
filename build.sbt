name := "LucreConfluent"

version in ThisBuild := "1.6.0-SNAPSHOT"

organization in ThisBuild := "de.sciss"

description in ThisBuild := "Confluently persistent references for Scala"

homepage in ThisBuild := Some( url( "https://github.com/Sciss/LucreConfluent" ))

licenses in ThisBuild := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion in ThisBuild := "2.10.0"

crossScalaVersions in ThisBuild := Seq( "2.10.0", "2.9.2" )

// libraryDependencies ++= Seq(
//    "de.sciss" %% "fingertree" % "1.2.+",
//    "de.sciss" %% "lucredata-core" % "1.4.+",
//    "de.sciss" %% "lucredata-views" % "1.4.+" % "test"
// )

retrieveManaged in ThisBuild := true

scalacOptions in ThisBuild ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

scalacOptions in ThisBuild += "-no-specialization" // mother*******

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution in ThisBuild /* in Test */ := false

// publishArtifact in (Compile, packageDoc) := false   // disable doc generation during development cycles

// ---- build info ----

// buildInfoSettings
// 
// sourceGenerators in Compile <+= buildInfo
// 
// buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
//    BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
//    BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
// )
// 
// buildInfoPackage := "de.sciss.lucre.confluent"

// ---- publishing ----

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild :=
<scm>
  <url>git@github.com:Sciss/LucreConfluent.git</url>
  <connection>scm:git:git@github.com:Sciss/LucreConfluent.git</connection>
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

(LsKeys.tags in LsKeys.lsync) := Seq( "confluent", "persistence" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "LucreConfluent" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
