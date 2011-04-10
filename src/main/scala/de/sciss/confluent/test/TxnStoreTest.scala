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