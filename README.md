## TemporalObjects

### statement

TemporalObjects is (C)opyright 2009-2011 Hanns Holger Rutz. It is released under the [GNU General Public License](http://github.com/Sciss/TemporalObjects/blob/master/licenses/TemporalObjects-License.txt).

TemporalObjects is an ongoing attempt to build a confluent persistence framework for the Scala programming language. It implements a transform which was described by [Fiat/Kaplan] [1] and which is extended with the support for the representation of multiplicites and quasi-retroactive fluent references, as described by [Rutz et al] [2]. We currently prepare for the addition of a storage persistence backend ("BerkeleyDB Java Edition":http://www.oracle.com/technetwork/database/berkeleydb/overview/index-093405.html) and integration with the "Scala STM API":http://nbronson.github.com/scala-stm/. The overall target is integration with a system for computer music composition.

 [1]: Fiat, A. and Kaplan, H., Making data structures confluently persistent, Proceedings of the 12th annual ACM-SIAM symposium on Discrete algorithms, 2001, pp. 537–546.

 [2]: Rutz, H. H. and Miranda, E. and Eckel, G., On the Traceability of the Compositional Process, Proceedings of the Sound an Music Computing Conference, 2010, pp. 38:1–38:7.

### requirements / installation

Builds with sbt 0.11 against Scala 2.9.1 and Java 1.6, using standard sbt targets. The dependencies [LucreSTM](https://github.com/Sciss/LucreSTM) and [LucreData](https://github.com/Sciss/LucreData) are currently not in any repository, so you need to clone them and export each of them with `sbt publish-local` first. An IntelliJ IDEA project can be created with the sbt-idea plugin which provides the `gen-idea` task.

__Note:__ see tag v0.14 for the example using actual audio file regions and sonogram view. The current version is stripped down to prepare for use as library.

### download

The current version can be downloaded from [github.com/Sciss/TemporalObjects](http://github.com/Sciss/TemporalObjects).
