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
   type FixtureParam = Cursor[ Confluent ]
   type S = Confluent

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
   class Entity( val id: S#ID, val field: S#Var[ Int ]) extends stm.Mutable.Impl[ S ] {
      protected def disposeData()( implicit tx: S#Tx ) { field.dispose() }
      protected def writeData( out: DataOutput ) { field.write( out )}
   }

   def withFixture( test: OneArgTest ) {
      val system = Confluent.tmp()
      try {
         val (_, cursor) = system.cursorRoot( _ => () )( tx => _ => tx.newCursor() )
         test( cursor )
      }
      finally {
         system.close()
      }
   }

   "An entity" should "serialize and deserialize via tx.refresh" in { cursor =>
      val value = 1234
      val h = cursor.step { implicit tx =>
         val ent = Entity( value )
         tx.newHandle( ent )
      }
      val res = cursor.step { implicit tx =>
         val ent = h.get // tx.refresh( csrStale, entStale )
         ent.field.get
      }
      assert( res == value, res )
   }
}
