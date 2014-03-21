import sbt._
import Keys._
import sbtbuildinfo.Plugin._

object Build extends sbt.Build {
  lazy val stmVersion       = "2.0.3+"
  lazy val dataVersion      = "2.2.3+"
  lazy val eventVersion     = "2.6.+"
  lazy val fingerVersion    = "1.5.+"
  lazy val scalaTestVersion = "2.1.2"

  lazy val root: Project = Project(
    id            = "lucreconfluent",
    base          = file("."),
    aggregate     = Seq(core, event),
    dependencies  = Seq(core, event), // i.e. root = full sub project. if you depend on root, will draw all sub modules.
    settings      = Project.defaultSettings ++ Seq(
      publishArtifact in(Compile, packageBin) := false, // there are no binaries
      publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
      publishArtifact in(Compile, packageSrc) := false  // there are no sources
    )
  )

  // convert the base version to a compatible version for
  // library dependencies. e.g. `"1.3.1"` -> `"1.3.+"`
  object Compatible {
    def unapply(v: String) = {
      require(v.count(_ == '.') == 2)
      val i = v.lastIndexOf('.') + 1
      val c = v.substring(0, i) + "+"
      Some(c)
    }
  }

  lazy val core = Project(
    id = "lucreconfluent-core",
    base = file("core"),
    settings = Project.defaultSettings ++ buildInfoSettings ++ Seq(
      libraryDependencies ++= Seq(
        "de.sciss" %% "fingertree"      % fingerVersion,
        "de.sciss" %% "lucredata-core"  % dataVersion,
        "de.sciss" %% "lucredata-views" % dataVersion      % "test",
        "de.sciss" %% "lucrestm-bdb"    % stmVersion       % "test",
        "org.scalatest" %% "scalatest"  % scalaTestVersion % "test"
      ),
      initialCommands in console := """import de.sciss.lucre.confluent._""",
      sourceGenerators in Compile <+= buildInfo,
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
  )

  lazy val event = Project(
    id = "lucreconfluent-event",
    base = file("event"),
    dependencies = Seq(core),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "de.sciss" %% "lucreevent-core" % eventVersion,
        "de.sciss" %% "lucrestm-bdb"    % stmVersion       % "test",
        "org.scalatest" %% "scalatest"  % scalaTestVersion % "test"
      )
    )
  )
}
