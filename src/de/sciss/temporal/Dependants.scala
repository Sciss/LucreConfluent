/*
 *  Dependants.scala
 *  (TemporalObjects)
 *
 *  Copyright (c) 2010 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.temporal

import scala.collection.mutable.{ WeakHashMap }

/**
 *    @version 0.11, 11-Apr-10
 */
object MutableModel {
   var verbose = false
}

trait MutableModelDependant[ T ] {
   import MutableModel._

   def modelReplaced( oldModel: T, newModel: T ) : Unit

   override def finalize {
      if( verbose ) println( "Finalized {" + this + "}" )
   }
}

trait MutableModel[ T ] {
   def addDependant( l: MutableModelDependant[ T ]) : MutableModelDependant[ T ]
   def printDependants : Unit
}

trait MutableModelImpl[ T ] extends MutableModel[ T ] {
   model: T =>
   
   import MutableModel._

   private val dependants = new WeakHashMap[ MutableModelDependant[ T ], Int ]() // maps to use count

   def replacedBy( newModel: T ) {
      if( verbose ) println( "=== ITER START {" + model + "}" )
      dependants.keysIterator.foreach( _.modelReplaced( model, newModel ))
      if( verbose ) println( "=== ITER STOP {" + model + "}" )
      dependants.clear
   }

   def addDependant( l: MutableModelDependant[ T ]) : MutableModelDependant[ T ] = {
      if( verbose ) println( "{" + model + "}.addDependant( " + l + " )" )
      dependants += l -> (dependants.getOrElse( l, 0 ) + 1)
      l
   }

   def printDependants {
      println( "Dependants of {" + model + "}:" )
      dependants.keysIterator.foreach( println( _ ))
   }
}