//package de.sciss.temporal
//
///**
// *    @version 0.10, 30-Mar-10
// */
//trait TransactionContext {
//   def created( a: AnyRef ) : Unit
//}
//
//object DummyTransactionContext extends TransactionContext {
//   def created( a: AnyRef ) {}
//}
//
//class MeldTransactionContext extends TransactionContext {
//   private var collCreated: List[ AnyRef ] = Nil
//
//   def created( a: AnyRef ) {
//      collCreated ::= a
//   }
//
//   def createdObjects: List[ AnyRef ] = collCreated
//}