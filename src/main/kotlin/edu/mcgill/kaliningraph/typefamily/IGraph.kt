package edu.mcgill.kaliningraph.typefamily

import edu.mcgill.kaliningraph.*

// Inheritable constructors
@Suppress("FunctionName")
interface IGF<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  // Inheritors must implement these three "constructors"
  fun Vertex(newId: String = "", edgeMap: (V) -> Set<E>): V
  fun Graph(vertices: Set<V> = setOf()): G
  fun Edge(s: V, t: V): E

  fun Vertex(newId: String = "", out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }

  fun Graph(vararg graphs: G): G = Graph(graphs.toList())
  fun Graph(vararg vertices: V): G = Graph(vertices.map { it.graph })

  fun <T: Any> Graph(
    vararg adjList: Pair<T, T>,
    p2v: (Pair<T, T>) -> V = { (s, t) -> Vertex("$s", setOf(Vertex("$t"))) }
  ): G = adjList.map { p2v(it) }.fold(Graph()) { acc, v -> acc + v.graph }

  fun <T: Any> Graph(list: List<T>): G = Graph(
    when {
      list.isEmpty() -> setOf()
      list allAre Graph() -> Graph(list.fold(Graph()) { it, acc -> it + acc as G }.vertices)
      list allAre Vertex() -> Graph(list.map { it as V }.toSet())
      list.any { it is IGF<*, *, *> } -> list.first { it is IGF<*, *, *> }
        .let { throw Exception("Unsupported: Graph(${it::class.java})") }
      else -> Graph(*list.toList().zipWithNext().toTypedArray())
    }
  )

  fun Graph(graph: String): G = graph.split(" ")
    .fold(Graph()) { acc, it -> acc + Graph(it.toCharArray().toList()) }
}

interface IGraph<G, E, V>: IGF<G, E, V>, Set<V>, (V) -> Set<V>
/*
 * TODO: Which primary interface should we expect graphs to fulfill?
 *
 * 1. a set Set<V>
 *   - Pros: Simple, has precedent cf. https://github.com/maxitg/SetReplace/
 *   - Cons: Finite, no consistency constraints on edges
 * 2. a [partial] function E ⊆ V×V / (V) -> Set<V> / graph(v)
 *   - Pros: Mathematically analogous, can represent infinite graphs
 *   - Cons: Memoization seems tricky to handle
 * 3. a [multi]map Map<V, Set<V>> / graph[v]
 *   - Pros: Computationally efficient representation
 *   - Cons: Finite, incompatible with Set<V> perspective
 * 4. a semiring
 *   - Pros: Useful for describing many algebraic path problems
 *   - Cons: Esoteric API / unsuitable as an abstract interface
 *
 * Algebraic perspective    : https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
 * Type-family perspective  : https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf#page=3
 * Inductive perspective    : https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf
 * Mathematical perspective : https://doi.org/10.1007/978-0-387-75450-5
 * Semiring perspective     : http://stedolan.net/research/semirings.pdf
 */

  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val vertices: Set<V>
  val edgList: List<Pair<V, E>> get() = vertices.flatMap { s -> s.outgoing.map { s to it } }
  val adjList: List<Pair<V, V>> get() = edgList.map { (v, e) -> v to e.target }
  val edgMap: Map<V, Set<E>> get() = vertices.associateWith { it.outgoing }
  val edges: Set<E> get() = edgMap.values.flatten().toSet()

  // Implements graph merge. For all vertices in common, merge their neighbors.
  // TODO: Figure out how to implement this operator "correctly"
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
  operator fun plus(that: G): G =
    Graph((this - that) + (this join that) + (that - this))

  operator fun minus(graph: G): G = Graph(vertices - graph.vertices)

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> Vertex(left.id) { left.outgoing + right.outgoing } }
      .toSet()

  // TODO: Reimplement using matrix transpose
  fun reversed(): G = Graph(
    (vertices.associateWith { setOf<E>() } +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to Edge(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })
      .map { (k, v) -> Vertex(k.id) { v } }.toSet()
  )
}

interface IEdge<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G
  val source: V
  val target: V
}

// TODO: Make this a "view" of the container graph
interface IVertex<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val id: String
  val graph: G
    get() = Graph(neighbors(-1))
  val incoming: Set<E>
    get() = graph.reversed().edgMap[this] ?: emptySet()
  val outgoing: Set<E>
    get() = edgeMap(this as V).toSet()
  val edgeMap: (V) -> Collection<E> // Make a self-loop by passing this

  open val neighbors
    get() = outgoing.map { it.target }.toSet()
  open val degree
    get() = neighbors.size

  // tailrec prohibited on open members? may be possible with deep recursion
  // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-deep-recursive-function/
  fun neighbors(k: Int = 0, vertices: Set<V> = neighbors + this as V): Set<V> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as V)

  // Removes all edges pointing outside the set
  private fun Set<V>.closure(): Set<V> =
    map { vertex -> Vertex(id) { vertex.outgoing.filter { it.target in this }.toSet() } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = Graph(neighbors(0).closure())
}

inline infix fun <reified S: Any, reified T: Any> S.isA(that: T) =
  this::class.java.let { that::class.java.isAssignableFrom(it) }

infix fun Collection<Any>.allAre(that: Any) = all { it isA that }

// https://github.com/amodeus-science/amod
//abstract class Map : IGraph<Map, Road, City>
//abstract class Road : IEdge<Map, Road, City>
//abstract class City : IVertex<Map, Road, City>