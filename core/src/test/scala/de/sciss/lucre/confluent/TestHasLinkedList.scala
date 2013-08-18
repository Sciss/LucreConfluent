package de.sciss
package lucre
package confluent

import stm.{Mutable, MutableSerializer}
import serial.{DataInput, DataOutput}

trait TestHasLinkedList {
  class Types[S <: Sys[S]](val s: S) {
    type Sys = S

    object Node {
      implicit object ser extends MutableSerializer[S, Node] {
        def readData(in: DataInput, _id: S#ID)(implicit tx: S#Tx): Node = new Node with Mutable.Impl[S] {
          val id    = _id
          val name  = in.readUTF()
          val value = tx.readIntVar(id, in)
          val next  = tx.readVar[Option[Node]](id, in)
        }
      }

      def apply(_name: String, init: Int)(implicit tx: S#Tx): Node = new Node with Mutable.Impl[S] {
        val id    = tx.newID()
        val name  = _name
        val value = tx.newIntVar(id, init)
        val next  = tx.newVar[Option[Node]](id, None)
      }
    }

    trait Node extends Mutable[S#ID, S#Tx] {
      def name: String
      def value: S#Var[Int]
      def next: S#Var[Option[Node]]

      protected def disposeData()(implicit tx: S#Tx): Unit = {
        value.dispose()
        next .dispose()
      }

      protected def writeData(out: DataOutput): Unit = {
        out.writeUTF(name)
        value.write(out)
        next .write(out)
      }

      override def toString = "Node(" + name + ", " + id + ")"
    }

    def toList(next: Option[Node])(implicit tx: S#Tx): List[(String, Int)] = next match {
      case Some(n)  => (n.name, n.value()) :: toList(n.next())
      case _        => Nil
    }
  }
}