package de.sciss.temporal.ex

import de.sciss.trees.{ FatIdentifier => FId, FatPointer => FPtr, Version }

trait CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ]
//   def plus( p: Double )( implicit version: Version ): CInterval = PlusInterval( FId( version.path, this ), p )
   def plus( p: Double ): CInterval = PlusInterval( this, p )
}

case class ConstantInterval( value: Tuple2[ Double, Double ]) extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = value
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

case class PlusInterval2( interval: FPtr[ CInterval ], plus: Double )( implicit creationVersion: Version )
extends CInterval {
   def span( implicit version: Version ): Tuple2[ Double, Double ] = {
//      val iv = interval.value.span( version )
      val iv = interval.get()( creationVersion ).value.span( version )
      (iv._1 + plus, iv._2 + plus )
   }
}

class CRegion {
   val interval   = new FPtr[ CInterval ]()
   val dependant  = new FPtr[ CRegion ]()
}