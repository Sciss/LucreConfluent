//package de.sciss.lucre
//package confluent
//
//import de.sciss.lucre.{event => evt}
//import expr.Type
//import event.Targets
//
//object Strings2 extends Type[ String ] {
//   private val typeID = 8
//
//   def readValue( in: DataInput ) : String = in.readString()
//   def writeValue( value: String, out: DataOutput ): Unit = out.writeString( value )
//
//   final class Ops[ S <: evt.Sys[ S ]]( ex: Ex[ S ])( implicit tx: S#Tx ) {
//      private type E = Ex[ S ]
//      import BinaryOp._
//
//      def ++( b: E ) : E = Append.make( ex, b )
//   }
//
//   private object BinaryOp {
//      sealed abstract class Op( val id: Int ) extends Tuple2Op[ String, String ] {
//         final def make[ S <: evt.Sys[ S ]]( a: Ex[ S ], b: Ex[ S ])( implicit tx: S#Tx ) : Ex[ S ] = {
//            new Tuple2( typeID, this, Targets.partial[ S ], a, b )
//         }
//         def value( a: String, b: String ) : String
//
//         def toString[ S <: stm.Sys[ S ]]( _1: Ex[ S ], _2: Ex[ S ]) : String = _1.toString + "." + name + "(" + _2 + ")"
//
//         def name: String = { val cn = getClass.getName
//            val sz   = cn.length
//            val i    = cn.indexOf( '$' ) + 1
//            "" + cn.charAt( i ).toLower + cn.substring( i + 1, if( cn.charAt( sz - 1 ) == '$' ) sz - 1 else sz )
//         }
//      }
//
//      case object Append extends Op( 0 ) {
//         def value( a: String, b: String ) : String = a + b
//      }
//   }
//
//   // ---- protected ----
//
//   def readTuple[ S <: evt.Sys[ S ]]( cookie: Int, in: DataInput, access: S#Acc, targets: Targets[ S ])
//                                ( implicit tx: S#Tx ) : ExN[ S ] = {
//      (cookie /* : @switch */) match {
////         case 1 =>
////            val tpe  = in.readInt()
////            require( tpe == typeID, "Invalid type id (found " + tpe + ", required " + typeID + ")" )
////            val opID = in.readInt()
////            import UnaryOp._
////            val op: Op = (opID: @switch) match {
////               case _  => sys.error( "Invalid operation id " + opID )
////            }
////            val _1 = readExpr( in, access )
////            new Tuple1( typeID, op, targets, _1 )
//
//         case 2 =>
//            val tpe = in.readInt()
//            require( tpe == typeID, "Invalid type id (found " + tpe + ", required " + typeID + ")" )
//            val opID = in.readInt()
//            import BinaryOp._
//            val op: Op = (opID /*: @switch */) match {
//               case 0 => Append
//            }
//            val _1 = readExpr( in, access )
//            val _2 = readExpr( in, access )
//            new Tuple2( typeID, op, targets, _1, _2 )
//
//         case _ => sys.error( "Invalid cookie " + cookie )
//      }
//   }
//}
