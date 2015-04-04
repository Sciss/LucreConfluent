/*
 *  IndexTreeImpl.scala
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

package de.sciss.lucre.confluent.impl

import de.sciss.lucre.confluent.Sys
import de.sciss.lucre.data.Ancestor
import de.sciss.lucre.stm
import de.sciss.serial.DataOutput

// an index tree holds the pre- and post-lists for each version (full) tree
private[impl] final class IndexTreeImpl[D <: stm.DurableLike[D]](val tree: Ancestor.Tree[D, Long], val level: Int)
  extends Sys.IndexTree[D] {

  override def hashCode: Int = term.toInt

  def term: Long = tree.root.version

  override def equals(that: Any): Boolean = {
    that.isInstanceOf[Sys.IndexTree[_]] && term == that.asInstanceOf[Sys.IndexTree[_]].term
  }

  def write(out: DataOutput): Unit = {
    tree.write(out)
    out.writeInt(level)
  }

  def dispose()(implicit tx: D#Tx): Unit = tree.dispose()

  override def toString = s"IndexTree<v=${term.toInt}, l=$level>"
}
