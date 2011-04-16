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
import concurrent.stm.{InTxnEnd, Txn => STMTxn, TxnLocal, Ref => STMRef, InTxn}
import java.io.{ObjectInputStream, ObjectOutputStream}
import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}
import com.sleepycat.je.{OperationStatus, Transaction => DBTxn, DatabaseEntry, Database, TransactionConfig, Environment, DatabaseConfig, EnvironmentConfig}

object BerkeleyDBStore {
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

   def open[ C <: Ct[ C ]]( env: Environment, name: String, dbCfg: DatabaseConfig = newDBCfg, txnCfg: TransactionConfig = newTxnCfg )
           ( implicit access: C ) : Handle[ C ] = {

      val envCfg = env.getConfig
      require( envCfg.getTransactional && dbCfg.getTransactional && !dbCfg.getSortedDuplicates )

      val txn  = env.beginTransaction( null, txnCfg )
      var ok   = false
      try {
         txn.setName( "Open '" + name + "'" )
//         val createDir = dbCfg.getAllowCreate && !dbCfg.getReadOnly
//         if( createDir ) env.getHome.mkdirs()
         val db = env.openDatabase( txn, name, dbCfg )
         try {
            val res = new HandleImpl[ C ]( env, db, txnCfg )
            ok = true
            res
         } finally {
            if( !ok ) db.close()
         }
      } finally {
         if( ok ) txn.commit() else txn.abort()
      }
   }

   private class HandleImpl[ C <: Ct[ C ]]( env: Environment, db: Database, txnCfg: TransactionConfig )
   extends Handle[ C ] with STMTxn.ExternalDecider {
      handle =>

//      val countRef      = STMRef( db.count() ) // XXX
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
         DBTxnHandle( dbTxn, to, new DatabaseEntry(), new DatabaseEntry() )
      }

//      def emptyVal[ V <: AnyRef ]( implicit txn: InTxn ): TxnStore[ Long, V ] = {
//         val id = countRef.get
//         countRef.set( id + 1 )
//         new StoreImpl( id )
//      }

      def emptyVal[ V ]( id: Long )( implicit access: C, s: Serializer[ C, V ]): TxnStore[ C, Long, V ] = {
//         val id = countRef.get
//         countRef.set( id + 1 )
         new StoreImpl[ C, V ]( id, s )
      }

      class StoreImpl[ C <: Ct[ C ], V ]( id: Long, s: Serializer[ C, V ]) extends TxnStore[ C, Long, V ] {
         def get( key: Long )( implicit access: C ) : Option[ V ] = {
            val h = dbTxnRef.get( access.txn )
            val out = h.to
            out.reset()  // actually this shouldn't be needed
//            val id: Long = error( "TODO" ) // = s.id // ( value )
            out.writeInt( (id >> 16).toInt )
            out.writeUnsignedShort( id.toInt )
            out.writeLong( key )
            h.dbKey.setData( out.toByteArray )
            out.reset()
            val dbValue = h.dbValue
            if( db.get( h.txn, h.dbKey, dbValue, null ) == OperationStatus.SUCCESS ) {
               val in = new TupleInput( dbValue.getData, dbValue.getOffset, dbValue.getSize )
               Some( s.readObject( in ))
            } else None
         }

         def put( key: Long, value: V )( implicit access: C ) {
            val h = dbTxnRef.get( access.txn )
            write( h, key, value )
         }

         def putAll( elems: Iterable[ (Long, V) ])( implicit access: C ) {
            val h = dbTxnRef.get( access.txn )
            elems.foreach { tup => write( h, tup._1, tup._2 )}
         }

         private def write( h: DBTxnHandle, key: Long, value : V )( implicit access: C ) {
            val out = h.to
            out.reset()  // actually this shouldn't be needed
//            val id: Long = error( "TODO" ) // val id = s.id // ( value )
            out.writeInt( (id >> 16).toInt )
            out.writeUnsignedShort( id.toInt )
            out.writeLong( key )
            h.dbKey.setData( out.toByteArray )
            out.reset()
//            h.oos.writeObject( value )
            s.writeObject( out, value )
            h.dbValue.setData( out.toByteArray )
            out.reset()
            db.put( h.txn, h.dbKey, h.dbValue )
         }

         def getWithPrefix( key: Long )( implicit access: C ) : Option[ (V, Int) ] = error( "Unsupported operation" )

         def inspect( implicit access: C ) {
            println( "DBStore" ) // [" + s.id + "]" )
         }
      }
   }

   private case class DBTxnHandle( txn: DBTxn, to: TupleOutput, dbKey: DatabaseEntry, dbValue: DatabaseEntry /*, oos: ObjectOutputStream */)

   /**
    * A handle to the database which also functions as a store factory.
    *
    * **Note** that the precision of the storage identifier, although given
    * as `Long`, is only 48 bit (the least significant 48 bit of the `Long`).
    */
   trait Handle[ C <: Ct[ C ]] extends TxnDBStoreFactory[ Long, C, Long  ] {
      def name: String
      def close( env: Boolean ) : Unit
      def environment : Environment
      def dbCfg : DatabaseConfig
   }
}