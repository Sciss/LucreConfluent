package de.sciss.lucre.confluent

/**
 * To run only this test:
 * test-only de.sciss.lucre.confluent.TimeStampSpec
 */
class TimeStampSpec extends ConfluentSpec {
   // ensure time stamps are distinct
   def sleep() { Thread.sleep( 10 )}

   "Time stamps and version info" should "work as expected" in { system =>
      val (access, cursor) = system.cursorRoot { implicit tx => 0
      } { implicit tx => _ => tx.newCursor() }

//      sleep()

      // test dummy step
      val path0a = cursor.step { implicit tx => access.get; tx.inputAccess }

      sleep()

      // test write step
      val path0 = cursor.step { implicit tx =>
         tx.info.message = "message 1"
         access.set( 1 )
         tx.inputAccess
      }

      assert( path0a === path0 )

      sleep()

      val path1 = cursor.step { implicit tx =>
         tx.info.message = "message 2"
         access.set( 2 )
         tx.inputAccess
      }

      val path2 = cursor.step( _.inputAccess )

      // first of all, ensure version infos can be read
      val (info0, info1, info2) = cursor.step { implicit tx =>
         (path0.info, path1.info, path2.info)
      }

      assert( info0.message === "" )
      assert( info1.message === "message 1" )
      assert( info2.message === "message 2" )
      assert( info0.timeStamp < info1.timeStamp )
      assert( info1.timeStamp < info2.timeStamp )

//      assert( res === IIdxSeq( 1, 2 ))
   }
}