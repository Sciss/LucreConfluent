import sbt._
import Keys._
import sbtbuildinfo.Plugin._

object Build extends sbt.Build {
   lazy val audiowidgets: Project = Project(
      id        = "lucreconfluent",
      base      = file( "." ),
      aggregate = Seq( core, event )
   )

   lazy val core = Project(
      id        = "lucreconfluent-core",
      base      = file( "core" ),
      settings     = Project.defaultSettings ++ buildInfoSettings ++ Seq(
         libraryDependencies ++= Seq(
            "de.sciss" %% "fingertree" % "1.2.+",
            "de.sciss" %% "lucredata-core" % "1.6.+",
            "de.sciss" %% "lucredata-views" % "1.6.+" % "test",
            "de.sciss" %% "lucrestm-bdb" % "1.6.+" % "test",
            ("org.scalatest" %% "scalatest" % "1.8" cross CrossVersion.full) % "test"
         ),
         initialCommands in console := """import de.sciss.lucre.confluent._
""",
         sourceGenerators in Compile <+= buildInfo,
         buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
            BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
            BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
         ),
         buildInfoPackage := "de.sciss.lucre.confluent"
      )
   )

   lazy val event = Project(
      id           = "lucreconfluent-event",
      base         = file( "event" ),
      dependencies = Seq( core ),
      settings     = Project.defaultSettings ++ Seq(
         libraryDependencies ++= Seq(
            "de.sciss" %% "lucreevent-core" % "1.6.+",
            ("org.scalatest" %% "scalatest" % "1.8" cross CrossVersion.full) % "test"
         )
      )
   )
}
