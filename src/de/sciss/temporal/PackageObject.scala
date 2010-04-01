package de.sciss {
   package object temporal {
      import temporal._

      type PeriodDependant    = MutableModelDependant[ PeriodLike ]
      type IntervalDependant  = MutableModelDependant[ IntervalLike ]

      implicit def doubleToPeriodConst( d: Double ) = new PeriodConstFactory( d )
//      implicit def intervalToExpr( iv: IntervalLike ) : IntervalExprLike = iv match {
//         case ivar: IntervalExprLike => ivar
//         case _ => IntervalExpr( iv.start, iv.stop )  // wrap
//      }

      val IndetStart = PeriodConst( Double.NegativeInfinity ) :? PeriodConst( Double.PositiveInfinity )
      val IndetDur   = PeriodConst( 0.0 ) :? PeriodConst( Double.PositiveInfinity )
   }
} 