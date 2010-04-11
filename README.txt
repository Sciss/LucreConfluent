TemporalObjects (C)opyright 2010 Hanns Holger is covered by the GNU GPL v2 (see "licenses" folder)

This builds against Scala 2.8 BETA / Java SE 6. An IDEA 9 project file is included.
Before building, the following libraries need to be installed:

libraries/
    iText-5.0.1.jar             // binary from http://sourceforge.net/projects/itext
    ScalaInterpreterPane.jar    // source (git) from http://github.com/Sciss/ScalaInterpreterPane
    ScissDSP.jar                // source (git) from http://github.com/Sciss/ScissDSP
    ScissLib.jar                // source (svn) from http://sourceforge.net/projects/scisslib
    SonogramOverview.jar        // source (git) from http://github.com/Sciss/SonogramOverview

Creating a standalone executable:

    ant -DSCALA_HOME=...

If you already have a shell variable SCALA_HOME, this would be simply:

    ant -DSCALA_HOME=${SCALA_HOME}

To run the executable:

    java -jar TemporalObjects.jar
