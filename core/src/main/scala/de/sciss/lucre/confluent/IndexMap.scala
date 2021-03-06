/*
 *  IndexMap.scala
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

import de.sciss.serial.Writable

trait IndexMap[S <: Sys[S], A] extends Writable {
  def add(term: Long, value: A)(implicit tx: S#Tx): Unit

  def nearest      (term: Long)(implicit tx: S#Tx): (Long, A)
  def nearestOption(term: Long)(implicit tx: S#Tx): Option[(Long, A)]

  def nearestUntil(timeStamp: Long, term: Long)(implicit tx: S#Tx): Option[(Long, A)]

  def debugPrint(implicit tx: S#Tx): String
}