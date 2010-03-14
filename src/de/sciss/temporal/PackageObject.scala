package de.sciss {
   package object temporal {
      import temporal._

      type PeriodDependant    = MutableModelDependant[ PeriodLike ]
      type IntervalDependant  = MutableModelDependant[ IntervalLike ]

      implicit def doubleToPeriodConst( d: Double ) = new PeriodConstFactory( d )
      implicit def intervalToVar( iv: IntervalLike ) : IntervalVarLike = iv match {
         case ivar: IntervalVarLike => ivar
         case _ => IntervalVar( iv.start, iv.stop )  // wrap
      }
   }
} 