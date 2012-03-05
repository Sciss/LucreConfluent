/*
 *  BerkeleyDBGraph.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2009-2012 Hanns Holger Rutz. All rights reserved.
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
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
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

import concurrent.stm.{InTxn, Ref => STMRef}
import collection.immutable.{IndexedSeq => IIdxSeq, Set => ISet}
import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}
import com.sleepycat.je.{OperationStatus, Database, DatabaseConfig}

object BerkeleyDBGraph extends BerkeleyDB.Provider {
   var DEBUG_PRINT = false

   def open[ C <: Ct[ C ]]( ctx: BerkeleyDB.Context, name: String, dbCfg: DatabaseConfig = BerkeleyDB.newDBCfg )
                          ( implicit access: C ) : BerkeleyDBGraph[ C ] =
      provide( ctx, name, dbCfg ) { (ctx, db) =>
         // read in graph
         val h          = ctx.txnHandle( access.txn )
         var sumsTaken  = ISet.empty[ Long ]
         var cnt        = 0
         var infos      = IIdxSeq.empty[ IDInfo ] // index=ids to id infos
         var keepReading= true
         while( keepReading ) {
            readVersion( cnt, db, h ) match {
               case Some( Record( info, inEdges, seminal )) =>
                  infos     :+= info
                  val rid     = info.rid
                  sumsTaken ++= sumsTaken.map( _ + rid )
                  if( seminal ) sumsTaken += rid.toLong
                  cnt        += 1
               case None =>
                  keepReading = false
            }
         }
         val idsTaken: ISet[ Int ] = infos.map( _.rid )( collection.breakOut )
         // Note: it is important to have 0 in idsTaken
         val idRef    = STMRef( IDGen( cnt, idsTaken + 0, sumsTaken ))
         val infoRef  = STMRef( infos )
         new HandleImpl[ C ]( ctx, db, idRef, infoRef )
      }

   private def readVersion( id: Int, db: Database, h: BerkeleyDB.TxnHandle ) : Option[ Record ] = {
      val out = h.to
      out.reset()  // actually this shouldn't be needed
      out.writeInt( id )
      h.dbKey.setData( out.toByteArray )
      out.reset()
      val dbValue = h.dbValue
      if( db.get( h.txn, h.dbKey, dbValue, null ) == OperationStatus.SUCCESS ) {
         val in   = new TupleInput( dbValue.getData, dbValue.getOffset, dbValue.getSize )
         val rec  = Record.readObject( in )
if( DEBUG_PRINT ) println( "readVersion " + id + " -> " + rec )
         Some( rec )
      } else None
   }

   private class HandleImpl[ C <: Ct[ C ]]( val ctx: BerkeleyDB.Context, db: Database,
                                            idRef: STMRef[ IDGen ], infoRef: STMRef[ IIdxSeq[ IDInfo ]])
   extends BerkeleyDBGraph[ C ] {
      private val idRnd = {
         if( RandomizedVersion.FREEZE_SEED ) new util.Random( 0 ) else new util.Random()
      }

      def name          = db.getDatabaseName
      def dbCfg         = db.getConfig

      def close( closeEnv: Boolean ) {
         try {
            db.close()
         } finally {
            if( closeEnv ) ctx.env.close()
         }
      }

      def newVersion( inEdges: Set[ Int ], preSums: Set[ Long ], seminal: Boolean )( implicit access: C ) : (RandomizedVersion, Set[ Long ]) = {
         // find new randomized ID and update hashes
         implicit val txn = access.txn
         val (id, rid, newSums0) = nextID( preSums )
         // create the version wrapper
         val v = new VersionImpl( id, rid )
         // 0L was not part of preSums which is fine because we already check
         // against idsTaken, so we do not need to duplicate the test
         // in sumsTaken. However, for the seminal paths to correctly
         // propagate, we need to add the new rid here if a seminal
         // node had been constructed.
         val newSums = if( seminal ) newSums0 + rid.toLong else newSums0

         // create and store corresponding info
         val info = IDInfo( rid, access.time, access.comment )
         infoRef.transform( _ :+ info )

         // persist to database
         val rec = Record( info, inEdges, seminal )
         writeVersion( id, rec, ctx.txnHandle )

         // return results
         (v, newSums)
      }

      /**
       * key: (Int) id
       * value: (Record)
       */
      def writeVersion( id: Int, rec: Record, h: BerkeleyDB.TxnHandle ) {
         val out = h.to
         out.reset()  // actually this shouldn't be needed
         out.writeInt( id )
         h.dbKey.setData( out.toByteArray )
         out.reset()
         rec.writeObject( out )
         h.dbValue.setData( out.toByteArray )
         out.reset()
         db.put( h.txn, h.dbKey, h.dbValue )
if( DEBUG_PRINT ) println( "writeVersion " + id + " -> " + rec )
      }

      // XXX TODO: the creation of the sums and the checking against sumsTaken
      // could happen in one loop, so that the process can be stopped as soon
      // as a collision happens
      def nextID( preSums: Set[ Long ])( implicit txn: InTxn ) : (Int, Int, Set[ Long ]) = {
         val IDGen( cnt, idsTaken, sumsTaken ) = idRef.get( txn )
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
         sys.error( "Never here" )
      }

      private def info( id: Int )( implicit txn: InTxn ) : IDInfo = infoRef.get.apply( id )

      def versionTime(    v: Version )( implicit txn: InTxn ) : Long   = info( v.id ).time
      def versionComment( v: Version )( implicit txn: InTxn ) : String = info( v.id ).comment
      def numVersions( implicit txn: InTxn ) : Int = infoRef.get.size
      def versionFromID( id: Int )( implicit txn: InTxn ) : RandomizedVersion = VersionImpl( id, info( id ).rid )
   }

   private case class IDGen( cnt: Int, idsTaken: ISet[ Int ], sumsTaken: ISet[ Long ])
   /**
    * Storage:
    *    (Int) rid
    *    (Short) flags
    *       0x01  seminal
    *    (Int) numInEdges
    *    [ (Int) inEdge-id ] * numInEdges
    *    (Long) time
    *    (String) comment
    */
   private object Record {
      def readObject( in: TupleInput ) : Record = {
         val rid        = in.readInt()
         val flags      = in.readUnsignedShort()
         val seminal    = (flags & 0x01) != 0
         val numInEdges = in.readInt()
         val inEdges    = {
            val b          = ISet.newBuilder[ Int ]
            var i = 0; while( i < numInEdges ) {
               b += in.readInt()
            i += 1 }
            b.result()
         }
         val time       = in.readLong()
         val comment    = in.readString()
         val info       = IDInfo( rid, time, comment )
         Record( info, inEdges, seminal )
      }
   }
   private case class Record( info: IDInfo, inEdges: Set[ Int ], seminal: Boolean ) {
      def writeObject( out: TupleOutput ) {
         out.writeInt( info.rid )
         val flags = if( seminal ) 0x01 else 0
         out.writeUnsignedShort( flags )
         // "For most any immutable set, you'll have a HashSet$HashTrieSet, which has O(1) size lookup"
         out.writeInt( inEdges.size )
         inEdges.foreach( out.writeInt( _ ))
         out.writeLong( info.time )
         out.writeString( info.comment )
      }

      override def toString = {
         "Record(" + info.toString + inEdges.mkString( ", inEdges = {" , ", ", "}, seminal = " ) + seminal + ")"
      }
   }

   // random id, time and comment (the latter taken from the corresponding context)
   private case class IDInfo( rid: Int, time: Long, comment: String ) {
      override def toString =
         "(rid = 0x" + ("00000000" + rid.toHexString.toUpperCase).takeRight( 8 ) +
         ", time = " + new java.util.Date( time ).toString +
         (if( comment.size > 0 ) ", comment = '" + comment + "')" else ")")
   }

   private case class VersionImpl( id: Int, rid: Int ) extends RandomizedVersion {
      override def toString = "v" + id
   }
}
trait BerkeleyDBGraph[ C <: Ct[ C ]] extends BerkeleyDB.Handle {
   def newVersion( inEdges: Set[ Int ], preSums: Set[ Long ], seminal: Boolean )( implicit access: C ) : (RandomizedVersion, Set[ Long ])
   def versionTime(    v: Version )( implicit txn: InTxn ) : Long
   def versionComment( v: Version )( implicit txn: InTxn ) : String
   def numVersions( implicit txn: InTxn ) : Int
   def versionFromID( id: Int )( implicit txn: InTxn ) : RandomizedVersion
}