package de.sciss
package lucre
package stm

import de.sciss.serial.{DataInput, DataOutput}

/** These are methods required by ConfluentImpl. Eventually these three methods should be made
  * package-private for `lucre` instead of `stm` in LucreSTM.
  */
object DurableSurgery {
  private[lucre] def newIDValue[D <: stm.DurableLike[D]](system: D)(implicit tx: D#Tx): Int =
    system.newIDValue()

  private[lucre] def write[D <: stm.DurableLike[D]](system: D)(id: Int)(valueFun: DataOutput => Unit)
                                                   (implicit tx: D#Tx): Unit =
    system.write(id)(valueFun)

  private[lucre] def read[D <: stm.DurableLike[D], A](system: D)(id: Int)(valueFun: DataInput => A)
                                                     (implicit tx: D#Tx): A =
    system.read(id)(valueFun)
}