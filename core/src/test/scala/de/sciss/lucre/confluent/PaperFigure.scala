package de.sciss
package lucre
package confluent

import stm.{MutableSerializer, Mutable}
import stm.store.BerkeleyDB
import java.io.File
import serial.{DataInput, DataOutput}

class Nodes[S <: stm.Sys[S]] {
  object Node {
    implicit object ser extends MutableSerializer[S, Node] {
      def readData(in: DataInput, _id: S#ID)(implicit tx: S#Tx) = new Node with Mutable.Impl[ S ] {
        val id    = _id
        val value = tx.readVar[Int](id, in)
        val next  = tx.readVar[Option[Node]](id, in)
      }
    }

    def apply(init: Int)(implicit tx: S#Tx): Node = new Node with Mutable.Impl[ S ] {
      val id    = tx.newID()
      val value = tx.newVar(id, init)
      val next  = tx.newVar(id, Option.empty[Node])
    }
  }
  trait Node extends Mutable[S#ID, S#Tx] {
    def value: S#Var[Int]
    def next: S#Var[Option[Node]]

    def disposeData()(implicit tx: S#Tx): Unit = {
      value.dispose()
      next .dispose()
    }

    def writeData(out: DataOutput): Unit = {
      value.write(out)
      next .write(out)
    }
  }
}

object PaperFigure extends App {
   val dir     = File.createTempFile( "database", "db" )
   dir.delete()
   val store   = BerkeleyDB.factory( dir )
   val s       = Confluent( store )

   new Example( s )
}

class Example[S <: Sys[S]](val s: S) {
  val nodes = new Nodes[S]
  import nodes._

  val (access, cursor) = s.cursorRoot(_ => Option.empty[Node])(implicit tx => _ => s.newCursor())

  cursor.step { implicit tx =>
    val w1    = Node(3)
    val w2    = Node(5)
    w1.next() = Some(w2)
    access()  = Some(w1)
  }
}
