package de.sciss.temporal.view

import de.sciss.temporal.AudioFileElement
import de.sciss.sonogram.{SonogramOverviewManager, SonogramOverview}
import de.sciss.io.CacheManager
import java.io.{File, IOException}

object SonogramManager extends SonogramOverviewManager {
   mgr =>

   private var map = Map[ AudioFileElement, SonogramOverview ]()

   def get( afe: AudioFileElement ) : Option[ SonogramOverview ] =
      map.get( afe ) orElse {
         try {
            afe.path.map( mgr.fromPath( _ ))
         }
         catch { case e: IOException => { e.printStackTrace; None }}
      }

   // ---- SonogramOverviewManager ----
   lazy val fileCache = {
      val cm      = new CacheManager
      val folder  = new File( new File( System.getProperty( "user.home" ), "TemporalObjects" ), "cache" )
      folder.mkdirs
      cm.setFolderAndCapacity( folder, 100 )
      cm.setActive( true )
      cm
   }

   val appCode = "Ttm "
}