/*
 *  LucreConfluent.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
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
