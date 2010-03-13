package de.sciss {
   package object temporal {
      import temporal._

      type PeriodDependant    = MutableModelDependant[ PeriodLike ]
      type IntervalDependant  = MutableModelDependant[ IntervalLike ]
   }
} 