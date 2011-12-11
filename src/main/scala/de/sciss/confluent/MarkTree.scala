/*
 *  MarkTree.scala
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

import de.sciss.lucrestm.{Writer, DataInput, DataOutput, Serializer, Sys}
import de.sciss.collection.txn.{Iterator, Ordered, Ordering, SpaceSerializers, SkipList, SkipOctree, TotalOrder}
import de.sciss.collection.geom.{DistanceMeasure3D, Point3D, Space}

object MarkTree {
   private type Order[ S <: Sys[ S ], A, V ] = TotalOrder.Map.Entry[ S, Vertex[ S, A, V ]]

   private val metric = DistanceMeasure3D.chebyshevXY.orthant( 2 )

   private sealed trait Vertex[ S <: Sys[ S ], A, @specialized V ] extends Writer {
      def fullVertex: FullTree.Vertex[ S, A ]
      final def toPoint( implicit tx: S#Tx ): Point3D = new Point3D( pre.tag, post.tag, fullVertex.version )
      def pre:  Order[ S, A, V ]
      def post: Order[ S, A, V ]
      def value: V

      def tree: TreeNew[ S, A, V ] // MarkTree[ S, A, V ]

      def write( out: DataOutput ) {
         fullVertex.write( out )
         pre.write( out )
         post.write( out )
         tree.valueSerializer.write( value, out )
      }

      def removeAndDispose()( implicit tx: S#Tx ) {
         tree.skip.remove( this )
         pre.removeAndDispose()
         post.removeAndDispose()
      }
   }

   def apply[ S <: Sys[ S ], A, @specialized V ]( full: FullTree[ S, A ], rootValue: V )(
      implicit tx: S#Tx, system: S, valueSerializer: Serializer[ V ],
      smf: Manifest[ S ], vmf: Manifest[ V ]) : MarkTree[ S, A, V ] = {

      new TreeNew[ S, A, V ]( full, rootValue )
   }

   private final class IsoResult[ S <: Sys[ S ], A, @specialized V ](
      val pre: Vertex[ S, A, V ], val preCmp: Int, val post: Vertex[ S, A, V ], val postCmp: Int )

   private final class TreeNew[ S <: Sys[ S ], A, @specialized V ]( full: FullTree[ S, A ], rootValue: V )(
      implicit tx: S#Tx, system: S, val valueSerializer: Serializer[ V ], smf: Manifest[ S ], vmf: Manifest[ V ])
   extends MarkTree[ S, A, V ] with TotalOrder.Map.RelabelObserver[ S#Tx, Vertex[ S, A, V ]] {
      me =>

      import full.versionManifest

      type MV = Vertex[ S, A, V ]

      private implicit val vertexSerializer = new Serializer[ MV ] {
         def write( v: MV, out: DataOutput ) { v.write( out )}

         def read( in: DataInput ) : MV = new MV {
            def tree       = me
            val fullVertex = full.vertexSerializer.read( in )
            val pre        = preOrder.readEntry( in )
            val post       = postOrder.readEntry( in )
            val value      = valueSerializer.read( in )
         }
      }

      private val preOrder  : TotalOrder.Map[ S, MV ] = TotalOrder.Map.empty[ S, MV ]( me, _.pre )
      private val postOrder : TotalOrder.Map[ S, MV ] = TotalOrder.Map.empty[ S, MV ]( me, _.post, rootTag = Int.MaxValue )

      val skip = {
         implicit val pointView = (p: MV, tx: S#Tx) => p.toPoint( tx )
         import SpaceSerializers.CubeSerializer
         SkipOctree.empty[ S, Space.ThreeDim, MV ]( FullTree.cube )
      }

      val root = new MV {
         def tree       = me
         def fullVertex = full.root
         def pre        = preOrder.root
         def post       = postOrder.root
         def value      = rootValue
      }

      val preList   = {
         implicit val ord = new Ordering[ S#Tx, MV ] {
            def compare( a: MV, b: MV )( implicit tx: S#Tx ) : Int = a.pre compare b.pre
         }
         val res = SkipList.empty[ S, MV ]
         res.add( root )
         res
      }

      val postList   = {
         implicit val ord = new Ordering[ S#Tx, MV ] {
            def compare( a: MV, b: MV )( implicit tx: S#Tx ) : Int = a.post compare b.post
         }
         val res = SkipList.empty[ S, MV ]
         res.add( root )
         res
      }

      def add( entry: (K, V) )( implicit tx: S#Tx ) : Boolean = {
         skip.add( wrap( entry ))
      }

      def +=( entry: (K, V) )( implicit tx: S#Tx ) : this.type = {
         add( entry )
         this
      }

      private def query( version: K ) : IsoResult[ S, A, V ] = {
         val cfPre = version.preHead
         val (cmPreN, cmPreCmp) = preList.isomorphicQuery( new Ordered[ S#Tx, MV ] {
            def compare( that: MV )( implicit tx: S#Tx ) : Int = {
               cfPre.compare( that.fullVertex.preHead )
            }
         })
         val cfPost = version.post
         val (cmPostN, cmPostCmp ) = postList.isomorphicQuery( new Ordered[ S#Tx, MV ] {
            def compare( that: MV )( implicit tx: S#Tx ) : Int = {
               cfPost.compare( that.fullVertex.post )
            }
         })
         new IsoResult[ S, A, V ]( cmPreN, cmPreCmp, cmPostN, cmPostCmp )
      }

      private def wrap( entry: (K, V) ) : MV = {
         val version = entry._1
         val iso = query( version )
         new MV {
            def tree       = me
            val fullVertex = version
            val pre        = preOrder.insert()
            val post       = postOrder.insert()
            if( iso.preCmp <= 0 ) {
               preOrder.placeBefore( iso.pre, this )
            } else {
               preOrder.placeAfter( iso.pre, this )
            }
            if( iso.postCmp <= 0 ) {
               postOrder.placeBefore( iso.post, this )
            } else {
               postOrder.placeAfter( iso.post, this )
            }
            val value      = entry._2
         }
      }

      def remove( version: K )( implicit tx: S#Tx ) : Boolean = {
         val iso = query( version )
         (iso.preCmp == 0) /* && (iso.postCmp == 0) */ && {
            assert( iso.postCmp == 0 )
            iso.pre.removeAndDispose() // iso.pre is a VM!
            true
         }
      }

      def -=( version: K )( implicit tx: S#Tx ) : this.type = {
         remove( version )
         this
      }

      def get( version: K )( implicit tx: S#Tx ) : Option[ V ] = {
         val iso = query( version )
         if( iso.preCmp == 0 ) {
            assert( iso.postCmp == 0 )
            Some( iso.pre.value )
         } else None
      }

      def nearest( version: K )( implicit tx: S#Tx ) : (K, V) = {
         val iso = query( version )
         if( iso.preCmp == 0 ) {
            assert( iso.postCmp == 0 )
            (version, iso.pre.value)
         } else {
            val preTag  = iso.pre.pre.tag
            val postTag = iso.post.post.tag
            val x       = if( iso.preCmp  < 0 ) preTag  - 1 else preTag
            val y       = if( iso.postCmp > 0 ) postTag + 1 else postTag
            val nn      = skip.nearestNeighbor( Point3D( x, y, version.version ), metric )
            (nn.fullVertex, nn.value)
         }
      }

      // ---- RelabelObserver ----
      def beforeRelabeling( iter: Iterator[ S#Tx, MV ])( implicit tx: S#Tx ) {
         iter.foreach( skip -= _ )
      }

      def afterRelabeling( iter: Iterator[ S#Tx, MV ])( implicit tx: S#Tx ) {
         iter.foreach( skip += _ )
      }
   }
}
sealed trait MarkTree[ S <:Sys[ S ], A, @specialized V ] {
   type K = FullTree.Vertex[ S, A ]

   def add( entry: (K, V) )( implicit tx: S#Tx ) : Boolean
   def +=( entry: (K, V) )( implicit tx: S#Tx ) : this.type
   def remove( version: K )( implicit tx: S#Tx ) : Boolean
   def -=( version: K )( implicit tx: S#Tx ) : this.type
   def get( version: K )( implicit tx: S#Tx ) : Option[ V ]
   def nearest( version: K )( implicit tx: S#Tx ) : (K, V)

   def valueSerializer: Serializer[ V ]
}