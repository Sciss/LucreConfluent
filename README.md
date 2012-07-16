## TemporalObjects

### statement

TemporalObjects is (C)opyright 2009&ndash;2012 Hanns Holger Rutz. It is released under the [GNU General Public License](http://github.com/Sciss/TemporalObjects/blob/master/licenses/TemporalObjects-License.txt).

TemporalObjects provides a transactional, durable, and confluently persistent memory and reaction framework for the Scala programming language. It implements a transform which was described by Fiat/Kaplan and which is extended with the support for the representation of multiplicites and quasi-retroactive fluent references as well as event propagation. It uses [LucreSTM](https://github.com/Sciss/LucreSTM) for the transactional layer, and builds on top of data structures provided by the [LucreData](https://github.com/Sciss/LucreData) project. The overall target is integration with a system for computer music composition.

Further reading:

 - Rutz, H. H., "A Reactive, Confluently Persistent Framework for the Design of Computer Music," in Proceedings of the 9th Sound an Music Computing Conference (SMC), Copenhagen 2012.

 - Rutz, H. H. and Miranda, E. and Eckel, G., "On the Traceability of the Compositional Process," in Proceedings of the 8th Sound an Music Computing Conference (SMC), Padua 2010, pp. 38:1–38:7.

 - Fiat, A. and Kaplan, H., "Making data structures confluently persistent," in Proceedings of the 12th annual ACM-SIAM symposium on Discrete algorithms, 2001, pp. 537–546.

### requirements / installation

Builds with sbt 0.11 against Scala 2.9.2 and Java 1.6, using standard sbt targets. The dependencies, [LucreSTM](https://github.com/Sciss/LucreSTM) and  [FingerTree](https://github.com/Sciss/FingerTree) should be found automatically by sbt. Due to a bug in sbt publishing of [LucreData](https://github.com/Sciss/LucreData) is currently a pita, so this project you need to clone yourself and perform a `publish-local` on it first.

### linking to TemporalObjects

The following dependency is necessary:

    "de.sciss" %% "temporalobjects" % "0.33"

### previous versions / tags

__Note:__ see tag v0.14 for the example using actual audio file regions and sonogram view. The current version is a rework based on a new modularisation.

### creating an IntelliJ IDEA project

To develop the sources of TemporalObjects, we recommend to use IntelliJ IDEA. If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run the following two commands from the sbt shell:

    > set ideaProjectName := "TemporalObjects"
    > gen-idea

### download

The current version can be downloaded from [github.com/Sciss/TemporalObjects](http://github.com/Sciss/TemporalObjects).
