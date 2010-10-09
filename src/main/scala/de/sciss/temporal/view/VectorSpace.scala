/*
 *  VectorSpace.java
 *  de.sciss.gui package
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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
 *		12-May-05	copied from de.sciss.meloncillo.gui.VectorSpace
 *		16-Jul-07	moved to de.sciss.gui
 */

package de.sciss.temporal.view

import java.awt.geom.Point2D

object VectorSpace {
   def linlin( hmin: Double, hmax: Double, vmin: Double, vmax: Double ) =
      new VectorSpace( hmin, hmax, vmin, vmax, false, false, hmin, vmin )

   def loglin( hmin: Double, hmax: Double, hcenter: Double, vmin: Double, vmax: Double ) = {
      val h0 = hcenter * hcenter / hmax
      new VectorSpace( hmin, hmax, vmin, vmax, true, false, h0, vmin )
   }

   /**
    * 	Creates a space whose horizontal axis is linearly scaled and vertical
    * 	axis is logarithmically scaled.
    *
    *	@param	hmin
    *	@param	hmax
    *	@param	vmin
    *	@param	vmax
    *	@param	vcenter
    *	@param	hlabel
    *	@param	hunit
    *	@param	vlabel
    *	@param	vunit
    */
   def linlog( hmin: Double, hmax: Double, vmin: Double, vmax: Double, vcenter: Double ) = {
      val v0 = vcenter * vcenter / vmax
      new VectorSpace( hmin, hmax, vmin, vmax, false, true, hmin, v0 )
   }

   def loglog( hmin: Double, hmax: Double, hcenter: Double, vmin: Double, vmax: Double, vcenter: Double ) = {
      val h0 = hcenter * hcenter / hmax
      val v0 = vcenter * vcenter / vmax
      new VectorSpace( hmin, hmax, vmin, vmax, true, true, h0, v0 )
   }
}
case class VectorSpace( hmin: Double, hmax: Double, vmin: Double, vmax: Double,
						      hlog: Boolean, vlog: Boolean, h0: Double, v0: Double )
{
   private val hlogfactor	= math.log( hmax / h0 )
   private val vlogfactor	= math.log( vmax / v0 )
	private val hoffset     = math.log( hmin / h0 ) / hlogfactor
   private val voffset     = math.log( vmin / v0 ) / vlogfactor

	def hUnityToSpace( hu: Double  ) : Double = if( hlog ) {
		math.exp( (hu * (1.0 - hoffset) + hoffset) * hlogfactor ) * h0
   } else {
		hmin + (hmax - hmin) * hu
	}

	def vUnityToSpace( vu: Double  ) : Double = if( vlog ) {
		math.exp( (vu * (1.0 - voffset) + voffset) * vlogfactor ) * v0
	} else {
		vmin + (vmax - vmin) * vu
   }

	def hSpaceToUnity( hs: Double ) : Double = if( hlog ) {
		(math.log( hs / h0 ) / hlogfactor - hoffset) / (1.0 - hoffset)
	} else {
		(hs - hmin) / (hmax - hmin)
  	}

	def vSpaceToUnity( vs: Double ) : Double = if( vlog ) {
		(math.log( vs / v0 ) / vlogfactor - voffset) / (1.0 - voffset)
	} else {
		(vs - vmin) / (vmax - vmin)
	}

	def unityToSpace( unityPt: Point2D ) : Point2D =
		new Point2D.Double( hUnityToSpace( unityPt.getX() ), vUnityToSpace( unityPt.getY() ))

	def spaceToUnity( spacePt: Point2D ) : Point2D =
	   new Point2D.Double( hSpaceToUnity( spacePt.getX() ), vSpaceToUnity( spacePt.getY() ))
}