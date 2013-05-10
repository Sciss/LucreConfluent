package de.sciss
package lucre
package confluent

import stm.store.BerkeleyDB
import java.io.File
import de.sciss.serial.{DataOutput, DataInput}

object DoubleRootTest extends App {
  type S  = Confluent
  type D  = S#D

  val dir       = File.createTempFile("double", "trouble")
  require(dir.delete())
  println(s"Directory: $dir")

  println("First iteration")
  iter()
  println("Second iteration")
  iter()

  class Data(val id: S#ID, val vr: S#Var[Int])

  implicit object DataSer extends serial.Serializer[S#Tx, S#Acc, Data] {
    def write(v: Data, out: DataOutput) {
      v.id.write(out)
      v.vr.write(out)
    }

    def read(in: DataInput, access: S#Acc)(implicit tx: S#Tx): Data = {
      val id = tx.readID(in, access)
      val vr = tx.readIntVar(id, in)
      new Data(id, vr)
    }
  }

  def iter() {
    val database            = BerkeleyDB.factory(dir, createIfNecessary = true)
    implicit val confluent  = Confluent(database)
    val durable             = confluent.durable

    val cursorAcc = durable.root { implicit tx =>
      println("New cursor")
      Cursor[S, D]()
    }

    val cursor = durable.step { implicit tx => cursorAcc() }

    val (varAcc /* , cursor */ ) = confluent./* cursorRoot */ root { implicit tx =>
      val id = tx.newID()
      val vr = tx.newIntVar(id, 33)
      new Data(id, vr)
    }
    //    { tx => _ =>
    //      implicit val dtx = confluent.durableTx(tx)
    //      cursorAcc()
    //    }

    cursor.step { implicit tx =>
      val vr      = varAcc().vr
      val current = vr()
      vr()        = current + 1
      println(s"Recovered $current")
    }

    confluent.close()
  }
}