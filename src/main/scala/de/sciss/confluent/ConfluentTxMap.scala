package de.sciss.confluent

import concurrent.stm.InTxn

trait ConfluentTxMap[ -Txn, A ] {
   def put( id: Int, path: PathLike, value: A )( implicit tx: Txn ) : Unit
   def get( id: Int, path: PathLike )( implicit tx: Txn ) : A
}
