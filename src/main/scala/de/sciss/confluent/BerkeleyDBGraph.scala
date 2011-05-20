package de.sciss.confluent

import com.sleepycat.je.{Database, DatabaseConfig}

object BerkeleyDBGraph extends BerkeleyDB.Provider {
   def open[ C <: Ct[ C ]]( ctx: BerkeleyDB.Context, name: String, dbCfg: DatabaseConfig = BerkeleyDB.newDBCfg )
                          ( implicit access: C ) : BerkeleyDBGraph[ C ] = provide( ctx, name, dbCfg )( new HandleImpl[ C ]( _, _ ))

   private class HandleImpl[ C <: Ct[ C ]]( val ctx: BerkeleyDB.Context, db: Database )
   extends BerkeleyDBGraph[ C ] {
      def name          = db.getDatabaseName
      def dbCfg         = db.getConfig

      def close( closeEnv: Boolean ) {
         try {
            db.close()
         } finally {
            if( closeEnv ) ctx.env.close()
         }
      }
   }
}
trait BerkeleyDBGraph[ C <: Ct[ C ]] extends BerkeleyDB.Handle {
//   def newVersion( inEdges: Set[ Int ]) : Version
}