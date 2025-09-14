package org.w3id.conjectures.handler;

import org.w3id.conjectures.*;

import java.util.*;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.graph.GraphFactory;

public class DefaultGraphHandler {

    // I do NOT understand the differences and I do NOT WANT to understand them
	// The differences between these uris are beyond me and often the reason of the hardest bugs in my code. 
    private static final Node URI1 = Quad.defaultGraphIRI;
    private static final Node URI2 = Quad.defaultGraphNodeGenerated;
    private static final Node URI3 = Node.ANY;
    private static final Node URI4 = null;

    private static int debug ;
	private final DecDataset				dataset;
	private final Map<Node, DecWorld>	worlds;

	public DefaultGraphHandler(DecDataset dataset) {
		this.dataset = dataset;
		this.worlds = dataset.getWorlds();
		debug = DecUtils.getDebugLevel(6); // Position 6 for DefaultGraphHandler
		DecUtils.out("DefaultGraphHandler: debug level (6): " + debug);
	}

    public boolean isRelevant(Node g, Node s, Node p, Node o) {
        return isRelevant(g);
    }

    public boolean isRelevant(Node g) {
        return g.isURI() && (g.equals(URI1) || g.equals(URI2) || g.equals(URI3) || g.equals(URI4));
    }

    public Node add(Node g, Node s, Node p, Node o) {
        if (debug >= 3) DecUtils.out("Added to reality", 8, true);
 
        /* Should we create a world for the default graph? I am not so sure */
        /* 
        if (!worlds.containsKey(Quad.defaultGraphNodeGenerated)) {
			DecWorld defaultWorld = new DecWorld(Quad.defaultGraphNodeGenerated.getURI(), "special", decDataset.getDatasetGraph().getDefaultGraph(), decDataset);
			worlds.put(Quad.defaultGraphNodeGenerated, defaultWorld);
		}
		*/

        /* I woud rather create a "reality world" explicitly to contain all "normal" triples */
        Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld realityWorld = worlds.computeIfAbsent(realityNode,
			key -> new DecWorld(realityNode.getURI(), "default", GraphFactory.createDefaultGraph(), dataset));
		realityWorld.getBaseGraph().add(s, p, o);
		dataset.getDatasetGraph().add(g, s, p, o);
		return realityNode;
    }

    public void clear() {
    }


    public void restoreDefaultGraph() {
		if (debug >= 1) DecUtils.out("restoreDefaultGraph", 4);
		
		// Add all triples from decStatement graph
//		DecUtils.out("worlds", worlds, 8);
		Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
		DecWorld decStatementWorld = worlds.get(decStatementGraph);
		if (decStatementWorld != null) {
			Graph decStatementGraphGraph = decStatementWorld.getBaseGraph();
			decStatementGraphGraph.find().forEachRemaining(triple -> {
				if (debug >= 3 && !DecUtils.isTrivial(triple)) DecUtils.out("Restoring triple: ", triple, 4);
				dataset.getDatasetGraph().add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject());
			});
		}
		
		// Add all triples from reality graph
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld reality = worlds.get(realityNode);
		if (reality != null) {
			Graph realityGraph = reality.getPermeatedGraph();
			realityGraph.find().forEachRemaining(triple -> {
				if (debug >= 3 && !DecUtils.isTrivial(triple)) DecUtils.out("Restoring triple: ", triple, 4);
				dataset.getDatasetGraph().add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject());
			});
		}
		

		dataset.getDatasetGraph().removeGraph(decStatementGraph);
		dataset.getDatasetGraph().removeGraph(realityNode);
		worlds.remove(realityNode);
	}

}
