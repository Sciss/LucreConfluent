/*
 *  BerkeleyDBStore.scala
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

import com.sleepycat.util.FastOutputStream
import java.io.ObjectOutputStream
import com.sleepycat.bind.tuple.TupleOutput
import com.sleepycat.je.{Transaction => DBTxn, DatabaseEntry, Database, TransactionConfig, Environment, DatabaseConfig, EnvironmentConfig}
import concurrent.stm.{InTxnEnd, Txn => STMTxn, TxnLocal, Ref => STMRef, InTxn}

class BerkeleyDBStore {
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

   def open( env: Environment, name: String, dbCfg: DatabaseConfig = newDBCfg, txnCfg: TransactionConfig = newTxnCfg )
           ( implicit txn: InTxn ) : Handle = {

      val envCfg = env.getConfig
      require( envCfg.getTransactional && dbCfg.getTransactional && !dbCfg.getSortedDuplicates )

      val txn  = env.beginTransaction( null, txnCfg )
      var ok   = false
      try {
         txn.setName( "Open '" + name + "'" )
         val createDir = dbCfg.getAllowCreate && !dbCfg.getReadOnly
         if( createDir ) env.getHome.mkdirs()
         val db = env.openDatabase( txn, name, dbCfg )
         try {
            new HandleImpl( env, db, txnCfg )
         } finally {
            if( !ok ) db.close()
         }
      } finally {
         if( ok ) txn.commit() else txn.abort()
      }
   }

   private class HandleImpl( env: Environment, db: Database, txnCfg: TransactionConfig ) extends Handle with STMTxn.ExternalDecider {
      handle =>

      val countRef      = STMRef( db.count() ) // XXX
      val dbTxnRef      = TxnLocal( initialValue = initDBTxn( _ ))

      def environment   = env
      def name          = db.getDatabaseName
      def dbCfg         = db.getConfig

      def close( closeEnv: Boolean ) {
         try {
            db.close()
         } finally {
            if( closeEnv ) env.close()
         }
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

      private def initDBTxn( implicit txn: InTxn ) : DBTxnHandle = {
         STMTxn.setExternalDecider( handle )
         val dbTxn = env.beginTransaction( null, txnCfg )
         STMTxn.afterRollback { status =>
            try { dbTxn.abort() } catch { case _ => }
         }
         val to = new TupleOutput
         DBTxnHandle( dbTxn, to, new ObjectOutputStream( to ))
      }

      def emptyVal[ V <: AnyRef ]( implicit txn: InTxn ): TxnStore[ Long, V ] = {
         val id = countRef.get
         countRef.set( id + 1 )
         new StoreImpl( id )
      }

      class StoreImpl[ V ]( id: Long ) extends TxnStore[ Long, V ] {
         def get( key: Long )( implicit txn: InTxn ) : Option[ V ] = {
            error( "TODO" )
         }

         def put( key: Long, value: V )( implicit txn: InTxn ) {
            val h = dbTxnRef.get
            write( h, key, value )
         }

         def putAll( elems: Iterable[ (Long, V) ])( implicit txn: InTxn ) {
            val h = dbTxnRef.get
            elems.foreach { tup => write( h, tup._1, tup._2 )}
         }

         private def write( h: DBTxnHandle, key: Long, value : V ) {
            h.to.reset()
            h.to.writeLong( id )
            h.to.writeLong( key )
            val dbKey   = new DatabaseEntry( h.to.toByteArray )
            h.to.reset()
            h.oos.writeObject( value )
            val dbValue = new DatabaseEntry( h.to.toByteArray )
            db.put( h.txn, dbKey, dbValue )
         }

         def getWithPrefix( key: Long )( implicit txn: InTxn ) : Option[ (V, Int) ] = error( "Unsupported operation" )

         def inspect( implicit txn: InTxn ) {
            println( "DBStore[" + id + "]" )
         }
      }
   }

   private case class DBTxnHandle( txn: DBTxn, to: TupleOutput, oos: ObjectOutputStream )

   trait Handle extends TxnValStoreFactory[ Long, AnyRef ] {
      def name: String
      def close( env: Boolean ) : Unit
      def environment : Environment
      def dbCfg : DatabaseConfig
   }
}