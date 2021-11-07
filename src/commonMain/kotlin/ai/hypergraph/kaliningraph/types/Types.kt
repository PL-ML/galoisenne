package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.times

/** Corecursive Fibonacci sequence of [Nat]s **/
tailrec fun <T, R: Nat<T, R>> Nat<T, R>.fibonacci(
  n: T,
  seed: Pair<T, T> = nil to one,
  fib: (Pair<T, T>) -> Pair<T, T> = { (a, b) -> b to a + b },
  i: T = nil,
): T =
  if (i == n) fib(seed).first
  else fibonacci(n = n, seed = fib(seed), i = i.next())

/** Returns [n]! **/
fun <T, R: Nat<T, R>> Nat<T, R>.factorial(n: T): T = prod(seq(to = n.next()))

/** Returns a sequence of [Nat]s starting from [from] until [to] **/
tailrec fun <T, R: Nat<T, R>> Nat<T, R>.seq(
  from: T = one, to: T,
  acc: Set<T> = emptySet()
): Set<T> = if (from == to) acc else seq(from.next(), to, acc + from)

/** Returns true iff [t] is prime **/
fun <T, R: Nat<T, R>> Nat<T, R>.isPrime(t: T, kps: Set<T> = emptySet()): Boolean =
  // Take Cartesian product, filter distinct pairs due to commutativity
  (if (kps.isNotEmpty()) kps * kps else seq(to = t) * seq(to = t))
    .distinctBy { (l, r) -> setOf(l, r) }
    .all { (i, j) -> if (i == one || j == one) true else i * j != t }

/** Returns [total] prime [Nat]s **/
tailrec fun <T, R: Nat<T, R>> Nat<T, R>.primes(
  total: T, // total number of primes
  i: T = nil, // counter
  c: T = one.next(), // prime candidate
  kps: Set<T> = emptySet() // known primes
): Set<T> =
  when {
    i == total -> kps
    isPrime(c) -> primes(total, i.next(), c.next(), kps + c)
    else -> primes(total, i, c.next(), kps)
  }

/** Returns the sum of two [Nat]s **/
tailrec fun <T, R: Nat<T, R>> Nat<T, R>.plus(l: T, r: T, acc: T = l, i: T = nil): T =
  if (i == r) acc else plus(l, r, acc.next(), i.next())

/** Returns the product of two [Nat]s **/
tailrec fun <T, R: Nat<T, R>> Nat<T, R>.times(l: T, r: T, acc: T = nil, i: T = nil): T =
  if (i == r) acc else times(l, r, acc + l, i.next())

tailrec fun <T, R: Nat<T, R>> Nat<T, R>.pow(base: T, exp: T, acc: T = one, i: T = one): T =
  if (i == exp) acc else pow(base, exp, acc * base, i.next())

fun <T, R: Nat<T, R>> Nat<T, R>.sum(list: Iterable<T>): T = list.reduce { acc, t -> acc + t }

fun <T, R: Nat<T, R>> Nat<T, R>.prod(list: Iterable<T>): T = list.reduce { acc, t -> (acc * t) }

interface Nat<T, R: Nat<T, R>> {
  val nil: T
  val one: T get() = nil.next()

  fun T.next(): T

  // TODO: implement pred, minus?
  // https://en.wikipedia.org/wiki/Church_encoding#Derivation_of_predecessor_function
  operator fun T.plus(t: T) = plus(this, t)
  operator fun T.times(t: T) = times(this, t)
  infix fun T.pow(t: T) = pow(this, t)
  class of<T>(override val nil: T, val vnext: T.() -> T): Nat<T, of<T>> {
    override fun T.next(): T = vnext()
  }
}

interface Group<T, R: Group<T, R>>: Nat<T, R> {
  override fun T.next(): T = this + one
  override fun T.plus(t: T): T

  class of<T>(
    override val nil: T, override val one: T,
    val plus: (T, T) -> T
  ): Group<T, of<T>> {
    override fun T.plus(t: T) = plus(this, t)
  }
}

interface Ring<T, R: Ring<T, R>>: Group<T, R> {
  override fun T.plus(t: T): T
  override fun T.times(t: T): T

  class of<T>(
    override val nil: T, override val one: T,
    val plus: (T, T) -> T,
    val times: (T, T) -> T
  ): Ring<T, of<T>> {
    override fun T.plus(t: T) = plus(this, t)
    override fun T.times(t: T) = times(this, t)
  }
}

@Suppress("NO_TAIL_CALLS_FOUND")
/** Returns the result of subtracting two [Field]s **/
tailrec fun <T, R: Field<T, R>> Field<T, R>.minus(l: T, r: T, acc: T = nil, i: T = nil): T = TODO()

@Suppress("NO_TAIL_CALLS_FOUND")
/** Returns the result of dividing of two [Field]s **/
tailrec fun <T, R: Field<T, R>> Field<T, R>.div(l: T, r: T, acc: T = l, i: T = nil): T = TODO()

interface Field<T, R: Field<T, R>>: Ring<T, R> {
  operator fun T.minus(t: T): T = minus(this, t)
  operator fun T.div(t: T): T = div(this, t)
  class of<T>(
    override val nil: T, override val one: T,
    val plus: (T, T) -> T,
    val times: (T, T) -> T,
    val minus: (T, T) -> T,
    val div: (T, T) -> T
  ): Field<T, of<T>> {
    override fun T.plus(t: T) = plus(this, t)
    override fun T.times(t: T) = times(this, t)
    override fun T.minus(t: T) = minus(this, t)
    override fun T.div(t: T) = div(this, t)
  }
}

interface Vector<T> {
  val ts: List<T>

  fun vmap(map: (T) -> T) = of(ts.map { map(it) })

  fun zip(other: Vector<T>, merge: (T, T) -> T) =
    of(ts.zip(other.ts).map { (a, b) -> merge(a, b) })

  class of<T>(override val ts: List<T>): Vector<T> {
    constructor(vararg ts: T): this(ts.toList())

    override fun toString() =
      ts.joinToString(",", "${ts::class.simpleName}[", "]")
  }
}

interface VectorField<T, F: Field<T, F>> {
  val f: F
  operator fun Vector<T>.plus(vec: Vector<T>): Vector<T> = zip(vec) { a, b -> with(f) { a + b } }
  infix fun T.dot(p: Vector<T>): Vector<T> = p.vmap { f.times(it, this) }
  class of<T, F: Field<T, F>>(override val f: F): VectorField<T, F>
}

// TODO: Clifford algebra?

// http://www.math.ucsd.edu/~alina/oldcourses/2012/104b/zi.pdf
data class GaussInt(val a: Int, val b: Int) {
  operator fun plus(o: GaussInt): GaussInt = GaussInt(a + o.a, b + o.b)
  operator fun times(o: GaussInt): GaussInt =
    GaussInt(a * o.a - b * o.b, a * o.b + b * o.a)
}

class Rational {
  constructor(i: Int, j: Int = 1) {
    if (j == 0) throw ArithmeticException("Denominator must not be zero!")
    canonicalRatio = reduce(i, j)
    a = canonicalRatio.first
    b = canonicalRatio.second
  }

  private val canonicalRatio: Pair<Int, Int>

  /**
   * TODO: Use [Nat] instead?
   */
  val a: Int
  val b: Int

  operator fun times(r: Rational) = Rational(a * r.a, b * r.b)

  operator fun plus(r: Rational) = Rational(a * r.b + r.a * b, b * r.b)

  operator fun minus(r: Rational) = Rational(a * r.b - r.a * b, b * r.b)

  operator fun div(r: Rational) = Rational(a * r.b, b * r.a)

  override fun toString() = "$a/$b"
  override fun equals(other: Any?) =
    (other as? Rational).let { a == it!!.a && b == it.b }

  override fun hashCode() = toString().hashCode()

  companion object {
    val ZERO = Rational(0, 1)
    val ONE = Rational(1, 1)
    fun reduce(a: Int, b: Int) = Pair(a / a.gcd(b), b / a.gcd(b))

    // https://github.com/binkley/kotlin-rational/blob/61be6f7df2d579ad83c6810a5f9426a4478b99a2/src/main/kotlin/hm/binkley/math/math-functions.kt#L93
    private tailrec fun Int.gcd(that: Int): Int = when {
      this == that -> this
      this in 0..1 || that in 0..1 -> 1
      this > that -> (this - that).gcd(that)
      else -> gcd(that - this)
    }
  }
}
