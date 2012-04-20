package de.sciss.confluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import de.sciss.lucre.{LucreSTM, DataInput, DataOutput, event => evt}
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm.{TxnSerializer, Sys, Cursor, Serializer}

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

      declare[ Collection ]( _.collectionChanged )
      declare[ Element ](    _.elementChanged    )

      sealed trait Update { def group: Group }
      sealed trait Collection extends Update { def children: IIdxSeq[ Child ]}
      final case class Added(   group: Group, children: IIdxSeq[ Child ]) extends Collection
      final case class Removed( group: Group, children: IIdxSeq[ Child ]) extends Collection
      final case class Element( group: Group, changes: IIdxSeq[ Child.Update ]) extends Update

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

      lazy val collectionChanged : evt.Trigger[ S, Group.Collection, Group ] = event[ Group.Collection ]
      lazy val elementChanged    = collection( (c: Child) => c.renamed ).map( Group.Element( this, _ ))
      lazy val changed           = collectionChanged | elementChanged

      def add( c: Child* )( implicit tx: S#Tx ) {
         val seq = c.toIndexedSeq
         childrenVar.transform( _ ++ seq )
         seq.foreach( elementChanged += _ )
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
      declare[ Renamed ]( _.renamed )

      def apply( _name: Expr[ S, String ])( implicit tx: S#Tx ) : Child = new Child {
         protected val targets   = evt.Targets[ S ]
         protected val name_#    = Strings.newConfluentVar[ S ]( _name )
      }

      sealed trait Update { def child: Child }
      final case class Renamed( child: Child, change: evt.Change[ String ]) extends Update

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

      def renamed = name_#.changed.map( Child.Renamed( this, _ ))

      override def toString() = "Child" + id

      def name( implicit tx: S#Tx ) : Expr[ S, String ] = name_#.get
      def name_=( ex: Expr[ S, String ])( implicit tx: S#Tx ) { name_#.set( ex )}

      protected def disposeData()( implicit tx: S#Tx ) {
         name_#.dispose()
      }

      protected def writeData( out: DataOutput ) {
         name_#.write( out )
      }
   }

   def run()( implicit system: S, cursor: Cursor[ S ]) {
      val imp = new EventMeld.ExprImplicits[ S ]
      import imp._

      implicit object stringVarSerializer extends TxnSerializer[ S#Tx, S#Acc, Expr.Var[ S, String ]] {
         def write( v: Expr.Var[ S, String ], out: DataOutput ) { v.write( out )}
         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Expr.Var[ S, String ] =
            Strings.readVar[ S ]( in, access )
      }

//      implicit def accessSer : TxnSerializer[ S#Tx, S#Acc, (Group, Expr.Var[ S, String])] = {
//         implicit val exprPeer = Strings.serializer[ S ]
//         TxnSerializer.tuple2[ S#Tx, S#Acc, Group, Expr.Var[ S, String ]]
//      }

      val access = system.root { implicit tx =>
         Group.empty -> Strings.newVar[ S ]( "A" )
      }

      def group( implicit tx: S#Tx )   = access.get._1
      def nameVar( implicit tx: S#Tx ) = access.get._2

      cursor.step { implicit tx =>
         group.changed.reactTx { implicit tx =>
            (e: Group.Update) => println( "____OBSERVE____ " + e )
         }
      }


      val v0 = cursor.step { implicit tx =>
         group.add( Child( nameVar ))
         tx.inputAccess
      }

      val v1 = cursor.step { implicit tx =>
         // dummy action to increment cursor
         group.add()
         tx.inputAccess
      }

      cursor.step { implicit tx =>
         group.add( access.meld( v1 )._1.elements.head )
      }

      def traverse() {
         println( "____TRAVERSE____ " + cursor.step { implicit tx =>
            group.elements.map( c => c -> c.name.value )
         })
      }

      traverse()

      cursor.step { implicit tx =>
         group.elements.head.name = "B"
      }

      traverse()

      cursor.step { implicit tx =>
         nameVar.set( "C" )
      }

      traverse()
   }
}