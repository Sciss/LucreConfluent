/*
 *  IDMapImpl.scala
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
package impl

import de.sciss.lucre.confluent.impl.{PathImpl => Path}
import de.sciss.lucre.stm.IdentifierMap
import de.sciss.serial
import de.sciss.serial.DataOutput

import scala.concurrent.stm.TxnLocal

private[impl] final class InMemoryIDMapImpl[S <: Sys[S], A](val store: InMemoryConfluentMap[S, Int])
  extends IdentifierMap[S#ID, S#Tx, A] with InMemoryCacheMapImpl[S, Int] {

  private val markDirtyFlag = TxnLocal(false)

  def id: S#ID = new ConfluentID(0, Path.empty[S])

  private def markDirty()(implicit tx: S#Tx): Unit =
    if (!markDirtyFlag.swap(true)(tx.peer)) {
      tx.addDirtyLocalCache(this)
    }

  def get(id: S#ID)(implicit tx: S#Tx): Option[A] =
    getCache[A](id.base, id.path)

  def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A =
    get(id).getOrElse(default)

  def put(id: S#ID, value: A)(implicit tx: S#Tx): Unit = {
    putCache[A](id.base, id.path, value)
    markDirty()
  }

  def contains(id: S#ID)(implicit tx: S#Tx): Boolean =
    get(id).isDefined // XXX TODO more efficient implementation

  def remove(id: S#ID)(implicit tx: S#Tx): Unit =
    if (removeCache(id.base, id.path)) markDirty()

  def write(out: DataOutput) = ()

  def dispose()(implicit tx: S#Tx) = ()

  override def toString = s"IdentifierMap<${hashCode().toHexString}>"
}

private[impl] final class DurableIDMapImpl[S <: Sys[S], A](val id: S#ID,
                                                     val store: DurablePersistentMap[S, Long])
                                                    (implicit serializer: serial.Serializer[S#Tx, S#Acc, A])
  extends IdentifierMap[S#ID, S#Tx, A] with DurableCacheMapImpl[S, Long] {

  private val nid           = id.base.toLong << 32
  private val markDirtyFlag = TxnLocal(false)

  private def markDirty()(implicit tx: S#Tx): Unit =
    if (!markDirtyFlag.swap(true)(tx.peer)) tx.addDirtyCache(this)

  def get(id: S#ID)(implicit tx: S#Tx): Option[A] = {
    val key = nid | (id.base.toLong & 0xFFFFFFFFL)
    getCacheTxn[A](key, id.path)
  }

  def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A = {
    get(id).getOrElse(default)
  }

  def put(id: S#ID, value: A)(implicit tx: S#Tx): Unit = {
    val key = nid | (id.base.toLong & 0xFFFFFFFFL)
    putCacheTxn[A](key, id.path, value)
    markDirty()
  }

  def contains(id: S#ID)(implicit tx: S#Tx): Boolean = {
    get(id).isDefined // XXX TODO more efficient implementation
  }

  def remove(id: S#ID)(implicit tx: S#Tx): Unit =
    if (removeCacheOnly(id.base, id.path)) markDirty()

  def write(out: DataOutput): Unit = out.writeInt(id.base)

  def dispose()(implicit tx: S#Tx): Unit = {
    println("WARNING: Durable IDMap.dispose : not yet implemented")
    tx.removeDurableIDMap(this)
  }

  override def toString = s"IdentifierMap<${id.base}>"
}