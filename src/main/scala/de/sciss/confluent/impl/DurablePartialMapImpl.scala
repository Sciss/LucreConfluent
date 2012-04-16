package de.sciss.confluent
package impl

import de.sciss.lucre.DataOutput
import annotation.switch
import de.sciss.lucre.stm.{TxnSerializer, Serializer, DataStore}

object DurablePartialMapImpl {
   private sealed trait Entry[ S <: KSys[ S ], +A ]
   private final case class EntryPre[ S <: KSys[ S ]]( hash: Long ) extends Entry[ S, Nothing ]
   private final case class EntrySingle[ S <: KSys[ S ], A ]( term: Long, v: A ) extends Entry[ S, A ]
   private final case class EntryMap[ S <: KSys[ S ], A ]( m: IndexMap[ S, A ]) extends Entry[ S, A ]
}
sealed trait DurablePartialMapImpl[ S <: KSys[ S ], @specialized( Int, Long) K ] extends DurablePersistentMap[ S, K ] {
   import DurablePartialMapImpl._

   protected def store: DataStore

//   override def toString = "VarMap(" + store + ")"

   protected def writeKey( key: K, out: DataOutput ) : Unit

   final def isFresh( key: K, conPath: S#Acc )( implicit tx: S#Tx ) : Boolean = {
      val path = conPath.partial
      store.get { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( path.indexSum )
      } { in =>
         // XXX TODO i don't think we'll encounter a partial hash unless
         // there is a hash collision. the whole method should thus be
         // simplified to a plain store.contains( key )
         (in.readUnsignedByte(): @switch) match {
            case 1 => true    // a single value is found
            case 2 => true    // a map is found
            case _ => false   // only a partial hash is found
         }
      } getOrElse( false )
   }

   final def put[ @specialized A ]( key: K, conPath: S#Acc, value: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
      val path = conPath.partial
      val (index, term) = conPath.splitIndex
//if( key == 0 ) {
//   println( "::::: put. write path = index " + index + "; term = " + term + "; sum = " + index.sum )
//}
      // first we need to see if anything has already been written to the index of the write path
      store.flatGet { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( path.indexSum /* index.sum */ )
      } { in =>
         (in.readUnsignedByte(): @switch) match {
            case 1 =>
               // a single 'root' value is found. extract it for successive re-write.
               val term2   = in.readLong()
               val prev    = ser.read( in )
               Some( EntrySingle( term2, prev ))
            case 2 =>
               // there is already a map found
               val m = tx.readIndexMap[ A ]( in, index )
               Some( EntryMap( m ))
            case _ => None // this would be a partial hash which we don't use
         }
      } match {
         // with the previous entry read, react as follows:
         // if there is a single entry, construct a new ancestor.map with the
         // entry's value taken as root value
         case Some( EntrySingle( prevTerm, prevValue )) =>
            putFullMap[ A ]( key, index, term, value, prevTerm, prevValue )
         // if there is an existing map, simply add the new value to it
         case Some( EntryMap( m )) =>
//if( key == 0 ) {
//   println( "::::: adding to existing map " + m )
//}
            m.add( term, value )
         // if there is no previous entry...
         case _ =>
            // we may write a single entry if and only if the value can be seen
            // as a root value (the write path corresponds to the construction
            // of the entity, e.g. path == <term, term>; or the entity was
            // re-written in the tree root, hence path.suffix == <term, term>)
            val indexTerm = index.term
            if( term == indexTerm ) {
               putPartials( key, index )
               putFullSingle[ A ]( key, index, term, value )
               // otherwise, we must read the root value for the entity, and then
               // construct a new map containing that root value along with the
               // new value
            } else {
               // however, there is a particular case of our unorthodox skip list
               // structure -- it allocates new variables while only maintaining one
               // main id. this yields perfectly valid structures which cannot be
               // accessed other than in the write path, although term and indexTerm
               // are _not_ the same. example: insertion in a full leaf -- a new
               // branch is created with two down vars which have the tree id's
               // index term and the write version's term. their initial value is
               // the newly created split leaves, there are no previous values.
               //
               // we may forbid this behaviour in future versions, but for now let's
               // be generous and allow it, by checking _if_ a previous value exists.
               // if not -- go again for the full single entry...
               //                  val prevValue = get[ A ]( key, path ).getOrElse(
               //                     sys.error( path.mkString( "Expected previous value not found for <" + key + " @ ", ",", ">" ))
               //                  )
               //                  putPartials( key, index )
               //                  putFullMap[ A ]( key, index, term, value, indexTerm, prevValue )
               get[ A ]( key, conPath ) match {
                  case Some( prevValue ) =>
                     putPartials( key, index )
                     putFullMap[ A ]( key, index, term, value, indexTerm, prevValue )

                  case _ =>
                     putPartials( key, index )
                     putFullSingle[ A ]( key, index, term, value )
               }
            }
      }
   }

   private def putFullMap[ @specialized A ]( key: K, conIndex: S#Acc, term: Long, value: A, prevTerm: Long,
                                             prevValue: A )( implicit tx: S#Tx, ser: Serializer[ A ]) {
      //         require( prevTerm != term, "Duplicate flush within same transaction? " + term.toInt )
      //         require( prevTerm == index.term, "Expected initial assignment term " + index.term.toInt + ", but found " + prevTerm.toInt )
      // create new map with previous value
      val m = tx.newIndexMap[ A ]( conIndex, prevTerm, prevValue )
      // store the full value at the full hash (path.sum)
//if( key == 0 ) {
//   println( "::::: full map. index = " + index + "; term = " + term + "; sum = " + index.sum + "; m = " + m )

      val index = conIndex.partial

      store.put { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( index.sum )
      } { out =>
         out.writeUnsignedByte( 2 ) // aka map entry
         m.write( out )
      }
      // then add the new value
      m.add( term, value )
   }

   // stores the prefixes
   private def putPartials( key: K, conIndex: S#Acc )( implicit tx: S#Tx ) {
      val index = conIndex.partial

      Hashing.foreachPrefix( index, hash => {
         val res = store.contains { out =>
            writeKey( key, out ) // out.writeInt( key )
            out.writeLong( hash )
         }
//if( key == 0 ) {
//   println( "::::: partial; test contains " + hash + "; index = " + index + "; result = " + res )
//}
         res
      }) {
         // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
         case (hash, preSum) => store.put { out =>
//if( key == 0 ) {
//   println( "::::: partial; store hash = " + hash + "; preSum = " + preSum )
//}
            writeKey( key, out ) // out.writeInt( key )
            out.writeLong( hash )
         } { out =>
            out.writeUnsignedByte( 0 ) // aka entry pre
            out.writeLong( preSum )
         }
      }
   }

   // store the full value at the full hash (path.sum)
   private def putFullSingle[ @specialized A ]( key: K, conIndex: S#Acc, term: Long, value: A )
                                              ( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, A ]) {
      val index = conIndex.partial

      store.put { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( index.sum )
      } { out =>
//if( key == 0 ) {
//   println( "::::: full single; index = " + index + "; term = " + term + "; sum = " + index.sum )
//}
         out.writeUnsignedByte( 1 ) // aka entry single
         out.writeLong( term )
         ser.write( value, out )
      }
   }

   final def get[ @specialized A ]( key: K, conPath: S#Acc )( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ A ] = {
      val (maxIndex, maxTerm) = conPath.splitIndex
      getWithPrefixLen[ A, A ]( key, maxIndex, maxTerm )( (_, _, value) => value )
   }

   final def getWithSuffix[ @specialized A ]( key: K, conPath: S#Acc )
                                            ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ (S#Acc, A) ] = {
      val (maxIndex, maxTerm) = conPath.splitIndex
      getWithPrefixLen[ A, (S#Acc, A) ]( key, maxIndex, maxTerm )( (preLen, writeTerm, value) =>
      //            (path.dropAndReplaceHead( preLen, writeTerm ), value)
         (writeTerm +: conPath.drop( preLen ), value)
      )
   }

   private def getWithPrefixLen[ @specialized A, B ]( key: K, maxConIndex: S#Acc, maxTerm: Long )
                                                    ( fun: (Int, Long, A) => B )
                                                    ( implicit tx: S#Tx, ser: Serializer[ A ]) : Option[ B ] = {
      val maxIndex = maxConIndex.partial

      val preLen = Hashing.maxPrefixLength( maxIndex, hash => store.contains { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( hash )
      })
      val preConLen = math.max( 0, (preLen << 1) - 1 )   // III
      val (conIndex, term0) = if( preConLen == maxConIndex.size ) {
         // maximum prefix lies in last tree
         (maxConIndex, maxTerm)
      } else {
         // prefix lies in other tree
         maxConIndex.splitAtIndex( preConLen )
      }
      val index   = conIndex.partial
      val preSum  = index.sum
      store.flatGet { out =>
         writeKey( key, out ) // out.writeInt( key )
         out.writeLong( preSum )
      } { in =>
         (in.readUnsignedByte(): @switch) match {
            case 0 => // partial hash
               val hash = in.readLong()
               //                  EntryPre[ S ]( hash )
               val idx     = maxIndex.indexOfSum( hash )
               val idxCon  = idx << 1  // III XXX TODO check correctness
               val (conIndex, fullTerm) = maxConIndex.splitAtIndex( idxCon )
               getWithPrefixLen( key, conIndex, fullTerm )( fun )

            case 1 =>
               // --- THOUGHT: This assertion is wrong. We need to replace store.get by store.flatGet.
               // if the terms match, we have Some result. If not, we need to ask the index tree if
               // term2 is ancestor of term. If so, we have Some result, if not we have None.
               //                  assert( term == term2, "Accessed version " + term.toInt + " but found " + term2.toInt )

               // --- ADDENDUM: I believe we do not need to store `term2` at all, it simply doesn't
               // matter. Given a correct variable system, there is no notion of uninitialised values.
               // Therefore, we cannot end up in this case without the previous stored value being
               // correctly the nearest ancestor of the search term. For example, say the index tree
               // is v0, and the variable was created in v2. Then there is no way that we try to
               // read that variable with v0. The value stored here is always the initialisation.
               // If there was a second assignment for the same index tree, we'd have found an
               // entry map, and we can safely _coerce_ the previous value to be the map's
               // _root_ value.

               val term2 = in.readLong()
               val value = ser.read( in )
               //                  EntrySingle[ S, A ]( term2, value )
               Some( fun( preConLen, term2, value ))

            case 2 =>
               val m = tx.readIndexMap[ A ]( in, conIndex )
               //                  EntryMap[ S, A ]( m )
               val term = if( term0 == maxTerm ) term0 else {
                  // index was split, we need to find the terminal version from which
                  // we went to tree maxIndex(preLen). then the value stored there
                  // should be re-written at next index tree (term0)
                  tx.inEdges
                  sys.error( "TODO" )
               }
               val (term2, value) = m.nearest( term )
               Some( fun( preConLen, term2, value ))
         }
      }
   }
}
final class PartialIntMapImpl[ S <: KSys[ S ]]( protected val store: DataStore ) extends DurablePartialMapImpl[ S, Int ] {
   protected def writeKey( key: Int, out: DataOutput ) { out.writeInt( key )}
}
final class PartialLongMapImpl[ S <: KSys[ S ]]( protected val store: DataStore ) extends DurablePartialMapImpl[ S, Long ] {
   protected def writeKey( key: Long, out: DataOutput ) { out.writeLong( key )}
}
