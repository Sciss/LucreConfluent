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

import de.sciss.lucrestm.{DataOutput, Writer, DataInput, Reader, Sys}


object FullTree {
   private object PreKey {
      implicit def reader[ S <: Sys[ S ]]( implicit vertexReader: Reader[ Vertex[ S ]]) : Reader[ PreKey[ S ]] =
         new ReaderImpl[ S ]( vertexReader )

      private final class ReaderImpl[ S <: Sys[ S ]]( vertexReader: Reader[ Vertex[ S ]])
      extends Reader[ PreKey[ S ]] {
         def read( in: DataInput ) : PreKey[ S ] = {
            val id = in.readUnsignedByte()
            val v  = vertexReader.read( in )
            if( id == 0 ) v.preHeadKey else v.preTailKey
         }
      }
   }
   private sealed trait PreKey[ S <: Sys[ S ], A ] extends Writer /* with VertexSource[ S, Vertex[ S ]] */ {
//      def order: FullPreOrder[ S ]
      def id: Int

      final def write( out: DataOutput ) {
         out.writeUnsignedByte( id )
         vertex.write( out )
      }

      def vertex: Vertex[ S, A ]

      override def equals( that: Any ) : Boolean = {
         (that.isInstanceOf[ PreKey[ _ ]] && {
            val thatPre = that.asInstanceOf[ PreKey[ _ ]]
            (id == thatPre.id) && (vertex == thatPre.vertex)
         })
      }
   }

   private final class PreHeadKey[ S <: Sys[ S ]]( val source: Vertex[ S ])
   extends PreKey[ S ] {
      def order = source.pre
      def id = 0

      override def toString = source.toString + "<pre>"
      def debugString( implicit tx: S#Tx ) = toString + "@" + source.pre.tag
   }

   private final class PreTailKey[ S <: Sys[ S ]]( val source: Vertex[ S ])
   extends PreKey[ S ] {
      def order = source.preTail
      def id = 1

      override def toString = source.toString + "<pre-tail>"
      def debugString( implicit tx: S#Tx ) = toString + "@" + source.preTail.tag
   }

   sealed trait Vertex[ S <: Sys[ S ], A ] extends Writer {
      def value: A
   }
}
sealed trait FullTree[ S <:Sys[ S ], A ] {
   private type V = FullTree.Vertex[ S, A ]

   def insertChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V

   def insertRetroChild( parent: V, newChild: A )( implicit tx: S#Tx ) : V

   def insertRetroParent( child: V, newParent: A )( implicit tx: S#Tx ) : V

//   def mark()( implicit tx: S#Tx ) : MarkTree[ S, A ]
}