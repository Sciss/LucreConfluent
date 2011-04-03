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
import concurrent.stm.{TxnExecutor, InTxn, TxnLocal, Ref}

object KSystemImpl {
   private type Holder[ T ] = Ref[ Store[ Version, T ]]

   def apply( implicit f: StoreFactory[ Version ]) : KSystem = new Sys( f )

   private class Sys( f: StoreFactory[ Version ]) extends KSystem with ModelImpl[ ECtx, KSystemLike.Update ] {
      sys =>

      override def toString = "KSystem"

      val dagRef = {
         val fat0 = f.empty[ VersionPath ]
         val vp   = VersionPath.init
         val fat1 = fat0.put( vp.path, vp )
         Ref( fat1 )
      }

      def t[ R ]( fun: ECtx => R ) : R = Factory.esystem.t( fun )

      def v[ T ]( init: T )( implicit m: ClassManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] = {
         val (ref, name) = prep( init )
         new Var( ref, name )
      }

      def modelVar[ T ]( init: T )( implicit m: ClassManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] with Model[ KSystem.Ctx, T ] = {
         val (ref, name) = prep( init )
         new ModelVar( ref, name )
      }

      def userVar[ T ]( init: T )( user: (KSystem.Ctx, T) => Unit )( implicit m: ClassManifest[ T ], c: KSystem.Ctx ) : KVar[ KSystem.Ctx, T ] = {
         val (ref, name) = prep( init )
         new UserVar( ref, name, user )
      }

      private def prep[ T ]( init: T )( implicit m: ClassManifest[ T ], c: KSystem.Ctx ) : (Holder[ T ], String) = {
         val fat0 = f.empty[ T ]
         val vp   = c.writePath
         val fat1 = fat0.put( vp.path, init )
         Ref( fat1 ) -> m.toString
      }

      def newBranch( v: VersionPath )( implicit c: ECtx ) : VersionPath = {
         val txn  = c.txn
         val pw   = v.newBranch( txn )
         dagRef.transform( _.put( pw.path, pw ))( txn )
         fireUpdate( KSystemLike.NewBranch( v, pw ))
         pw
      }

//      def dag( implicit c: CtxLike ) : LexiTrie[ OracleMap[ VersionPath ]] = dagRef.get( c.txn ).trie
      def dag( implicit c: CtxLike ) : Store[ Version, VersionPath ] = dagRef.get( c.txn ) //.trie

      def kProjector    = KEProjImpl
      def keProjector   = KEProjImpl

      object KEProjImpl extends KEProjector[ KSystem.Ctx, KSystem.Var ]
      with ModelImpl[ ECtx, Projector.Update[ KSystem.Ctx, KSystem.Cursor ]] {

         val cursorsRef = Ref( ISet.empty[ KSystem.Cursor ])
         def cursors( implicit c: CtxLike ) : Iterable[ KSystem.Cursor ] = cursorsRef.get( c.txn )

         def projectIn( vp: VersionPath ) : KSystem.Projection = new CursorImpl( sys, vp )

         def cursorIn( vp: VersionPath )( implicit c: ECtx ) : KSystem.Cursor = {
            val csr = new CursorImpl( sys, vp )
            cursorsRef.transform( _ + csr )( c.txn )
            fireUpdate( Projector.CursorAdded[ KSystem.Ctx, KSystem.Cursor ]( csr ))
            csr
         }

         def removeKCursor( cursor: KSystem.Cursor )( implicit c: ECtx ) {
            cursorsRef.transform( _ - cursor )( c.txn )
            fireUpdate( Projector.CursorRemoved[ KSystem.Ctx, KSystem.Cursor ]( cursor ))
         }

         def in[ R ]( version: VersionPath )( fun: KSystem.Ctx => R ) : R = TxnExecutor.defaultAtomic { tx =>
            fun( new Ctx( sys, tx, version ))
         }

         def range[ T ]( vr: KSystem.Var[ T ], interval: (VersionPath, VersionPath) )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ] =
            error( "NOT YET IMPLEMENTED" )
      }
   }

   private class Ctx[ V <: VersionPath ]( system: Sys, val txn: InTxn, initPath: V )
   extends KCtx[ V ] {
      ctx =>

//      type Child = Ctx[ V#Child ]

      override def toString = "KCtx"

      private val pathRef = TxnLocal[ VersionPath ]( initPath )

      def path : VersionPath = pathRef.get( txn )

      def eph : ECtx = ESystemImpl.join( txn )

   //   private[proc] def readPath : VersionPath = pathRef.get( txn )

      private[confluent] def writePath : VersionPath = {
         val p = pathRef.get( txn )
         if( p == initPath ) {
            val pw = system.newBranch( p )( ctx.eph ) // p.newBranch
            pathRef.set( pw )( txn )
//            system.dagRef.transform( _.assign( pw.path, pw ))( txn )
//            system.fireUpdate( KTemporal.NewBranch( p, pw ))( ctx )  // why NewBranch here without type parameter?
            pw
         } else p
      }
   }

   private trait AbstractVar[ T ] // ( ref: Ref[ FatValue[ T ]], typeName: String )
   extends KVar[ KSystem.Ctx, T ] /* with ModelImpl[ KCtx, T ] */ {
//      protected def txn( c: C ) = c.repr.txn
      protected val ref: Holder[ T ] // Ref[ FatValue[ T ]]
      protected val typeName : String

      override def toString = "KVar[" + typeName + "]"

      def get( implicit c: KSystem.Ctx ) : T = {
         val vp   = c.path // readPath
         ref.get( c.txn ).get( vp.path )
            .getOrElse( error( "No assignment for path " + vp ))
      }

      def set( v: T )( implicit c: KSystem.Ctx ) {
         ref.transform( _.put( c.writePath.path, v ))( c.txn )
         fireUpdate( v )
      }

      def transform( f: T => T )( implicit c: KSystem.Ctx ) {
         implicit val txn  = c.txn
         val h    = ref.get
         val v1   = h.get( c.path.path ).get
         val v2   = f( v1 )
         ref.set( h.put( c.writePath.path, v2 ))
         fireUpdate( v2 )
      }

      protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) : Unit

      def kRange( vStart: VersionPath, vStop: VersionPath )( implicit c: CtxLike ) : Traversable[ (VersionPath, T) ] =
         error( "NOT YET IMPLEMENTED" )

//      def transform( f: T => T )( implicit c: C ) {
//         ref.transform( _.assign( c.repr.writePath.path, v ))( txn( c ))
//         fireUpdate( v, c )
//      }
   }

   private class Var[ T ]( val ref: Holder[ T ], val typeName: String ) extends AbstractVar[ T ] {
      protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) {}
   }

   private class ModelVar[ T ]( val ref: Holder[ T ], val typeName: String )
   extends AbstractVar[ T ] with ModelImpl[ KSystem.Ctx, T ]

   private class UserVar[ T ]( val ref: Holder[ T ], val typeName: String, user: (KSystem.Ctx, T) => Unit )
   extends AbstractVar[ T ] {
      protected def fireUpdate( v: T )( implicit c: KSystem.Ctx ) { user( c, v )}
   }

   private class CursorImpl( sys: Sys, initialPath: VersionPath )
   extends ECursor[ KSystem.Ctx ] with KProjection[ KSystem.Ctx ] with ModelImpl[ ECtx, Cursor.Update ] {
      csr =>

      private val vRef = Ref( initialPath )

      private val txnInitiator = TxnLocal[ Boolean ]( false )

      def isApplicable( implicit c: KSystem.Ctx ) = txnInitiator.get( c.txn )
      def path( implicit c: CtxLike ) : VersionPath = vRef.get( c.txn )

      def dispose( implicit c: ECtx ) {
         sys.kProjector.removeKCursor( csr )
      }

      def t[ R ]( fun: KSystem.Ctx => R ) : R = {
         // XXX todo: should add t to KTemporalSystemLike and pass txn to in
         // variant so we don't call atomic twice
         // (although that is ok and the existing transaction is joined)
         // ; like BitemporalSystem.inRef( vRef.getTxn( _ )) { ... } ?
         TxnExecutor.defaultAtomic { t =>
            val oldPath = vRef.get( t )
            txnInitiator.set( true )( t )
            sys.kProjector.in( oldPath ) { implicit c =>
               val res     = fun( c )
               val newPath = c.path
               if( newPath != oldPath ) {
                  vRef.set( newPath )( c.txn )
                  fireUpdate( Cursor.Moved )( c.eph )
               }
               txnInitiator.set( false )( t )
               res
            }
         }
      }
   }
}