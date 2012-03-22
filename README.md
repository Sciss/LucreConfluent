## TemporalObjects

### statement

TemporalObjects is (C)opyright 2009&ndash;2012 Hanns Holger Rutz. It is released under the [GNU General Public License](http://github.com/Sciss/TemporalObjects/blob/master/licenses/TemporalObjects-License.txt).

TemporalObjects is an ongoing attempt to build a confluent persistence framework for the Scala programming language. It implements a transform which was described by Fiat/Kaplan [^1] and which is extended with the support for the representation of multiplicites and quasi-retroactive fluent references, as described by Rutz et al [^2]. It uses [LucreData](https://github.com/Sciss/LucreData) for the transactional layer. The overall target is integration with a system for computer music composition.

 [^1]: Fiat, A. and Kaplan, H., Making data structures confluently persistent, Proceedings of the 12th annual ACM-SIAM symposium on Discrete algorithms, 2001, pp. 537–546.

 [^2]: Rutz, H. H. and Miranda, E. and Eckel, G., On the Traceability of the Compositional Process, Proceedings of the Sound an Music Computing Conference, 2010, pp. 38:1–38:7.

### requirements / installation

Builds with sbt 0.11 against Scala 2.9.1 and Java 1.6, using standard sbt targets. The dependencies, [LucreSTM](https://github.com/Sciss/LucreSTM) and  [FingerTree](https://github.com/Sciss/FingerTree) should be found automatically by sbt. Due to a bug in sbt publishing of [LucreData](https://github.com/Sciss/LucreData) is currently a pita, so this project you need to clone yourself and perform a `publish-local` on it first.

## linking to TemporalObjects

The following dependency is necessary:

    "de.sciss" %% "temporalobjects" % "0.30"

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
