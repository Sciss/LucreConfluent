import sbt._

/**
 *    @version 0.11, 05-May-10
 */
class TemporalObjectsProject( info: ProjectInfo ) extends ProguardProject( info ) {
   // stupidly, we need to redefine the dependancy here, because
   // for some reason, sbt will otherwise try to look in the maven repo
   val dep1 = "jsyntaxpane" % "jsyntaxpane" % "0.9.5-b29" from "http://jsyntaxpane.googlecode.com/files/jsyntaxpane-0.9.5-b29.jar"
   val dep2 = "de.sciss" %% "scalainterpreterpane" % "0.16"
   val dep3 = "de.sciss" %% "sonogramoverview" % "0.14"
//   val dep4 = "com.itextpdf" % "itext" % "5.0.2" from "http://sourceforge.net/projects/itext/files/iText/iText5.0.2/iText-5.0.2.jar/download"

   val camelCaseName          = "TemporalObjects"

   private val jarExt                 = ".jar"
   private val jarFilter: FileFilter  = "*" + jarExt

   /**
    *    Note: there have been always problems in the shrinking,
    *    even with the most severe keep options, and anyway the
    *    size reduction was minimal (some 8%), so now we just
    *    use proguard to put everything in one jar, without
    *    shrinking.
    */
   override def proguardOptions = List(
      "-target 1.6",
      "-dontobfuscate",
      "-dontshrink",
      "-dontpreverify",
      "-forceprocessing"
   )

   override def minJarName = camelCaseName + "-full" + jarExt
   override def minJarPath: Path = minJarName

   private def allJarsPath = (publicClasspath +++ buildLibraryJar +++ buildCompilerJar +++ jarPath) ** jarFilter
   override def proguardInJars = allJarsPath --- jarPath // the plugin adds jarPath again!!

   lazy val standalone = proguard
}
