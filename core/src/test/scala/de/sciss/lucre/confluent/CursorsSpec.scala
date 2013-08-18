package de.sciss
package lucre
package confluent

import stm.MutableSerializer
import collection.immutable.{IndexedSeq => IIdxSeq}
import serial.{DataInput, DataOutput}

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.CursorsSpec
 */
class CursorsSpec extends ConfluentSpec {
  //   confluent.showLog = true

  object Entity {
    implicit object CursorSer extends serial.Serializer[S#Tx, S#Acc, Cursor[S, D]] {
      def write(c: Cursor[S, D], out: DataOutput): Unit = c.write(out)

      def read(in: DataInput, access: S#Acc)(implicit tx: S#Tx): Cursor[S, D] = {
        tx.system.readCursor(in)
      }
    }

    implicit object Ser extends MutableSerializer[S, Entity] {
      protected def readData(in: DataInput, id: S#ID)(implicit tx: S#Tx) = {
        val field = tx.readIntVar(id, in)
        //            val dtx: D#Tx  = tx.durable
        //            val did        = dtx.readID( in, () )
        //            val cursors    = dtx.readVar[ IIdxSeq[ Cursor[ S ]]]( did, in )
        val cursors = tx.readVar[IIdxSeq[Cursor[S, D]]](id, in)
        new Entity(id, field, cursors)
      }
    }

    def apply(init: Int)(implicit tx: S#Tx): Entity = {
      val id = tx.newID()
      val field = tx.newIntVar(id, init)
      //         val dtx: D#Tx  = tx.durable
      //         val did        = dtx.newID()
      val initCsr = IIdxSeq(tx.system.newCursor(tx.inputAccess), tx.system.newCursor(tx.inputAccess))
      //         val cursors    = dtx.newVar[ IIdxSeq[ Cursor[ S ]]]( did, initCsr )
      val cursors = tx.newVar[IIdxSeq[Cursor[S, D]]](id, initCsr)
      new Entity(id, field, cursors)
    }
  }

  class Entity(val id: S#ID, val field: S#Var[Int], cursorsVar: S#Var[IIdxSeq[Cursor[S, D]]])
    extends stm.Mutable.Impl[S] {
    protected def disposeData()(implicit tx: S#Tx): Unit = {
      //         implicit val dtx: D#Tx  = tx.durable
      field     .dispose()
      cursorsVar.dispose()
    }

    def cursors(implicit tx: S#Tx): IIdxSeq[Cursor[S, D]] = {
      //         implicit val dtx: D#Tx  = tx.durable
      cursorsVar()
    }

    protected def writeData(out: DataOutput): Unit = {
       field     .write(out)
       cursorsVar.write(out)
     }
   }

   "Multiple cursors" should "work independently" in { system =>
      val (access, cursors) = system.cursorRoot { implicit tx =>
         Entity( 0 )
      } { implicit tx => _.cursors }

      val zipped = cursors.zipWithIndex

      zipped.foreach { case (cursor, idx) =>
         cursor.step { implicit tx =>
            val e = access()
            e.field() = idx + 1
         }
      }

      val res = cursors.map { cursor =>
         cursor.step { implicit tx =>
            val e = access()
            e.field()
         }
      }

      assert( res === IIdxSeq( 1, 2 ))
   }
}
