package de.sciss
package lucre
package confluent

import stm.store.BerkeleyDB
import serial.{DataInput, DataOutput}

// \ref{lst:lucre_durable_linkedlist}, \ref{lst:lucre_durable_traverse}, \ref{lst:linkedlist_init}
object ThesisFiatKaplanExample extends App {
  object LinkedList {
    implicit def listSer[S <: Sys[S], A](implicit peerSer: serial.Serializer[S#Tx, S#Acc, A]): serial.Serializer[S#Tx, S#Acc, LinkedList[S, A]] = new ListSer[S, A]

    private class ListSer[S <: Sys[S], A](implicit _peerSer: serial.Serializer[S#Tx, S#Acc, A])
      extends stm.MutableSerializer[S, LinkedList[S, A]] {

      protected def readData(in: DataInput, _id: S#ID)(implicit tx: S#Tx): LinkedList[S, A] =
        new Impl[S, A] {
          val peerSer = _peerSer
          val id = _id
          val head = tx.readVar[Option[Cell]](id, in)(serial.Serializer.option(CellSer))
        }
    }

    def apply[S <: Sys[S], A]()(implicit tx: S#Tx, _peerSer: serial.Serializer[S#Tx, S#Acc, A]): LinkedList[S, A] =
      new Impl[S, A] {
        val peerSer = _peerSer
        val id = tx.newID()
        val head = tx.newVar(id, Option.empty[Cell])(serial.Serializer.option(CellSer))
    }

    private abstract class Impl[S <: Sys[S], A] extends LinkedList[S, A] with stm.Mutable.Impl[S] {
      implicit def peerSer: serial.Serializer[S#Tx, S#Acc, A]

      def cell(init: A)(implicit tx: S#Tx): Cell = new Cell {
        val next  = tx.newVar(id, Option.empty[Cell])
        val value = init
      }

      implicit object CellSer extends serial.Serializer[S#Tx, S#Acc, Cell] {
        def write(cell: Cell, out: DataOutput): Unit = {
          cell.next.write(out)
          peerSer.write(cell.value, out)
        }

        def read(in: DataInput, access: S#Acc)(implicit tx: S#Tx): Cell = new Cell {
          val next  = tx.readVar[Option[Cell]](id, in)
          val value = peerSer.read(in, access)
        }
      }

      def disposeData()(implicit tx: S#Tx): Unit = head.dispose()

      def writeData(out: DataOutput): Unit = head.write(out)
    }
  }
  trait LinkedList[S <: Sys[S], A] extends stm.Mutable[S#ID, S#Tx] {
    list =>
    override def toString = "LinkedList"
    def head: S#Var[Option[Cell]]
    trait Cell {
      override def toString = s"$list.cell<$next, $value>"
      def next: S#Var[Option[Cell]]
      def value: A
    }

    def cell(init: A)(implicit tx: S#Tx): Cell
  }

  val store = BerkeleyDB.tmp()
  val s     = Confluent(store)

  val (access, cursor) = s.cursorRoot { implicit tx =>
    val list    = LinkedList[Confluent, Int]()
    val w0      = list.cell(init = 2)
    val w1      = list.cell(init = 1)
    list.head() = Some(w0)
    w0.next()   = Some(w1)
    list
  } { implicit tx => _ => s.newCursor() }

  cursor.step { implicit tx =>
    val list      = access()
    val Some(w0)  = list.head()
    val Some(w1)  = w0.next()
    w0.next()     = None
    list.head()   = Some(w1)
    w1.next()     = Some(w0)
  }

  def traverse[S <: Sys[S], A](l: LinkedList[S, A])(implicit tx: S#Tx): Unit = {
    def loop(opt: Option[l.Cell]): Unit =
      opt match {
        case Some(cell) =>
          println(cell.value)
          loop(cell.next())
        case _ =>
      }

    loop(l.head())
  }

  cursor.step { implicit tx => traverse(access()) } // 1, 2
}