package org.w3id.conjectures.handler;

import org.w3id.conjectures.*;

import java.util.*;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.core.Quad;

public class RDFStarHandler {
  
    private static int debug = 1;
	private final DecDataset dataset;

	private final Map<Node, DecWorld>	worlds;

	private String defaultRdfStarDecType = DecUtils.DEFAULT_RDF_STAR_DEC_TYPE;


	public RDFStarHandler(DecDataset dataset) {
		this.dataset = dataset;
		this.worlds = dataset.getWorlds();
		debug = DecUtils.getDebugLevel(8); // Position 8 for RDFStarHandler
		DecUtils.out("RDFStarHandler: debug level (8): " + debug);
	}

    public boolean isRelevant(Node g, Node s, Node p, Node o) {
        return s.isNodeTriple() || o.isNodeTriple();
    }

    public Node add(Node g, Node s, Node p, Node o) {
        if (debug >= 3) DecUtils.out("   adding RDF-star triple ", g, s, p, o, 4, true);

		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld realityWorld = worlds.computeIfAbsent(realityNode,
		key -> new DecWorld(realityNode.getURI(), "default", GraphFactory.createDefaultGraph(), dataset));
		if(dataset.isDefaultGraph(g)) {
			String hash;
			Node s1, p1, o1;

			if (o.isNodeTriple()) {
				hash = DecUtils.hash(s.getURI(), p.getURI(), "TRIPLES", DecUtils.decPrefix + "rdfstar/");
				s1 = o.getTriple().getSubject();
				p1 = o.getTriple().getPredicate();
				o1 = o.getTriple().getObject();
				realityWorld.getBaseGraph().add(s, p, NodeFactory.createURI(hash));
				if (debug >= 1) DecUtils.out("Hash for ", s, p, o, "is", hash);
			} else {
				hash = DecUtils.hash("TRIPLES", p.getURI(), o.getURI(), DecUtils.decPrefix + "rdfstar/");
				s1 = s.getTriple().getSubject();
				p1 = s.getTriple().getPredicate();
				o1 = s.getTriple().getObject();
				realityWorld.getBaseGraph().add(NodeFactory.createURI(hash), p, o);
				if (debug >= 1) DecUtils.out("Hash for ", s, p, o, "is", hash);
			}
			Node hashNode = NodeFactory.createURI(hash);
			DecWorld hashWorld = worlds.computeIfAbsent(hashNode,
				key -> {
					Graph hashGraph = GraphFactory.createDefaultGraph();
					return new DecWorld(hash, "rdf-star", hashGraph, dataset);
				});
			hashWorld.getBaseGraph().add(s1, p1, o1);

			return hashNode;
		} else {
			DecWorld gWorld = worlds.computeIfAbsent(g,
			key -> new DecWorld(g.getURI(), "named", GraphFactory.createDefaultGraph(), dataset));
			gWorld.getBaseGraph().add(s, p, o);
			return g;
		}
    }

    public void clear() {
    }


    public void restoreRdfStarTriples() {
		if (debug >= 1) DecUtils.out("restoreRdfTriples",4);
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld realityWorld = dataset.getWorlds().get(realityNode);
		if (realityWorld == null) {
			if (debug >= 1) DecUtils.out("   No reality world found");
			return;
		}

		Graph defaultGraph = dataset.getDefaultGraph();
		Set<Node> worldsToRemove = new HashSet<>();
		boolean addAllTriples = false;
		// XXX MAKE THIS A PARAMETER

		for (Map.Entry<Node, DecWorld> entry : worlds.entrySet()) {
			Node n = entry.getKey();
			DecWorld world = entry.getValue();
			if (!world.getGraphType().equals("rdf-star")) continue;
			if (debug >= 3) DecUtils.out("Processing RDF-star world: " + n, world.getPermeatedGraph(), false, 8);

			// Find triple with n as object
			Iterator<Triple> it = realityWorld.getPermeatedGraph().find(Node.ANY, Node.ANY, n);
			if (it.hasNext()) {
				Triple t = it.next();
				Node s = t.getSubject();
				Node p = t.getPredicate();
				if (debug >= 3) DecUtils.out("   Found triple with n as object: " + t);
				
				// Convert each triple in world to RDF-star quad
				Iterator<Triple> worldTriples = world.getPermeatedGraph().find();
				while (worldTriples.hasNext()) {
					Triple T = worldTriples.next();
					Node starNode = NodeFactory.createTripleNode(T);
					if (!DecUtils.isTrivial(T) || addAllTriples) {
						realityWorld.getPermeatedGraph().add(s, p, starNode);
						if (debug >= 3 && !DecUtils.isTrivial(T)) DecUtils.out("   Added RDF-star triple to reality graph: " + s + " " + p + " " + starNode);
					}
				}
				// Remove original triple from reality graph
				realityWorld.getBaseGraph().delete(t);
				if (debug >= 3) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			// Find triple with n as subject
			it = realityWorld.getPermeatedGraph().find(n, Node.ANY, Node.ANY);
			if (it.hasNext()) {
				Triple t = it.next();
				Node p = t.getPredicate();
				Node o = t.getObject();
				if (debug >= 3) DecUtils.out("   Found triple with n as subject: " + t);
				
				// Convert each triple in world to RDF-star quad
				Iterator<Triple> worldTriples = world.getPermeatedGraph().find();
				while (worldTriples.hasNext()) {
					Triple T = worldTriples.next();
					Node starNode = NodeFactory.createTripleNode(T);
					if (!DecUtils.isTrivial(T) || addAllTriples) {
						realityWorld.getPermeatedGraph().add(starNode, p, o);
						if (debug >= 3 && !DecUtils.isTrivial(T)) DecUtils.out("   Added RDF-star triple to default graph: " + starNode + " " + p + " " + o);
					}
				}
				
				// Remove original triple from reality graph
				realityWorld.getBaseGraph().delete(t);
				if (debug >= 3) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			worldsToRemove.add(n);
			if (debug >= 3) DecUtils.out("   Marked world for removal: " + n);
			
			// Search and delete triples from DecStatementGraph that have n as subject or object
			Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
			DecWorld decStatementWorld = worlds.get(decStatementGraph);
			if (false && decStatementWorld != null) {
				Graph decStatementGraphGraph = decStatementWorld.getBaseGraph();
				
				// Collect triples to delete (to avoid ConcurrentModificationException)
				List<Triple> triplesToDelete = new ArrayList<>();
				
				// Find triples with n as subject
				Iterator<Triple> subjectTriples = decStatementGraphGraph.find(n, Node.ANY, Node.ANY);
				while (subjectTriples.hasNext()) {
					Triple t = subjectTriples.next();
					triplesToDelete.add(t);
					if (debug >= 1) DecUtils.out("   Found triple to delete from DecStatementGraph (subject): " + t);
				}
				
				// Find triples with n as object
				Iterator<Triple> objectTriples = decStatementGraphGraph.find(Node.ANY, Node.ANY, n);
				while (objectTriples.hasNext()) {
					Triple t = objectTriples.next();
					triplesToDelete.add(t);
					if (debug >= 1) DecUtils.out("   Found triple to delete from DecStatementGraph (object): " + t);
				}
				
				// Delete all collected triples
				for (Triple t : triplesToDelete) {
					decStatementGraphGraph.delete(t);
					if (debug >= 1) DecUtils.out("   Deleted triple from DecStatementGraph: " + t);
				}
			}
		}

			// Remove worlds after iteration
			for (Node n : worldsToRemove) {
				worlds.remove(n);
				if (debug >= 3) DecUtils.out("   Removed world: " + n);
			}
	}

}
