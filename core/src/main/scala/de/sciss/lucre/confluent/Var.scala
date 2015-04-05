/*
 *  Var.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.confluent

import de.sciss.lucre.stm

trait Var[S <: Sys[S], /* @spec(ialized) */ A] extends stm.Var[S#Tx, A] {
  private[confluent] def setInit(value: A)(implicit tx: S#Tx): Unit
}