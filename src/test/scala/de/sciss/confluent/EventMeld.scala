package de.sciss.confluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import de.sciss.lucre.{LucreSTM, DataInput, DataOutput, event => evt}
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm.{Sys, Cursor, Serializer}

object EventMeld extends App {
   LucreSTM.showEventLog   = true
   val dir                 = File.createTempFile( "database", "db" )
   dir.delete()
   val store               = BerkeleyDB.factory( dir )
   implicit val s          = Confluent( store )
   val p = new EventMeld[ Confluent ]
   p.run()

   object ExprImplicits {
      implicit def stringConst[  S <: Sys[ S ]]( s: String )  : Expr[ S, String  ] = Strings.newConst(  s )
   }
   class ExprImplicits[ S <: Sys[ S ]] {
      implicit def stringConst( s: String ) : Expr[ S, String ] = Strings.newConst( s )
      implicit def stringOps[ A ]( ex: A )( implicit tx: S#Tx, view: A => Expr[ S, String ]) : Strings.Ops[ S ] =
         new Strings.Ops( ex )
   }
}
class EventMeld[ S <: KSys[ S ]] {
   implicit def seqSer[ A ]( implicit peer: Serializer[ A ]) : Serializer[ IIdxSeq[ A ]] = Serializer.indexedSeq[ A ]

   object Group extends evt.Decl[ S, Group ] {
      implicit val serializer : evt.NodeSerializer[ S, Group ] = Ser

      declare[ Update ]( _.collectionChanged )

      sealed trait Update { def group: Group }
      final case class Added(   group: Group, children: IIdxSeq[ Child ]) extends Update
      final case class Removed( group: Group, children: IIdxSeq[ Child ]) extends Update

      def empty( implicit tx: S#Tx ) : Group = new Group {
         protected val targets = evt.Targets[ S ]
         protected val childrenVar  = tx.newVar[ IIdxSeq[ Child ]]( targets.id, IIdxSeq.empty )
      }

      private object Ser extends evt.NodeSerializer[ S, Group ] {
         def read( in: DataInput, access: S#Acc, _targets: evt.Targets[ S ])( implicit tx: S#Tx ) : Group = new Group {
            protected val targets   = _targets
            protected val childrenVar  = tx.readVar[ IIdxSeq[ Child ]]( id, in )
         }
      }
   }
   trait Group extends evt.Compound[ S, Group, Group.type ] {
      protected def childrenVar: S#Var[ IIdxSeq[ Child ]]
      protected def decl = Group

      lazy val collectionChanged : evt.Trigger[ S, Group.Update, Group ] = event[ Group.Update ]

      def add( c: Child* )( implicit tx: S#Tx ) {
         val seq = c.toIndexedSeq
         childrenVar.transform( _ ++ seq )
         collectionChanged( Group.Added( this, seq ))
      }

      def elements( implicit tx: S#Tx ) : IIdxSeq[ Child ] = childrenVar.get

      protected def disposeData()( implicit tx: S#Tx ) {
         childrenVar.dispose()
      }

      protected def writeData( out: DataOutput ) {
         childrenVar.write( out )
      }
   }

   object Child extends evt.Decl[ S, Child ] {
      def apply( _name: String )( implicit tx: S#Tx ) : Child = new Child {
         protected val targets   = evt.Targets[ S ]
         protected val name_#    = Strings.newConfluentVar[ S ]( Strings.newConst( "name" ))
      }

      implicit val serializer : evt.NodeSerializer[ S, Child ] = new evt.NodeSerializer[ S, Child ] {
//         def write( c: Child, out: DataOutput ) { out.writeString( c.name )}
         def read( in: DataInput, access: S#Acc, _targets: evt.Targets[ S ])( implicit tx: S#Tx ) : Child =
            new Child {
               protected val targets   = _targets
               protected val name_#    = Strings.readVar[ S ]( in, access )
            }
      }
   }
   trait Child extends evt.Compound[ S, Child, Child.type ] {
      protected def decl = Child
      protected def name_# : Expr.Var[ S, String ]

      override def toString() = "Child" + id

      final protected def disposeData()( implicit tx: S#Tx ) {
         name_#.dispose()
      }

      final protected def writeData( out: DataOutput ) {
         name_#.write( out )
      }
   }

   def run()( implicit system: S, cursor: Cursor[ S ]) {
      val imp = new EventMeld.ExprImplicits[ S ]
      import imp._

      val groupAcc = system.root( Group.empty( _ ))
      cursor.step { implicit tx =>
         groupAcc.get.collectionChanged.reactTx { implicit tx =>
            (e: Group.Update) => println( "____OBSERVE____ " + e )
         }
      }

      def group( implicit tx: S#Tx ) = groupAcc.get

      val v0 = cursor.step { implicit tx =>
         group.add( Child( "A" ))
         tx.inputAccess
      }

      val v1 = cursor.step( _.inputAccess )

      cursor.step { implicit tx =>
         group.add( groupAcc.meld( v1 ).elements.head )
      }

      println( "Children = " + cursor.step { implicit tx =>
         group.elements
      })
   }
}