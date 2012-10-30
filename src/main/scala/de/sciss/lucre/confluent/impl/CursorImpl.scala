package de.sciss.lucre
package confluent
package impl

import concurrent.stm.TxnExecutor

object CursorImpl {
//   implicit def serializer[ S <: Sys[ S ], D <: stm.DurableLike[ D ]]( implicit bridge: S#Tx => D#Tx ) : stm.Serializer[ S#Tx, S#Acc, Cursor[ S ]] =

//   private def pathSerializer[ S <: Sys[ S ]]( system: S ) : stm.Serializer[ S#Tx, S#Acc, S#Acc ] = anyPathSer.asInstanceOf[ PathSer[ S ]]
//
//   private val anyPathSer = new PathSer[ Confluent ]

   private final class PathSer[ S <: Sys[ S ], D1 <: stm.DurableLike[ D1 ]]( implicit system: S { type D = D1 })
   extends stm.Serializer[ D1#Tx, D1#Acc, S#Acc ] {
      def write( v: S#Acc, out: DataOutput) { v.write( out )}
      def read( in: DataInput, access: D1#Acc )( implicit tx: D1#Tx ) : S#Acc = system.readPath( in )
   }

   def apply[ S <: Sys[ S ], D1 <: stm.DurableLike[ D1 ]]( init: S#Acc )
                                                         ( implicit tx: S#Tx, system: S { type D = D1 } /* bridge: S#Tx => D#Tx */ ) : Cursor[ S ] = {
      implicit val dtx     = system.durableTx( tx )
      implicit val pathSer = new PathSer[ S, D1 ]
      val id   = dtx.newID()
      val path = dtx.newVar[ S#Acc ]( id, init )
      new Impl[ S, D1 ]( id, path )
   }

   def read[ S <: Sys[ S ], D1 <: stm.DurableLike[ D1 ]]( in: DataInput, access: S#Acc )
                                                        ( implicit tx: S#Tx, system: S { type D = D1 }) : Cursor[ S ] = {
      implicit val dtx     = system.durableTx( tx )
      implicit val pathSer = new PathSer[ S, D1 ]
      val id   = dtx.readID( in, access )
      val path = dtx.readVar[ S#Acc ]( id, in )
      new Impl[ S, D1 ]( id, path )
   }

   private final class Impl[ S <: Sys[ S ], D1 <: stm.DurableLike[ D1 ]]( id: D1#ID, path: D1#Var[ S#Acc ])
                                                                        ( implicit system: S { type D = D1 })
   extends Cursor[ S ] with Cache[ S#Tx ] {
      override def toString = "Cursor" + id

      def step[ A ]( fun: S#Tx => A ) : A = {
         TxnExecutor.defaultAtomic { itx =>
            implicit val dtx  = system.durable.wrap( itx )
            val inputAccess   = path.get
            val tx            = system.createTxn( dtx, inputAccess, this )
            logCursor( id.toString + " step. input path = " + inputAccess )
            fun( tx )
         }
      }

      def flushCache( term: Long )( implicit tx: S#Tx ) {
         implicit val dtx: D1#Tx = system.durableTx( tx )
         val newPath             = tx.inputAccess.addTerm( term )
         path.set( newPath )
         logCursor( id.toString + " flush path = " + newPath )
      }

      def position( implicit tx: S#Tx ) : S#Acc = {
         implicit val dtx: D1#Tx = system.durableTx( tx )
         path.get
      }

      def position_=( pathVal: S#Acc )( implicit tx: S#Tx ) {
         implicit val dtx: D1#Tx = system.durableTx( tx )
         path.set( pathVal )
      }

      def dispose()( implicit tx: S#Tx ) {
         implicit val dtx: D1#Tx = system.durableTx( tx )
         id.dispose()
         path.dispose()
         logCursor( id.toString + " dispose" )
      }

      def write( out: DataOutput ) {
         id.write( out )
         path.write( out )
      }
   }
}