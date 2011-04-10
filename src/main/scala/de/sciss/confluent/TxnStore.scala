/*
 *  TxnStore.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2011 Hanns Holger Rutz. All rights reserved.
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
 *	 You should have received a copy of the GNU General Public
 *	 License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

import de.sciss.fingertree.FingerTree
import concurrent.stm.InTxn

trait TxnStoreLike[ K, @specialized V, Repr ] {
//   type Path = TxnStore.Path[ K ]

   def put( key: K, value: V )( implicit txn: InTxn, rec: TxnDirtyRecorder[ K ]) : Unit
   def get( key: K )( implicit txn: InTxn ) : Option[ V ]
   def getOrElse( key: K, default: => V )( implicit txn: InTxn ) : V = get( key ).getOrElse( default )

   /**
    *    Finds the value which is the nearest
    *    ancestor in the trie. It returns a tuple
    *    composed of this value as an Option
    *    (None if no ancestor assignment found),
    *    along with an offset Int which is the
    *    offset into path for the first key
    *    element _not_ found in the trie.
    *
    *    Like findMaxPrefixOffset, but with support
    *    for multiplicities
    */
   def getWithPrefix( key: K )( implicit txn: InTxn ) : Option[ (V, Int) ]

   def inspect( implicit txn: InTxn ) : Unit
}

trait TxnStore[ K, V ] extends TxnStoreLike[ K, V, TxnStore[ K, V ]]

//object TxnStore {
//   type Path[ K ] = FingerTree.IndexedSummed[ K, Long ]
//}

trait TxnStoreFactory[ K ] {
   def empty[ V ]: TxnStore[ K, V ]
}

trait TxnStoreCommitter[ K ] {
   def commit( txn: InTxn, keyTrns: KeyTransformer[ K ]) : Unit
}

trait KeyTransformer[ K ] {
   def transform( key: K ) : K
}

trait TxnDirtyRecorder[ K ] {
   def addDirty( key: K, com: TxnStoreCommitter[ K ])
}