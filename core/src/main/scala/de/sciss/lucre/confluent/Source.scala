/*
 *  Source.scala
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

trait Source[S <: Sys[S], +A] extends stm.Source[S#Tx, A] {
  def meld(from: S#Acc)(implicit tx: S#Tx): A
}
