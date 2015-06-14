package de.sciss.lucre.confluent

/*

To run only this test:

test-only de.sciss.lucre.confluent.TimeStampSpec

 */
class TimeStampSpec extends ConfluentSpec {
  // ensure time stamps are distinct
  def sleep(): Unit = Thread.sleep(10)

  "Time stamps and version info" should "work in a non-melded graph" in { system =>
    val (access, cursor) = system.cursorRoot { implicit tx => 0
    } { implicit tx => _ => system.newCursor() }

    //      sleep()

    // test dummy step
    val path0a = cursor.step { implicit tx => access(); tx.inputAccess }

    sleep()

    // test write step
    val path0 = cursor.step { implicit tx =>
      tx.info.message = "message 1"
      access() = 1
      tx.inputAccess
    }

    assert(path0a === path0)

    sleep()

    val path1 = cursor.step { implicit tx =>
      tx.info.message = "message 2"
      access() = 2
      tx.inputAccess
    }

    val path2 = cursor.step(_.inputAccess)

      // ---- first of all, ensure version infos can be read ----

      val (info0, info1, info2) = cursor.step { implicit tx =>
         (path0.info, path1.info, path2.info)
      }

      assert( info0.message === "" )
      assert( info1.message === "message 1" )
      assert( info2.message === "message 2" )
      assert( info0.timeStamp < info1.timeStamp )
      assert( info1.timeStamp < info2.timeStamp )

      // ---- now perform `until` queries ----

      val (path0u1, path0u2, path0u3) = cursor.step { implicit tx =>
         (path0.takeUntil( info0.timeStamp - 1 ), path0.takeUntil( info0.timeStamp ), path0.takeUntil( System.currentTimeMillis() ))
      }

      assert( path0u1 === path0 )
      assert( path0u2 === path0 )
      assert( path0u3 === path0 )

      val (path1u0, path1u1, path1u2, path1u3) = cursor.step { implicit tx =>
         val res0 = path1.takeUntil( 0L )
         val res1 = path1.takeUntil( info1.timeStamp - 1 )
         val res2 = path1.takeUntil( info1.timeStamp )
         val res3 = path1.takeUntil( System.currentTimeMillis() )
         (res0, res1, res2, res3)
      }

      assert( path1u0 === path0 )
      assert( path1u1 === path0 )
      assert( path1u2 === path1 )
      assert( path1u3 === path1 )

      val (path2u0, path2u1, path2u2, path2u3, path2u4, path2u5) = cursor.step { implicit tx =>
         val res0 = path2.takeUntil( 0L )
         val res1 = path2.takeUntil( info1.timeStamp - 1 )
         val res2 = path2.takeUntil( info1.timeStamp )
         val res3 = path2.takeUntil( info2.timeStamp - 1 )
         val res4 = path2.takeUntil( info2.timeStamp )
         val res5 = path2.takeUntil( System.currentTimeMillis() )
         (res0, res1, res2, res3, res4, res5)
      }

      assert( path2u0 === path0 )
      assert( path2u1 === path0 )
      assert( path2u2 === path1 )
      assert( path2u3 === path1 )
      assert( path2u4 === path2 )
      assert( path2u5 === path2 )

//      assert( res === Vec( 1, 2 ))
   }
}