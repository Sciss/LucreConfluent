package de.sciss.confluent

trait NodeAccess[ T ] {
   def access( readPath: Path, writePath: Path ) : T
}

trait NodeID[ T ] extends NodeAccess[ T ] {
   protected def readPath: Path
//   protected def writePath: Path
   protected def nodeAccess: NodeAccess[ T ]

   def access( acReadPath: Path, acWritePath: Path ): T = {
      val p    = readPath
      val sp1  = p( p.length - 2 )
      val rpd  = acReadPath.dropWhile( _ != sp1 )
      val rpn  = if( rpd.length == 2 ) {
         p.dropRight( 2 ) ++ rpd
      } else {
//         p = <v0, v1>
//         ac = <v0, v0, v2, v2>
//         tgt = <v0, v1, v2, v2>  right?
         p ++ rpd.drop( 2 )
      }
      val wpd  = acWritePath.dropWhile( _ != sp1 )
      val wpn  = if( wpd.length == 2 ) {
         p.dropRight( 2 ) ++ wpd
      } else {
         p ++ wpd.drop( 2 )
      }
      nodeAccess.access( rpn, wpn )
   }
}

case class Handle[ T ]( protected val nodeAccess: NodeAccess[ T ], seminalPath: Path )
extends NodeID[ T ] {
   protected def readPath = seminalPath
//   // lazy access
//   def access( readPath: Path, writePath: Path ): T = {
//      // first simple implementation : assume read and write paths
//      // contain the seminal path
//      val sp0 = seminalPath( 0 )
//      val rpd = readPath.dropWhile(  _ != sp0 )
//      val wpd = writePath.dropWhile( _ != sp0 )
//      node.access( rpd, wpd )
//   }
}