package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.allPairs
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Generic matrix which supports overloadable addition and multiplication
 * using an abstract algebra (e.g., tropical semiring). Useful for many
 * problems in graph theory.
 *
 * @see [MatrixRing]
 */

interface Matrix<T, A : Ring<T>, M : Matrix<T, A, M>> : SparseTensor<Π3<Int, Int, T>> {
  val algebra: A
  val data: List<T>

  // TODO: Tensor stuff
  // TODO: create a constructor that takes a List<Pair<List<Int>, T>>
  //       for sparse tensors, e.g.: [([1, 2, 3], "a"), ([2, 4, 1], "b"), ...]
  override val map: MutableMap<Π3<Int, Int, T>, Int> get() = TODO()
  fun shape() = numRows cc numCols /** TODO: return [Π3] instead */
  operator fun get(r: Any, c: Any): T = TODO("Implement support for named indexing")

  val numRows: Int
  val numCols: Int

  operator fun plus(t: M): M = join(t) { i, j -> this@Matrix[i, j] + t[i, j] }
  operator fun times(t: M): M = join(t) { i, j -> this@Matrix[i] dot t.transpose[j] }
  fun <Y> map(f: (T) -> Y) = new(numRows, numCols, data.map(f) as List<T>)

  fun getElements(filterBy: (Int, Int) -> Boolean) =
    allPairs(numRows, numCols).mapNotNull { (r, c) -> if(filterBy(r, c)) this[r, c] else null }

  infix fun List<T>.dot(es: List<T>): T =
    with(algebra) { zip(es).map { (a, b) -> a * b }.reduce { a, b -> a + b } }

  // Constructs a new instance with the same concrete matrix type
  fun new(numRows: Int, numCols: Int, data: List<T>, alg: A = algebra): M
// TODO = this::class.primaryConstructor!!.call(algebra, numRows, numCols, data) as M

  fun join(
    that: M,
    ids: Set<V2<Int>> = allPairs(numRows, that.numCols),
    op: A.(Int, Int) -> T
  ): M = require(numCols == that.numRows) {
    "Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}"
  }.let { new(numRows, that.numCols, ids.map { (i, j) -> algebra.op(i, j) }) }

  operator fun get(r: Int, c: Int): T = data[r * numCols + c]
  operator fun get(r: Int): List<T> = data.toList().subList(r * numCols, r * numCols + numCols)
}

// Only include nonzero indices for sparse matrices?
val <T, A : Ring<T>, M : Matrix<T, A, M>> Matrix<T, A, M>.idxs      by cache { allPairs(numRows, numCols) }
val <T, A : Ring<T>, M : Matrix<T, A, M>> Matrix<T, A, M>.rows      by cache { data.chunked(numCols) }
val <T, A : Ring<T>, M : Matrix<T, A, M>> Matrix<T, A, M>.cols      by cache { (0 until numCols).map { c -> rows.map { it[c] } } }
val <T, A : Ring<T>, M : Matrix<T, A, M>> Matrix<T, A, M>.transpose by cache { new(numCols, numRows, cols.flatten()) }

// https://www.ijcai.org/Proceedings/2020/0685.pdf
val BOOLEAN_ALGEBRA: Ring<Boolean> =
  Ring.of(
    nil = false,
    one = true,
    plus = { a, b -> a || b },
    times = { a, b -> a && b }
  )

val XOR_ALGEBRA =
  Ring.of(
    nil = false,
    one = true,
    plus = { a, b -> a xor b },
    times = { a, b -> a and b }
  )

val INTEGER_FIELD: Field<Int> =
  Field.of(
    nil = 0,
    one = 1,
    plus = { a, b -> a + b },
    minus = { a, b -> a - b },
    times = { a, b -> a * b },
    div = { _, _ -> throw NotImplementedError("Division not defined on integer field.") }
  )

val DOUBLE_FIELD: Field<Double> =
  Field.of(
    nil = 0.0,
    one = 1.0,
    plus = { a, b -> a + b },
    minus = { a, b -> a - b },
    times = { a, b -> a * b },
    div = { a, b -> a / b }
  )

val MINPLUS_ALGEBRA: Ring<Int> =
  Ring.of(
    nil = Int.MAX_VALUE,
    one = 0,
    plus = { a, b -> min(a, b) },
    times = { a, b -> a + b }
  )

val MAXPLUS_ALGEBRA: Ring<Int> =
  Ring.of(
    nil = Int.MIN_VALUE,
    one = 0,
    plus = { a, b -> max(a, b) },
    times = { a, b -> a + b }
  )

val GF2_ALGEBRA: Ring<Int> =
  Ring.of(
    nil = 0,
    one = 1,
    plus = { a, b -> a + b % 2 },
    times = { a, b -> a * b % 2 }
  )

private fun <T> TODO_ALGEBRA(t: T): Ring<T> =
  Ring.of(
    nil = t,
    plus = { a, b -> TODO() },
    times = { a, b -> TODO() }
  )

abstract class AbstractMatrix<T, A: Ring<T>, M: AbstractMatrix<T, A, M>> constructor(
  override val algebra: A,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
): Matrix<T, A, M> {
  val values by lazy { data.toSet() }
  override val map: MutableMap<Π3<Int, Int, T>, Int> by lazy {
    idxs.fold(mutableMapOf()) { map, (r, c) ->
      val element = get(r, c)
      if (element != algebra.nil) map[Π(r, c, element)] = 1
      map
    }
  }

  override fun toString() =
    data.maxOf { it.toString().length + 2 }.let { pad ->
      data.foldIndexed("") { i, a, b ->
        a + "$b".padEnd(pad, ' ') + " " + if (i > 0 && (i + 1) % numCols == 0) "\n" else ""
      }
    }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as AbstractMatrix<*, *, *>

    if (numRows != other.numRows) return false
    if (numCols != other.numCols) return false
    if (data != other.data) return false
//    if (algebra != other.algebra) return false

    return true
  }

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + numRows
    result = 31 * result + numCols
    result = 31 * result + data.hashCode()
    result = 31 * result + algebra.hashCode()
    return result
  }
}

// A free matrix has no associated algebra by default. If you try to do math
// with the default implementation it will fail at runtime.
open class FreeMatrix<T> constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
  override val algebra: Ring<T> = TODO_ALGEBRA(data.first()),
): AbstractMatrix<T, Ring<T>, FreeMatrix<T>>(algebra, numRows, numCols, data) {
  constructor(elements: List<T>) : this(
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(algebra: Ring<T>, elements: List<T>) : this(
    algebra = algebra,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> T) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(
    algebra: Ring<T>,
    numRows: Int,
    numCols: Int = numRows,
    f: (Int, Int) -> T
  ) : this(
    algebra = algebra,
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  override fun new(numRows: Int, numCols: Int, data: List<T>, alg: Ring<T>) =
    FreeMatrix(numRows, numCols, data, algebra)

  override fun toString() =
    "\n" + cols.map { it.maxOf { "$it".length } }.let { colWidth ->
      rows.joinToString("\n") {
        it.mapIndexed { i, c -> "$c".padEnd(colWidth[i]) }.joinToString(" ")
      }
    }
}

// Concrete subclasses
open class BooleanMatrix constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Boolean>,
  override val algebra: Ring<Boolean> = BOOLEAN_ALGEBRA,
): AbstractMatrix<Boolean, Ring<Boolean>, BooleanMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Boolean>): this(
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(algebra: Ring<Boolean>, elements: List<Boolean>): this(
    algebra = algebra,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Boolean): this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(vararg rows: Short): this(rows.fold("") { a, b -> a + b })
  constructor(rows: String): this(
    rows.filter { !it.isWhitespace() }.toCharArray().let { chars ->
      val values = chars.distinct()
      require(values.size <= 2) { "Expected two values or less" }
      values.maxOrNull()!!.let { hi -> chars.map { it == hi } }
    }
  )

  constructor(
    numRows: Int,
    numCols: Int = numRows,
    values: List<Π2<Π2<Int, Int>, Boolean>>
  ): this(numRows, numCols,
    values.toMap().let { map ->
      List(numRows * numCols) { map[it / numCols to it % numCols] ?: false }
    }
  )

  // TODO: Implement Four Russians for speedy Boolean matmuls https://arxiv.org/pdf/0811.1714.pdf#page=5
  // override fun BooleanMatrix.times(t: BooleanMatrix): BooleanMatrix = TODO()

  val isFull by lazy { data.all { it } }

  companion object {
    fun grayCode(size: Int): BooleanMatrix = TODO()
    fun ones(size: Int) = BooleanMatrix(size) { _, _ -> true }
    fun zeroes(size: Int) = BooleanMatrix(size) { _, _ -> false }
    fun one(size: Int) = BooleanMatrix(size) { i, j -> i == j }
    fun random(size: Int) = BooleanMatrix(size) { _, _ -> Random.nextBoolean() }
  }

  override fun toString() = "\n" + data.foldIndexed("") { i, a, b ->
    a + (if (b) 1 else 0) + " " + if (i > 0 && (i + 1) % numCols == 0) "\n" else ""
  }

  override fun new(numRows: Int, numCols: Int, data: List<Boolean>, alg: Ring<Boolean>) =
     BooleanMatrix(numRows, numCols, data, alg)
}

open class DoubleMatrix constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Double>,
  override val algebra: Field<Double> = DOUBLE_FIELD,
): AbstractMatrix<Double, Field<Double>, DoubleMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Double>) : this(
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Double) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(vararg rows: Double) : this(rows.toList())

  operator fun minus(that: DoubleMatrix): DoubleMatrix =
    join(that) { i, j -> algebra.minus(this@DoubleMatrix[i, j], that[i][j]) }

  companion object {
    fun random(size: Int) = DoubleMatrix(size) { _, _ -> Random.nextDouble() }
  }

  override fun new(numRows: Int, numCols: Int, data: List<Double>, alg: Field<Double>) =
    DoubleMatrix(numRows, numCols, data, alg)
}

operator fun Double.times(value: DoubleMatrix): DoubleMatrix = value * this
operator fun DoubleMatrix.times(value: Double): DoubleMatrix =
  DoubleMatrix(numRows, numCols, data.map { it * value })

tailrec fun <T: FreeMatrix<S>, S> T.seekFixpoint(i: Int = 0, op: (T) -> T): T {
  val next = op(this)
  return if (this == next) next//.also { println("Converged in $i iterations") }
  else next.seekFixpoint(i + 1, op)
}

fun DoubleMatrix.toBMat(
  threshold: Double = (data.maxOf { it } + data.minOf { it }) / 2,
  partitionFn: (Double) -> Boolean = { it > threshold }
) = BooleanMatrix(numRows, numCols) { i, j -> partitionFn(get(i, j)) }

operator fun BooleanMatrix.times(mat: DoubleMatrix): DoubleMatrix = toDoubleMatrix() * mat
operator fun BooleanMatrix.plus(mat: DoubleMatrix): DoubleMatrix = toDoubleMatrix() + mat
operator fun DoubleMatrix.minus(mat: BooleanMatrix): DoubleMatrix = this - mat.toDoubleMatrix()
fun BooleanMatrix.toDoubleMatrix() = DoubleMatrix(numRows, numCols) { i, j -> if (get(i, j)) 1.0 else 0.0 }

/**cf. [P]*/
// Alternatively: a length-2ⁿ array which can be "parsed" into a certain shape?
// See: http://conal.net/talks/can-tensor-programming-be-liberated.pdf
interface SparseTensor<T/*Should be a named tuple or dataclass of some kind*/> {
// TODO: Precompute specific [Borel] subsets of T's attributes that we expect to be queried at runtime
// e.g., (n-1)-D slices and 1D fibers
// https://mathoverflow.net/questions/393427/generalization-of-sinkhorn-s-theorem-to-stochastic-tensors
// private val marginals: MutableMap<List<T>, Int> = mutableMapOf()
  val map: MutableMap<T, Int>
  operator fun get(t: T) = map.getOrElse(t) { 0 }

//  TODO: Support mutability but also map-reduce-ability/merge-ability for parallelism
//  operator fun plus(other: SparseTensor<T>) = SparseTensor(map = this.map + other.map)
//  operator fun MutableMap<T, Int>.plus(map: MutableMap<T, Int>): MutableMap<T, Int> =
//    HashMap(this).apply { map.forEach { (k, v) -> merge(k, v, Int::plus) } }

  operator fun set(index: T, i: Int) { map[index] = i }

  fun count(selector: (T) -> Boolean) =
    map.entries.sumOf { if(selector(it.key)) it.value else 0 }
}