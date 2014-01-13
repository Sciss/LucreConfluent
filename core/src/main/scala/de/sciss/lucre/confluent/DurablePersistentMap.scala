/*
 *  DurablePersistentMap.scala
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

package de.sciss
package lucre
package confluent

import stm.DataStore
import impl.{PartialIntMapImpl, ConfluentLongMapImpl, ConfluentIntMapImpl}
import scala.{specialized => spec}
import data.KeySpec
import serial.ImmutableSerializer

object DurablePersistentMap {
  def newConfluentIntMap[S <: Sys[S]](store: DataStore, handler: Sys.IndexMapHandler[S],
                                      isOblivious: Boolean): DurablePersistentMap[S, Int] =
    new ConfluentIntMapImpl[S](store, handler, isOblivious)

  def newConfluentLongMap[S <: Sys[S]](store: DataStore, handler: Sys.IndexMapHandler[S],
                                       isOblivious: Boolean): DurablePersistentMap[S, Long] =
    new ConfluentLongMapImpl[S](store, handler, isOblivious)

  def newPartialMap[S <: Sys[S]](store: DataStore, handler: Sys.PartialMapHandler[S]): DurablePersistentMap[S, Int] =
    new PartialIntMapImpl[S](store, handler)
}
/**
 * Interface for a confluently or partially persistent storing key value map. Keys (type `K`) might
 * be single object identifiers (as the variable storage case), or combined keys
 * (as in the live map case).
 *
 * @tparam S   the underlying system
 * @tparam K   the key type
 */
trait DurablePersistentMap[S <: Sys[S], @spec(KeySpec) K] {
  /**
   * Stores a new value for a given write path.
   *
   * The serializer given is _non_transactional. This is because this trait bridges confluent
   * and ephemeral world (it may use a durable backend, but the data structures used for
   * storing the confluent graph are themselves ephemeral). If the value `A` requires a
   * transactional serialization, the current approach is to pre-serialize the value into
   * an appropriate format (e.g. a byte array) before calling into `put`. In that case
   * the wrapping structure must be de-serialized after calling `get`.
   *
   * @param key        the identifier for the object
   * @param path       the path through which the object has been accessed (the version at which it is read)
   * @param value      the value to store
   * @param tx         the transaction within which the access is performed
   * @param serializer the serializer used to store the entity's values
   * @tparam A         the type of values stored with the entity
   */
  def put[/* @spec(ValueSpec) */ A](key: K, path: S#Acc, value: A)(implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Unit
  // XXX boom! specialized

  /**
   * Finds the most recent value for an entity `id` with respect to version `path`.
   *
   * The serializer given is _non_transactional. This is because this trait bridges confluent
   * and ephemeral world (it may use a durable backend, but the data structures used for
   * storing the confluent graph are themselves ephemeral). If the value `A` requires a
   * transactional serialization, the current approach is to pre-serialize the value into
   * an appropriate format (e.g. a byte array) before calling into `put`. In that case
   * the wrapping structure must be de-serialized after calling `get`.
   *
   * @param key        the identifier for the object
   * @param path       the path through which the object has been accessed (the version at which it is read)
   * @param tx         the transaction within which the access is performed
   * @param serializer the serializer used to store the entity's values
   * @tparam A         the type of values stored with the entity
   * @return           `None` if no value was found, otherwise a `Some` of that value.
   */
  def get[A](key: K, path: S#Acc)(implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Option[A]

  /**
   * Finds the most recent value for an entity `id` with respect to version `path`. If a value is found,
   * it is return along with a suffix suitable for identifier path actualisation.
   *
   * @param key        the identifier for the object
   * @param path       the path through which the object has been accessed (the version at which it is read)
   * @param tx         the transaction within which the access is performed
   * @param serializer the serializer used to store the entity's values
   * @tparam A         the type of values stored with the entity
   * @return           `None` if no value was found, otherwise a `Some` of the tuple consisting of the
   *                   suffix and the value. The suffix is the access path minus the prefix at which the
   *                   value was found. However, the suffix overlaps the prefix in that it begins with the
   *                   tree entering/exiting tuple at which the value was found.
   */
  def getWithSuffix[A](key: K, path: S#Acc)
                                   (implicit tx: S#Tx, serializer: ImmutableSerializer[A]): Option[(S#Acc, A)]

  /**
   * '''Note:''' requires that `path` is non-empty.
   */
  def isFresh(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean

  def remove(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean
}
