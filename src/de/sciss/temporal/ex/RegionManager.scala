package de.sciss.temporal.ex

object RegionManager {
   case class RegionAdded( r: Region )
   case class RegionRemoved( r: Region )
   case class RegionReplaced( oldR: Region, newR: Region )
}

class RegionManager extends Model {
   import RegionManager._

   private var regions = Map.empty[ Long, Region ]
   private var idCnt   = 0L

   protected[ex] def newHandle : RegionHandle = {
      val rh = RegionHandle( this, idCnt )
      idCnt += 1
      rh
   }
   
   protected[ex] def add( r: Region ) {
      regions += r.handle.id -> r
      dispatch( RegionAdded( r ))
   }

   protected[ex] def remove( r: Region ) {
      regions -= r.handle.id
      dispatch( RegionRemoved( r ))
   }

   protected[ex] def replace( oldR: Region, newR: Region ) {
      require( oldR.handle == newR.handle )
      regions += newR.handle.id -> newR // overwrites previous mapping
      dispatch( RegionReplaced( oldR, newR ))
   }

   def getRegion( handle: RegionHandle ) : Option[ Region ] =
      regions.get( handle.id )
}