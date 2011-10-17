name := "temporalobjects"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.12",
   "org.scala-tools" %% "scala-stm" % "0.3",
   "com.sleepycat" % "je" % "4.1.7"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishTo <<= version { (v: String) =>
   Some( "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/".+(
      if( v.endsWith( "-SNAPSHOT")) "snapshots/" else "releases/"
   ))
}

pomExtra :=
<licenses>
  <license>
    <name>GPL v2+</name>
    <url>http://www.gnu.org/licenses/gpl-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

