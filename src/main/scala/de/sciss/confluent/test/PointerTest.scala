package de.sciss.confluent
package test

import de.sciss.fingertree.FingerTree
import annotation.tailrec

object PointerTest {
   def main( args: Array[ String ]) { new PointerTest }

//   def printPath( p: FingerTree.IndexedSummed[ Int, Long ]) = p.toList.toString
}

class PointerTest {
   import Hashing._

   verbose = false

   val f       = new HashedStoreFactory[ Int ]
   type Path   = FingerTree.IndexedSummed[ Int, Long ]

   var access  = f.empty[ Option[ (Path, W) ]]

   val vn   = FingerTree.IndexedSummed.emptyWithView[ Int, Long ]
   val v0   = append( vn ) // genSeq( 1 )
   val w0   = new W( "w0" )
   val w1   = new W( "w1" )
   w1.elem  = w1.elem.put( v0, 1 )
//   w1.next  = w1.next.put( v0, None )
   w0.elem  = w0.elem.put( v0, 2 )
   w0.next  = w0.next.put( v0, Some( v0 -> w1 ))
   access   = access.put( v0, Some( v0 -> w0 ))

   val v1   = append( v0 )
   reverse( v1 )
//   val a1   = reverse( a0.copy( _1 = v1 ), v1 )

//   val v2   = append( v0 )
//   val v2i  = v2.takeRight( 1 )
   val v2i  = mappend( vn, v0 )
   val v2   = v0 :+ v2i
   val v2b  = vn :+ v2i
   dropHead( v2 )
//println( "after dropHead : " + getAndSubstitute( access, v2 ).map( tup => tup._1.toList.toString + " - " + tup._2.name ))
//println( "access:" ); access.inspect

   val w2   = new W( "w2" )
   w2.elem  = w2.elem.put( v2b, 4 /* 1 */) // 4 easier to distinguish
//   w2.next  = w2.next.put( v2i, None )
   appendTail( v2, (v2b, w2) )

//   val v23  =
   val v3i  = mappend( v1, v2, v2b )
   val v23  = v2 :+ v3i
   val v13  = v1 :+ v3i

println( "w1.elem:" ); w1.elem.inspect
//verbose = true
   addToAll( v23, 2 )
//verbose = false
println( "w1.elem:" ); w1.elem.inspect


//   catSelf( v13, v23 )

   println( "vn:" ); inspect( vn )
   println( "v0:" ); inspect( v0 )
   println( "v1:" ); inspect( v1 )
   println( "v2:" ); inspect( v2 )
   println( "v23:" ); inspect( v23 )
//   println( "Access:" ); access.inspect

//   println( "w0.next:" ); w0.next.inspect
//   println( "w1.next:" ); w1.next.inspect

   assert( toList( vn ) == Nil, "vn" )
   assert( toList( v0 ) == (("w0" -> 2) :: ("w1" -> 1) :: Nil), "v0" )
   assert( toList( v1 ) == (("w1" -> 1) :: ("w0" -> 2) :: Nil), "v1" )
   assert( toList( v2 ) == (("w1" -> 1) :: ("w2" -> 4) :: Nil), "v2" )
   assert( toList( v23 ) == (("w1" -> 3) :: ("w2" -> 6) :: Nil), "v23" )
   println( "Tests succeeded." )

   class W( val name: String ) {
      var elem = f.empty[ Int ]
      var next = f.empty[ Option[ (Path, W) ]]

      override def toString = name
   }

   def catSelf( pw: Path, pm: Path ) {

   }

   def addToAll( pw: Path, inc: Int ) {
      @tailrec def iter( nextOption: Option[ (Path, W) ]) : Unit = nextOption match {
         case None =>
         case Some( (p1, w) ) =>
            val old = w.elem.get( p1 ).get
println( "addToAll : " + w.name + " : from " + old + " to " + (old+inc) + " in " +  p1.toList )
            w.elem = w.elem.put( p1, old + inc )
            iter( getAndSubstitute( w.next, p1 ))
      }
      iter( getAndSubstitute( access, pw ))
   }

   def dropHead( pw: Path ) {
      getAndSubstitute( access, pw ).foreach {
         case (p1, w) =>
//            val headOption = w.next.getOrElse( p1, None )
            val headOption = getAndSubstitute( w.next, p1 )
//println( "dropHead. pw = " + pw.toList + "; p1 = " + p1.toList + "; h = " + headOption )
            access = access.put( pw, headOption )
      }
   }

   def appendTail( pw: Path, node: (Path, W) ) {
      var tail = (pw, access, (v: Store[ Int, Option[ (Path, W) ]]) => access = v) // Option.empty[ (Path, W) ]
//println( "appendTail..." )
      @tailrec def iter( nextOption: Option[ (Path, W) ]) : Unit = nextOption match {
         case None =>
            val (ptail, stail, fun) = tail
//println( "appendTail " + pw.toList + "; ptail = " + ptail.toList + "; node = " + node )
            fun( stail.put( ptail, Some( node )))
         case Some( (p1, w) ) =>
            tail  = (p1, w.next, (v: Store[ Int, Option[ (Path, W) ]]) => w.next = v)
            iter( getAndSubstitute( w.next, p1 ))
      }
      iter( getAndSubstitute( access, pw ))
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

   def toList( p: Path ) : List[ (String, Int) ] = {
      val b = List.newBuilder[ (String, Int) ]
      @tailrec def iter( nextOption: Option[ (Path, W )]) : Unit = nextOption match {
         case None => // println( "Nil" )
         case Some( (p1, w) ) =>
val test = p1.toList
            val elem = w.elem.get( p1 ).get
            b += w.name -> elem
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
            print( "(" + w.name + " -> " + w.elem.get( p1 ).map( _.toString ).getOrElse( "?" ) + ") :: " )
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