package de.sciss.confluent

import de.sciss.lucre.{DataOutput, DataInput}
import de.sciss.lucre.stm.{Cursor, Sys, MutableSerializer, Mutable}
import de.sciss.lucre.stm.impl.BerkeleyDB
import java.io.File

class Nodes[S <: Sys[S]] {
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

    def disposeData()(implicit tx: S#Tx) {
      value.dispose()
      next.dispose()
    }

    def writeData(out: DataOutput) {
      value.write(out)
      next.write(out)
    }
  }
}

object PaperFigure extends App {
   val dir     = File.createTempFile( "database", "db" )
   dir.delete()
   val store   = BerkeleyDB.factory( dir )
   val s       = Confluent( store )

   new Example( s, s )
}

class Example[S <:Sys[S]](s: S, c: Cursor[S]) {
  val nodes = new Nodes[S]
  import nodes._

  val access = s.root(_ => Option.empty[Node])

  c.step { implicit tx =>
    val w1 = Node(3)
    val w2 = Node(5)
    w1.next set Some(w2)
    access set Some(w1)
  }
}
