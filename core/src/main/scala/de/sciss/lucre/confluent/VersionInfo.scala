/*
 *  VersionInfo.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.lucre
package confluent

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

object VersionInfo {
   trait Modifiable extends VersionInfo {
      def message: String
      def message_=( value: String ) : Unit
   }

   def apply( message: String, timeStamp: Long ) : VersionInfo = Impl( message, timeStamp )

   private val df = new SimpleDateFormat( "d MMM yyyy, HH:mm''ss.SSS", Locale.US )

   private final case class Impl( message: String, timeStamp: Long ) extends VersionInfo {
      override def toString = "VersionInfo(" + (if( message != "" ) message + ", " else "") +
         "date = " + df.format( new Date( timeStamp )) + ")"
   }
}
trait VersionInfo {
   /**
    * (Possibly empty) message describing the associated transaction. This can be seen as similar to
    * a commit message, the difference being that it must be given (currently) at the moment the
    * transaction is opened and not at the moment when it is closed.
    */
   def message: String

   /**
    * System time stamp of the associated transaction. The format and offset corresponds to
    * `System.currentTimeMillis`.
    */
   def timeStamp: Long
}