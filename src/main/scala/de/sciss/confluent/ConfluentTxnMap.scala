/*
 *  ConfluentTxnMap.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.confluent

import de.sciss.lucre.stm.Serializer

/**
 * Interface for persistently storing values. The design now assumes that put is accessed only
 * after the commit of the transaction has begun, as it requires knowledge about the meld state.
 *
 * @tparam Txn       the transaction of the underlying system (typically `KSys#Tx`)
 * @tparam Access    the access type of the system (typically `KSys#Acc`)
 */
trait ConfluentTxnMap[ Txn, Access ] {
   /**
    * Stores a new value for a given write path. This requires to pass in information about the
    * meld state (path expanding state) of the transaction, thus it will most likely be called
    * from the txn commit phase.
    *
    * The serializer given is _non_transactional. This is because this trait bridges confluent
    * and ephemeral world (it may use a durable backend, but the data structures used for
    * storing the confluent graph are themselves ephemeral). If the value `A` requires a
    * transactional serialization, the current approach is to pre-serialize the value into
    * an appropriate format (e.g. a byte array) before calling into `put`. In that case
    * the wrapping structure must be de-serialized after calling `get`.
    *
    * @param id         the identifier for the object
    * @param path       the path through which the object has been accessed (the version at which it is read)
    * @param newTree    whether the path terminates in a newly constructed tree (`true`) or not (`false`).
    *                   Since transactionally serialized values will have been serialized already into an
    *                   intermediate format (e.g. byte array), they cannot extend any included path information,
    *                   so it is up to this map to annotate writes accordingly, so that information is
    *                   available when retrieving the values at a later point.
    * @param value      the value to store
    * @param tx         the transaction within which the access is performed
    * @param serializer the serializer used to store the entity's values
    * @tparam A         the type of values stored with the entity
    */
   def put[ A ]( id: Int, path: Access, newTree: Boolean, value: A )( implicit tx: Txn, serializer: Serializer[ A ]) : Unit

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
    * @param id         the identifier for the object
    * @param path       the path through which the object has been accessed (the version at which it is read)
    * @param tx         the transaction within which the access is performed
    * @param serializer the serializer used to store the entity's values
    * @tparam A         the type of values stored with the entity
    * @return           `None` if no value was found, otherwise a `Some` of that value.
    */
   def get[ A ]( id: Int, path: Access )( implicit tx: Txn, serializer: Serializer[ A ]) : Option[ A ]

   /**
    * Finds the most recent value for an entity `id` with respect to version `path`. If a value is found,
    * it is return along with a suffix suitable for identifier path actualisation.
    *
    * @param id         the identifier for the object
    * @param path       the path through which the object has been accessed (the version at which it is read)
    * @param tx         the transaction within which the access is performed
    * @param serializer the serializer used to store the entity's values
    * @tparam A         the type of values stored with the entity
    * @return           `None` if no value was found, otherwise a `Some` of the tuple consisting of the
    *                   suffix and the value. The suffix is the access path minus the prefix at which the
    *                   value was found. However, the suffix overlaps the prefix in that it begins with the
    *                   tree entering/exiting tuple at which the value was found.
    */
   def getWithSuffix[ A ]( id: Int, path: Access )( implicit tx: Txn, serializer: Serializer[ A ]) : Option[ (Access, A) ]
}
