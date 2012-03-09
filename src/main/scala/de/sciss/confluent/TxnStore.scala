package de.sciss.confluent

import de.sciss.lucre.DataOutput
import java.io.DataInput

trait TxnStore[ Txn ] {
   def put(      key: Array[ Byte ])( writer: DataOutput => Unit )( implicit tx: Txn ) : Unit
   def get[ A ]( key: Array[ Byte ])( reader: DataInput  => A    )( implicit tx: Txn ) : Option[ A ]
}
