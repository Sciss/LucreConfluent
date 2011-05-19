package de.sciss.confluent.test

class Precalc {
   /*

   e.g. 0 1 2 3 4

   0
   0 1
   0 1 2
   0 1 2 3
   0 1 2 3 4
   1
   1 2
   1 2 3
   1 2 3 4
   0 2
   0 2 3
   0 2 3 4
   0 1 3
   0 1 3 4
   0 1 2 4
   2
   2 3
   2 3 4
   0 3
   0 3 4
   0 1 4
   3
   3 4
   0 4
   4
   0 2 4
   // sind das alle?
   // das waeren 26 tests -- also quasi quadratisch?

   // no... for a sequence of length n, there are (1 << n) - 1 possible paths
   // (all n bit combinations except 0). thus above should be 31 combinations,
   // 5 are missing?
   // in other words -- this idea would be exhausted after a sequence length of 64 :-(

    */
}