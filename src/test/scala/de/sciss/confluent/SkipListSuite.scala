package de.sciss.confluent

import scala.collection.immutable.IntMap
import scala.collection.mutable.{Set => MSet}
import org.scalatest.{GivenWhenThen, FeatureSpec}
import concurrent.stm.{InTxn, TxnExecutor, Ref}
import de.sciss.lucre.stm.impl.BerkeleyDB
import java.io.File
import de.sciss.collection.txn.{SkipList, HASkipList}
import de.sciss.lucre.stm.{Cursor, Sys}

/**
 * To run this test copy + paste the following into sbt:
 * {{
 * test-only de.sciss.confluent.SkipListSuite
 * }}
 */
class SkipListSuite extends FeatureSpec with GivenWhenThen {
   val CONSISTENCY   = true
   val OBSERVATION   = true
   val REMOVAL       = true
   val TWO_GAP_SIZES = true

   // large
   val NUM1          = 0x4000
   val NUM2          = 0x2000

   require( NUM1 >= 1 && NUM2 >= 6 )

   // small
   val NUM3          = 10

   val SEED          = 0L

//   val rnd           = new util.Random( SEED )
   val rnd           = TxnRandom( SEED )

   // make sure we don't look tens of thousands of actions
   TemporalObjects.showLog = false

   def withSys[ S <: KSys[ S ] with Cursor[ S ]]( sysName: String, sysCreator: () => S, sysCleanUp: S => Unit ) {
      if( TWO_GAP_SIZES ) {
         withList[ S ]( "HA-1 (" + sysName + ")", { oo =>
            implicit val sys = sysCreator()
            implicit val ser = HASkipList.serializer[ S, Int ]( oo )
            val access = sys.root { implicit tx =>
               HASkipList.empty[ S, Int ]( minGap = 1, keyObserver = oo )
            }
            (sys, access, () => sysCleanUp( sys ))
         })
      }
      withList[ S ]( "HA-2 (" + sysName + ")", { oo =>
         implicit val sys = sysCreator()
         implicit val ser = HASkipList.serializer[ S, Int ]( oo )
         val access = sys.root[ HASkipList[ S, Int ]] { implicit tx =>
            HASkipList.empty[ S, Int ]( minGap = 2, keyObserver = oo )
         }
         (sys, access, () => sysCleanUp( sys ))
      })
   }

   withSys[ Confluent ]( "Confluent", () => {
      val dir     = File.createTempFile( "skiplist", "_database" )
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

   def atomic[ A ]( fun: InTxn => A ) : A = TxnExecutor.defaultAtomic( fun )

   def randFill[ S <: Sys[ S ]]( access: Access[ S, HASkipList[ S, Int ]], s: MSet[ Int ])( implicit cursor: Cursor[ S ]) {
      given( "a randomly filled structure" )
      val s1 = cursor.step { implicit tx =>
         implicit val itx = tx.peer
         Seq.fill( NUM1 )( rnd.nextInt( 0x7FFFFFFF )).toSet
      }
      s ++= s1
      cursor.step { implicit tx =>
         val l = access.get
         s1.foreach( l.add( _ ))
      }
   }

   def randFill2( implicit tx: InTxn ) : Set[ Int ] = {
      given( "a set of random numbers" )
      var res   = Set.empty[ Int ]
      for( i <- 0 until NUM2 ) {
         res += rnd.nextInt() & ~1   // any int except MaxValue
      }
      res
   }

   def randFill3( implicit tx: InTxn ) : Set[ Int ] = {
      var res   = Set.empty[ Int ]
      for( i <- 0 until NUM3 ) {
         res += rnd.nextInt( 100 )
      }
      given( "a small set of numbers : " + res.mkString( ", " ))
      res
   }

   def verifyOrder[ S <: Sys[ S ]]( access: Access[ S, HASkipList[ S, Int ]])( implicit cursor: Cursor[ S ]) {
      when( "the structure is mapped to its pairwise comparisons" )
//atomic { implicit tx => println( l.toList )}
      var res  = Set.empty[ Int ]
      val seq  = cursor.step( implicit tx => access.get.toIndexedSeq )
//      val iter = atomic( l.iterator( _ ))
      var prev = -2
      seq.foreach { next =>
         res      += prev compare next
         prev      = next
      }
      res

      then( "the resulting set should only contain -1" )
      assert( res == Set( -1 ), res.toString() )
   }
   def verifyElems[ S <: Sys[ S ]]( access: Access[ S, HASkipList[ S, Int ]], s: MSet[ Int ])
                                  ( implicit cursor: Cursor[ S ]) {
      when( "the structure l is compared to an independently maintained set s" )
      val ll       = cursor.step { implicit tx => access.get.toIndexedSeq }
      val onlyInS  = cursor.step { implicit tx => val l = access.get; s.filterNot( l.contains( _ ))}
      val onlyInL  = ll.filterNot( s.contains( _ ))
      val szL      = cursor.step { implicit tx => access.get.size }
      val szS      = s.size
      then( "all elements of s should be contained in l" )
      assert( onlyInS.isEmpty, onlyInS.take( 10 ).toString() )
      then( "all elements of l should be contained in s" )
      assert( onlyInL.isEmpty, onlyInL.take( 10 ).toString() )
      then( "both should report the same size" )
      assert( szL == szS, "skip list has size " + szL + " / set has size " + szS )

      when( "the structure l is compared to the output from its iterator" )
      then( "both should have the same size" )
      assert( ll.size == szL, "skip list has size " + szL + " / iterator has size " + ll.size )
   }

   def verifyContainsNot[ S <: Sys[ S ]]( access: Access[ S, HASkipList[ S, Int ]], s: MSet[ Int ])
                                        ( implicit cursor: Cursor[ S ]) {
      when( "the structure l is queried for keys not in the independently maintained set s" )
      var testSet = Set.empty[ Int ]
      val inL = cursor.step { implicit tx =>
         val l = access.get
         while( testSet.size < 100 ) {
            val x = rnd.nextInt()( tx.peer )
            if( !s.contains( x )) testSet += x
         }
         testSet.filter( l.contains( _ ))
      }
      then( "none of them should be contained in l" )
      assert( inL.isEmpty, inL.take( 10 ).toString() )
   }

   def verifyAddRemoveAll[ S <: Sys[ S ]]( access: Access[ S, HASkipList[ S, Int ]], s: MSet[ Int ])
                                         ( implicit cursor: Cursor[ S ]) {
      when( "all elements of the independently maintained set are added again to l" )
      val szBefore = cursor.step { implicit tx => access.get.size }
      val newInL   = cursor.step { implicit tx => val l = access.get; s.filter( l.add( _ ))}
      val szAfter  = cursor.step { implicit tx => access.get.size }
      then( "none of the add operations should return 'true'" )
      assert( newInL.isEmpty, newInL.take( 10 ).toString() )
      then( "the size of l should not change" )
      assert( szBefore == szAfter, "l had size " + szBefore + " before, but now reports " + szAfter )

      if( REMOVAL ) {
         when( "all elements of the independently maintained set are removed from l" )
         val keptInL  = cursor.step { implicit tx => val l = access.get; s.filterNot( l.remove( _ ))}
         val szAfter2 = cursor.step { implicit tx => access.get.size }
         then( "all of the remove operations should return 'true'" )
         assert( keptInL.isEmpty, "the following elements were not found in removal: " + keptInL.take( 10 ).toString() )
         then( "the size of l should be zero" )
         assert( szAfter2 == 0, szAfter2.toString )
      }
   }

   private def withList[ S <: Sys[ S ]]( name: String,
      lf: SkipList.KeyObserver[ S#Tx, Int ] => (Cursor[ S ], Access[ S, HASkipList[ S, Int ]], () => Unit) ) {

      def scenarioWithTime( descr: String )( body: => Unit ) {
         scenario( descr ) {
            val t1 = System.currentTimeMillis()
            body
            val t2 = System.currentTimeMillis()
            println( "For " + name + " the tests took " + TestUtil.formatSeconds( (t2 - t1) * 0.001 ))
         }
      }

      if( CONSISTENCY ) {
         feature( "The " + name + " skip list structure should be consistent" ) {
            info( "Several mass operations on the structure" )
            info( "are tried and expected behaviour verified" )

            scenarioWithTime( "Consistency is verified on a randomly filled structure" ) {
               val (_csr, access, cleanUp)  = lf( SkipList.NoKeyObserver )
               implicit val cursor = _csr
               try {
                  val s  = MSet.empty[ Int ]
                  randFill( access, s )
                  verifyOrder( access )
                  verifyElems( access, s )
                  verifyContainsNot( access, s )
                  verifyAddRemoveAll( access, s )
//                  cursor.step { implicit tx => access.get.dispose() }
               } finally {
                  cleanUp()
               }
            }
         }
      }

      if( OBSERVATION ) {
         feature( "The " + name + " skip list structure should be observable" ) {
            info( "Several operations are performed on the structure while an" )
            info( "observer monitors key promotions and demotions" )

            scenarioWithTime( "Observation is verified on a randomly filled structure" ) {
               val obs   = new Obs[ S ]
               val (_sys, access, cleanUp) = lf( obs )
               implicit val system = _sys
               try {
                  val s = system.step( tx => randFill2( tx.peer )) // randFill3
                  when( "all the elements of the set are added" )
                  var uppedInsKey = false
                  system.step { implicit tx =>
                     val l = access.get
                     s.foreach { i =>
                        obs.oneUp.transform( _ - i )( tx.peer )
                        l.add( i )
                        uppedInsKey |= obs.oneUp()( tx.peer ).contains( i )
                     }
                  }
                  then( "none was ever promoted during their insertion" )
                  assert( !uppedInsKey )
                  then( "there haven't been any demotions" )
                  assert( obs.allDn.single().isEmpty, obs.allDn.single().take( 10 ).toString() )
                  then( "there were several promotions" )
                  assert( obs.allUp.single().nonEmpty )
                  then( "the height of the list is equal to the maximally promoted key + 1" )
                  val maxProm = obs.allUp.single().maxBy( _._2 )._2
                  val (h, minGap, maxGap) = system.step { implicit tx =>
                     val l = access.get
                     (l.height, l.minGap, l.maxGap)
                  }
//println( "height = " + h )
                  assert( h == maxProm + 1, "Height is reported as " + h + ", while keys were maximally promoted " + maxProm + " times" )
                  val sz = s.size + 1 // account for the 'maxKey'
                  val minH = math.ceil( math.log( sz ) / math.log( maxGap + 1 )).toInt
                  val maxH = math.ceil( math.log( sz ) / math.log( minGap + 1 )).toInt
                  then( "ceil(log(n+1)/log(maxGap+1)) <= height <= ceil(log(n+1)/log(minGap+1))" )
                  assert( minH <= h && h <= maxH, "Not: " + minH + " <= " + h + " <= " + maxH )

                  if( REMOVAL ) {
                     when( "all the elements are removed again" )
                     var uppedDelKey = false
                     system.step { implicit tx =>
                        val l = access.get
                        s.foreach { i =>
                           obs.oneUp.transform( _ - i )( tx.peer )
                           l.remove( i )
                           uppedDelKey |= obs.oneUp()( tx.peer ).contains( i )
                        }
                     }
                     then( "none was ever promoted during their deletion" )
                     assert( !uppedDelKey, "elements were promoted during their deletion" )
                     val upsNotInS = obs.allUp.single().keys.filterNot( s.contains( _ ))
                     then( "no key was ever promoted which was not in s" )
                     assert( upsNotInS.isEmpty, upsNotInS.take( 10 ).toString() )
                     val dnsNotInS = obs.allDn.single().keys.filterNot( s.contains( _ ))
                     then( "no key was ever demoted which was not in s" )
                     assert( dnsNotInS.isEmpty, dnsNotInS.take( 10 ).toString() )
                     val unbal = atomic { implicit tx =>
                        s.map( i => i -> (obs.allUp().getOrElse( i, 0 ) - obs.allDn().getOrElse( i, 0 ))).filterNot( _._2 == 0 )
                     }
                     then( "the number of promotions and demotions for every key is equal" )
                     assert( unbal.isEmpty, unbal.take( 10 ).toString() )
                  }
//                  system.step { implicit tx => access.get.dispose() }

               } finally {
                  cleanUp()
               }
            }
         }
      }
   }

   final class Obs[ S <: Sys[ S ]] extends SkipList.KeyObserver[ S#Tx, Int ] {
      var allUp = Ref( IntMap.empty[ Int ])
      var allDn = Ref( IntMap.empty[ Int ])
      var oneUp = Ref( IntMap.empty[ Int ])

      def keyUp( key: Int )( implicit tx: S#Tx ) {
         implicit val itx = tx.peer
         allUp.transform( m => m + (key -> (m.getOrElse( key, 0 ) + 1)))
         oneUp.transform( m => m + (key -> (m.getOrElse( key, 0 ) + 1)))
      }

      def keyDown( key: Int )( implicit tx: S#Tx ) {
         implicit val itx = tx.peer
         allDn.transform( m => m + (key -> (m.getOrElse( key, 0 ) + 1)))
      }
   }
}