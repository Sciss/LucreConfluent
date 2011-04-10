package de.sciss.confluent.test

import concurrent.stm.{InTxn, TxnLocal, Ref}

object TxnStoreTest {
   type Path   = IndexedSeq[ Int ]
   type M[ A ] = Map[ Path, A ]

   trait MyRef[ A ] {
      private val perm = Ref.make[ M[ A ]] // ... or initial
      private val temp = TxnLocal.apply[ M[ A ]]( beforeCommit = persist( _ ))

      def get( path: Path )( implicit txn: InTxn ) : Option[ A ] = temp.get.get( path ).orElse( perm.get.get( path ))
      def set( path: Path, v: A )( implicit txn: InTxn ) : Unit = temp.transform( _ + (path -> v) )

      private def persist( implicit txn: InTxn ) {
         perm.transform( _ ++ temp.get )
      }
   }

   trait Persist { def persist( implicit txn: InTxn ) : Unit }

   case class Compound[ A ]( perm: Map[ Path, A ] = Map.empty[ Path, A ], temp: Map[ Path, A ] = Map.empty[ Path, A ])

   trait TxnWrapper {
      private val ps = TxnLocal( Set.empty[ Persist ], beforeCommit = persistAll( _ ))

      def txn: InTxn
      def addPersist( p: Persist ) = ps.transform( _ + p )( txn )

      private def persistAll( implicit txn: InTxn ) {
         ps.get.foreach( _.persist( txn ))
      }
   }

   trait MyRef2[ A ] extends Persist {
      me =>

      private val impl = {
         val c = Compound[ A ]()
         Ref( c )
      }

      def get( path: Path )( implicit w: TxnWrapper ) : Option[ A ] = {
         implicit val txn = w.txn
         val c = impl.get
         c.temp.get( path ).orElse( c.perm.get( path ))
      }
      def set( path: Path, v: A )( implicit w: TxnWrapper ) {
         implicit val txn = w.txn
         impl.transform { c =>
            val add = c.temp.isEmpty
            val res = c.copy( temp = c.temp + (path -> v) )
            if( add ) w.addPersist( this )
            res
         }
      }

      def persist( implicit txn: InTxn ) {
         impl.transform( c => c.copy( perm = c.perm ++ c.temp, temp = Map.empty ))
      }
   }
}

object TxnStoreTest2 {
   trait MyRefLike[ K, V ] {
      def get( k: K )( implicit txn: InTxn ) : Option[ V ]
      def put( k: K, v: V )( implicit txn: InTxn ) : Unit
   }

   trait MyPersistableRef[ K, V ] extends MyRefLike[ K, V ] {
      def putAllNoCache( pairs: (K, V)* )( implicit txn: InTxn ) : Unit
   }

   trait MyRef[ K, V ] extends MyPersistableRef[ K, V ] {
      def cache : MyRefLike[ K, V ]
      private val r = Ref.make[ Map[ K, V ]]
      def get( k: K )( implicit txn: InTxn ) : Option[ V ] = cache.get( k ).orElse( r.get.get( k ))
      def put( k: K, v: V )( implicit txn: InTxn ) : Unit = {
         cache.put( k, v )
//         r.touchWrite  // !
      }
      def putAllNoCache( pairs: (K, V)* )( implicit txn: InTxn ) {
         r.transform( _ ++ pairs )
      }
   }

   trait MyCache[ K, V ] extends MyRefLike[ K, V ] {
      private val r = TxnLocal[ Map[ K, V ]]()
      def get( k: K )( implicit txn: InTxn ) : Option[ V ] = r.get.get( k )
      def put( k: K, v: V )( implicit txn: InTxn ) : Unit = r.transform( _ + (k ->v) )
      def flush( store: MyPersistableRef[ K, V ])( implicit txn: InTxn ) {
         val map = r.swap( Map.empty )
         store.putAllNoCache( map.toSeq: _* )
      }
   }
}