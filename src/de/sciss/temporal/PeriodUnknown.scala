package de.sciss.temporal

case object PeriodUnknown
extends PeriodExpr {
   val inf     = PeriodConst( Double.NegativeInfinity )
   val sup     = PeriodConst( Double.PositiveInfinity )
   def fixed   = this

   override def toString = "?"
}