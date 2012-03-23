package de.sciss.confluent

import de.sciss.lucre.stm
import stm.{Sys, Txn}

trait ProjectionTest {
//   trait KVar[ -Tx, ~ ] extends Var[ Tx, ~ ]

   object KTx {
      implicit def downCast( implicit tx: BiTx ) : KTx[ KTemp ] = sys.error( "TODO" )
   }
   trait KTx[ S <: KTempLike[ S ]] extends Txn[ S ]

   trait KTempLike[ S <: KTempLike[ S ]] extends Sys[ S ] {
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

   def test( implicit tx: KTemp#Tx ) {}

   def test2( implicit tx: BiTemp#Tx ) { test }

//   def txDownCastWorks[ S <: KTempLike[ S ]]( x: S#Var[ Int ])( implicit tx: BiTemp#Tx ) {
//      x.set( 33 )( tx )
//   }
//
//   def txUpCastFails[ S <: KTempLike[ S ]]( x: BiTemp#Var[ Int ])( implicit tx: S#Tx ) {
////      x.set( 33 )
//   }
}
