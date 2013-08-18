/*
 *  LucreConfluent.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2013 Hanns Holger Rutz. All rights reserved.
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

import annotation.elidable
import elidable.CONFIG
import java.util.{Locale, Date}
import java.text.SimpleDateFormat

package object confluent {
  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'Confluent' - ", Locale.US)
  var showLog         = false
  var showPartialLog  = false
  var showCursorLog   = false

  @elidable(CONFIG) private[confluent] def log(what: => String): Unit =
    if (showLog) Console.out.println(logHeader.format(new Date()) + what)

  @elidable(CONFIG) private[confluent] def logPartial(what: => String): Unit =
    if (showPartialLog) Console.out.println(logHeader.format(new Date()) + "partial " + what)

  @elidable(CONFIG) private[confluent] def logCursor(what: => String): Unit =
    if (showCursorLog) Console.out.println(logHeader.format(new Date()) + "cursor " + what)
}
