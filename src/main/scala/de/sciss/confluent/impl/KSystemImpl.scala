/*
 *  KSystemImpl.scala
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
package impl

import collection.immutable.{Set => ISet}
import concurrent.stm.{Txn, TxnExecutor, InTxn, TxnLocal, Ref => STMRef}
import com.sleepycat.je.Environment
import HashedTxnDBStore.{Value => DBValue, ValueNone => DBValueNone, ValuePre => DBValuePre, ValueFull => DBValueFull }
import com.sleepycat.bind.tuple.{TupleOutput, TupleInput}
import com.sleepycat.je.EnvironmentConfig
import java.io.{ObjectInputStream, ObjectOutputStream, File}

object KSystemImpl {
   var CHECK_READS            = false
   var LOG_FLUSH              = false
   var DB_CONSOLE_LOG_LEVEL   = "OFF" // "OFF", "ALL"

   private type Holder[ T ]   = TxnStore[ KCtx, Path, T ]
//   private type StoreFactory  = TxnStoreFactory[ Path ]

   def apply[ A <: Node[ KCtx, A ]]( ap: AccessProvider[ KCtx, A ]) : KSystem[ A ] = new Sys[ A ]( ap )

   private class Sys[ A <: Node[ KCtx, A ]]( ap: AccessProvider[ KCtx, A ])
   extends KSystem[ A ] with ModelImpl[ ECtx, KSystemLike.Update ] {
      sys =>

      type RefHolder[ T <: Node[ KCtx, T ]] = Holder[ T ] // TxnStore[ Path, T ]

      val nodeIDRef = STMRef( 0 )
      val atomic = TxnExecutor.defaultAtomic

      val cacheValFactory  = CachedTxnStoreTest.valFactory[ KCtx, Version ]( Cache.Val )
//      val hashValFactory   = HashedTxnStore.valFactory[ Version, Any ]
      val hashValFactory   = HashedTxnDBStore.valFactory[ KCtx, Version, Any ]
      val dbValFactory     = atomic { txn =>
         implicit val ctx = Ctx( txn, VersionPath.init.path )
         val (env, dbCfg ) = {
            val envCfg  = BerkeleyDBStore.newEnvCfg
            val dbCfg   = BerkeleyDBStore.newDBCfg
            envCfg.setAllowCreate( true )
            dbCfg.setAllowCreate( true )  // why do we need to specify this twice??
            val dir     = new File( new File( System.getProperty( "user.home" ), "Desktop" ), "ksys" )
            dir.mkdirs()

//            envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
            envCfg.setConfigParam( EnvironmentConfig.CONSOLE_LOGGING_LEVEL, DB_CONSOLE_LOG_LEVEL )

            (new Environment( dir, envCfg ), dbCfg)
         }
         BerkeleyDBStore.open[ KCtx ]( env, "ksys", dbCfg )
      }

      val cacheRefFactory  = CachedTxnStoreTest.refFactory[ KCtx, Version, KCtx ]( Cache.Ref )
//      val hashRefFactory   = HashedTxnStore.refFactory[ Version, Any ]

      val aInit : A = atomic { txn =>
         val ctx  = Ctx( txn, VersionPath.init.path )
         val res  = ap.init( ctx )
         assert( Cache.isEmpty( ctx ))
         res
      }

      def dispose : Unit = dbValFactory.close( true )

//      def newMutable( implicit access: A ) : Path = access.path.takeRight( 1 ) // XXX seminalPath should go somewhere else
//      def newMutable( implicit ctx: KCtx ) : KCtx = {
//         // XXX seminalPath should go somewhere else
//         val p    = ctx.path
////         if( p.size == 1 ) access else {
////            access.substitute( ctx.substitute( p.takeRight( 1 )))
////         }
//         if( p.size == 0 ) ctx else ctx.substitute( EmptyPath )
//      }

      override def toString = "KSystem"

//      val dagRef = {
//         val fat0 = f.empty[ VersionPath ]
//         val vp   = VersionPath.init
//         val fat1 = fat0.put( vp.path, vp )
//         Ref( fat1 )
//      }

      def t[ R ]( fun: ECtx => R ) : R = atomic { txn =>
         val ctx  = EphCtx( txn )
         val res  = fun( ctx )
//         assert( Cache.isEmpty( txn ))
         res
      }

//      def v[ T ]( init: T )( implicit m: OptManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] = {
//         val (ref, name) = prep( init )
//         new Var( ref, name )
//      }

//      def refVar[ C1 <: KSystem.Ctx, T[ _ <: KSystem.Ctx ] <: Access[ KSystem.Ctx, Path, T ]]( init: T[ C1 ])( implicit m: OptManifest[ T[ _ ]], c: KSystem.Ctx ) : KSystem.RefVar[ T ] = {
//         val (ref, name) = prepRef[ C1, T ]( init )
//         val res = new RefVar[ T ]( ref, name )
//         res
//      }

//      def modelVar[ T ]( init: T )( implicit m: OptManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] with Model[ KSystem.Ctx, T ] = {
//         val (ref, name) = prep( init )
//         new ModelVar( ref, name )
//      }
//
//      def userVar[ T ]( init: T )( user: (KSystem.Ctx, T) => Unit )( implicit m: OptManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] = {
//         val (ref, name) = prep( init )
//         new UserVar( ref, name, user )
//      }

//      private def prepRef[ C1 <: KSystem.Ctx, T[ _ <: KSystem.Ctx ] <: Access[ KSystem.Ctx, Path, T ]]( init: T[ C1 ])( implicit m: OptManifest[ T[ _ ]], c: KSystem.Ctx ) : (RefHolder[ T ], String) = {
//         val fat0 = f.empty[ T[ _ ]]
//         val vp   = c.writePath
////         val p    = vp.path
//         val p    = vp.seminalPath
//         val fat1 = fat0.put( p, init )
//         Ref( fat1 ) -> m.toString
//      }

//      private def prep[ T ]( init: T )( implicit m: OptManifest[ T ], c: KSystem.Ctx ) : (Holder[ T ], String) = {
//         val fat0 = f.empty[ T ]
//         val vp   = c.writePath
////         val p    = vp.path
//         val p    = vp.seminalPath
//         val fat1 = fat0.put( p, init )
//         Ref( fat1 ) -> m.toString
//      }

      def newBranch( v: VersionPath )( implicit c: ECtx ) : VersionPath = {
         val txn  = c.txn
         val pw   = v.newBranch( txn )
//         dagRef.transform( _.put( pw.path, pw ))( txn )
         fireUpdate( KSystemLike.NewBranch( v, pw ))
         pw
      }

//      def dag( implicit c: CtxLike ) : LexiTrie[ OracleMap[ VersionPath ]] = dagRef.get( c.txn ).trie
//      def dag( implicit c: CtxLike ) : Store[ Version, VersionPath ] = dagRef.get( c.txn ) //.trie

      def kProjector : KProjector[ KCtx, KSystem.Projection[ A ], KSystem.Cursor[ A ]] = KEProjImpl
      def keProjector : KEProjector[ A, KCtx ] = KEProjImpl

      private def nodeAlloc( implicit txn: InTxn ) : Int = {
         nodeIDRef += 1
         nodeIDRef.get
      }

      private case class NID( value: Int ) extends NodeID
//      private case class FID( value: Short ) extends FieldID

      private object KEProjImpl extends KEProjector[ A, KCtx ] // with KProjector[ A, KSystem.Projection, KSystem.Cursor[ A ]]
      with ModelImpl[ ECtx, Projector.Update[ KCtx, KSystem.Cursor[ A ]]] {

         val cursorsRef = STMRef( ISet.empty[ KSystem.Cursor[ A ]])
//         def cursors( implicit c: CtxLike ) : Iterable[ KSystem.Cursor[ A ]] = cursorsRef.get( c.txn )

//         def projectIn( vp: VersionPath ) : KSystem.Projection = new CursorImpl( /* sys, */ vp )

         def cursorIn( path: Path )( implicit c: ECtx ) : KSystem.Cursor[ A ] = {
            val csr = new CursorImpl( /* sys, */ path )
            cursorsRef.transform( _ + csr )( c.txn )
            fireUpdate( Projector.CursorAdded[ KCtx, KSystem.Cursor[ A ]]( csr ))
            csr
         }

         def removeKCursor( cursor: KSystem.Cursor[ A ])( implicit c: ECtx ) {
            cursorsRef.transform( _ - cursor )( c.txn )
            fireUpdate( Projector.CursorRemoved[ KCtx, KSystem.Cursor[ A ]]( cursor ))
         }

         def in( version: Path ) : EProjection[ Path, A, KCtx ] with KProjection[ KCtx ] = new EProj( version )

//         def in( version: Path )( fun: A => Unit ) : Path = atomic { tx =>
//            error( "NO FUNCTIONA" )
////            fun( new Ctx( /* sys, */ tx, version ))
//         }

//         def range[ T ]( vr: KSystem.Var[ T ], interval: (VersionPath, VersionPath) )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ] =
//            error( "NOT YET IMPLEMENTED" )
      }

      // XXX TODO: should factor out common stuff with CursorImpl
      private class EProj( path: Path ) extends EProjection[ Path, A, KCtx ] with KProjection[ KCtx ] {
         def meld[ R ]( fun: A => R )( implicit main: KCtx ) : R = {
            val am = aInit.substitute( main.substitute( path ))
            fun( am )
         }

         def t( fun: A => Unit ) : Path = {
            require( Txn.findCurrent.isEmpty, "Cannot nest transactions" )
            atomic { txn =>
               val c = Ctx( txn, path )
               val a = aInit.substitute( c )
               fun( a )
               Cache.flush( c ).map( suffix => {
                  path :+ suffix
               }).getOrElse( path )
            }
         }
      }

      private class CursorImpl( initialPath: Path )
      extends ECursor[ Path, A, KCtx ] with KProjection[ KCtx ] with ModelImpl[ ECtx, Cursor.Update ] {
         csr =>

         private val vRef = STMRef( initialPath )

//         private val txnInitiator = TxnLocal[ Boolean ]( false )
//
//         def isApplicable( implicit c: KSystem.Ctx ) = txnInitiator.get( c.txn )
//         def path( implicit c: CtxLike ) : Path = vRef.get( c.txn )

         def dispose( implicit c: ECtx ) {
//            sys.kProjector.removeKCursor( csr )
//            KEProjImpl.removeKCursor( csr )
         }

         def meld[ R ]( fun: A => R )( implicit main: KCtx ) : R = {
            val txn     = main.txn
            val cMeld   = Ctx( txn, vRef.get( txn ))
            val a       = aInit.substitute( cMeld )
            fun( a )
         }

         def t( fun: A => Unit ) : Path = {
            require( Txn.findCurrent.isEmpty, "Cannot nest transactions" )

            // XXX todo: should add t to KTemporalSystemLike and pass txn to in
            // variant so we don't call atomic twice
            // (although that is ok and the existing transaction is joined)
            // ; like BitemporalSystem.inRef( vRef.getTxn( _ )) { ... } ?
            atomic { txn =>
//               error( "NO FUNCTIONA" )
               val oldPath = vRef.get( txn )
               val c = Ctx( txn, oldPath )
               val a = aInit.substitute( c )
               fun( a )
//               val oldPath = vRef.get( t )
//               txnInitiator.set( true )( t )
//               KEProjImpl.in( oldPath ) { implicit c =>
//                  val res     = fun( c )
//                  val newPath = c.path
//                  if( newPath != oldPath ) {
//                     vRef.set( newPath )( c.txn )
//                     fireUpdate( Cursor.Moved )( c.eph )
//                  }
//                  txnInitiator.set( false )( t )
//                  res
//               }
               // ...
               Cache.flush( c ).map( suffix => {
                  val newPath = oldPath :+ suffix
                  vRef.set( newPath )( txn )
                  newPath
               }).getOrElse( oldPath )
            }
         }
      }

      private case class EphCtx( txn: InTxn )
      extends ECtx {
         def substitute( newPath: Unit ) : ECtx = this
         def eph: ECtx = this
         def newNode[ T ]( fun: NodeFactory[ ECtx ] => T ) : T             = fun( new ENodeFactory( this ))
         def oldNode[ T ]( id: Int )( fun: NodeFactory[ ECtx ] => T ) : T  = fun( new ENodeFactory( this ))

         def readObject( in: TupleInput ) : ECtx = this
         def writeObject( out: TupleOutput ) {}
      }

      private class ENodeFactory( ctx: ECtx ) extends NodeFactory[ ECtx ] {
         def path = ctx

         val id = NID( nodeAlloc( ctx.txn ))

         def emptyVal[ T ]( implicit s: Serializer[ ECtx, T ]) : Val[ ECtx, T ] = {
            error( "NO FUNCTIONA" ) // new ValImpl[ T ]( valFactory.emptyVal[ T ]( txn ), "val" )
         }
         def emptyRef[ T <: Node[ ECtx, T ]]( implicit s: Serializer[ ECtx, T ]) : Ref[ ECtx, T ] = {
//         val t: T => T = gimmeTrans[ T ]
            error( "NO FUNCTIONA" ) // new RefImpl[ T ]( refFactory.emptyRef[ T ]( txn ), "ref" )
         }
      }

      private object Cache {
         val hashSet = TxnLocal( Set.empty[ Long ])
val pathSet = TxnLocal( Set.empty[ PathLike[ Version ]])

//         trait SubCache[ X ] extends TxnCacheGroup[ KCtx, Long, X ] { ... }
         trait SubCache[ X ] extends TxnCacheGroup[ KCtx, PathLike[ Version ], X ] {
            val cacheSet = TxnLocal( Set.empty[ TxnCacheLike[ KCtx, X ]])

//            def addDirty( cache: TxnCacheLike[ KCtx, X ], hash: Long )( implicit access: KCtx ) { ... }
            def addDirty( cache: TxnCacheLike[ KCtx, X ], hash0: PathLike[ Version ])( implicit access: KCtx ) {
               implicit val txn = access.txn
val hash = hash0.sum
pathSet.transform( _ + hash0 )
               hashSet.transform( _ + hash )
               cacheSet.transform( _ + cache )
            }

//            def addAllDirty( cache: TxnCacheLike[ KCtx, X ], hashes: Traversable[ Long ])( implicit access: KCtx ) { ... }
            def addAllDirty( cache: TxnCacheLike[ KCtx, X ], hashes0: Traversable[ PathLike[ Version ]])( implicit access: KCtx ) {
               implicit val txn = access.txn
val hashes = hashes0.map( _.sum )
pathSet.transform( _ ++ hashes0 )
               hashSet.transform( _ ++ hashes )
               cacheSet.transform( _ + cache )
            }

            def flush( suffix: Version )( implicit access: KCtx ) : Unit
         }

         object Val extends SubCache[ Path ] {
            def flush( suffix: Version )( implicit access: KCtx ) {
error( "TODO" )
//               val rid = suffix.rid
               cacheSet.get( access.txn ).foreach( _.flush( _ :+ suffix ))
            }
         }

         object Ref extends SubCache[ (Path, KCtx) ] {
            def flush( suffix: Version )( implicit access: KCtx ) {
//               val rid = suffix.rid
               cacheSet.get( access.txn ).foreach( _.flush( tup => {
                  val pset = tup._1
                  val ctx  = tup._2 // .path
                  val ctxm = ctx.substitute( ctx.path :+ suffix )
//                  (tup._1 :+ suffix, tup._2.substitute( ctxm ))
                  (pset :+ suffix, ctxm) // tup._2.substitute( ctxm ))
               }))
            }
         }

         def flush( implicit access: KCtx ) : Option[ Version ] = {
error( "TODO" )
            implicit val txn = access.txn
            val hashes = hashSet.swap( Set.empty[ Long ])
            if( hashes.isEmpty ) return None
            val suffix = Version.newFrom( hashes )
            Val.flush( suffix )
            Ref.flush( suffix )
if( LOG_FLUSH ) println( "FLUSH : " + suffix + " (rid = " + suffix.rid + ")" )
            Some( suffix )
         }

         def isEmpty( implicit access: KCtx ) : Boolean = hashSet.get( access.txn ).isEmpty
      }

//      private object Recorder extends HashedTxnStore.Recorder {
//         private val comSet   = TxnLocal( Set.empty[ HashedTxnStore.Committer ] /*, beforeCommit = persistAll( _ ) */ )
//         private val hashSet  = TxnLocal( Set.empty[ Long ])
//
//         def addDirty( hash: Long, com: HashedTxnStore.Committer )( implicit txn: InTxn ) {
//            comSet.transform(  _ + com )
//            hashSet.transform( _ + hash )
//         }
//
//         def persistAll( implicit txn: InTxn ) : Option[ Int ] = {
//            val hashs   = hashSet.get
//            if( hashs.isEmpty ) return None
//            val suffix  = Hashing.nextUnique( hashs )
//   //         val v: Version = error( "NO FUNCTIONA" )
//   //         val trns    = new KeyTransformer[ Path ] {
//   //            def transform( key: Path ) : Path = key :+ v
//   //         }
//            comSet.get.foreach( _.commit( txn, suffix ))
//            Some( suffix )
//         }
//
//         def isEmpty( implicit txn: InTxn ) : Boolean = hashSet.get.isEmpty
//      }

      private case class Ctx( txn: InTxn, path: Path )
      extends KCtx {
         ctx =>

         def substitute( newPath: Path ) : KCtx = copy( path = newPath )

//      type Child = Ctx[ V#Child ]

         def seminal: KCtx = {
            // XXX seminalPath should go somewhere else
            if( path.size == 0 ) this else substitute( EmptyPath )
         }

//         override def toString = "KCtx"
         override def toString = "KCtx[" + path + "]"

//         private val pathRef = TxnLocal[ VersionPath ]( initPath )
//
//         def path : VersionPath = pathRef.get( txn )

         def eph : ECtx = error( "NO FUNCTIONA" ) // ESystemImpl.join( txn )

         def newNode[ T ]( fun: NodeFactory[ KCtx ] => T ) : T = fun( new KNodeFactory( nodeAlloc( ctx.txn ), seminal ))
         def oldNode[ T ]( id: Int )( fun: NodeFactory[ KCtx ] => T ) : T = fun( new KNodeFactory( id, ctx ))

      //   private[proc] def readPath : VersionPath = pathRef.get( txn )

//         private[confluent] def writePath : VersionPath = {
//            val p = pathRef.get( txn )
//            if( p == initPath ) {
//               val pw = newBranch( p )( ctx.eph ) // p.newBranch
//               pathRef.set( pw )( txn )
////            system.dagRef.transform( _.assign( pw.path, pw ))( txn )
////            system.fireUpdate( KTemporal.NewBranch( p, pw ))( ctx )  // why NewBranch here without type parameter?
//               pw
//            } else p
//         }

         def readObject( in: TupleInput ) : KCtx = {
            val len = in.readInt()
            substitute( Path( Seq.fill( len )( Version.testWrapXXX( in.readInt(), in.readInt() )): _* ))
         }

         def writeObject( out: TupleOutput ) {
            out.writeInt( path.size )
            path.foreach { v =>
               out.writeInt( v.id )
               out.writeInt( v.rid )
            }
         }
      }

      private class KNodeFactory( nid: Int, ctx: KCtx ) extends NodeFactory[ KCtx ] {
         def path = ctx

//         val nid     = nodeAlloc( ctx.txn )
         def id      = NID( nid )
         val fidRef  = TxnLocal( nid.toLong << 16 )

         def emptyVal[ T ]( implicit s: Serializer[ KCtx, T ]): Val[ KCtx, T ] = {
            implicit val c       = ctx
            implicit val txn     = c.txn
            val fid              = fidRef.get
            fidRef              += 1
            implicit val serial  = new KValSerializer[ T ]( s )
            val db               = dbValFactory.emptyVal[ DBValue[ T ]]( fid )
            val hashed           = hashValFactory.emptyVal[ T ]( db )
            val cached           = cacheValFactory.emptyVal[ T ]( hashed )
            new ValImpl[ T ]( /* fid, */ cached, "val" )
         }

         def emptyRef[ T <: Node[ KCtx, T ]]( implicit s: Serializer[ KCtx, T ]): Ref[ KCtx, T ] = {
            implicit val c    = ctx
            implicit val txn  = c.txn
            val fid = fidRef.get
            fidRef += 1
            implicit val serial = new KValSerializer[ T ]( s )   // XXX hmmm....
            val db      = dbValFactory.emptyVal[ DBValue[ T ]]( fid )
            val hashed  = hashValFactory.emptyVal[ T ]( db )
            val cached  = cacheRefFactory.emptyRef[ T ]( hashed )
            new RefImpl[ T ]( /* fid, */ cached, "ref" )
         }
      }

      private class KValSerializer[ T ]( s: Serializer[ KCtx, T ]) extends DirectSerializer[ KCtx, DBValue[ T ]] {
         def readObject( in: TupleInput )( implicit access: KCtx ) : DBValue[ T ] = {
            in.read() match {
               case 0 =>
                  DBValueNone
               case 1 =>
                  DBValuePre( in.readLong() )
               case 2 =>
//                  val ois = new ObjectInputStream( in )
//                  val v = ois.readObject().asInstanceOf[ T ]
                  val v = s.readObject( in )
                  DBValueFull( v )
            }
         }

         def writeObject( out: TupleOutput, dbv: DBValue[ T ]) /* ( implicit access: KCtx ) */ {
            dbv match {
               case DBValueNone =>
                  out.write( 0 )
               case DBValuePre( hash ) =>
                  out.write( 1 )
                  out.writeLong( hash )
               case DBValueFull( v ) =>
                  out.write( 2 )
//                  val oos = new ObjectOutputStream( out )
//                  oos.writeObject( v ) // XXX hmmm...
                  s.writeObject( out, v )
            }
         }
      }

      // ---- RefFactory ----

//      private trait RefFact extends RefFactory[ KCtx ] {
//         def emptyVal[ T ]( implicit path: KCtx ): Val[ KCtx, T ] = {
//            new ValImpl[ T ]( valFactory.emptyVal[ T ]( path.txn ), "val" )
//         }
//         def emptyRef[ T <: Mutable[ KCtx, T ]]( implicit path: KCtx ): Ref[ KCtx, T ] = {
////         val t: T => T = gimmeTrans[ T ]
//            new RefImpl[ T ]( refFactory.emptyRef[ T ]( path.txn ), "ref" )
//         }
//      }

//      private def gimmeTrans[ T <: Mutable[ A, T ]] : (T => T) = (t: T) => t.substitute( t.path )
//      private def gimmeTrans[ T <: Mutable[ A, T ]]( t: T ) : T = t.substitute( t.path )

private val CHECK_REF = STMRef( Set.empty[ List[ Int ]])

      private trait AbstractRef[ T <: Node[ KCtx, T ]]
      extends Ref[ KCtx, T ] {
         protected val ref: RefHolder[ T ]
         protected val typeName : String

         override def toString = "KRefVar[" + typeName + "]"

         def get( implicit ctx: KCtx ) : T = {
            val txn  = ctx.txn
            val p    = ctx.path

if( CHECK_READS ) {
   val l = p.toList.map( _.id )
   if( !CHECK_REF.get( txn ).contains( l )) {
      println( "Assertion failed for path " + l )
   }

//   val hash = p.sum
//   if( !Version.assertExistsHash( hash )( txn )) {
//      println( "Assertion failed for path " + p.toList + " (hash = " + hash + ")" )
//   }
}

            val tup = ref.getWithPrefix( p ).getOrElse( error( "No assignment for path " + p ))
            // what the f***, tuple unpacking doesn't work, probably scalac bug (has problems with type bounds of T[ _ ])
            val raw = tup._1
            val pre = tup._2


//         val (raw, pre) = ref.get( c.txn ).getWithPrefix( p )
//            .getOrElse( error( "No assignment for path " + vp ))

//         raw.substitute( raw.accessPath ++ p.drop( pre ))
//            aInit.substitute()
//

//            val a2 = if( pre == p.size ) access else {
//               // XXX ouch... path.path.path -- we'll get back to this as a problem
//               // once the serialization is implemented :-(
//               access.substitute( ctx.substitute( raw.path.path.path ++ p.drop( pre )))
//            }
//            raw.substitute( a2 )

            if( pre == p.size ) raw else {
               // XXX ouch... path.path.path -- we'll get back to this as a problem
               // once the serialization is implemented :-(
               raw.substitute( ctx.substitute( raw.path.path ++ p.drop( pre )))
            }
         }

         def set( v: T )( implicit ctx: KCtx ) {
            val p = ctx.path
            ref.put( p, v )
//            ref.transform( _.put( p, v ))( txn )
//            fireUpdate( v )( txn )
         }

//         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) : Unit
         def inspect( implicit ctx: KCtx ) : Unit = ref.inspect
      }

      private class RefImpl[ T <: Node[ KCtx, T ] ]( /* fid: Long, */ val ref: RefHolder[ T ], val typeName: String ) extends AbstractRef[ T ] {
         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) {}
//         def id = FID( fid.toShort )
      }

      private trait AbstractVal[ T ] // ( ref: Ref[ FatValue[ T ]], typeName: String )
      extends Val[ KCtx, T ] { // KVar[ KSystem.Ctx, T ] /* with ModelImpl[ KCtx, T ] */ {
//      protected def txn( c: C ) = c.repr.txn
         protected val ref: Holder[ T ] // Ref[ FatValue[ T ]]
         protected val typeName : String

         override def toString = "KVar[" + typeName + "]"

         def get( implicit ctx: KCtx ) : T = {
            val p    = ctx.path
if( CHECK_READS ) {
   val txn  = ctx.txn
   val l = p.toList.map( _.id )
   if( !CHECK_REF.get( txn ).contains( l )) {
      println( "Assertion failed for path " + l )
   }
//   val hash = p.sum
//   if( !Version.assertExistsHash( hash )( txn )) {
//      println( "Assertion failed for path " + p.toList + " (hash = " + hash + ")" )
//   }
}
            ref.get( p ).getOrElse( error( "No assignment for path " + p ))
         }

         def set( v: T )( implicit ctx: KCtx ) {
            val p    = ctx.path
            ref.put( p, v )
//            fireUpdate( v )
         }

//         def transform( f: T => T )( implicit c: KSystem.Ctx ) {
//            implicit val txn  = c.txn
//            val h    = ref.get
//            val v1   = h.get( c.path.path ).get
//            val v2   = f( v1 )
//            ref.set( h.put( c.writePath.path, v2 ))
//            fireUpdate( v2 )
//         }
//
//         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) : Unit

//         def kRange( vStart: VersionPath, vStop: VersionPath )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ] =
//            error( "NOT YET IMPLEMENTED" )

//      def transform( f: T => T )( implicit c: C ) {
//         ref.transform( _.assign( c.repr.writePath.path, v ))( txn( c ))
//         fireUpdate( v, c )
//      }

         def inspect( implicit ctx: KCtx ) : Unit = ref.inspect
      }

      private class ValImpl[ T ]( /* fid: Long, */ val ref: Holder[ T ], val typeName: String ) extends AbstractVal[ T ] {
         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) {}
//         def id = FID( fid.toShort )
      }
   }
//   private class ModelVar[ T ]( val ref: Holder[ T ], val typeName: String )
//   extends AbstractVar[ T ] with ModelImpl[ KSystem.Ctx, T ]
//
//   private class UserVar[ T ]( val ref: Holder[ T ], val typeName: String, user: (KSystem.Ctx, T) => Unit )
//   extends AbstractVar[ T ] {
//      protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) { user( c, v )}
//   }
}