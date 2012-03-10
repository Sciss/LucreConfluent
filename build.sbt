name := "TemporalObjects"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "de.sciss" %% "fingertree" % "0.20-SNAPSHOT",
   "de.sciss" %% "lucredata-txn" % "0.21-SNAPSHOT"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

initialCommands in console := """import de.sciss.confluent._
import impl.KSysImpl.Path
import concurrent.stm.TxnExecutor.{defaultAtomic => atomic}
val rnd = new util.Random( 0L )
val p0 = Path.test_empty
val p1 = p0 test_:+ rnd.nextInt()
val p2 = p1 test_:+ rnd.nextInt()
val p3 = p1 test_:+ rnd.nextInt()
val m = impl.ConfluentMemoryMap.ref[ String ]()"""
