package de.sciss.confluent

import com.sleepycat.je.{Database, DatabaseConfig}
import concurrent.stm.{InTxn, Ref => STMRef}
import collection.immutable.{Set => ISet}

object BerkeleyDBGraph extends BerkeleyDB.Provider {
   def open[ C <: Ct[ C ]]( ctx: BerkeleyDB.Context, name: String, dbCfg: DatabaseConfig = BerkeleyDB.newDBCfg )
                          ( implicit access: C ) : BerkeleyDBGraph[ C ] = provide( ctx, name, dbCfg )( new HandleImpl[ C ]( _, _ ))

   private class HandleImpl[ C <: Ct[ C ]]( val ctx: BerkeleyDB.Context, db: Database )
   extends BerkeleyDBGraph[ C ] {
      private val idRnd       = {
         if( RandomizedVersion.FREEZE_SEED ) new util.Random( -1 ) else new util.Random()
      }
      private val idRef = STMRef( IDGen( 1 /* 0 */, ISet( 1 ), ISet( 1 )))

      def name          = db.getDatabaseName
      def dbCfg         = db.getConfig

      def close( closeEnv: Boolean ) {
         try {
            db.close()
         } finally {
            if( closeEnv ) ctx.env.close()
         }
      }

      def newVersion( preSums: Set[ Long ])( implicit txn: InTxn ) : (RandomizedVersion, Set[ Long ]) = {
         val (id, rid, newSums) = nextID( preSums )
         (new VersionImpl( id, rid ), newSums)
      }

      private def nextID( preSums: Set[ Long ])( implicit txn: InTxn ) : (Int, Int, Set[ Long ]) = {
         val IDGen( cnt, idsTaken, sumsTaken ) = idRef.get( txn )
         val view = preSums // .view
         while( true ) {
            val rid = idRnd.nextInt() & 0x7FFFFFFF
            if( !idsTaken.contains( rid )) {   // unique vertices
               val sums = preSums.map( _ + rid )
               if( sums.forall( !sumsTaken.contains( _ ))) {
                  idRef.set( IDGen( cnt + 1, idsTaken + rid, sumsTaken ++ sums ))( txn )
                  return (cnt, rid, sums)
               }
            }
         }
         error( "Never here" )
      }
   }

   private case class IDGen( cnt: Int, idsTaken: ISet[ Int ], sumsTaken: ISet[ Long ])

   private case class VersionImpl( id: Int, rid: Int ) extends RandomizedVersion
}
trait BerkeleyDBGraph[ C <: Ct[ C ]] extends BerkeleyDB.Handle {
   def newVersion( preSums: Set[ Long ])( implicit txn: InTxn ) : (RandomizedVersion, Set[ Long ])
}