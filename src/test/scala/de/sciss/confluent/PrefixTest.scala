package de.sciss.confluent

import concurrent.stm.TxnExecutor
import impl.{KSysImpl, ConfluentPersistentMap}

object PrefixTest extends App {
   import TxnExecutor.{defaultAtomic => atomic}
   import KSysImpl.Path

   val rnd     = new util.Random( 0L )
   val map     = ConfluentPersistentMap.ref[ String ]()
   val id      = 0
   val path0   = Path.test_empty
   val path1   = path0 test_:+ rnd.nextInt()
   val path2   = path1 test_:+ rnd.nextInt()

   def halt() {
      println( "Halt" )
   }

   atomic { implicit tx =>
      map.put( id, path1, "hallo" )
   }
   val res = atomic { implicit tx =>
      halt()
      map.get( id, path1 )
   }
   println( res )
}
