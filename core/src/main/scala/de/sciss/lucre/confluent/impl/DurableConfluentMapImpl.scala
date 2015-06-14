/*
 *  DurableConfluentMapImpl.scala
 *  (LucreConfluent)
 *
 *  Copyright (c) 2009-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.confluent
package impl

import de.sciss.lucre.stm.DataStore
import de.sciss.serial.{DataInput, Serializer, DataOutput, ImmutableSerializer}

import scala.annotation.switch

object DurableConfluentMapImpl {
  private sealed trait     Entry      [S <: Sys[S], A]
  private final case class EntryPre   [S <: Sys[S], A](hash: Long) extends Entry[S, A]
  private final case class EntrySingle[S <: Sys[S], A](term: Long, v: A) extends Entry[S, A]
  private final case class EntryMap   [S <: Sys[S], A](m: IndexMap[S, A]) extends Entry[S, A]

  private final class WithPrefix[A](val len: Int, val term: Long, val value: A)
}

// XXX boom! specialized
sealed trait DurableConfluentMapImpl[S <: Sys[S], K] extends DurablePersistentMap[S, K] {
  import DurableConfluentMapImpl._

  protected def store: DataStore
  protected def handler: IndexMapHandler[S]
  protected def isOblivious: Boolean

  protected def writeKey(key: K, out: DataOutput): Unit

  final def isFresh(key: K, path: S#Acc)(implicit tx: S#Tx): Boolean = {
    store.get { out =>
      writeKey(key, out)
      out.writeLong(path.indexSum)
    } { in =>
      (in.readByte(): @switch) match {
        case 1 => true // a single value is found
        case 2 => true // a map is found
        case _ => false // only a partial hash is found
      }
    } getOrElse false
  }

  // XXX boom! specialized
  final def putImmutable[ /* @spec(ValueSpec) */ A](key: K, path: S#Acc, value: A)
                                          (implicit tx: S#Tx, ser: ImmutableSerializer[A]): Unit = {
    val (index, term) = path.splitIndex
    // first we need to see if anything has already been written to the index of the write path
    store.flatGet { out =>
      writeKey(key, out)
      out.writeLong(index.sum)
    } { in =>
      (in.readByte(): @switch) match {
        case 1 =>
          // a single 'root' value is found. extract it for successive re-write.
          val term2 = in.readLong()
          val prev = ser.read(in)
          Some(EntrySingle(term2, prev))
        case 2 =>
          // there is already a map found
          val m = handler.readIndexMap[A](in, index)
          Some(EntryMap(m))
        case _ => None // this would be a partial hash which we don't use
      }
    } match {
      // with the previous entry read, react as follows:
      // if there is a single entry, construct a new ancestor.map with the
      // entry's value taken as root value
      case Some(EntrySingle(prevTerm, prevValue)) =>
        putFullMap[A](key, index, term, value, prevTerm, prevValue)
      // if there is an existing map, simply add the new value to it
      case Some(EntryMap(m)) =>
        m.add(term, value)
      // if there is no previous entry...
      case _ =>
        // we may write a single entry if and only if the value can be seen
        // as a root value (the write path corresponds to the construction
        // of the entity, e.g. path == <term, term>; or the entity was
        // re-written in the tree root, hence path.suffix == <term, term>)
        val indexTerm = index.term
        if (term == indexTerm) {
          putPartials(key, index)
          putFullSingle[A](key, index, term, value)
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
          getImmutable[A](key, path) match {
            case Some(prevValue) =>
              putPartials(key, index)
              putFullMap   [A](key, index, term, value, indexTerm, prevValue)

            case _ =>
              putPartials(key, index)
              putFullSingle[A](key, index, term, value)
          }
        }
    }
  }

  final def put[A](key: K, path: S#Acc, value: A)(implicit tx: S#Tx, ser: Serializer[S#Tx, S#Acc, A]): Unit = {
    val (index, term) = path.splitIndex
    val arrOut  = DataOutput()
    ser.write(value, arrOut)
    val arr     = arrOut.toByteArray
    // first we need to see if anything has already been written to the index of the write path
    store.flatGet { out =>
      writeKey(key, out)
      out.writeLong(index.sum)
    } { in =>
      (in.readByte(): @switch) match {
        case 1 =>
          // a single 'root' value is found. extract it for successive re-write.
          val term2 = in.readLong()
          val arr   = ByteArraySerializer.read(in)
          // val prev = ser.read(in)
          Some(EntrySingle(term2, arr /* prev */))
        case 2 =>
          // there is already a map found
          val m = handler.readIndexMap[Array[Byte]/* A */](in, index)(tx, ByteArraySerializer)
          Some(EntryMap(m))
        case _ => None // this would be a partial hash which we don't use
      }
    } match {
      // with the previous entry read, react as follows:
      // if there is a single entry, construct a new ancestor.map with the
      // entry's value taken as root value
      case Some(EntrySingle(prevTerm, prevArr)) =>
        putFullMap[Array[Byte]](key, index, term, arr, prevTerm, prevArr)(tx, ByteArraySerializer)
      // if there is an existing map, simply add the new value to it
      case Some(EntryMap(m)) =>
        m.add(term, arr)
      // if there is no previous entry...
      case _ =>
        // we may write a single entry if and only if the value can be seen
        // as a root value (the write path corresponds to the construction
        // of the entity, e.g. path == <term, term>; or the entity was
        // re-written in the tree root, hence path.suffix == <term, term>)
        val indexTerm = index.term
        if (term == indexTerm) {
          putPartials(key, index)
          putFullSingle[Array[Byte]](key, index, term, arr)(tx, ByteArraySerializer)
          // otherwise, we must read the root value for the entity, and then
          // construct a new map containing that root value along with the
          // new value
        } else {
          val prevAccI  = path.index
          val prevAcc   = prevAccI :+ prevAccI.last   // path where term is replaced by index-term (tree root)
          val prevOpt   = get[A](key, prevAcc)
          // println(s"prev value for new full tree, input = $prevAcc; path = $path; prev = $prevOpt")
          prevOpt match {
            case Some(prevValue) =>
              // re-serialize previous value
              arrOut.reset()
              ser.write(prevValue, arrOut)
              val prevArr = arrOut.toByteArray
              putPartials(key, index)
              putFullMap   [Array[Byte]](key, index, term, arr, indexTerm, prevArr)(tx, ByteArraySerializer)

            case _ =>
              putPartials(key, index)
              putFullSingle[Array[Byte]](key, index, term, arr                    )(tx, ByteArraySerializer)
          }
        }
    }
  }

  private[this] def putFullMap[A](key: K, index: S#Acc, term: Long, value: A, prevTerm: Long, prevValue: A)
                                 (implicit tx: S#Tx, ser: ImmutableSerializer[A]): Unit = {
    //         require( prevTerm != term, "Duplicate flush within same transaction? " + term.toInt )
    //         require( prevTerm == index.term, "Expected initial assignment term " + index.term.toInt + ", but found " + prevTerm.toInt )
    // create new map with previous value
    val m = handler.newIndexMap[A](index, prevTerm, prevValue)
    // store the full value at the full hash (path.sum)
    store.put { out =>
      writeKey(key, out) // out.writeInt( key )
      out.writeLong(index.sum)
    } { out =>
      out.writeByte(2) // aka map entry
      m.write(out)
    }
    // then add the new value
    m.add(term, value)
  }

  def remove(key: K, index: S#Acc)(implicit tx: S#Tx): Boolean = {
    Hashing.foreachPrefix(index, hash => {
      store.contains { out =>
        writeKey(key, out)
        out.writeLong(hash)
      }
    }) {
      case (hash, _) => store.remove { out =>
        writeKey(key, out)
        out.writeLong(hash)
      }
    }
    store.remove { out =>
      writeKey(key, out)
      out.writeLong(index.sum)
    }
  }

  // stores the prefixes
  private[this] def putPartials(key: K, index: S#Acc)(implicit tx: S#Tx): Unit =
    Hashing.foreachPrefix(index, hash => {
      val res = store.contains { out =>
        writeKey(key, out)
        out.writeLong(hash)
      }
      res
    }) {
      // for each key which is the partial sum, we store preSum which is the longest prefix of \tau' in \Pi
      case (hash, preSum) => store.put { out =>
        writeKey(key, out) // out.writeInt( key )
        out.writeLong(hash)
      } { out =>
        out.writeByte(0) // aka entry pre
        out.writeLong(preSum)
      }
    }

  // store the full value at the full hash (path.sum)
  private[this] def putFullSingle[A](key: K, index: S#Acc, term: Long, value: A)
                                    (implicit tx: S#Tx, ser: ImmutableSerializer[A]): Unit =
    store.put { out =>
      writeKey(key, out)
      out.writeLong(index.sum)
    } { out =>
      out.writeByte(1) // aka entry single
      out.writeLong(term)
      ser.write(value, out)
    }

  final def getImmutable[A](key: K, path: S#Acc)(implicit tx: S#Tx, ser: ImmutableSerializer[A]): Option[A] = {
    if (path.isEmpty) return None
    val (maxIndex, maxTerm) = path.splitIndex
    getWithPrefixLen[A](key, maxIndex, maxTerm).map(_.value)
  }

  final def get[A](key: K, path: S#Acc)(implicit tx: S#Tx, ser: Serializer[S#Tx, S#Acc, A]): Option[A] = {
    if (path.isEmpty) return None
    val (maxIndex, maxTerm) = path.splitIndex
    val opt = getWithPrefixLen[Array[Byte]](key, maxIndex, maxTerm)(tx, ByteArraySerializer)
    opt.map { wp =>
      //            (path.dropAndReplaceHead( preLen, writeTerm ), value)
      val access  = wp.term +: path.drop(wp.len)
      val in      = DataInput(wp.value)
      ser.read(in, access)
    }
  }

  private[this] def getWithPrefixLen[A](key: K, maxIndex: S#Acc, maxTerm: Long)
                                       (implicit tx: S#Tx, ser: ImmutableSerializer[A]): Option[WithPrefix[A]] = {
    val preLen = Hashing.maxPrefixLength(maxIndex, hash => store.contains { out =>
      writeKey(key, out)
      out.writeLong(hash)
    })
    val (index, term) = if (preLen == maxIndex.size) {
      // maximum prefix lies in last tree
      (maxIndex, maxTerm)
    } else {
      // prefix lies in other tree
      maxIndex.splitAtIndex(preLen)
    }
    val preSum = index.sum
    store.flatGet { out =>
      writeKey(key, out)
      out.writeLong(preSum)
    } { in =>
      (in.readByte(): @switch) match {
        case 0 => // partial hash
          val hash = in.readLong()
          //                  EntryPre[ S ]( hash )
          val (fullIndex, fullTerm) = maxIndex.splitAtSum(hash)
          getWithPrefixLen[A](key, fullIndex, fullTerm)

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

          // --- ADDENDUM 2 (23-Oct-12): the above addendum fails for event variables which may well be
          // uninitialised. Therefore go back to the original thought...

          val term2 = in.readLong()
          val isOk  = if (isOblivious) {
            handler.isAncestor(/* index, */ term2, term)
          } else true

          if (isOk) {
            val value = ser.read(in)
            Some(new WithPrefix(preLen, term2, value))
          } else {
            None
          }

        case 2 =>
          val m = handler.readIndexMap[A](in, index)
          if (isOblivious) {
            m.nearestOption(term).map {
              case (term2, value) => new WithPrefix(preLen, term2, value)
            }

          } else {
            val (term2, value) = m.nearest(term)
            Some(new WithPrefix(preLen, term2, value))
          }
      }
    }
  }
}

final class ConfluentIntMapImpl[S <: Sys[S]](protected val store: DataStore, protected val handler: IndexMapHandler[S],
                                             protected val isOblivious: Boolean)
  extends DurableConfluentMapImpl[S, Int] {

  protected def writeKey(key: Int, out: DataOutput): Unit = out.write(key)
}

final class ConfluentLongMapImpl[S <: Sys[S]](protected val store: DataStore, protected val handler: IndexMapHandler[S],
                                              protected val isOblivious: Boolean)
  extends DurableConfluentMapImpl[S, Long] {

  protected def writeKey(key: Long, out: DataOutput): Unit = out.writeLong(key)
}