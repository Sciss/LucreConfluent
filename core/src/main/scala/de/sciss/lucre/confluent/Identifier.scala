/*
 *  Identifier.scala
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

trait Identifier[S <: Sys[S]] extends stm.Identifier[S#Tx] {
  def base: Int  // name, origin, base, agent, ancestry, germ, parent, root
  def path: S#Acc

  def copy(path: S#Acc): Identifier[S]
}
