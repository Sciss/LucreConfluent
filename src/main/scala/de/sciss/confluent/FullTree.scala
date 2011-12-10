/*
 *  FullTree.scala
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
 */

package de.sciss.confluent

import de.sciss.collection.txn.{SpaceSerializers, SkipOctree, TotalOrder}
import de.sciss.collection.geom.{Point3D, Cube, Space}
import de.sciss.lucrestm.{Serializer, DataOutput, DataInput, Sys}

object FullTree {
   private[confluent] val cube = Cube( 0x40000000, 0x40000000, 0x40000000, 0x40000000 )

//   private object PreKey {
//      implicit def reader[ S <: Sys[ S ], A ]( implicit vertexReader: Reader[ Vertex[ S, A ]]) : Reader[ PreKey[ S, A ]] =
//         new ReaderImpl[ S, A ]( vertexReader )
//
//      private final class ReaderImpl[ S <: Sys[ S ], A ]( vertexReader: Reader[ Vertex[ S, A ]])
//      extends Reader[ PreKey[ S, A ]] {
//         def read( in: DataInput ) : PreKey[ S, A ] = {
//            val id = in.readUnsignedByte()
//            val v  = vertexReader.read( in )
//            if( id == 0 ) v.preHeadKey else v.preTailKey
//         }
//      }
//   }

//   private type PreOrder[  S <: Sys[ S ], A ] = TotalOrder.Map.Entry[ S, PreKey[ S, A ]]
//   private type PostOrder[ S <: Sys[ S ], A ] = TotalOrder.Map.Entry[ S, Vertex[ S, A ]]
   private type PreOrder[  S <: Sys[ S ]] = TotalOrder.Set.Entry[ S ]
   private type PostOrder[ S <: Sys[ S ]] = TotalOrder.Set.Entry[ S ]

//   private sealed trait PreKey[ S <: Sys[ S ], A ] extends VertexProxy[ S, A ] {
//      def order: PreOrder[ S ]
//      def id: Int
//
//      final def write( out: DataOutput ) {
//         out.writeUnsignedByte( id )
//         source.write( out )
//      }
//
//      override def equals( that: Any ) : Boolean = {
//         (that.isInstanceOf[ PreKey[ _, _ ]] && {
//            val thatPre = that.asInstanceOf[ PreKey[ _, _ ]]
//            (id == thatPre.id) && (source == thatPre.source)
//         })
//      }
//   }
//
//   private final class PreHeadKey[ S <: Sys[ S ], A ]( val source: Vertex[ S, A ])
//   extends PreKey[ S, A ] {
//      def order   = source.preHead
//      def id      = 0
//
//      override def toString = source.toString + "<pre>"
//      def debugString( implicit tx: S#Tx ) = toString + "@" + source.preHead.tag
//   }
//
//   private final class PreTailKey[ S <: Sys[ S ], A ]( val source: Vertex[ S, A ])
//   extends PreKey[ S, A ] {
//      def order   = source.preTail
//      def id      = 1
//
//      override def toString = source.toString + "<pre-tail>"
//      def debugString( implicit tx: S#Tx ) = toString + "@" + source.preTail.tag
//   }
//
//   sealed trait VertexProxy[ S <: Sys[ S ], A ] extends Writer {
//      private[FullTree] def source: Vertex[ S, A ]
//   }

   object Vertex {
      private[FullTree] implicit def toPoint[ S <: Sys[ S ], A ]( v: Vertex[ S, A ], tx: S#Tx ) : Point3D =
         new Point3D( v.preHead.tag( tx ), v.post.tag( tx ), v.version )

//      private[FullTree] implicit def vertexSerializer[ S <: Sys[ S ], A ](
//         implicit valueReader: Reader[ A ], versionView: A => Int ) : Serializer[ Vertex[ S, A ]] =
//            new SerializerImpl[ S, A ]( valueReader, versionView )
   }
   sealed trait Vertex[ S <: Sys[ S ], A ] /* extends VertexProxy[ S, A ] */ {
      def value: A
      final def version: Int = tree.versionView( value )

//      private[confluent] final def preCompare(  that: Vertex[ S, A ]) : Int = preHead.compare( that.preHead )
//      private[confluent] final def postCompare( that: Vertex[ S, A ]) : Int = post.compare(    that.post )

//      private[FullTree] final def source: Vertex[ S, A ] = this

//      private[FullTree] final val preHeadKey  = new PreHeadKey( this )
//      private[FullTree] final val preTailKey  = new PreTailKey( this )
      private[confluent] def preHead: PreOrder[ S ]
      private[confluent] def preTail: PreOrder[ S ]
      private[confluent] def post:    PostOrder[ S ]

      private[FullTree] def tree: FullTree[ S, A ]

      final def write( out: DataOutput ) {
         tree.valueSerializer.write( value, out )
         preHead.write( out )
         preTail.write( out )
         post.write( out )
      }
   }

   def empty[ S <: Sys[ S ], A ]( rootValue: A )( implicit tx: S#Tx, system: S, valueSerializer: Serializer[ A ],
                                                  versionView: A => Int, smf: Manifest[ S ],
                                                  versionManifest: Manifest[ A ]) : FullTree[ S, A ] =
      new TreeNew[ S, A ]( rootValue )

   private final class TreeNew[ S <: Sys[ S ], A ]( rootValue: A )(
      implicit tx: S#Tx, system: S, val valueSerializer: Serializer[ A ], val versionView: A => Int,
      smf: Manifest[ S ], val versionManifest: Manifest[ A ])
   extends FullTree[ S, A ] /* with TotalOrder.Map.RelabelObserver[ S#Tx, VertexProxy[ S, A ]] */ {
      me =>

      private implicit object VertexSerializer extends Serializer[ V ] {
         def write( v: V, out: DataOutput ) { v.write( out )}

         def read( in: DataInput ) : V = {
            new V {
               def tree    = me
               val value   = valueSerializer.read( in )
               val preHead = preOrder.readEntry( in )
               val preTail = preOrder.readEntry( in )
               val post    = postOrder.readEntry( in )
            }
         }
      }

      def vertexSerializer : Serializer[ V ] = VertexSerializer

      val skip = {
         import SpaceSerializers.CubeSerializer
         SkipOctree.empty[ S, Space.ThreeDim, V ]( cube )
      }
//      val preOrder      = TotalOrder.Map.empty[ S, PreKey[ S, A ]]( me, _.order, 0 )
//      val postOrder     = TotalOrder.Map.empty[ S, V ](             me, _.post,  Int.MaxValue )
      val preOrder      = TotalOrder.Set.empty[ S ]( 0 )
      val postOrder     = TotalOrder.Set.empty[ S ]( Int.MaxValue )
      val root = new V {
         def tree    = me
         def value   = rootValue
         val preHead = preOrder.root
         val preTail = preHead.append() // preOrder.insert()
         val post    = postOrder.root
//         preOrder.placeAfter( preHeadKey, preTailKey )   // preTailKey must come last
      }
      skip += root

      def insertChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V = {
         val v = new V {
            def tree    = me
            val value   = newChild
            val preHead = parent.preTail.prepend() // preOrder.insert()
            val preTail = preHead.append() // preOrder.insert()
            val post    = parent.post.prepend() // postOrder.insert()
//            preOrder.placeBefore( parent.preTailKey, preHeadKey )
//            postOrder.placeBefore( parent, this )
//            preOrder.placeAfter( preHeadKey, preTailKey )   // preTailKey must come last!
         }
         skip += v
         v
      }

      def insertRetroChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V = {
         val v = new V {
            def tree    = me
            val value   = newChild
            val preHead = parent.preHead.append()  // preOrder.insert()
            val preTail = parent.preTail.prepend() // preOrder.insert()
            val post    = parent.post.prepend()    // postOrder.insert()
//            preOrder.placeAfter( parent.preHeadKey, preHeadKey )
//            postOrder.placeBefore( parent, this )
//            preOrder.placeBefore( parent.preTailKey, preTailKey ) // preTailKey must come last
            override def toString = super.toString + "@r-ch"
         }
         skip += v
         v
      }

      def insertRetroParent( child: V, newParent: A )( implicit tx: S#Tx ) : V = {
         require( child != root )
         val v = new V {
            def tree    = me
            val value   = newParent
            val preHead = child.preHead.prepend()  // preOrder.insert()
            val preTail = child.preTail.append()   // preOrder.insert()
            val post    = child.post.append()      // postOrder.insert()
//            preOrder.placeBefore( child.preHeadKey, preHeadKey )
//            postOrder.placeAfter( child, this )
//            preOrder.placeAfter( child.preTailKey, preTailKey )   // preTailKey must come last
            override def toString = super.toString + "@r-par"
         }
         skip += v
         v
      }

//      // ---- RelabelObserver ----
//
//      def beforeRelabeling( iter: Iterator[ S#Tx, VertexProxy[ S, A ]])( implicit tx: S#Tx ) {
//         // the nasty thing is, in the pre-order list the items appear twice
//         // due to pre versus preTail. thus the items might get removed twice
//         // here, too, and we cannot assert that t.remove( v ) == true
//         iter.foreach( skip -= _.source )
//      }
//
//      def afterRelabeling( iter: Iterator[ S#Tx, VertexProxy[ S, A ]])( implicit tx: S#Tx ) {
//         iter.foreach( skip += _.source )
//      }
   }
}
sealed trait FullTree[ S <:Sys[ S ], A ] {
   protected type V = FullTree.Vertex[ S, A ]

   protected def valueSerializer: Serializer[ A ]
   protected def versionView: A => Int

   def vertexSerializer : Serializer[ V ]
   implicit def versionManifest : Manifest[ A ]

   def root : V

   def insertChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V

   def insertRetroChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V

   def insertRetroParent( child: V, newParent: A )( implicit tx: S#Tx ) : V

//   def mark()( implicit tx: S#Tx ) : MarkTree[ S, A ]
}