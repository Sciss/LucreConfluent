package de.sciss
package lucre
package confluent

import serial.DataOutput

trait ProjectionTest {
//   trait KVar[ -Tx, ~ ] extends Var[ Tx, ~ ]

   object KTx {
      implicit def downCast( implicit tx: BiTx ) : KTx[ KTemp ] = sys.error( "TODO" )
   }
   trait KTx[ S <: KTempLike[ S ]] extends stm.Txn[ S ]

   trait KTempLike[ S <: KTempLike[ S ]] extends stm.Sys[ S ] {
      type Tx <: KTx[ S ]
//      type Var[ @specialized ~ ] = stm.Var[ S#Tx, ~ ]
   }

   trait KTemp extends KTempLike[ KTemp ] {
      final type Tx = KTx[ KTemp ]
      final type Var[ @specialized ~ ] = stm.Var[ KTemp#Tx, ~ ]
   }

//   trait BiVar[ ~ ] extends stm.Var[ BiTemp#Tx, ~ ]

//   object BiTx {
//      implicit def downCast( implicit tx: BiTx ) : KTx[ KTemp ] = sys.error( "TODO" )
//   }
   trait BiTx extends KTx[ BiTemp ]

   trait BiTemp extends KTempLike[ BiTemp ] {
      final type Tx = BiTx
//      override type Var[ @specialized ~ ] = BiVar[ ~ ]
   }

  def test(implicit tx: KTemp#Tx) = ()

  def test2(implicit tx: BiTemp#Tx): Unit = test

  //   def txDownCastWorks[ S <: KTempLike[ S ]]( x: S#Var[ Int ])( implicit tx: BiTemp#Tx ): Unit = {
  //      x.set( 33 )( tx )
  //   }
  //
  //   def txUpCastFails[ S <: KTempLike[ S ]]( x: BiTemp#Var[ Int ])( implicit tx: S#Tx ): Unit = {
  ////      x.set( 33 )
  //   }

  def test3[S <: stm.Sys[S], Time](dynVar: stm.Var[Time, Int])(implicit tx: S#Tx, dynView: S#Tx => Time): Unit =
    dynVar.transform(_ + 33)(tx)

  trait PCursor[+Tx] {
    def time: Double

    def peer: Tx
  }

  class DynamicVar[-Tx, A] extends stm.Var[PCursor[Tx], A] {
    def apply()(implicit tx: PCursor[Tx]): A = getAt(tx.time)(tx.peer)

    def getAt(time: Double)(implicit tx: Tx): A = ???

    def transform(fun: A => A)(implicit tx: PCursor[Tx]): Unit = this() = fun(this())

    def update(v: A)(implicit tx: PCursor[Tx]): Unit = setAt(tx.time, v)(tx.peer)

    def setAt(time: Double, v: A)(implicit tx: Tx): Unit = ???

    def dispose()( implicit tx: PCursor[ Tx ]) = ()

    def write(out: DataOutput) = ()

    def isFresh(implicit tx: PCursor[Tx]): Boolean = ???
  }
}
