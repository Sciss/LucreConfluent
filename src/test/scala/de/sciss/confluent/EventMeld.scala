package de.sciss.confluent

import de.sciss.lucre.{DataInput, DataOutput, event => evt}
import de.sciss.lucre.stm.Serializer
import collection.immutable.{IndexedSeq => IIdxSeq}

object EventMeld {
}
class EventMeld[ S <: KSys[ S ]] {
   implicit def seqSer[ A ]( implicit peer: Serializer[ A ]) : Serializer[ IIdxSeq[ A ]] = Serializer.indexedSeq[ A ]

   object Group extends evt.Decl[ S, Group ] {
      val serializer : evt.Reader[ S, Group ] = Ser

      declare[ Update ]( _.collectionChanged )

      sealed trait Update { def group: Group }
      final case class Added(   group: Group, children: IIdxSeq[ Child ]) extends Update
      final case class Removed( group: Group, children: IIdxSeq[ Child ]) extends Update

      def empty( implicit tx: S#Tx ) : Group = new Group {
         protected val targets = evt.Targets[ S ]
         protected val children  = tx.newVar[ IIdxSeq[ Child ]]( targets.id, IIdxSeq.empty )
      }

      private object Ser extends evt.Reader[ S, Group ] {
         def read( in: DataInput, access: S#Acc, _targets: evt.Targets[ S ])( implicit tx: S#Tx ) : Group = new Group {
            protected val targets   = _targets
            protected val children  = tx.readVar[ IIdxSeq[ Child ]]( id, in )
         }
      }
   }
   trait Group extends evt.Compound[ S, Group, Group.type ] {
      protected def children: S#Var[ IIdxSeq[ Child ]]
      final protected def decl = Group

      final lazy val collectionChanged : evt.Trigger[ S, Group.Update, Group ] = event[ Group.Update ]

      def add( c: Child* )( implicit tx: S#Tx ) {
         val seq = c.toIndexedSeq
         children.transform( _ ++ seq )
         collectionChanged( Group.Added( this, seq ))
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         children.dispose()
      }

      final protected def writeData( out: DataOutput ) {
         children.write( out )
      }
   }

//   object Child extends evt.Decl[ S, Child ] {
//      implicit val serializer : evt.Reader[ S, Child ] = Ser
//
//      private object Ser extends evt.Reader[ S, Child ] {
//         def read( in: DataInput, access: S#Acc, _targets: evt.Targets[ S ])
//      }
//   }
//   trait Child extends evt.Compound[ S, Child, Child.type ] {
//      final protected def decl = Child
//
//      final protected def disposeData()( implicit tx: S#Tx ) {
//      }
//
//      final protected def writeData( out: DataOutput ) {
//      }
//   }

   object Child {
      def apply( _name: String ) : Child = new Child {
         val name = _name
      }

      implicit val serializer : Serializer[ Child ] = new Serializer[ Child ] {
         def write( c: Child, out: DataOutput ) { out.writeString( c.name )}
         def read( in: DataInput ) : Child = new Child { val name = in.readString() }
      }
   }
   trait Child {
      def name: String
   }
}