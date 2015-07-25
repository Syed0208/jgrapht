/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2008, by Barak Naveh and Contributors.
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
/* -----------------
 * EdmondsKarpMaximumFlow.java
 * -----------------
 * (C) Copyright 2008-2008, by Ilya Razenshteyn and Contributors.
 *
 * Original Author:  Ilya Razenshteyn
 * Contributor(s):   -
 *
 * $Id$
 *
 * Changes
 * -------
 */
package org.jgrapht.alg.flow;

import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.util.ExtensionManager.ExtensionFactory;

import java.util.*;


/**
 * A <a href = "http://en.wikipedia.org/wiki/Flow_network">flow network</a> is a
 * directed graph where each edge has a capacity and each edge receives a flow.
 * The amount of flow on an edge can not exceed the capacity of the edge (note,
 * that all capacities must be non-negative). A flow must satisfy the
 * restriction that the amount of flow into a vertex equals the amount of flow
 * out of it, except when it is a source, which "produces" flow, or sink, which
 * "consumes" flow.
 *
 * <p>This class computes maximum flow in a network using <a href =
 * "http://en.wikipedia.org/wiki/Edmonds-Karp_algorithm">Edmonds-Karp
 * algorithm</a>. Be careful: for large networks this algorithm may consume
 * significant amount of time (its upper-bound complexity is O(VE^2), where V -
 * amount of vertices, E - amount of edges in the network).
 *
 * <p>For more details see Andrew V. Goldberg's <i>Combinatorial Optimization
 * (Lecture Notes)</i>.
 */
public final class EdmondsKarpMaximumFlow<V, E> extends MaximumFlowAlgorithmBase<V, E> {
    /**
     * Default tolerance.
     */
    public static final double DEFAULT_EPSILON = 0.000000001;

    private DirectedGraph<V, E> network; // our network

    private double epsilon;     // tolerance (DEFAULT_EPSILON or user-defined)

    private VertexExtension currentSource;  // current source vertex
    private VertexExtension currentSink;    // current sink vertex

    class EdgeExtension extends EdgeExtensionBase { }

    class VertexExtension extends VertexExtensionBase {
        boolean visited; // this mark is used during BFS to mark visited nodes
        List<EdgeExtension> lastArcs; // last arc(-s) in the shortest path
    }

    /**
     * Constructs <tt>MaximumFlow</tt> instance to work with <i>a copy of</i>
     * <tt>network</tt>. Current source and sink are set to <tt>null</tt>. If
     * <tt>network</tt> is weighted, then capacities are weights, otherwise all
     * capacities are equal to one. Doubles are compared using <tt>
     * DEFAULT_EPSILON</tt> tolerance.
     *
     * @param network network, where maximum flow will be calculated
     */
    public EdmondsKarpMaximumFlow(DirectedGraph<V, E> network)
    {
        this(network, DEFAULT_EPSILON);
    }

    /**
     * Constructs <tt>MaximumFlow</tt> instance to work with <i>a copy of</i>
     * <tt>network</tt>. Current source and sink are set to <tt>null</tt>. If
     * <tt>network</tt> is weighted, then capacities are weights, otherwise all
     * capacities are equal to one.
     *
     * @param network network, where maximum flow will be calculated
     * @param epsilon tolerance for comparing doubles
     */
    public EdmondsKarpMaximumFlow(DirectedGraph<V, E> network, double epsilon)
    {
        init(
            new ExtensionFactory<VertexExtension>() {
                @Override
                public VertexExtension create() {
                    return EdmondsKarpMaximumFlow.this.new VertexExtension();
                }
            },
            new ExtensionFactory<EdgeExtension>() {
                @Override
                public EdgeExtension create() {
                    return EdmondsKarpMaximumFlow.this.new EdgeExtension();
                }
            }
        );

        if (network == null) {
            throw new NullPointerException("network is null");
        }
        if (epsilon <= 0) {
            throw new IllegalArgumentException(
                "invalid epsilon (must be positive)");
        }
        for (E e : network.edgeSet()) {
            if (network.getEdgeWeight(e) < -epsilon) {
                throw new IllegalArgumentException(
                    "invalid capacity (must be non-negative)");
            }
        }

        this.network = network;
        this.epsilon = epsilon;

        buildInternal();
    }


    /**
     * Sets current source to <tt>source</tt>, current sink to <tt>sink</tt>,
     * then calculates maximum flow from <tt>source</tt> to <tt>sink</tt>. Note,
     * that <tt>source</tt> and <tt>sink</tt> must be vertices of the <tt>
     * network</tt> passed to the constructor, and they must be different.
     *
     * @param source source vertex
     * @param sink sink vertex
     */
    public MaximumFlow<V, E> buildMaximumFlow(V source, V sink)
    {
        if (!network.containsVertex(source)) {
            throw new IllegalArgumentException(
                "invalid source (null or not from this network)");
        }
        if (!network.containsVertex(sink)) {
            throw new IllegalArgumentException(
                "invalid sink (null or not from this network)");
        }

        if (source.equals(sink)) {
            throw new IllegalArgumentException("source is equal to sink");
        }

        currentSource   = vertexExtended0(source);
        currentSink     = vertexExtended0(sink);

        Map<E, Double> maxFlow;

        double maxFlowValue;

        for (;;) {
            breadthFirstSearch();

            if (!currentSink.visited) {
                maxFlow = composeFlow();
                maxFlowValue = 0.0;
                for (E e : network.incomingEdgesOf(currentSink.prototype)) {
                    maxFlowValue += maxFlow.get(e);
                }
                break;
            }

            augmentFlow();
        }

        return new VerbatimMaximumFlow<V, E>(maxFlowValue, maxFlow);
    }

    protected VertexExtension vertexExtended0(V v) {
        return this.vertexExtended(v);
    }

    protected EdgeExtension edgeExtended0(E e) {
        return this.edgeExtended(e);
    }

    private void breadthFirstSearch()
    {
        for (V v : network.vertexSet()) {
            vertexExtended0(v).visited  = false;
            vertexExtended0(v).lastArcs = null;
        }

        Queue<VertexExtension> queue = new LinkedList<VertexExtension>();
        queue.offer(currentSource);

        currentSource.visited   = true;
        currentSource.excess    = Double.POSITIVE_INFINITY;

        currentSink.excess = 0.0;

        boolean seenSink = false;

        while (queue.size() != 0) {
            VertexExtension ux = queue.poll();

            for (int i = 0; i < ux.outgoing.size(); ++i) {
                EdgeExtension ex = (EdgeExtension) ux.outgoing.get(i);

                if ((ex.flow + epsilon) < ex.capacity) {

                    VertexExtension vx = (VertexExtension) ex.target;

                    if (vx == currentSink) {

                        vx.visited = true;

                        if (vx.lastArcs == null)
                            vx.lastArcs = new ArrayList<EdgeExtension>();

                        vx.lastArcs.add(ex);
                        vx.excess += Math.min(ux.excess, ex.capacity - ex.flow);

                        seenSink = true;

                    } else if (!vx.visited) {

                        vx.visited  = true;
                        vx.excess   = Math.min(ux.excess, ex.capacity - ex.flow);

                        vx.lastArcs = Collections.singletonList(ex);

                        if (!seenSink)
                            queue.add(vx);
                    }
                }
            }
        }
    }

    private void augmentFlow()
    {
        Set<VertexExtension> seen = new HashSet<VertexExtension>();

        for (EdgeExtension ex : currentSink.lastArcs) {
            double deltaFlow =
                Math.min(ex.source.excess, ex.capacity - ex.flow);

            if (augmentFlowAlongInternal(deltaFlow, (VertexExtension) ex.source, seen)) {
                ex.flow         += deltaFlow;
                ex.inverse.flow -= deltaFlow;
            }

            // _DBG
            assert (ex.flow + DEFAULT_EPSILON <= ex.capacity);
        }
    }

    private boolean augmentFlowAlongInternal(double deltaFlow, VertexExtension node, Set<VertexExtension> seen) {
        if (node == currentSource)
            return true;
        if (seen.contains(node))
            return false;

        seen.add(node);

        EdgeExtension prev = node.lastArcs.get(0);
        if (augmentFlowAlongInternal(deltaFlow, (VertexExtension) prev.source, seen)) {
            prev.flow         += deltaFlow;
            prev.inverse.flow -= deltaFlow;
            return true;
        }

        return false;
    }

    /**
     * Returns current source vertex, or <tt>null</tt> if there was no <tt>
     * calculateMaximumFlow</tt> calls.
     *
     * @return current source
     */
    public V getCurrentSource()
    {
        return currentSource == null ? null : currentSource.prototype;
    }

    /**
     * Returns current sink vertex, or <tt>null</tt> if there was no <tt>
     * calculateMaximumFlow</tt> calls.
     *
     * @return current sink
     */
    public V getCurrentSink()
    {
        return currentSink == null ? null : currentSink.prototype;
    }

    @Override
    DirectedGraph<V, E> getNetwork() {
        return network;
    }
}

// End EdmondsKarpMaximumFlow.java
