/*
 *  MeldInfo.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	 This software is free software; you can redistribute it and/or
 *	 modify it under the terms of the GNU General Public License
 *	 as published by the Free Software Foundation; either
 *	 version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	 This software is distributed in the hope that it will be useful,
 *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	 General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 */

package de.sciss.lucre
package confluent

object MeldInfo {
  def empty[S <: Sys[S]]: MeldInfo[S] = anyMeldInfo.asInstanceOf[MeldInfo[S]]

  private val anyMeldInfo = MeldInfo[Confluent](-1, Set.empty)
}

final case class MeldInfo[S <: Sys[S]](highestLevel: Int, highestTrees: Set[S#Acc]) {
  def requiresNewTree: Boolean = highestTrees.size > 1

  def outputLevel: Int = if (requiresNewTree) highestLevel + 1 else highestLevel

  /** An input tree is relevant if its level is higher than the currently observed
    * highest level, or if it has the same level but was not recorded in the set
    * of highest trees.
    */
  def isRelevant(level: Int, seminal: S#Acc): Boolean =
    level > highestLevel || level == highestLevel && !highestTrees.contains(seminal)

  def add(level: Int, seminal: S#Acc): MeldInfo[S] =
    if (isRelevant(level, seminal)) MeldInfo[S](level, highestTrees + seminal) else this

  def isEmpty: Boolean = highestLevel < 0
}