/*
 *  VersionTree.scala
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
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 *
 *	 For further information, please contact Hanns Holger Rutz at
 *	 contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.confluent

/**
 *    Note: this is a sub-_tree_,
 *    not the whole DAG
 */
trait VersionTree {
   def level: Int
   def preOrder:  PreOrder[ Version ]  // XXX atomic
   def postOrder: PostOrder[ Version ] // XXX atomic

   def insertRoot( version: Version ) : VersionTreeOrder
   def insertChild( parent: Version )( child: Version ) : VersionTreeOrder
   def insertRetroParent( child: Version )( parent: Version ) : VersionTreeOrder
   def insertRetroChild( parent: Version )( child: Version ) : VersionTreeOrder

   def inspect {
      println( "--PRE--")
      preOrder.inspect
      println( "--POST--")
      postOrder.inspect
   }
}

object VersionTree {
   def empty( level: Int ) : VersionTree = new Impl( level )

   private class Impl( val level: Int )
   extends VersionTree {
      val preOrder   = new PreOrder[ Version ]
      val postOrder  = new PostOrder[ Version ]

      def insertRoot( version: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRoot( version )
         val postRec = postOrder.insertRoot( version )
         (preRec, postRec)
      }

      def insertChild( parent: Version )( child: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertChild( parent.preRec, child )
         val postRec = postOrder.insertChild( parent.postRec, child )
         (preRec, postRec)
      }

      def insertRetroParent( child: Version )( parent: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRetroParent( child.preRec, parent )
         val postRec = postOrder.insertRetroParent( child.postRec, parent )
         (preRec, postRec)
      }

      def insertRetroChild( parent: Version )( child: Version ) : VersionTreeOrder = {
         val preRec  = preOrder.insertRetroChild( parent.preRec, child )
         val postRec = postOrder.insertRetroChild( parent.postRec, child )
         (preRec, postRec)
      }
   }

}