package de.sciss.lucre
package confluent

import org.scalatest.{FeatureSpec, GivenWhenThen}
import collection.breakOut
import collection.mutable.{Set => MSet}
import java.io.File
import stm.store.BerkeleyDB
import concurrent.stm.InTxn
import data.{SkipOctree, DeterministicSkipOctree}
import geom.{DistanceMeasure, IntDistanceMeasure3D, QueryShape, IntCube, Space, IntPoint3D, IntSpace}
import IntSpace.ThreeDim

/*
 To run this test copy + paste the following into sbt:

test-only de.sciss.confluent.OctreeSuite

 */
class OctreeSuite extends FeatureSpec with GivenWhenThen {
   val CONSISTENCY   = true
   val RANGE_SEARCH  = true
   val NN_SEARCH     = true
   val REMOVAL       = true
   val INMEMORY      = true
   val DATABASE      = true

   val n             = 0x800 // too slow -- 0x1000    // tree size ;  0xE0    // 0x4000 is the maximum acceptable speed
   val n2            = n >> 3    // 0x1000    // range query and nn

   val rnd           = TxnRandom.plain( 2L ) // ( 12L )

   val cube          = IntCube( 0x40000000, 0x40000000, 0x40000000, 0x40000000 )

   // make sure we don't look tens of thousands of actions
   showLog = false

  def withSys[S <: Sys[S]](sysName: String, sysCreator: () => S, sysCleanUp: (S, Boolean) => Unit): Unit = {
    withTree[S](sysName, () => {
      implicit val sys: S = sysCreator()
      // import SpaceSerializers.{IntPoint3DSerializer, IntCubeSerializer}
      implicit val pointView = (p: IntPoint3D, _: Any) => p
      implicit val ser = DeterministicSkipOctree.serializer[S, ThreeDim, IntPoint3D]
      val (access, cursor) = sys.cursorRoot { implicit tx =>
        DeterministicSkipOctree.empty[S, ThreeDim, IntPoint3D](cube)
      } {
        implicit tx => _ => sys.newCursor()
      }

      (cursor, access, succ => sysCleanUp(sys, succ))
    })
  }

  withSys[Confluent]("Confluent", () => {
    val dir = File.createTempFile("totalorder", "_database")
    dir.delete()
    dir.mkdir()
    println(dir.getAbsolutePath)
    val store = BerkeleyDB.factory(dir)
    val res = Confluent(store)
    //      res.root[ Unit ] { _ => }
    res
  }, (s, success) => {
    //      val sz = bdb.step( bdb.numUserRecords( _ ))
    ////         println( "FINAL DB SIZE = " + sz )
    //      assert( sz == 0, "Final DB user size should be 0, but is " + sz )
    //      bdb.close()
    s.close()
  })

  val pointFun3D = (itx: InTxn) => (mask: Int) => IntPoint3D(
    rnd.nextInt()(itx) & mask,
    rnd.nextInt()(itx) & mask,
    rnd.nextInt()(itx) & mask
  )

  def randFill[S <: Sys[S], D <: Space[D]](access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]],
                                           m: MSet[D#Point],
                                           pointFun: InTxn => Int => D#Point)(implicit cursor: stm.Cursor[S]): Unit = {
    Given("a randomly filled structure")

    for (i <- 0 until n) {
      //         if( i == n - 1 ) {
      //            println( "here" )
      //         }
      m += cursor.step { implicit tx =>
        val k = pointFun(tx.peer)(0x7FFFFFFF)
        access() += k
        k
      }
    }
  }

  def verifyConsistency[S <: Sys[S], D <: Space[D]](access: stm.Source[S#Tx, DeterministicSkipOctree[S, D, D#Point]])
                                                   (implicit cursor: stm.Cursor[S]): Unit = {
    When("the internals of the structure are checked")
    Then("they should be consistent with the underlying algorithm")

    type Branch = DeterministicSkipOctree[S, D, D#Point]#Branch
    //      type Leaf   = DeterministicSkipOctree[ S, D, D#Point ]#Leaf

      val (t: DeterministicSkipOctree[S, D, D#Point], q, h0, numOrthants) = cursor.step { implicit tx =>
         val _t = access()
         (_t, _t.hyperCube, _t.lastTreeImpl, _t.numOrthants)
      }
      var currUnlinkedOcs  = Set.empty[ D#HyperCube ]
      var currPoints       = Set.empty[ D#PointLike ]
      var prevs = 0
      var h: Branch = h0
      do {
         assert( h.hyperCube == q, "Root level quad is " + h.hyperCube + " while it should be " + q + " in level n - " + prevs )
         val nextUnlinkedOcs  = currUnlinkedOcs
         val nextPoints       = currPoints
         currUnlinkedOcs      = Set.empty
         currPoints           = Set.empty

        def checkChildren(n: Branch, depth: Int)(implicit tx: S#Tx): Unit = {
            def assertInfo = " in level n-" + prevs + " / depth " + depth

            var i = 0; while( i < numOrthants ) {
               n.child( i ) match {
                  case cb: t.Branch =>
                     val nq = n.hyperCube.orthant( i )
                     val cq = cb.hyperCube
                     assert( nq.contains( cq ), "Node has invalid hyper-cube (" + cq + "), expected: " + nq + assertInfo )
                     assert( n.hyperCube.indexOf( cq ) == i, "Mismatch between index-of and used orthant (" + i + "), with parent " + n.hyperCube + " and " + cq )
                     cb.nextOption match {
                        case Some( next ) =>
                           assert( next.prevOption == Some( cb ), "Asymmetric next link " + cq + assertInfo )
                           assert( next.hyperCube == cq, "Next hyper-cube does not match (" + cq + " vs. " + next.hyperCube + ")" + assertInfo )
                        case None =>
                           assert( !nextUnlinkedOcs.contains( cq ), "Double missing link for " + cq + assertInfo )
                     }
                     cb.prevOption match {
                        case Some( prev ) =>
                           assert( prev.nextOption == Some( cb ), "Asymmetric prev link " + cq + assertInfo )
                           assert( prev.hyperCube == cq, "Next hyper-cube do not match (" + cq + " vs. " + prev.hyperCube + ")" + assertInfo )
                        case None => currUnlinkedOcs += cq
                     }
                     checkChildren( cb, depth + 1 )

                  case l: t.Leaf =>
                     currPoints += l.value

                  case _ =>
               }
            i += 1 }
         }
         cursor.step { implicit tx => checkChildren( h, 0 )}
         val pointsOnlyInNext    = nextPoints.filterNot( currPoints.contains( _ ))
         assert( pointsOnlyInNext.isEmpty, "Points in next which aren't in current (" + pointsOnlyInNext.take( 10 ) + "); in level n-" + prevs )
         h = h.prevOption.orNull
         prevs += 1
      } while( h != null )
   }

  def verifyElems[S <: Sys[S], D <: Space[D]](access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]],
                                              m: MSet[D#Point])(implicit cursor: stm.Cursor[S]): Unit = {
    When( "the structure t is compared to an independently maintained map m" )
      val onlyInM  = cursor.step { implicit tx =>
         val t = access()
         m.filterNot { e => t.contains( e )}
      }
      val onlyInT  = cursor.step { implicit tx => access().iterator.toList.filterNot( e => m.contains( e ))}
      val szT      = cursor.step { implicit tx => access().size }
      val szM      = m.size
      Then( "all elements of m should be contained in t" )
      assert( onlyInM.isEmpty, onlyInM.take( 10 ).toString() )
      Then( "all elements of t should be contained in m" )
      assert( onlyInT.isEmpty, onlyInT.take( 10 ).toString() )
      Then( "both should report the same size" )
      assert( szT == szM, "octree has size " + szT + " / map has size " + szM )
   }

  def verifyContainsNot[S <: Sys[S], D <: Space[D]](access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]],
                                                    m: MSet[D#Point],
                                                    pointFun: InTxn => Int => D#Point)
                                                   (implicit cursor: stm.Cursor[S]): Unit = {
    When( "the structure t is queried for keys not in the independently maintained map m" )
      var testSet = Set.empty[ D#Point ]
      while( testSet.size < 100 ) {
         val x = cursor.step( tx => pointFun( tx.peer )( 0xFFFFFFFF ))
         if( !m.contains( x )) testSet += x
      }
      val inT = cursor.step { implicit tx =>
         val t = access()
         testSet.filter { p => t.contains( p )}
      }
      Then( "none of them should be contained in t" )
      assert( inT.isEmpty, inT.take( 10 ).toString() )
   }

  def verifyAddRemoveAll[S <: Sys[S], D <: Space[D]](access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]],
                                                     m: MSet[D#Point])(implicit cursor: stm.Cursor[S]): Unit = {

    When( "all elements of the independently maintained map are added again to t" )
      val szBefore = cursor.step { implicit tx => access().size }
//println( "BEFORE " + t.system.step { implicit tx => t.toList })

//LucreConfluent.showLog = true

      val newInT   = cursor.step { implicit tx =>
         val t = access()
         val res = m.filter({ e =>
            t.update( e ).isEmpty
         })
//         println( "Aqui" )
         res
      }
//println( "AFTER " + t.system.step { implicit tx => t.toList })
      val szAfter  = cursor.step { implicit tx => access().size }
      Then( "all of the put operations should return 'Some'" )
      assert( newInT.isEmpty, newInT.take( 10 ).toString() )
      Then( "the size of t should not change" )
      assert( szBefore == szAfter, "t had size " + szBefore + " before, but now reports " + szAfter )

      When( "all elements of the independently maintained map are removed from t" )

//LucreConfluent.showLog = true

      val keptInT  = cursor.step { implicit tx =>
         val t = access()
         m.filter( e => t.removeAt( e ).isEmpty )
      }
//      val keptInT = m.filter( e => cursor.step { implicit tx => access.get.removeAt( e ).isEmpty })

      val szAfter2 = cursor.step { implicit tx => access().size }
      Then( "all of the remove operations should return 'Some'" )
      assert( keptInT.isEmpty, keptInT.take( 10 ).toString() )
      Then( "the size of t should be zero" )
      assert( szAfter2 == 0, szAfter2.toString )
   }

   val queryFun3D = (itx: InTxn) => (max: Int, off: Int, ext: Int) =>
      IntCube( rnd.nextInt( max )( itx ) - off,
               rnd.nextInt( max )( itx ) - off,
               rnd.nextInt( max )( itx ) - off,
               rnd.nextInt( ext )( itx ))

   val sortFun3D = (p: ThreeDim#PointLike) => (p.x, p.y, p.z)

  def verifyRangeSearch[S <: Sys[S], A, D <: Space[D], Sort](
      access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]], m: MSet[D#Point],
      queryFun: InTxn => (Int, Int, Int) => QueryShape[A, D],
      sortFun: D#PointLike => Sort)(implicit ord: math.Ordering[Sort], cursor: stm.Cursor[S]): Unit = {

    When( "the octree is range searched" )
      val qs = cursor.step { tx =>
         val f = queryFun( tx.peer )
         Seq.fill( n2 )( f( 0x7FFFFFFF, 0x40000000, 0x40000000 ))
      }
      val rangesT = cursor.step { implicit tx =>
         val t = access()
         qs.map( q => t.rangeQuery( q ).toSet )
      }
      val ks      = m // keySet
      val rangesM = qs.map( q => ks.filter( q.contains( _ )))
      Then( "the results should match brute force with the corresponding set" )
      rangesT.zip(rangesM).foreach { case (s1, s2) =>
         assert( s1 == s2, s1.toList.sortBy( sortFun ).take( 10 ).toString + " -- " +
                           s2.toList.sortBy( sortFun ).take( 10 ))
      }
   }

   val pointFilter3D = (p: ThreeDim#PointLike) => {
      val dx = if( p.x < cube.cx ) (cube.cx + (cube.extent - 1)).toLong - p.x else p.x - (cube.cx - cube.extent)
      val dy = if( p.y < cube.cy ) (cube.cy + (cube.extent - 1)).toLong - p.y else p.y - (cube.cy - cube.extent)
      val dz = if( p.z < cube.cz ) (cube.cz + (cube.extent - 1)).toLong - p.z else p.z - (cube.cz - cube.extent)
      dx <= 0xB504F300L && dy <= 0xB504F300L && dz <= 0xB504F300L &&
         dx * dx + dy * dy > 0L &&
         dx * dx + dz * dz > 0L &&
         dy * dy + dz * dz > 0L
   }

   val euclideanDist3D = IntDistanceMeasure3D.euclideanSq

  // JUHUUUUU SPECIALIZATION BROKEN ONCE MORE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  def verifyNN[S <: Sys[S], M, D <: Space[D]](access: stm.Source[S#Tx, SkipOctree[S, D, D#Point]], m: MSet[D#Point],
                                              pointFun: InTxn => Int => D#Point, pointFilter: D#PointLike => Boolean,
                                              euclideanDist: DistanceMeasure[M, D])
                                             (implicit ord: math.Ordering[M], cursor: stm.Cursor[S]): Unit = {

    When( "the quadtree is searched for nearest neighbours" )
      val ps0 = cursor.step { tx =>
         val f = pointFun( tx.peer )
         Seq.fill( n2 )( f( 0xFFFFFFFF ))
      }
      // tricky: this guarantees that there are no 63 bit overflows,
      // while still allowing points outside the root hyperCube to enter the test
      val ps: Seq[ D#Point ] = ps0.filter( pointFilter )
      val nnT: Map[ D#Point, D#Point ] = cursor.step { implicit tx =>
         val t = access()
         ps.map( p => p -> t.nearestNeighbor( p, euclideanDist ))( breakOut )
      }
      val ks   = m // .keySet
//      val nnM: Map[ D#Point, D#Point ] = ps.map( p => p -> ks.minBy( _.distanceSq( p ))( t.space.bigOrdering ))( breakOut )
      val nnM: Map[ D#Point , D#Point ] = ps.map( p => p -> ks.minBy( p2 => euclideanDist.distance( p2, p )))( breakOut )
      Then( "the results should match brute force with the corresponding set" )
      assert( nnT == nnM, {
         nnT.collect {
           case (q, v) if nnM(q) != v => (q, v, nnM(q))
         }.take( 10 ).toString()
      })
   }

  def withTree[S <: Sys[S]](name: String, tf: () => (stm.Cursor[S],
    stm.Source[S#Tx, DeterministicSkipOctree[S, ThreeDim, ThreeDim#Point]], Boolean => Unit)): Unit = {

    feature( "The " + name + " octree structure should be consistent" ) {
         info( "Several mass operations on the structure" )
         info( "are tried and expected behaviour verified" )

      def scenarioWithTime(descr: String)(body: => Unit): Unit =
        scenario(descr) {
          val t1 = System.currentTimeMillis()
          body
          val t2 = System.currentTimeMillis()
          println("For " + name + " the tests took " + TestUtil.formatSeconds((t2 - t1) * 0.001))
        }

      scenarioWithTime( "Consistency is verified on a randomly filled structure" ) {
            val (_sys, access, cleanUp)  = tf()
            implicit val system    = _sys
            var success = false
            try {
               val m  = MSet.empty[ ThreeDim#Point ]

               randFill[ S, ThreeDim ]( access, m, pointFun3D )
               if( CONSISTENCY ) verifyConsistency[ S, ThreeDim ]( access )
               verifyElems[ S, ThreeDim ]( access, m )
               verifyContainsNot[ S, ThreeDim ]( access, m, pointFun3D )

               if( RANGE_SEARCH ) verifyRangeSearch[ S, BigInt, ThreeDim, (Int, Int, Int) ]( access, m, queryFun3D, sortFun3D )
               if( NN_SEARCH ) verifyNN[ S, BigInt, ThreeDim ]( access, m, pointFun3D, pointFilter3D, euclideanDist3D )
               if( REMOVAL ) verifyAddRemoveAll[ S, ThreeDim ]( access, m )

               system.step { implicit tx =>
                  val t = access()
//                  try {
                     t.clear()
//                     t.dispose()
//                  } catch {
//                     case e =>
//                        e.printStackTrace()
//                        throw e
//                  }
               }
               success = true

            } finally {
               cleanUp( success )
            }
         }
      }
   }
}