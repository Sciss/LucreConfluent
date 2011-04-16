package de.sciss.confluent

import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}

object Serializer {
   implicit object IntSerializer extends DirectSerializer[ Any, Int ] {
      def readObject( in: TupleInput )( implicit access: Any ) : Int = in.readInt()
      def writeObject( out: TupleOutput, v: Int )( implicit access: Any ) { out.writeInt( v )}
   }
//   implicit def has[ V ]( hs: HasSerializer[ V ]) : Serializer[ V ] = hs.serializer
}

sealed trait Serializer[ -C, V ] {
   def writeObject( out: TupleOutput, v: V )( implicit access: C ) : Unit
   def readObject( in: TupleInput )( implicit access: C ) : V
}
trait DirectSerializer[ -C, V ] extends Serializer[ C, V ]
trait IndirectSerializer[ -C, V ] extends Serializer[ C, V ] {
   /**
    * Retrieve the 48-bit (!) identifier of the
    * object. That is, it is assumed that the
    * upper 16 bits are meaningless.
    */
   def id: Long // ( v: V ) : Long
}

// trait HasSerializer[ V ] { def serializer : Serializer[ V ]}