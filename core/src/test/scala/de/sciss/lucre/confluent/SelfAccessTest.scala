//package de.sciss.lucre
//package confluent
//
//import stm.{Disposable, Durable, InMemory, Serializer}
//import java.util.concurrent.{TimeUnit, Executors, ScheduledExecutorService}
//import concurrent.stm.Txn
//import stm.impl.BerkeleyDB
//import java.io.File
//
//object SelfAccessTest extends App {
////   inMem()
////   dur()
//   conf()
//
//   def inMem(): Unit = {
//      type S = InMemory
//      implicit val sys = InMemory()
//      implicit val cSer = Counter.serializer[ S ]
//      val access = sys.root[ Counter[ S ]]( implicit tx => Counter() )
//      new SelfAccessTest( access )
//   }
//
//   private def tmpDir() : File = {
//      val f = File.createTempFile( "database", "db" )
//      f.delete()
//      f.mkdir()
//      f
//   }
//
//   private def durFact() = BerkeleyDB.factory( tmpDir(), createIfNecessary = true )
//
//   def dur(): Unit = {
//      type S = Durable
//      implicit val sys = Durable( durFact() )
//      implicit val cSer = Counter.serializer[ S ]
//      val access = sys.root[ Counter[ S ]]( implicit tx => Counter() )
//      new SelfAccessTest( access )
//   }
//
//   def conf(): Unit = {
//      type S = Confluent
//      implicit val sys = Confluent( durFact() )
////      LucreConfluent.showConfluentLog = true
//      implicit val cSer = Counter.serializer[ S ]
//      val (access, cursor) = sys.cursorRoot[ Counter[ S ], stm.Cursor[ S ]] { implicit tx =>
//         implicit val csr = tx.newCursor()
//         Counter()
//      } { _: S#Tx => _.cursor }
//
//      new SelfAccessTest( access )( cursor )
//   }
//
//   object Counter {
//      implicit def serializer[ S <: stm.Sys[ S ]]( implicit cursor: stm.Cursor[ S ]) : Serializer[ S#Tx, S#Acc, Counter[ S ]] = new Ser // ( cursor )
//
//      def apply[ S <: stm.Sys[ S ]]()( implicit tx: S#Tx, _cursor: stm.Cursor[ S ]) : Counter[ S ] = {
//         val res = new Impl {
//            val id      = tx.newID()
//            val cnt     = tx.newIntVar( id, 0 )
//            val play    = tx.newBooleanVar( id, init = false )
//            val cursor  = _cursor
////            val csrPos  = cursor.position
//            val me: stm.Source[ S#Tx, Counter ] = tx.newHandle( this: Counter )
////            val self = tx.newVar[ Counter ]( id, null )
////            self.set( this )
//         }
////if( map == null ) map = tx.newDurableIDMap[ Counter ]
////map.put( res.id, res )
//         res
//      }
//
////private var map: IdentifierMap[ S#Tx, S#ID, Counter ] = null
//
//      private final class Ser[ S <: stm.Sys[ S ]]( implicit cursor: stm.Cursor[ S ]) extends Serializer[ S#Tx, S#Acc, Counter[ S ]] {
//         ser =>
//
//         def write( c: Counter[ S ], out: DataOutput ): Unit = {
////            if( c == null ) {
////               out.writeUnsignedByte( 0 )
////            } else {
//               c.write( out )
////            }
//         }
//         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Counter = {
////            if( in.readUnsignedByte() == 0 ) return null
//            new Impl {
//               val id      = tx.readID( in, access )
//               val cnt     = tx.readIntVar( id, in )
//               val play    = tx.readBooleanVar( id, in )
//               val csr     = cursor
////               val csrPos  = csr.position
//               val me: stm.Source[ S#Tx, Counter ] = tx.newHandle( this: Counter )
////               val self    = tx.readVar[ Counter ]( id, in )( ser )
//            }
//         }
//      }
//
//      private abstract class Impl[ S <: stm.Sys[ S ]]
//      extends Counter[ S ] with Runnable {
////         me =>
//
//         protected def me: stm.Source[ S#Tx, Counter ]
//
//         def id: S#ID
//         protected def cnt: S#Var[ Int ]
//         protected def play: S#Var[ Boolean ]
////         protected def csrPos: S#Acc
////         protected def self: S#Var[ Counter ]
//
//         override def toString = "Counter" + id
//
//         final def write( out: DataOutput ): Unit = {
////            out.writeUnsignedByte( 1 )
//            id.write( out )
//            cnt.write( out )
//            play.write( out )
////            self.write( out )
//         }
//
//         final def dispose()( implicit tx: S#Tx ): Unit = {
//            id.dispose()
//            cnt.dispose()
//            play.dispose()
////            self.dispose()
//         }
//
//         final def run(): Unit = {
//            cursor.step { implicit tx =>
////               val icke = self.get
////               val ickeO = map.get( id )
////               val icke = tx.access( self )
//               val icke = me.get // tx.refresh[ Counter ]( csrPos, me )
////               println( "...run " + tx + " -> " + icke )
//               icke.step()
//            }
//         }
//
//         final def step()( implicit tx: S#Tx ): Unit = {
//            val p = play.get
////            println( "Step in " + tx + " found " + p )
//            if( p ) {
//               cnt.transform( _ + 1 )
//               implicit val itx = tx.peer
//               Txn.beforeCommit { implicit itx =>
//                  val v = value()
//                  Txn.afterCommit { _ =>
//                     println( "Count of " + me + " = " + v )
//                  }
//               }
//               spawn()
//            }
//         }
//
//         final def start()( implicit tx: S#Tx ): Unit = {
//            val wasPlaying = play.get
//            if( !wasPlaying ) {
////               println( "Setting in " + tx + " play = true " )
//               play.set( true )
//               spawn()
//            }
//         }
//
//         final def stop()( implicit tx: S#Tx ): Unit = {
//            val wasPlaying = play.get
//            if( wasPlaying ) {
//               play.set( false )
//            }
//         }
//
//         private def spawn()( implicit tx: S#Tx ): Unit = {
//            implicit val itx = tx.peer
//            Txn.afterCommit { _ =>
//               pool.schedule( this, 1, TimeUnit.SECONDS )
//            }
//         }
//
//         final def value()( implicit tx: S#Tx ) : Int = cnt.get
//      }
//   }
//   sealed trait Counter[ S <: stm.Sys[ S ]] extends Writable with Disposable[ S#Tx ] {
//      def cursor: stm.Cursor[ S ]
//
//      def start()( implicit tx: S#Tx ) : Unit
//      def stop()( implicit tx: S#Tx ) : Unit
//      def value()( implicit tx: S#Tx ) : Int
//
//      // quasi-private
//      def step()( implicit tx: S#Tx ) : Unit
//   }
//}
//class SelfAccessTest[ S <: stm.Sys[ S ]]( access: S#Entry[ SelfAccessTest.Counter[ S ]])( implicit cursor: stm.Cursor[ S ]) {
//   lazy val pool : ScheduledExecutorService = Executors.newScheduledThreadPool( 1 ) // > 0 to prevent immediate VM shutdown
//
//   println( "Start" )
//
////   val (access, cursor) = system.root( implicit tx => Counter.apply()( tx, createCursor( tx )))( (_: S#Tx) => (cnt: Counter) => cnt.cursor )
////   cursor.step { implicit tx =>
////      access.get.start()
////   }
//
//   cursor.step { implicit tx => access.get.start() }
//
//   pool.schedule( new Runnable {
//      def run(): Unit = {
//         cursor.step { implicit tx =>
//            implicit val itx = tx.peer
//            val c = access.get
//            val v = c.value()
//            Txn.afterCommit { _ =>
//               println( "Stop. Last value was " + v )
//               pool.shutdown()
//               sys.exit( 0 )
//            }
//            c.stop()
//         }
//      }
//   }, 10, TimeUnit.SECONDS )
//}