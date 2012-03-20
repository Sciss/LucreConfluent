package de.sciss.confluent

import impl.KSysImpl
import java.io.File
import de.sciss.lucre.stm.impl.BerkeleyDB
import de.sciss.lucre.{DataInput, DataOutput}
import de.sciss.lucre.stm.{MutableSerializer, Mutable}
import annotation.tailrec

object Test1 extends App {
   val dir     = File.createTempFile( "database", "db" )
   dir.delete()
   val store   = BerkeleyDB.factory( dir )
   val s       = KSysImpl( store )
   new Test1( s )
}
class Test1[ S <: KSys[ S ]]( s: S ) {
   object Node {
      implicit object ser extends MutableSerializer[ S, Node ] {
         def readData( in: DataInput, _id: S#ID )( implicit tx: S#Tx ) : Node = new Node {
            val id      = _id
            val name    = in.readString()
            val value   = tx.readIntVar( id, in )
            val next    = tx.readVar[ Option[ Node ]]( id, in )
         }
      }

      def apply( _name: String, init: Int )( implicit tx: S#Tx ) : Node = new Node {
         val id      = tx.newID()
         val name    = _name
         val value   = tx.newIntVar( id, init )
         val next    = tx.newVar[ Option[ Node ]]( id, None )
      }
   }
   trait Node extends Mutable[ S ] {
      def name: String
      def value: S#Var[ Int ]
      def next: S#Var[ Option[ Node ]]
      protected def disposeData()( implicit tx: S#Tx ) {
         value.dispose()
         next.dispose()
      }
      protected def writeData( out: DataOutput ) {
         out.writeString( name )
         value.write( out )
         next.write( out )
      }

      override def toString = "Node" + id
   }

   def toList( next: Option[ Node ])( implicit tx: S#Tx ) : List[ (String, Int) ] = next match {
      case Some( n ) => (n.name, n.value.get) :: toList( n.next.get )
      case _ => Nil
   }

   // v0 : "Allocate nodes w0, w1, with x=2 and x=1, concatenate them"

   val access = s.root[ Option[ Node ]] { implicit tx =>
      val w0      = Node( "w0", 2 )
      val w1      = Node( "w1", 1 )
      w0.next.set( Some( w1 ))
      Some( w0 )
   }

   println( "list after writing v0:" )
   val (v0, res0) = s.atomic { implicit tx =>
      val node = access.get
      (tx.inputAccess, toList( node ))
   }
   println( "@ " + v0 + " -> " + res0 )
   println()

   // v1 : "Invert order of input linked list"

   s.atomic { implicit tx =>
      // urrgh, this got pretty ugly. but well, it does its job...
      access.transform { no =>
         def reverse( node: Node ) : Node = node.next.get match {
            case Some( pred ) =>
               val res = reverse( pred )
               pred.next.set( Some( node ))
               res

            case _ => node
         }
         val newHead = no.map { n =>
            val res = reverse( n )
            n.next.set( None )
            res
         }
         newHead
      }
   }

   println( "list after writing v1:" )
   val (v1, res1) = s.atomic { implicit tx =>
      val node = access.get
      tx.inputAccess -> toList( node )
   }
   println( "@ " + v1 + " -> " + res1 )
   println()

   // XXX time warp
   val s1 = s.asInstanceOf[ KSysImpl.System ]
   s1.atomic( s1.setLastPath( KSysImpl.Path.root )( _ ))

   // v2: "Delete first node of list, allocate new node x=1, concatenate to input list"
   // --> use a variant to better verify the results: set x=3 instead

   s.atomic { implicit tx =>
      access.transform {
         case Some( n ) =>
            val res = n.next.get
            @tailrec def step( last: Node ) {
               last.next.get match {
                  case None =>
                     last.next.set( Some( Node( "w2", 3 )))
                  case Some( n1 ) => step( n1 )
               }
            }
            step( n )
            res

         case none => none
      }
   }

   println( "list after writing v2:" )
   val (v2, res2) = s.atomic { implicit tx =>
      val node = access.get
      tx.inputAccess -> toList( node )
   }
   println( "@ " + v2 + " -> " + res2 )
   println()

   // XXX time warp
   s1.atomic( s1.setLastPath( v1.asInstanceOf[ KSysImpl.Path ])( _ ))

   // v3: "Add +2 to all elements of right list. Concatenate left and right lists"
   s.atomic { implicit tx =>
      val right = access.meld( v2 )
      @tailrec def concat( pred: Node, tail: Option[ Node ]) {
         pred.next.get match {
            case None => pred.next.set( tail )
            case Some( succ ) => concat( succ, tail )
         }
      }
      @tailrec def inc( pred: Option[ Node ], amount: Int ) {
         pred match {
            case None =>
            case Some( n ) =>
               n.value.transform( _ + amount )
               inc( n.next.get, amount )
         }
      }
      inc( right, 2 )
      access.get.foreach( concat( _, right ))
   }

   println( "list after writing v3:" )
   val (v3, res3) = s.atomic { implicit tx =>
      val node = access.get
      tx.inputAccess -> toList( node )
   }
   println( "@ " + v3 + " -> " + res3 )

   println( "Done." )
}
