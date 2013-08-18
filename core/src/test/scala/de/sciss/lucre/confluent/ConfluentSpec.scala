package de.sciss.lucre
package confluent

import org.scalatest.fixture
import org.scalatest.matchers.ShouldMatchers
import stm.store.BerkeleyDB

// helper trait providing a fixture
trait ConfluentSpec extends fixture.FlatSpec with ShouldMatchers {
  final type S = Confluent
  final type D = stm.Durable
  final type FixtureParam = Confluent // confluent.Cursor[ S ]

  final def withFixture(test: OneArgTest): Unit = {
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