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

object KSystemImpl {
   private type Holder[ T ]   = TxnStore[ Path, T ]
//   private type StoreFactory  = TxnStoreFactory[ Path ]

   def apply[ A <: Mutable[ KCtx, A ]]( ap: AccessProvider[ KCtx, A ]) : KSystem[ A ] = new Sys[ A ]( ap )

   private class Sys[ A <: Mutable[ KCtx, A ]]( ap: AccessProvider[ KCtx, A ])
   extends KSystem[ A ] with ModelImpl[ ECtx, KSystemLike.Update ] {
      sys =>

      val valFactory = CachedTxnStore.valFactory[ Version ](
         HashedTxnStore.valFactory[ Version, Any ], Cache.Val ) // HashedTxnStore.cache( HashedTxnStore.cacheGroup ))

      val refFactory = CachedTxnStore.refFactory[ Version, A ]( // ({type λ[α] = Mutable[A,α]})#λ
         HashedTxnStore.valFactory[ Version, Any ], Cache.Ref )

      type RefHolder[ T <: Mutable[ A, T ]] = Holder[ T ] // TxnStore[ Path, T ]

      val atomic = TxnExecutor.defaultAtomic

      val aInit :A = atomic { txn =>
         val res = ap.init( sys, Ctx( txn, VersionPath.init.path ))
         assert( Cache.isEmpty( txn ))
         res
      }

//      def newMutable( implicit access: A ) : Path = access.path.takeRight( 1 ) // XXX seminalPath should go somewhere else
      def newMutable( implicit access: A ) : A = {
         // XXX seminalPath should go somewhere else
         val ctx  = access.path
         val p    = ctx.path
//         if( p.size == 1 ) access else {
//            access.substitute( ctx.substitute( p.takeRight( 1 )))
//         }
         if( p.size == 0 ) access else {
            access.substitute( ctx.substitute( EmptyPath ))
         }
      }

      override def toString = "KSystem"

//      val dagRef = {
//         val fat0 = f.empty[ VersionPath ]
//         val vp   = VersionPath.init
//         val fat1 = fat0.put( vp.path, vp )
//         Ref( fat1 )
//      }

      def t[ R ]( fun: ECtx => R ) : R = atomic { txn =>
         val res = fun( EphCtx( txn ))
         assert( Cache.isEmpty( txn ))
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

      def kProjector : KProjector[ A, KSystem.Projection[ A ], KSystem.Cursor[ A ]] = KEProjImpl
      def keProjector : KEProjector[ A ] = KEProjImpl

      private object KEProjImpl extends KEProjector[ A ] // with KProjector[ A, KSystem.Projection, KSystem.Cursor[ A ]]
      with ModelImpl[ ECtx, Projector.Update[ A, KSystem.Cursor[ A ]]] {

         val cursorsRef = STMRef( ISet.empty[ KSystem.Cursor[ A ]])
//         def cursors( implicit c: CtxLike ) : Iterable[ KSystem.Cursor[ A ]] = cursorsRef.get( c.txn )

//         def projectIn( vp: VersionPath ) : KSystem.Projection = new CursorImpl( /* sys, */ vp )

         def cursorIn( path: Path )( implicit c: ECtx ) : KSystem.Cursor[ A ] = {
            val csr = new CursorImpl( /* sys, */ path )
            cursorsRef.transform( _ + csr )( c.txn )
            fireUpdate( Projector.CursorAdded[ A, KSystem.Cursor[ A ]]( csr ))
            csr
         }

         def removeKCursor( cursor: KSystem.Cursor[ A ])( implicit c: ECtx ) {
            cursorsRef.transform( _ - cursor )( c.txn )
            fireUpdate( Projector.CursorRemoved[ A, KSystem.Cursor[ A ]]( cursor ))
         }

         def in( version: Path ) : EProjection[ Path, A ] with KProjection[ A ] = new EProj( version )

//         def in( version: Path )( fun: A => Unit ) : Path = atomic { tx =>
//            error( "NO FUNCTIONA" )
////            fun( new Ctx( /* sys, */ tx, version ))
//         }

//         def range[ T ]( vr: KSystem.Var[ T ], interval: (VersionPath, VersionPath) )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ] =
//            error( "NOT YET IMPLEMENTED" )
      }

      // XXX TODO: should factor out common stuff with CursorImpl
      private class EProj( path: Path ) extends EProjection[ Path, A ] with KProjection[ A ] {
         def meld[ R ]( fun: A => R )( implicit main: A ) : R = {
            val am = main.substitute( main.path.substitute( path ))
            fun( am )
         }

         def t( fun: A => Unit ) : Path = {
            require( Txn.findCurrent.isEmpty, "Cannot nest transactions" )
            atomic { txn =>
               val c = Ctx( txn, path )
               val a = aInit.substitute( c )
               fun( a )
               Cache.flush( txn ).map( suffix => {
                  path :+ suffix
               }).getOrElse( path )
            }
         }
      }

      private class CursorImpl( initialPath: Path )
      extends ECursor[ Path, A ] with KProjection[ A ] with ModelImpl[ ECtx, Cursor.Update ] {
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

         def meld[ R ]( fun: A => R )( implicit main: A ) : R = {
            val cMain   = main.path
            val txn     = cMain.txn
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
               Cache.flush( txn ).map( suffix => {
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
      }

      private object Cache {
         val hashSet = TxnLocal( Set.empty[ Long ])

         trait SubCache[ X ] extends TxnCacheGroup[ Long, X ] {
            val cacheSet = TxnLocal( Set.empty[ TxnCacheLike[ X ]])

            def addDirty( cache: TxnCacheLike[ X ], hash: Long )( implicit txn: InTxn ) {
               hashSet.transform( _ + hash )
               cacheSet.transform( _ + cache )
            }

            def addAllDirty( cache: TxnCacheLike[ X ], hashes: Traversable[ Long ])( implicit txn: InTxn ) {
               hashSet.transform( _ ++ hashes )
               cacheSet.transform( _ + cache )
            }

            def flush( suffix: Version )( implicit txn: InTxn ) : Unit
         }

         object Val extends SubCache[ Path ] {
            def flush( suffix: Version )( implicit txn: InTxn ) {
//               val rid = suffix.rid
               cacheSet.get.foreach( _.flush( _ :+ suffix ))
            }
         }

         object Ref extends SubCache[ (Path, A) ] {
            def flush( suffix: Version )( implicit txn: InTxn ) {
//               val rid = suffix.rid
               cacheSet.get.foreach( _.flush( tup => {
                  val ctx  = tup._2.path
                  val p    = ctx.path
                  val ctxm = ctx.substitute( p :+ suffix )
                  (tup._1 :+ suffix, tup._2.substitute( ctxm ))
               }))
            }
         }

         def flush( implicit txn: InTxn ) : Option[ Version ] = {
            val hashes = hashSet.swap( Set.empty[ Long ])
            if( hashes.isEmpty ) return None
            val suffix = Version.newFrom( hashes )
            Val.flush( suffix )
            Ref.flush( suffix )
println( "FLUSH : " + suffix + " (rid = " + suffix.rid + ")" )
            Some( suffix )
         }

         def isEmpty( implicit txn: InTxn ) : Boolean = hashSet.get.isEmpty
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

//         override def toString = "KCtx"
         override def toString = "KCtx[" + path + "]"

//         private val pathRef = TxnLocal[ VersionPath ]( initPath )
//
//         def path : VersionPath = pathRef.get( txn )

         def eph : ECtx = error( "NO FUNCTIONA" ) // ESystemImpl.join( txn )

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
      }

      // ---- RefFactory ----

      def emptyVal[ T ]( implicit access: A ): Val[ A, T ] = {
         new ValImpl[ T ]( valFactory.emptyVal[ T ]( access.path.txn ), "val" )
      }
      def emptyRef[ T <: Mutable[ A, T ]]( implicit access: A ): Ref[ A, T ] = {
//         val t: T => T = gimmeTrans[ T ]
         new RefImpl[ T ]( refFactory.emptyRef[ T ]( access.path.txn ), "ref" )
      }

//      private def gimmeTrans[ T <: Mutable[ A, T ]] : (T => T) = (t: T) => t.substitute( t.path )
//      private def gimmeTrans[ T <: Mutable[ A, T ]]( t: T ) : T = t.substitute( t.path )

      private trait AbstractRef[ T <: Mutable[ A, T ]]
      extends Ref[ A, T ] {
         protected val ref: RefHolder[ T ]
         protected val typeName : String

         override def toString = "KRefVar[" + typeName + "]"

         def get( implicit access: A ) : T = {
            val ctx  = access.path
            val txn  = ctx.txn
            val p    = ctx.path

            val tup = ref.getWithPrefix( p )( txn ).getOrElse( error( "No assignment for path " + p ))
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
               raw.substitute( access.substitute( ctx.substitute( raw.path.path.path ++ p.drop( pre ))))
            }
         }

         def set( v: T )( implicit access: A ) {
            val ctx  = access.path
            val txn  = ctx.txn
            val p    = ctx.path
            ref.put( p, v )( txn )
//            ref.transform( _.put( p, v ))( txn )
//            fireUpdate( v )( txn )
         }

//         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) : Unit
         def inspect( implicit txn: InTxn ) : Unit = ref.inspect
      }

      private class RefImpl[ T <: Mutable[ A, T ] ]( val ref: RefHolder[ T ], val typeName: String ) extends AbstractRef[ T ] {
         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) {}
      }

      private trait AbstractVal[ T ] // ( ref: Ref[ FatValue[ T ]], typeName: String )
      extends Val[ A, T ] { // KVar[ KSystem.Ctx, T ] /* with ModelImpl[ KCtx, T ] */ {
//      protected def txn( c: C ) = c.repr.txn
         protected val ref: Holder[ T ] // Ref[ FatValue[ T ]]
         protected val typeName : String

         override def toString = "KVar[" + typeName + "]"

         def get( implicit access: A ) : T = {
            val ctx  = access.path
            val txn  = ctx.txn
            val p    = ctx.path

            ref.get( p )( txn ).getOrElse( error( "No assignment for path " + p ))
         }

         def set( v: T )( implicit access: A ) {
            val ctx  = access.path
            val txn  = ctx.txn
            val p    = ctx.path
            ref.put( p, v )( txn )
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

         def inspect( implicit txn: InTxn ) : Unit = ref.inspect
      }

      private class ValImpl[ T ]( val ref: Holder[ T ], val typeName: String ) extends AbstractVal[ T ] {
         protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) {}
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