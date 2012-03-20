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

trait ConfluentTxnMap[ Txn, Access ] {
   def put[ A ]( id: Int, path: Access, value: A )( implicit tx: Txn, serializer: Serializer[ A ]) : Unit
   def get[ A ]( id: Int, path: Access )( implicit tx: Txn, serializer: Serializer[ A ]) : Option[ A ]

   /**
    * Finds the most recent value for an entity `id` with respect to version `path`.
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
