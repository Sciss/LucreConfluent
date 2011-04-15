package de.sciss.confluent

import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}

object Serializer {
   implicit object IntSerializer extends DirectSerializer[ Int ] {
      def readObject( in: TupleInput ) : Int = in.readInt()
      def writeObject( out: TupleOutput, v: Int ) { out.writeInt( v )}
   }
}

sealed trait Serializer[ V ] {
   def writeObject( out: TupleOutput, v: V ) : Unit
   def readObject( in: TupleInput ) : V
}
trait DirectSerializer[ V ] extends Serializer[ V ]
trait IndirectSerializer[ V ] extends Serializer[ V ] {
   /**
    * Retrieve the 48-bit (!) identifier of the
    * object. That is, it is assumed that the
    * upper 16 bits are meaningless.
    */
   def id: Long // ( v: V ) : Long
}