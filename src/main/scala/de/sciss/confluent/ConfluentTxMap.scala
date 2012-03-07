package de.sciss.confluent

import concurrent.stm.InTxn

trait ConfluentTxMap[ A ] {
   def put( key: Int, path: PathLike, value: A )( implicit tx: InTxn ) : Unit
   def get( key: Int, path: PathLike )( implicit tx: InTxn ) : A
}
