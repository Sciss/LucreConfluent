/**
 *  PDFExport.scala
 *  (TemporalObjects)
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

package de.sciss.temporal.view

import com.itextpdf.text.{ Document => TDocument, Rectangle => TRectangle }
import java.io.{ File, FileOutputStream }
import com.itextpdf.text.pdf.{ PdfWriter }
import java.awt.{ Component, Insets }

/**
 *    @version 0.10, 29-Mar-10
 */
object PDFExport {
   // note: resolution is 72 dpi
   def export( c: Component, f: File, margin: Insets = new Insets( 0, 0, 0, 0 )) { // Insets( 72, 72, 72, 72 )
      val cmpWidth   = c.getWidth
      val cmpHeight  = c.getHeight
      val pageWidth  = cmpWidth + (margin.left + margin.right)
      val pageHeight = cmpHeight + (margin.top + margin.bottom)
      val pageSize	= new TRectangle( 0, 0, pageWidth, pageHeight )
      val doc		   = new TDocument( pageSize, margin.left, margin.right, margin.top, margin.bottom )
      val stream     = new FileOutputStream( f )
      val writer     = PdfWriter.getInstance( doc, stream )
      doc.open
      val cb		   = writer.getDirectContent
      val tp		   = cb.createTemplate( cmpWidth, cmpHeight )
      val g2		   = tp.createGraphics( cmpWidth, cmpHeight )
      c.paint( g2 )
      g2.dispose
      cb.addTemplate( tp, (pageWidth - cmpWidth) / 2, (pageHeight - cmpHeight) / 2 )  // centered
      doc.close
   }
}