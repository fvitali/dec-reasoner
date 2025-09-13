package org.w3id.conjectures.handler;

import org.w3id.conjectures.*;

import java.util.*;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.graph.GraphFactory;

public class ReificationHandler {
  
    private static int debug = 1;
	private final DecDataset				dataset;

	private final Map<Node, DecWorld>	worlds;
    private final Map<Node, Node[]>		reifications		;
	private final Map<Node, Quad>		reificationMapping;



	public ReificationHandler(DecDataset dataset) {
		this.dataset = dataset;
		this.worlds = dataset.getWorlds();
        this.reifications = new HashMap<>() ;
        this.reificationMapping = new HashMap<>();
	}

    public boolean isRelevant(Node g, Node s, Node p, Node o) {
        return 
            p.equals(NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI)) ||
            p.equals(NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI)) ||
            p.equals(NodeFactory.createURI(DecUtils.RDF_OBJECT_URI)) ||
            o.equals(NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI));
    }

    public void add(Node g, Node s, Node p, Node o) {
        Node[] reification = reifications.computeIfAbsent(s, key -> new Node[3]);
        if (p.equals(NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI))) {
            reification[0] = o;	
        } else if (p.equals(NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI))) {
            reification[1] = o;
        } else if (p.equals(NodeFactory.createURI(DecUtils.RDF_OBJECT_URI))) {
            reification[2] = o;
        }
    }

    public Map<Node, Node[]> getReifications() { return reifications; }
    
    public void clear() {
        reifications.clear();
    }


    public void handleReifications() {
		if (debug >= 1) DecUtils.out("handleReifications",4);
		
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld reality = worlds.computeIfAbsent(realityNode, key -> new DecWorld(key.getURI(), "default", GraphFactory.createDefaultGraph(), dataset));
				
		for (Map.Entry<Node, Node[]> entry : reifications.entrySet()) {
			Node reificationNode = entry.getKey();
			Node[] components = entry.getValue();
			if (components[0] == null || components[1] == null || components[2] == null) continue;
			
			// Collect triples to modify
			List<Triple> objectTriplesToModify = new ArrayList<>();
			Iterator<Triple> objectTriples = reality.getBaseGraph().find(Node.ANY, Node.ANY, reificationNode);
			while (objectTriples.hasNext()) {
				objectTriplesToModify.add(objectTriples.next());
			}
			if (debug >= 3) DecUtils.out("      objectTriplesToModify: " + objectTriplesToModify);
			
			// Process collected triples
			for (Triple t : objectTriplesToModify) {
				String hash = DecUtils.hash(t.getSubject().toString(), t.getPredicate().toString(), "REIFICATION", DecUtils.decPrefix + "reification/");
				Node hashNode = NodeFactory.createURI(hash);
				
				DecWorld reificationWorld = worlds.computeIfAbsent(hashNode, key -> new DecWorld(hash, "reification", GraphFactory.createDefaultGraph(), dataset));
				reificationWorld.getBaseGraph().add(components[0], components[1], components[2]);
				reality.getBaseGraph().add(t.getSubject(), t.getPredicate(), hashNode);
				reality.getBaseGraph().delete(t.getSubject(), t.getPredicate(), reificationNode);
				reificationMapping.put(hashNode, new Quad(reificationNode, components[0], components[1], components[2]));
				dataset.getDefaultGraph().delete(t.getSubject(), t.getPredicate(), reificationNode);
			}
			
			// Collect triples to modify
			List<Triple> subjectTriplesToModify = new ArrayList<>();
			Iterator<Triple> subjectTriples = reality.getBaseGraph().find(reificationNode, Node.ANY, Node.ANY);
			while (subjectTriples.hasNext()) {
				Triple t = subjectTriples.next();
				if (t.getPredicate().toString().equals(DecUtils.RDF_PREDICATE_URI) || 
					t.getPredicate().toString().equals(DecUtils.RDF_OBJECT_URI) || 
					t.getPredicate().toString().equals(DecUtils.RDF_SUBJECT_URI) || 
					t.getObject().toString().equals(DecUtils.RDF_STATEMENT_URI)) continue;
				subjectTriplesToModify.add(t);
			}

			// Process collected triples
			for (Triple t : subjectTriplesToModify) {
				String hash = DecUtils.hash("REIFICATION", t.getPredicate().toString(), t.getObject().toString(), DecUtils.decPrefix + "reification/");
				Node hashNode = NodeFactory.createURI(hash);
				
				DecWorld reificationWorld = worlds.computeIfAbsent(hashNode, key -> new DecWorld(hash, "reification", GraphFactory.createDefaultGraph(), dataset));
				reificationWorld.getBaseGraph().add(components[0], components[1], components[2]);				
				reality.getBaseGraph().add(hashNode, t.getPredicate(), t.getObject());
				reality.getBaseGraph().delete(reificationNode, t.getPredicate(), t.getObject());
				dataset.getDefaultGraph().delete(reificationNode, t.getPredicate(), t.getObject());
			}
		}
	}


    public void restoreReifications() {
		if (debug >= 1) DecUtils.out("restoreReifications",4);
		if (debug >= 2) DecUtils.out(worlds);
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecWorld realityWorld = dataset.getWorlds().get(realityNode);
		if (realityWorld == null) {
			if (debug >= 3) DecUtils.out("   No reality world found");
			return;
		}
		Graph realityGraph = realityWorld.getPermeatedGraph();

		int statementsCount = 0;
		Graph defaultGraph = dataset.getDefaultGraph();
		Set<Node> worldsToRemove = new HashSet<>();
		boolean addAllTriples = false;
		for (Map.Entry<Node, DecWorld> entry : worlds.entrySet()) {
			Node n = entry.getKey();
			DecWorld world = entry.getValue();
			if (!world.getGraphType().equals("reification")) continue;
			if (debug >= 3) DecUtils.out("     Processing reification world: " + n);

			Quad originalQuad = reificationMapping.get(n);

			// Find triple with n as object
			Iterator<Triple> it = realityWorld.getPermeatedGraph().find(Node.ANY, Node.ANY, n);
			if (it.hasNext()) {
				Triple t = it.next();
				Node s = t.getSubject();
				Node p = t.getPredicate();
				if (debug >= 3) DecUtils.out("   Found triple with n as object: " + DecUtils.replacePrefixes(t));
				
				// Convert each triple in world to reification statements
				Iterator<Triple> worldTriples = world.getPermeatedGraph().find();
				while (worldTriples.hasNext()) {
					Triple reifiedTriple = worldTriples.next();
					if (debug >= 3 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + reifiedTriple);
					Boolean isOrigTriple =	reifiedTriple.getSubject().equals(originalQuad.getSubject()) && 
											reifiedTriple.getPredicate().equals(originalQuad.getPredicate()) && 
											reifiedTriple.getObject().equals(originalQuad.getObject());
					String counter = isOrigTriple ? "" : "-inf" + statementsCount++;
					
					if (DecUtils.isTrivial(reifiedTriple) && !addAllTriples) continue;
					if (debug >= 3 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + DecUtils.replacePrefixes(reifiedTriple));
					Node name = NodeFactory.createURI(originalQuad.getGraph().getURI() + counter);
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_TYPE_URI), NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI));
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI), reifiedTriple.getSubject());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI), reifiedTriple.getPredicate());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_OBJECT_URI), reifiedTriple.getObject());
					realityGraph.add(s, p, name);
					if (debug >= 3) DecUtils.out("     Added to reality " + name + ": " + DecUtils.replacePrefixes(reifiedTriple));
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
				
				Iterator<Triple> worldTriples = world.getPermeatedGraph().find();
				while (worldTriples.hasNext()) {
					Triple reifiedTriple = worldTriples.next();
					if (DecUtils.isTrivial(reifiedTriple) && !addAllTriples) continue;
					if (debug >= 3 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + reifiedTriple);
					Node name = NodeFactory.createURI(n.getURI() + "-" + statementsCount++);
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_TYPE_URI), NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI));
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI), reifiedTriple.getSubject());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI), reifiedTriple.getPredicate());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_OBJECT_URI), reifiedTriple.getObject());
					realityGraph.add(name, p, o);
					if (debug >= 1) DecUtils.out("     Added to reality " + name + ": " + DecUtils.replacePrefixes(reifiedTriple));
				}
				
				// Remove original triple from reality graph
				realityWorld.getBaseGraph().delete(t);
				if (debug >= 3) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			worldsToRemove.add(n);
			if (debug >= 3) DecUtils.out("   Marked world for removal: " + n);
		}

			// Remove worlds after iteration
			for (Node n : worldsToRemove) {
				worlds.remove(n);
				if (debug >= 3) DecUtils.out("   Removed world: " + n);
			}
	}
}

