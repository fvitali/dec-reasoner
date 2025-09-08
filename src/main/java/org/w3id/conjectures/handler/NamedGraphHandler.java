package org.w3id.conjectures.handler;

import org.w3id.conjectures.*;

import java.util.*;

import org.apache.jena.graph.*;
import org.apache.jena.sparql.core.*;

public class NamedGraphHandler {
  
    private static final int debug = 1;
	private final DecDataset				dataset;

	private final Map<Node, DecWorld>	worlds;

	public NamedGraphHandler(DecDataset dataset) {
		this.dataset = dataset;
		this.worlds = dataset.getWorlds();
	}

    public boolean isRelevant(Node g, Node s, Node p, Node o) {
        return g != null && !g.equals(Node.ANY) && !g.equals(Quad.defaultGraphIRI) && !g.equals(Quad.defaultGraphNodeGenerated);
    }

    /**
     * Handles the addition of a triple to a named graph.
     *
     * @param graph the graph to add the triple to
     * @param s the subject of the triple
     * @param p the predicate of the triple
     * @param o the object of the triple
     */
    public Node add(Node g, Node s, Node p, Node o) { 
        if (debug >= 3) DecUtils.out("Added to graph", g, 8, true);
        DecWorld world = worlds.computeIfAbsent(g, 
            key -> new DecWorld(g.getURI(), "named", dataset.getDatasetGraph().getGraph(g), dataset));
        dataset.getDatasetGraph().add(new Quad(g, s, p, o));
        return g;
    }

    /**
     * Returns the list of triples in the specified named graph.
     *
     * @param graph the graph to retrieve triples from
     * @return the list of triples
     */
    public List<Triple> getTriples(Node graph) {
        return new ArrayList<>();
    }

    public void clear() {
    }

}
