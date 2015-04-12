lazy val baseName         = "LucreConfluent"

lazy val baseNameL        = baseName.toLowerCase

lazy val stmVersion       = "2.1.1"
lazy val dataVersion      = "2.3.0"
lazy val eventVersion     = "2.7.3"
lazy val fingerVersion    = "1.5.2"
lazy val scalaTestVersion = "2.2.4"

lazy val commonSettings = Seq(
  version             := "2.10.0-SNAPSHOT",
  organization        := "de.sciss",
  description         := "Confluently persistent references for Scala",
  homepage            := Some(url("https://github.com/Sciss/LucreConfluent")),
  licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
  scalaVersion        := "2.11.6",
  crossScalaVersions  := Seq("2.11.6", "2.10.5"),
  resolvers           += "Oracle Repository" at "http://download.oracle.com/maven",  // required for sleepycat
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8"),
  scalacOptions      ++= {
    if (isSnapshot.value) Nil else Seq("-Xelide-below", "INFO") // elide debug logging!
  },
  testOptions in Test += Tests.Argument("-oDF"),   // ScalaTest: durations and full stack traces
  parallelExecution  /* in Test */ := false,
  // ---- publishing ----
  publishMavenStyle  := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra  := { val n = baseName
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
)

lazy val root = Project(
  id            = baseNameL,
  base          = file("."),
  aggregate     = Seq(core, event),
  dependencies  = Seq(core, event), // i.e. root = full sub project. if you depend on root, will draw all sub modules.
  settings      = lsSettings ++ commonSettings ++ Seq(
    name := baseName,
    publishArtifact in(Compile, packageBin) := false, // there are no binaries
    publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in(Compile, packageSrc) := false, // there are no sources
    // ---- ls.implicit.ly ----
    (LsKeys.tags   in LsKeys.lsync) := Seq("confluent", "persistence"),
    (LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")
  )
)

lazy val core = Project(
  id = s"$baseNameL-core",
  base = file("core"),
  settings = commonSettings ++ Seq(
    name := s"$baseName-core",
    libraryDependencies ++= Seq(
      "de.sciss"      %% "fingertree"      % fingerVersion,
      "de.sciss"      %% "lucredata-core"  % dataVersion,
      "de.sciss"      %% "lucredata-views" % dataVersion      % "test",
      "de.sciss"      %% "lucrestm-bdb"    % stmVersion       % "test",
      "org.scalatest" %% "scalatest"       % scalaTestVersion % "test"
    ),
    initialCommands in console := """import de.sciss.lucre.confluent._"""
  )
)
.enablePlugins(BuildInfoPlugin).settings(
  // sourceGenerators in Compile <+= buildInfo,
  buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
    BuildInfoKey.map(homepage) {
      case (k, opt) => k -> opt.get
    },
    BuildInfoKey.map(licenses) {
      case (_, Seq((lic, _))) => "license" -> lic
    }
  ),
  buildInfoPackage := "de.sciss.lucre.confluent"
)

lazy val event = Project(
  id = s"$baseNameL-event",
  base = file("event"),
  dependencies = Seq(core),
  settings = commonSettings ++ Seq(
    name := s"$baseName-event",
    libraryDependencies ++= Seq(
      "de.sciss"      %% "lucreevent-core" % eventVersion,
      "de.sciss"      %% "lucrestm-bdb"    % stmVersion       % "test",
      "org.scalatest" %% "scalatest"       % scalaTestVersion % "test"
    )
  )
)