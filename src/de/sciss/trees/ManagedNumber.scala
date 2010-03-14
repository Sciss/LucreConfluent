package de.sciss.trees

/**
 *  @version  0.12, 14-Mar-10
 */
@specialized trait ManagedNumber[ N ] {
	@inline def <( x: N ) : Boolean
	@inline def >( x: N ) : Boolean
	@inline def +( x: N ) : N
	@inline def -( x: N ) : N
	@inline def *( x: N ) : N
	@inline def /( x: N ) : N
	@inline def abs : N
	@inline def min( x: N ) : N
	@inline def max( x: N ) : N
}

@specialized trait NumberManager[ N ] {
	@inline val zero : N
	@inline val one : N
	@inline val min : N
	@inline val max : N
}

class ManagedInt( i: Int ) extends ManagedNumber[ Int ] {
	@inline def <( x: Int ) = i < x
	@inline def >( x: Int ) = i > x
	@inline def +( x: Int ) = i + x
	@inline def -( x: Int ) = i - x
	@inline def *( x: Int ) = i * x
	@inline def /( x: Int ) = i / x
	@inline def abs = Math.abs( i )
	@inline def min( x: Int ) = Math.min( i, x )
	@inline def max( x: Int ) = Math.max( i, x )
}

class ManagedLong( n: Long) extends ManagedNumber[ Long ] {
	@inline def <( x: Long ) = n < x
	@inline def >( x: Long ) = n > x
	@inline def +( x: Long ) = n + x
	@inline def -( x: Long ) = n - x
	@inline def *( x: Long ) = n * x
	@inline def /( x: Long ) = n / x
	@inline def abs = Math.abs( n )
	@inline def min( x: Long ) = Math.min( n, x )
	@inline def max( x: Long ) = Math.max( n, x )
}

class ManagedDouble( d: Double ) extends ManagedNumber[ Double ] {
	@inline def <( x: Double ) = d < x
	@inline def >( x: Double ) = d > x
	@inline def +( x: Double ) = d + x
	@inline def -( x: Double ) = d - x
	@inline def *( x: Double ) = d * x
	@inline def /( x: Double ) = d / x
	@inline def abs = Math.abs( d )
	@inline def min( x: Double ) = Math.min( d, x )
	@inline def max( x: Double ) = Math.max( d, x )
}

object IntManager extends NumberManager[ Int ] {
	@inline val zero = 0
	@inline val one = 1
	@inline val min = Int.MinValue
	@inline val max = Int.MaxValue
}

object LongManager extends NumberManager[ Long ] {
	@inline val zero = 0L
	@inline val one = 1L
	@inline val min = Long.MinValue
	@inline val max = Long.MaxValue
}

object DoubleManager extends NumberManager[ Double ] {
	@inline val zero = 0.0
	@inline val one = 1.0
	@inline val min = Double.MinValue
	@inline val max = Double.MaxValue
}

object Implicits {
	implicit def managedInt( i: Int ) = new ManagedInt( i )
	implicit val intManager = IntManager
}
