package de.sciss.confluent

import impl.Confluent
import org.scalatest.{GivenWhenThen, FeatureSpec}
import de.sciss.lucre.stm.impl.BerkeleyDB
import java.io.File
import de.sciss.lucre.stm.Cursor
import de.sciss.collection.txn.TotalOrder

/**
 * To run this test copy + paste the following into sbt:
 * {{
 * test-only de.sciss.confluent.TotalOrderSuite
 * }}
 */
class TotalOrderSuite extends FeatureSpec with GivenWhenThen {
   val MONITOR_LABELING = false

   val NUM              = 0x8000 // 0x10000 // 0x80000  // 0x200000
   val RND_SEED         = 0L

   // make sure we don't look tens of thousands of actions
   TemporalObjects.showLog = false

   withSys[ Confluent.System ]( "Confluent", () => {
      val dir     = File.createTempFile( "totalorder", "_database" )
      dir.delete()
      dir.mkdir()
      println( dir.getAbsolutePath )
      val store = BerkeleyDB.factory( dir )
      val res = Confluent( store )
//      res.root[ Unit ] { _ => }
      res
   }, s => {
//      val sz = bdb.step( bdb.numUserRecords( _ ))
////         println( "FINAL DB SIZE = " + sz )
//      assert( sz == 0, "Final DB user size should be 0, but is " + sz )
//      bdb.close()
      s.close()
   })

   def withSys[ S <: KSys[ S ]]( sysName: String, sysCreator: () => S with Cursor[ S ], sysCleanUp: S => Unit ) {
      def scenarioWithTime( descr: String )( body: => Unit ) {
         scenario( descr ) {
            val t1 = System.currentTimeMillis()
            body
            val t2 = System.currentTimeMillis()
            println( "For " + sysName + " the tests took " + TestUtil.formatSeconds( (t2 - t1) * 0.001 ))
         }
      }

      feature( "The ordering of the structure should be consistent" ) {
         info( "Each two successive elements of the structure" )
         info( "should yield '<' in comparison" )

         scenarioWithTime( "Ordering is verified on a randomly filled " + sysName + " structure" ) {
            given( "a randomly filled structure (" + sysName + ")" )

//            type E = TotalOrder.Set.Entry[ S ]
            implicit val system = sysCreator()
            try {
               implicit val ser = TotalOrder.Set.serializer[ S ]
               val access = system.root { implicit tx =>
                  TotalOrder.Set.empty[ S ] /* ( new RelabelObserver[ S#Tx, E ] {
                     def beforeRelabeling( first: E, num: Int )( implicit tx: S#Tx ) {
                        if( MONITOR_LABELING ) {
   //                     Txn.afterCommit( _ =>
                              println( "...relabeling " + num + " entries" )
   //                     )
                        }
                     }

                     def afterRelabeling( first: E, num: Int )( implicit tx: S#Tx ) {}
                  }) */
               }
//               val rnd   = new util.Random( RND_SEED )
               val rnd   = TxnRandom( RND_SEED )
               // would be nice to test maximum possible number of labels
               // but we're running out of heap space ...
               val n     = NUM // 113042 // 3041
      //        to        = to.append() // ( 0 )

               val set = system.step { implicit tx =>
                  var e = access.get.root
                  var coll = Set[ TotalOrder.Set.Entry[ S ]]() // ( e )
                  for( i <- 1 until n ) {
//if( (i % 1000) == 0 ) println( "i = " + i )
                     if( rnd.nextBoolean()( tx.peer ) ) {
                        e = e.append() // to.insertAfter( e ) // to.insertAfter( i )
                     } else {
                        e = e.prepend() // to.insertBefore( e ) // e.prepend() // to.insertBefore( i )
                     }
                     coll += e
                  }
                  coll
               }
//println( "AQUI" )

               when( "the structure size is determined" )
               val sz = system.step { implicit tx => access.get.size }
      //        val sz = {
      //           var i = 1; var x = to; while( !x.isHead ) { x = x.prev; i +=1 }
      //           x = to; while( !x.isLast ) { x = x.next; i += 1 }
      //           i
      //        }
               then( "it should be equal to the number of elements inserted" )
               assert( sz == n, sz.toString + " != " + n )

               when( "the structure is mapped to its pairwise comparisons" )
               val result = system.step { implicit tx =>
                  var res   = Set.empty[ Int ]
                  var prev  = access.get.head
                  var next  = prev.next.orNull
                  while( next != null ) {
//                  res     += prev compare next
                     res    += prev.tag compare next.tag
                     prev    = next
                     next    = next.next.orNull
                  }
                  res
               }

               then( "the resulting set should only contain -1" )
               assert( result == Set( -1 ), result.toString + " -- " + system.step( implicit tx =>
                  access.get.tagList( access.get.head )
               ))

               when( "the structure is emptied" )
               val sz2 = system.step { implicit tx =>
//                  set.foreach( _.removeAndDispose() )
                  val to = access.get
                  var prev = to.head
                  var next = prev
                  while( prev != null ) {
                     next    = next.next.orNull
                     if( prev != to.root ) prev.removeAndDispose()
                     prev     = next
                  }

                  to.size
               }
               then( "the order should have size 1" )
               assert( sz2 == 1, "Size is " + sz2 + " and not 1" )

//               system.step { implicit tx =>
////                  set.foreach( _.removeAndDispose() )
//                  access.dispose()
//               }

            } finally {
               sysCleanUp( system )
            }
         }
      }
   }
}