/*
 *  BerkeleyDB.scala
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

import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}
import com.sleepycat.je.{OperationStatus, Transaction => DBTxn, DatabaseEntry, Database, TransactionConfig, Environment, DatabaseConfig, EnvironmentConfig}
import concurrent.stm.{Txn => STMTxn, InTxn, InTxnEnd, TxnLocal}

object BerkeleyDB {
   def newEnvCfg: EnvironmentConfig = {
      val cfg = new EnvironmentConfig()
      cfg.setTransactional( true )
      cfg
   }

   def newDBCfg : DatabaseConfig = {
      val cfg = new DatabaseConfig()
      cfg.setTransactional( true )
      cfg
   }

   def newTxnCfg: TransactionConfig = new TransactionConfig()

   def newCtx( env: Environment, txnCfg: TransactionConfig = newTxnCfg ): Context = new ContextImpl( env, txnCfg )

   final case class TxnHandle( txn: DBTxn, to: TupleOutput, dbKey: DatabaseEntry, dbValue: DatabaseEntry /*, oos: ObjectOutputStream */)

   private class ContextImpl( val env: Environment, val txnCfg: TransactionConfig )
   extends Context with STMTxn.ExternalDecider {
      context =>

      private val dbTxnRef = TxnLocal( initialValue = initDBTxn( _ ))

      def txnHandle( implicit txn: InTxnEnd ) : TxnHandle = dbTxnRef.get

      private def initDBTxn( implicit txn: InTxn ) : TxnHandle = {
         STMTxn.setExternalDecider( context )
         val dbTxn = env.beginTransaction( null, txnCfg )
         STMTxn.afterRollback { status =>
            try { dbTxn.abort() } catch { case _ => }
         }
         val to = new TupleOutput
         TxnHandle( dbTxn, to, new DatabaseEntry(), new DatabaseEntry() )
      }

      def shouldCommit( implicit txn: InTxnEnd ) : Boolean = {
         val h = dbTxnRef.get
         try {
            h.txn.commit()
            true
         } catch { case e =>
            try { h.txn.abort() } catch { case _ => }
            false
         }
      }
   }

   sealed trait Context {
      def env: Environment
      def txnCfg: TransactionConfig
      def txnHandle( implicit txn: InTxnEnd ) : TxnHandle
   }

   trait Handle {
      def name: String
      def close( env: Boolean ) : Unit
//      def env : Environment
      def ctx: Context
      def dbCfg : DatabaseConfig
   }

   trait Provider {
      protected def provide[ Res ]( ctx: Context, name: String, dbCfg: DatabaseConfig = BerkeleyDB.newDBCfg )
                                  ( fun: (Context, Database) => Res ) : Res = {
         val env     = ctx.env
         val envCfg  = env.getConfig
         require( envCfg.getTransactional && dbCfg.getTransactional && !dbCfg.getSortedDuplicates )

         val txn  = env.beginTransaction( null, ctx.txnCfg )
         var ok   = false
         try {
            txn.setName( "Open '" + name + "'" )
//         val createDir = dbCfg.getAllowCreate && !dbCfg.getReadOnly
//         if( createDir ) env.getHome.mkdirs()
            val db = env.openDatabase( txn, name, dbCfg )
            try {
               val res = fun( ctx, db )
               ok = true
               res
            } finally {
               if( !ok ) db.close()
            }
         } finally {
            if( ok ) txn.commit() else txn.abort()
         }
      }
   }
}