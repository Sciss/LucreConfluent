package de.sciss.confluent

import concurrent.stm.InTxn

trait ConfluentTxMap[ A ] {
   def put( id: Int, path: PathLike, value: A )( implicit tx: InTxn ) : Unit
   def get( id: Int, path: PathLike )( implicit tx: InTxn ) : A
}
