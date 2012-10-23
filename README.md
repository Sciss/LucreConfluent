## ConfluentReactive

### statement

ConfluentReactive is (C)opyright 2012 Hanns Holger Rutz. It is released under the [GNU General Public License](http://github.com/Sciss/ConfluentReactive/blob/master/licenses/ConfluentReactive-License.txt).

ConfluentReactive is a simple layer on top of [LucreConfluent](https://github.com/Sciss/LucreConfluent) and [LucreEvent](https://github.com/Sciss/LucreEvent) to create a combined system.

### building

Builds with sbt 0.12 against Scala 2.9.2 and Java 1.6, using standard sbt targets. The dependencies should be found automatically by sbt.

### linking

The following dependency is necessary:

    "de.sciss" %% "confluentreactive" % "1.4.+"
