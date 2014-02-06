package de.sciss.lucre
package confluent

import org.scalatest.{Outcome, Matchers, fixture}
import stm.store.BerkeleyDB

// helper trait providing a fixture
trait ConfluentSpec extends fixture.FlatSpec with Matchers {
  final type S = Confluent
  final type D = stm.Durable
  final type FixtureParam = Confluent // confluent.Cursor[ S ]

  final def withFixture(test: OneArgTest): Outcome = {
    val system = Confluent(BerkeleyDB.tmp())
    try {
      //         val (_, cursor) = system.cursorRoot( _ => () )( tx => _ => tx.newCursor() )
      //         test( cursor )
      test(system)
    }
    finally {
      system.close()
    }
  }
}