/*
 *  FatValueMap
 *  (de.sciss.confluent package)
 *
 *  Copyright (c) 2009-2010 Hanns Holger Rutz. All rights reserved.
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
 *	 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
 *    @version 0.10, 09-Apr-10
 */
class FatFieldMap[ V ]
extends LexiTreeMap[ Version, OracleMap[ V ]]()( Version.IdOrdering ) {

   import ordering._
   
   /**
    *    Like findMaxPrefixOffset, but with support
    *    for multiplicities
    */
   def multiFindMaxPrefix( path: Path ) : Tuple2[ Option[ OracleMap[ V ]], Int ] = {
      var sup: Access = RootAccess
      var l: Node = null
       var cnt = 0

      val iter = path.iterator
      var cond = true
      while( cond && iter.hasNext && sup.middle != null ) {
         val key = iter.next
         splay( key, sup )
         var n          = sup.middle
         var keyUnequal = compare( key, n.key ) != 0
         if( keyUnequal ) {
            val key2 = key.fallBack
            if( key2 != key ) {  // retry if there is a different fall back
               splay( key2, sup )
               n	         = sup.middle
               keyUnequal  = compare( key2, n.key ) != 0
            }
         }
         if( keyUnequal ) {
            cond = false
         } else {
            if( n.terminal ) l = n
            sup	= n
            cnt  += 1
         }
       }
      (if( l != null ) Some( l.value ) else None, cnt)
   }
}