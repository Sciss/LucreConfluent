package de.sciss.temporal.ex

import de.sciss.trees.{ FatIdentifier => FId, FatPointer => FPtr, Version }

trait CInterval {
   def kukuu( implicit version: Version ): Tuple2[ Double, Double ] = {
      println( "aqui" )
      span( version )
   }
   def span( implicit version: Version ): Tuple2[ Double, Double ]
//   def plus( p: Double )( implicit version: Version ): CInterval = PlusInterval( FId( version.path, this ), p )
   def plus( p: Double ): CInterval = PlusInterval( this, p )
}

case class ConstantInterval( value: Tuple2[ Double, Double ]) extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = value
   override def toString = "I(" + value._1 + " :: " + value._2 + ")"
}

// actually we do not need the FId of the interval arg...
//case class PlusInterval( interval: FId[ CInterval ], plus: Double ) extends CInterval {}
case class PlusInterval( interval: CInterval, plus: Double ) extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = {
//      val iv = interval.value.span( version )
      val iv = interval.span( version )
      (iv._1 + plus, iv._2 + plus )
   }
}

case class PlusInterval2( interval: FPtr[ CInterval ], plus: Double )( implicit val inputIntervalVersion: Version )
extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = {
//      val iv = interval.value.span( version )
      val iv = interval.get()( inputIntervalVersion ).value.span( version )
      (iv._1 + plus, iv._2 + plus )
   }
}

case class PlusInterval3( interval: FPtr[ CInterval ], plus: Double )
extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = {
//      val iv = interval.value.span( version )
      var iv2: CInterval = this
      var version2 = version
      while( iv2 == this ) {
         iv2 = interval.get()( version2 ).value
         version2 = Version( version2.path.dropRight( 1 ))
      }
      val iv = iv2.span( version )
      (iv._1 + plus, iv._2 + plus )
   }
}

class CRegion {
   val interval   = new FPtr[ CInterval ]()
   val dependant  = new FPtr[ CRegion ]()
}