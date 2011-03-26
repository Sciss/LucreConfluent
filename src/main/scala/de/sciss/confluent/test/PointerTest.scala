package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree
import annotation.tailrec

object PointerTest {
   def main( args: Array[ String ]) { new PointerTest }
}

class PointerTest {
   import Hashing._

   verbose = false

   val f       = new HashedStoreFactory[ Int ]
   type Path   = FingerTree.IndexedSummed[ Int, Long ]

   var access  = f.empty[ Option[ (Path, W) ]]

   val vn   = FingerTree.IndexedSummed.emptyWithView[ Int, Long ]
   val v0   = append( vn ) // genSeq( 1 )
   val w0   = new W
   val w1   = new W
   w1.elem  = w1.elem.put( v0, 1 )
   w1.next  = w1.next.put( v0, None )
   w0.elem  = w0.elem.put( v0, 2 )
   w0.next  = w0.next.put( v0, Some( v0 -> w1 ))
   access   = access.put( v0, Some( v0 -> w0 ))

   val v1   = append( v0 )
   reverse( v1 )
//   val a1   = reverse( a0.copy( _1 = v1 ), v1 )

   println( "vn:" ); inspect( vn )
   println( "v0:" ); inspect( v0 )
   println( "v1:" ); inspect( v1 )
//   println( "Access:" ); access.inspect

//   println( "w0.next:" ); w0.next.inspect

   assert( toList( vn ) == Nil, "vn" )
   assert( toList( v0 ) == (2 :: 1 :: Nil), "v0" )
   assert( toList( v1 ) == (1 :: 2 :: Nil), "v1" )
   println( "Tests succeeded.")

   class W {
      var elem = f.empty[ Int ]
      var next = f.empty[ Option[ (Path, W) ]]
   }

   def reverse( pw: Path ) {
      var pred          = Option.empty[ (Path, W) ]
      var tail          = pred
//println( "reverse " + pw.toList )
      val oldHeadOption = getAndSubstitute( access, pw )

      @tailrec def iter( nextOption: Option[ (Path, W) ]) : Unit = nextOption match {
         case None =>
            (pred, tail) match {
               case (Some( w1 @ (ppred, wpred) ), headOption @ Some( w2 @ (phead, whead) )) if( w1 != w2 ) =>
                  access = access.put( pw, headOption )
//println( "reverse. pred.next put " + ppred.toList )
                  wpred.next = wpred.next.put( ppred, None )
                  whead.next = whead.next.put( phead, oldHeadOption )
               case _ =>
            }

         case x @ Some( (p1, w) ) =>
//println( "reverse. iter. p1 = " + p1.toList )
            pred  = tail
            tail  = x
//            iter( w.next, p1 )
            iter( getAndSubstitute( w.next, p1 ))
      }

//      iter( access, pw )
      iter( oldHeadOption )

//      val (ap0, nexto) = access
//      nexto foreach {
//         case (ap1, w) =>
//            val ap2 = substitute( ap0, ap1 )
//      }
   }

   def toList( p: Path ) : List[ Int ] = {
      val b = List.newBuilder[ Int ]
      @tailrec def iter( nextOption: Option[ (Path, W )]) : Unit = nextOption match {
         case None => // println( "Nil" )
         case Some( (p1, w) ) =>
            b += w.elem.get( p1 ).get
            iter( getAndSubstitute( w.next, p1 ))
      }
      iter( getAndSubstitute( access, p ))
      b.result()
   }

   def inspect( p: Path ) {
      println( "  (path = " + p.toList + ")" )
      print( "  " )
      @tailrec def iter( nextOption: Option[ (Path, W )]) : Unit = nextOption match {
         case None => println( "Nil" )
         case Some( (p1, w) ) =>
            print( w.elem.get( p1 ).map( _.toString ).getOrElse( "?" ) + " :: " )
            iter( getAndSubstitute( w.next, p1 ))

      }
      iter( getAndSubstitute( access, p ))
   }

   def getAndSubstitute[ T ]( s: Store[ Int, Option[ (Path, T )]], p: Path ) : Option[ (Path, T )] = {
      s.getWithPrefix( p ).flatMap {
         case (Some( (ap, v) ), pre) =>
//            println( "getAndSubstitute p = " + p.toList + " -> pre is " + pre + ", ap = " + ap.toList )
            Some( ap.++( p.drop( pre )) -> v )
         case _ => None
      }
   }

//   def substitute( from: Path, to: Path ) : Path = from.drop( to.size ) // correct ??
}