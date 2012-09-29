package de.sciss.lucre
package confluent

import org.scalatest.fixture
import org.scalatest.matchers.ShouldMatchers
import stm.MutableSerializer

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.RefreshSpec
 */
class RefreshSpec extends fixture.FlatSpec with ShouldMatchers {
   type FixtureParam = Confluent
   type S = FixtureParam

   confluent.showLog = true

   object Entity {
      implicit object Ser extends MutableSerializer[ S, Entity ] {
            protected def readData( in: DataInput, id: S#ID )( implicit tx: S#Tx ) = {
               val field = tx.readIntVar( id, in )
               new Entity( id, field )
            }
         }

      def apply( init: Int )( implicit tx: S#Tx ) : Entity = {
         val id      = tx.newID()
         val field   = tx.newIntVar( id, init )
         new Entity( id, field )
      }
   }
   class Entity( val id: S#ID, val field: S#Var[ Int ]) extends stm.Mutable[ S#ID, S#Tx ] {
      def dispose()( implicit tx: S#Tx ) { field.dispose() }
      def write( out: DataOutput) { field.write( out )}
   }

   def withFixture( test: OneArgTest ) {
      val system = Confluent.tmp()
      try {
         system.root( _ => () )
         test( system )
      }
      finally {
         system.close()
      }
   }

   "An entity" should "serialize and deserialize via tx.refresh" in { system =>
      val value = 1234
      val (entStale, csrStale) = system.step { implicit tx =>
         val ent = Entity( value )
         ent -> system.position
      }
      val res = system.step { implicit tx =>
         val ent = tx.refresh( csrStale, entStale )
         ent.field.get
      }
      assert( res == value, res )
   }
}
