# LucreConfluent

## statement

LucreConfluent is (C)opyright 2009&ndash;2014 Hanns Holger Rutz. It is released under the [GNU General Public License](http://github.com/Sciss/LucreConfluent/blob/master/licenses/LucreConfluent-License.txt).

LucreConfluent provides a transactional, durable, and confluently persistent memory framework for the Scala programming language. It implements a transform which was described by Fiat/Kaplan and which is extended with the support for the representation of multiplicites and quasi-retroactive fluent references as well as event propagation (provided separately through the [ConfluentReactive](https://github.com/Sciss/ConfluentReactive) library). It uses [LucreSTM](https://github.com/Sciss/LucreSTM) for the transactional layer, and builds on top of data structures provided by the [LucreData](https://github.com/Sciss/LucreData) project. The overall target is integration with a system for computer music composition.

Further reading:

 - Rutz, H. H., "A Reactive, Confluently Persistent Framework for the Design of Computer Music," in Proceedings of the 9th Sound an Music Computing Conference (SMC), Copenhagen 2012.

 - Rutz, H. H. and Miranda, E. and Eckel, G., "On the Traceability of the Compositional Process," in Proceedings of the 8th Sound an Music Computing Conference (SMC), Padua 2010, pp. 38:1–38:7.

 - Fiat, A. and Kaplan, H., "Making data structures confluently persistent," in Proceedings of the 12th annual ACM-SIAM symposium on Discrete algorithms, 2001, pp. 537–546.

## building

Builds with sbt 0.12 against Scala 2.10, using standard sbt targets. The dependencies, [LucreData](https://github.com/Sciss/LucreData) and [FingerTree](https://github.com/Sciss/FingerTree) should be found automatically by sbt.

## linking

The following dependency is necessary:

    resolvers += "Oracle Repository" at "http://download.oracle.com/maven"
    
    "de.sciss" %% "lucreconfluent" % v

The current stable version `v` is `"2.5.+"`. The current snapshot version is `"2.6.+"`.

## previous versions / tags

__Note:__ see tag v0.14 for the example using actual audio file regions and sonogram view. The current version is a rework based on a new modularisation.
