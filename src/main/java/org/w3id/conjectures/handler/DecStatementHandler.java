package org.w3id.conjectures.handler;

import org.w3id.conjectures.*;

import java.util.*;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.*;



public class DecStatementHandler {
  
    private final int            debug ;
	private final DecDataset			dataset;

	private final Map<Node, DecWorld>	worlds;

    private final Map<Node, Triple>		DecStatements;
	private final Map<Node, String>		DecPredicates;
	private final Map<Node, String>		DecReversePredicates;
	private final List<Node>			DecPointOfViewPredicates;
	private final List<Node>			DecPointOfViewReversePredicates;
    private final Set<Node> 			closedForSubjects;
	private final Set<Node> 			closedForObjects;

	private Boolean						generateReportAnalysis = true;
	private Boolean						checkClosedFor = false;
	private Boolean						checkInconsistencies = false;

	private DecWorld reality ;
	private DecWorld decWorld ;


	// RDF Vocabulary URIs
		private static final String decPrefix = DecUtils.decPrefix;

		private static final String RDF_PREFIX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
		public static final String RDF_TYPE_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" ;
		public static final String RDFS_RANGE_URI = "http://www.w3.org/2000/01/rdf-schema#range" ;
		public static final String OWL_RANGE_URI = "http://www.w3.org/2002/07/owl#range" ;
		public static final String RDF_SUBJECT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject";
		public static final String RDF_PREDICATE_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate";
		public static final String RDF_OBJECT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object";
		public static final String RDF_STATEMENT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement";
		public static final String OWL_SAME_AS_URI = "http://www.w3.org/2002/07/owl#sameAs";

		public static final String decStatementWorldS = decPrefix + "statements" ;
		public static final String realityWorldS = decPrefix + "reality";
		public static final String delusionalWorld = decPrefix + "delusionalWorld";
		public static final String disagreesWithS = decPrefix + "disagreesWith";

		public static final String disagreementsPrefix = decPrefix + "disagreements/";
		public static final String disagreementType = decPrefix + "DisagreementReport";


		
		public static final String CHECK_INCONSISTENCIES = decPrefix + "checkInconsistencies";
		public static final String CLOSED_FOR = decPrefix + "closedFor";
		public static final String POINT_OF_VIEW = decPrefix + "pointofview";
		public static final String POINT_OF_VIEW_PREDICATE = decPrefix + "pointofviewPredicate";
		public static final String POINT_OF_VIEW_REVERSE_PREDICATE = decPrefix + "pointofviewReversePredicate";
	
	public DecStatementHandler(DecDataset dataset) {
		this.dataset = dataset;
		this.worlds = dataset.getWorlds();
        this.DecStatements = new HashMap<>() ;
        this.DecPredicates = new HashMap<>() ;
        this.DecReversePredicates = new HashMap<>() ;
        this.DecPointOfViewPredicates = new ArrayList<>() ;
        this.DecPointOfViewReversePredicates = new ArrayList<>() ;
        closedForSubjects = new LinkedHashSet<>(); 
        closedForObjects = new LinkedHashSet<>(); 
        debug = DecUtils.getDebugLevel(4); // Position 4 for DecStatementHandler
	}

    public boolean isRelevant(Node g, Node s, Node p, Node o) {
        return s.toString().startsWith(decPrefix) || 
        p.toString().startsWith(decPrefix) || 
        o.toString().startsWith(decPrefix);
    }

    public Node add(Node g, Node s, Node p, Node o) {
        if (debug >= 3) out("adding DEC statement", s, p, o, 8);
        
        Node decStatementGraph = NodeFactory.createURI(decStatementWorldS);
        DecWorld decStatementWorld = worlds.computeIfAbsent(decStatementGraph,
            key -> new DecWorld(decStatementGraph.getURI(), "special", GraphFactory.createDefaultGraph(), dataset));

        decStatementWorld.getBaseGraph().add(s, p, o);
        dataset.getDatasetGraph().addGraph(decStatementGraph, decStatementWorld.getBaseGraph());
		Triple t = Triple.create(s, p, o);
        DecStatements.put(s, t);

        return decStatementGraph;
    }

    public Map<Node, Triple> getDecStatements() { return DecStatements; }
    
    public void clear() {
        DecStatements.clear();
    }

	public void assignDecTypes() {
		if (debug >= 1) out("assignDecTypes",4);

		reality = worlds.get(NodeFactory.createURI(realityWorldS));
		decWorld = worlds.get(NodeFactory.createURI(decStatementWorldS));

		for (Map.Entry<Node, Triple> entry : DecStatements.entrySet()) {
			Triple t = entry.getValue();
			Node s = t.getSubject();
			Node p = t.getPredicate();
			Node o = t.getObject();

			if (debug >= 3) out("dec statement: ", t, 8);
			if (
				!decTypeAttribution(s,p,o) &&						//  e.g., :g1         rdf:type      dec:epistemicWorld

				!decPredicateAttribution(s,p,o) &&					//  e.g., :knows      rdf:type      dec:epistemicPredicate
				!decReversePredicateAttribution(s,p,o) &&			//  e.g., :knownBy    rdf:type      dec:epistemicReversePredicate
				!decRangeAttribution(s,p,o) &&						//  e.g., :knows      rdfs:range    dec:epistemicWorld

				!pointOfViewAttribution(s,p,o) &&					//  e.g., :g1         rdf:type      dec:pointOfViewWorld
				!pointOfViewPredicateAttribution(s,p,o) &&			//  e.g., :knows      rdfs:range    dec:pointOfViewPredicate
				!pointOfViewReversePredicateAttribution(s,p,o) &&	//  e.g., :knownBy    rdfs:range    dec:pointOfViewReversePredicate
				!pointOfViewRangeAttribution(s,p,o) &&				//  e.g., :knows      rdfs:range    dec:pointOfView

				!checkInconsistenciesAttribution(s,p,o) &&			// e.g.,  []          dec:checkInconsistencies true
				!closedForAttribution(s,p,o) ) {					//  e.g., rdf:Subject dec:closedFor :knows
					out("Unhandled dec statement: ", t, 4);
			}
		}

		assignDecPredicates();
		assignDecPointOfViewPredicates();

//		if (debug >= 1) out("After dec type assignments:", worlds, 4, false);

	}

	private boolean decTypeAttribution(Node s, Node p, Node o) {
		if (debug >= 2) out("decTypeAttribution", s, p, o, 8);
		// Case 1: :g1 rdf:type dec:epistemicWorld
		if (debug >= 4) out("Expecting one of ", DecUtils.DECtypes, RDF_TYPE_URI, 8); 

		if(!(
			p.equals(NodeFactory.createURI(RDF_TYPE_URI)) && 
			DecUtils.DECtypes.containsKey(o.getURI())
		)) return false;

		if (debug >= 3) out("decTypeAttribution approved", 8); 
		String decType = DecUtils.DECtypes.get(o.getURI());
		DecWorld world = worlds.computeIfAbsent(
			s, key -> new DecWorld(s.getURI(), "named", dataset.getDatasetGraph().getGraph(s), dataset)
		);
		world.setDecType(decType);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean decPredicateAttribution(Node s, Node p, Node o) {
		if (debug >= 2) out("decPredicateAttribution", s, p, o, 8);

		// Case 2: ex:predicate rdf:type dec:epistemicPredicate

		if (!(
			p.equals(NodeFactory.createURI(RDF_TYPE_URI)) && 
			DecUtils.DECpredicateTypes.containsKey(o.getURI())
		)) return false;

		if (debug >= 2) out("decPredicateAttribution approved for ", s, p, o, 8); 
		String decType = DecUtils.DECpredicateTypes.get(o.getURI());
		DecPredicates.put(s, decType);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean decReversePredicateAttribution(Node s, Node p, Node o) {
			// Case 4: ex:predicate rdf:type dec:epistemicReversePredicate
		if (debug >= 2) out("decReversePredicateAttribution", s, p, o, 8);
		if (!(
			p.equals(NodeFactory.createURI(RDF_TYPE_URI)) && 
			DecUtils.DECReversePredicateTypes.containsKey(o.getURI())
		)) return false;

		if (debug >= 2) out("decReversePredicateAttribution approved", 8); 
		String decType = DecUtils.DECReversePredicateTypes.get(o.getURI());
		DecReversePredicates.put(s, decType);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean decRangeAttribution(Node s, Node p, Node o) {
		// Case 5: :knows rdfs:range dec:epistemicWorld

		if (debug >= 2) out("decRangeAttribution", s, p, o, 8);
		if (! (
			p.equals(NodeFactory.createURI(RDFS_RANGE_URI)) || 
			p.equals(NodeFactory.createURI(OWL_RANGE_URI)) 
			&& DecUtils.DECtypes.containsKey(o.getURI()))
		)	return false;
		
		if (debug >= 2) out("decRangeAttribution approved", 8); 
		String decType = DecUtils.DECtypes.get(o.getURI());
		DecPredicates.put(s, decType);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean pointOfViewAttribution(Node s, Node p, Node o) {
		// Case 6 :g1 rdf:type dec:pointOfViewWorld

		if (debug >= 2) out("pointOfViewAttribution", s, p, o, 8);
		if (!(
			p.equals(NodeFactory.createURI(RDF_TYPE_URI)) && 
			o.equals(NodeFactory.createURI(POINT_OF_VIEW))
		))  return false ;

		if (debug >= 2) out("pointOfViewAttribution approved", 8); 

		DecWorld world = worlds.computeIfAbsent(
			s, key -> new DecWorld(s.getURI(), "named", dataset.getDatasetGraph().getGraph(s), dataset)
		);
		world.setPointOfView(true);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));

		return true;

	}

	private boolean pointOfViewPredicateAttribution(Node s, Node p, Node o) {
		// Case 7: ex:predicate rdf:type dec:pointOfViewPredicate
		if (debug >= 2) out("pointOfViewPredicateAttribution", s, p, o, 8);
		if (!(
			p.equals(NodeFactory.createURI(RDFS_RANGE_URI)) && 
			o.equals(NodeFactory.createURI(POINT_OF_VIEW))
		)) return false;

		if (debug >= 2) out("pointOfViewPredicateAttribution approved", 8); 
		DecPointOfViewPredicates.add(s);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean pointOfViewReversePredicateAttribution(Node s, Node p, Node o) {
		// Case 8: ex:predicate rdf:type dec:pointOfViewReversePredicate
		if (debug >= 2) out("pointOfViewReversePredicateAttribution", s, p, o, 8);
		if (!(
			p.equals(NodeFactory.createURI(RDFS_RANGE_URI)) && 
			o.equals(NodeFactory.createURI(POINT_OF_VIEW_REVERSE_PREDICATE))
		)) return false;

		if (debug >= 2) out("pointOfViewReversePredicateAttribution approved", 8); 
		DecPointOfViewReversePredicates.add(s);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean pointOfViewRangeAttribution(Node s, Node p, Node o) {
		// Case 9: ex:predicate rdfs:range dec:pointOfView

		if (debug >= 2) out("pointOfViewRangeAttribution", s, p, o, 8);
		if (! (
			p.equals(NodeFactory.createURI(RDFS_RANGE_URI)) && 
			o.equals(NodeFactory.createURI(POINT_OF_VIEW))
		)) return false;

		if (debug >= 2) out("pointOfViewRangeAttribution approved", 8); 
		DecPointOfViewPredicates.add(s);
		decWorld.getBaseGraph().add(Triple.create(s, p, o));
		return true;
	}

	private boolean checkInconsistenciesAttribution(Node s, Node p, Node o) {
		if (debug >= 2) out("checkInconsistenciesAttribution for", s, p, o, 8);
		if (! (
			p.equals(NodeFactory.createURI(CHECK_INCONSISTENCIES))
		)) return false;

		checkInconsistencies = Boolean.parseBoolean(o.getLiteralLexicalForm());
		if (debug >= 2) out("checkInconsistencies: " + checkInconsistencies, 8); 
		return true; 
	}

	private boolean closedForAttribution(Node s, Node p, Node o) {
		if (debug >= 2) out("closedForAttribution for", s, p, o, 8);
		if (!p.equals(NodeFactory.createURI(CLOSED_FOR))) return false ;

		if (s.equals(NodeFactory.createURI(RDF_SUBJECT_URI))) {
			// Case 10: rdf:Subject dec:closedFor :createdBy
				checkClosedFor = true;
				closedForSubjects.add(o);
				if (debug >= 3) out("closedFor Subject approved", 8); 
			} else if (s.equals(NodeFactory.createURI(RDF_OBJECT_URI))) {
			// Case 11: rdf:Object dec:closedFor :create
				checkClosedFor = true;
				closedForObjects.add(o); 
				if (debug >= 3) out("closedFor Object approved", 8); 
			} else {
			return false;
		}
		return true;
	}

	private void assignDecPredicates() {
		if (debug >= 1) out("assignDecPredicates",4);

		for (Map.Entry<Node, String> entry : DecPredicates.entrySet()) {
			// Assign dec types from DecPredicates
			// e.g.: :alice :knows :G1
			// :knows rdf:type dec:epistemicPredicate
			Node predicate = entry.getKey();
			String assignedDecType = entry.getValue();
			Iterator<Triple> triples = reality.getBaseGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :alice :knows :G1 
				if (debug >= 2) out("   assigning dec type: " + assignedDecType + " to " + triple);
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecWorld world = worlds.computeIfAbsent(
					o,
					key -> new DecWorld(o.getURI(), "named", dataset.getDatasetGraph().getGraph(o), dataset)
				);

				world.setDecType(assignedDecType);
				String decType = decPrefix + assignedDecType + "World";
				// Adds the type triple for the world
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(world.getName()),
					NodeFactory.createURI(RDF_TYPE_URI),
					NodeFactory.createURI(decType)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decWorld.getBaseGraph().add(typeTriple);
				decWorld.getBaseGraph().add(originalTriple);

			});
		}

		for (Map.Entry<Node, String> entry : DecReversePredicates.entrySet()) {
			// Assign dec types from DecReversePredicates
			// e.g.: :G1 :knownBy :alice
			// :knowBy rdf:type dec:epistemicReversePredicate
			Node predicate = entry.getKey();
			String assignedDecType = entry.getValue();
			Iterator<Triple> triples = reality.getBaseGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :G1 :knownBy :alice
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecWorld world = worlds.computeIfAbsent(
					s,
					key -> new DecWorld(s.getURI(), "named", dataset.getDatasetGraph().getGraph(s), dataset)
				);
				world.setDecType(assignedDecType);
				String decType = decPrefix + assignedDecType + "World";
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(world.getName()),
					NodeFactory.createURI(RDF_TYPE_URI),
					NodeFactory.createURI(decType)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decWorld.getBaseGraph().add(typeTriple);
				decWorld.getBaseGraph().add(originalTriple);
			});
		}


	}

	private void assignDecPointOfViewPredicates() {
		if (debug >= 1) out("assignDecPointOfViewPredicates",4);

		for (Node predicate : DecPointOfViewPredicates) {
			// Assign dec types from DecPointOfViewPredicates
			// e.g.: :alice :knows :G1 
			// e.g.: :knows rdf:type dec:pointOfViewPredicate	

			Iterator<Triple> triples = dataset.getDefaultGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecWorld world = worlds.computeIfAbsent(
					o,
					key -> new DecWorld(o.getURI(), "named", dataset.getDatasetGraph().getGraph(o), dataset)
				);
				world.setPointOfView(true);
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(world.getName()),
					NodeFactory.createURI(RDF_TYPE_URI),
					NodeFactory.createURI(POINT_OF_VIEW)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decWorld.getBaseGraph().add(typeTriple);
				decWorld.getBaseGraph().add(originalTriple);
			});
		}


		for (Node predicate : DecPointOfViewReversePredicates) {
			// Assign dec types from DecPointOfViewReversePredicates
			// e.g.: :G1 :knownBy :alice
			// :knowBy rdf:type dec:pointOfViewReversePredicate
			Iterator<Triple> triples = dataset.getDefaultGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecWorld world = worlds.computeIfAbsent(
					s,
					key -> new DecWorld(o.getURI(), "named", dataset.getDatasetGraph().getGraph(o), dataset)
				);
				world.setPointOfView(true);
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(world.getName()),
					NodeFactory.createURI(RDF_TYPE_URI),
					NodeFactory.createURI(POINT_OF_VIEW)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decWorld.getBaseGraph().add(typeTriple);
				decWorld.getBaseGraph().add(originalTriple);
			});
		}
		if (debug >= 4) out(dataset, true, true);

	}



	public void assignDecPermeationsAndInferenceSettings() {
		if (debug >= 1) out("assignDecPermeationsAndInferenceSettings",4);
//		realityModels.clear();
//		sharedModels.clear();
		worlds.values().forEach(u -> {
			int decTypeIndex = DecUtils.decCategories.indexOf(u.getDecType());
			Boolean inferenceSetting = DecUtils.DecInferenceSetting[decTypeIndex];
			List<DecWorld> permeations = new ArrayList<>();
			worlds.values().forEach(other -> {
				// out("   I am " + u.getDecType() + " and the other is " + other.getDecType() );
				int otherDecTypeIndex = DecUtils.decCategories.indexOf(other.getDecType());
				if (other != u && DecUtils.DecPermeations[decTypeIndex][otherDecTypeIndex]) {
					permeations.add(other);
				}
			}); 
			u.setPermeations(permeations);
			u.setEnableInference(inferenceSetting);

			});
	}


	public void clearDecData() {
		if (debug >= 1) out("clearDecData",4);

		decWorld = worlds.get(NodeFactory.createURI(decStatementWorldS));

		if (decWorld != null && debug >= 3) out("decStatements: ", DecStatements, false, 8);

		// 1. Clear all special worlds
		Iterator<Node> graphNodes = dataset.getDatasetGraph().listGraphNodes();
		while (graphNodes.hasNext()) {
			Node graphNode = graphNodes.next();
			DecWorld world = worlds.get(graphNode);
			if (world != null && "special".equals(world.getGraphType())) {
				Graph g = dataset.getDatasetGraph().getGraph(graphNode);
				if (g != null) {
					g.clear();
				}
			}
		}
		// 2. Create filtered graph for default graph
		Graph defaultGraph = dataset.getDefaultGraph();
		Graph filteredGraph = GraphFactory.createDefaultGraph();
		ExtendedIterator<Triple> it = defaultGraph.find();
		while (it.hasNext()) {
			Triple t = it.next();
			Node p = t.getPredicate();
			Node o = t.getObject();
			Node s = t.getSubject();
			if (!(
				p.toString().startsWith(decPrefix) ||
				o.isNodeTriple() ||
				s.isNodeTriple() ||
				p.toString().equals(RDF_SUBJECT_URI) || 
				p.toString().equals(RDF_PREDICATE_URI) || 
				p.toString().equals(RDF_OBJECT_URI)
			 )) {
				filteredGraph.add(t);
			}
		}

		// 3. Set filtered graph as base graph for default world
		DecWorld defaultWorld = worlds.get(Node.ANY);
		if (defaultWorld != null) {
			defaultWorld.setBaseGraph(filteredGraph);
			defaultWorld.markNotReady();
		}

		// 4. Clear predicate maps and sets
		DecPredicates.clear();
		DecReversePredicates.clear();
		DecPointOfViewPredicates.clear();
		DecPointOfViewReversePredicates.clear();
		closedForSubjects.clear();
		closedForObjects.clear();
		checkClosedFor = false;

		// 5. Reset flags
		checkInconsistencies = false;
		checkClosedFor = false;
		generateReportAnalysis = false;
	}


	public void verifyClosedFor() {
		if (debug >= 1) out("verifyClosedFor", 4);

		if (!checkClosedFor) return;

		DecWorld decWorld = worlds.get(NodeFactory.createURI(decStatementWorldS));
		Node disagreesWith = NodeFactory.createURI(disagreesWithS);
	
		if (closedForSubjects.isEmpty() && closedForObjects.isEmpty()) {
			if (debug >= 1) out("No closedFor rules found. Skipping.");
			return;
		}
	
			List<Node> worldList = new ArrayList<>(worlds.keySet());
			for (int i = 0; i < worldList.size(); i++) {
				Node N1 = worldList.get(i);
				DecWorld W1 = worlds.get(N1);

				if ("special".equals(W1.getGraphType())) continue;
		
	//			InfModel M1 = W1.getInfModel();
				Graph G1 = W1.getPermeatedGraph();
		
				// Precompute permeations for N1
				List<DecWorld> W1Permeations = W1.getPermeations() == null ? Collections.emptyList() : W1.getPermeations();
		
				for (int j = i + 1; j < worldList.size(); j++) {
					Node N2 = worldList.get(j);
					DecWorld W2 = worlds.get(N2);

					if ("special".equals(W2.getGraphType())) continue;
					List<DecWorld> W2Permeations = W2.getPermeations() == null ? Collections.emptyList() : W2.getPermeations();

					if (W2Permeations.contains(W1) || W1Permeations.contains(W2)) {
						if (debug >= 1) out("Skipping permeated pair",	N1, "->", N2, 8);
						continue;
					}
		
	//				InfModel M2 = W2.getInfModel();
					Graph G2 = W2.getPermeatedGraph();
		
					// Case A: rdf:Subject dec:closedFor <predicate>
					for (Node p : closedForSubjects) {
						if (debug >= 1) out("Checking closed for predicate", p);
						Iterator<Triple> iterator1 = G1.find(Node.ANY, p, Node.ANY);
						while (iterator1.hasNext()) {
							Triple t = iterator1.next();
							Node s = t.getSubject();

							Set<Node> objects1 = new HashSet<>();
							G1.find(s, p, Node.ANY).forEachRemaining(triple -> objects1.add(triple.getObject()));

							Set<Node> objects2 = new HashSet<>();
							G2.find(s, p, Node.ANY).forEachRemaining(triple -> objects2.add(triple.getObject()));

							if (objects1.isEmpty() || objects2.isEmpty()) break;

							Set<Node> matchedInObjects1 = new HashSet<>();
							Set<Node> matchedInObjects2 = new HashSet<>();

							for (Node o1 : objects1) {
								for (Node o2 : objects2) {
									if (o1.equals(o2) || o2.isBlank() || isSameEntity(o1, o2)) {
										matchedInObjects1.add(o1);
										matchedInObjects2.add(o2);
									}
								}
							}

							if (matchedInObjects1.size() <= objects1.size() || matchedInObjects2.size() <= objects2.size()) {
								decWorld.getBaseGraph().add(Triple.create(N1, disagreesWith, N2));
								decWorld.getBaseGraph().add(Triple.create(N2, disagreesWith, N1));
								if (W1.getDecType().equals("reality")) {
									decWorld.getBaseGraph().add(Triple.create(N2, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
								} else if (W2.getDecType().equals("reality")) {
									decWorld.getBaseGraph().add(Triple.create(N1, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
								}
								if (debug >= 1 && !DecUtils.isTrivial(t)) out("Inconsistency between ", N1, " and ", N2, " for predicate ", p, "\n objects1:", objects1, "\n objects2:", objects2, 8);
							}
						}
					}	

					// Case B: rdf:Object dec:closedFor <predicate>

					for (Node p : closedForObjects) {
						Iterator<Triple> iterator1 = G1.find(Node.ANY, p, Node.ANY);
						while (iterator1.hasNext()) {
							Triple t = iterator1.next();
							Node o = t.getObject();

							// Collect all subjects for this object and predicate in G1
							Set<Node> subjects1 = new HashSet<>();
							G1.find(Node.ANY, p, o).forEachRemaining(triple -> subjects1.add(triple.getSubject()));

							// Collect all subjects for this object and predicate in G2
							Set<Node> subjects2 = new HashSet<>();
							G2.find(Node.ANY, p, o).forEachRemaining(triple -> subjects2.add(triple.getSubject()));

							if (subjects1.isEmpty() || subjects2.isEmpty()) break;

							Set<Node> matchedInSubjects1 = new HashSet<>();
							Set<Node> matchedInSubjects2 = new HashSet<>();

							for (Node s1 : subjects1) {
								for (Node s2 : subjects2) {
									if (s1.equals(s2) || s2.isBlank() || isSameEntity(s1, s2)) {
										matchedInSubjects1.add(s1);
										matchedInSubjects2.add(s2);
									}
								}
							}

							if (matchedInSubjects1.size() != subjects1.size() || matchedInSubjects2.size() != subjects2.size()) {
								if (debug >= 1 && !DecUtils.isTrivial(t)) out("checking inconsistency", t, "with ", subjects2, 8);

								decWorld.getBaseGraph().add(Triple.create(N1, disagreesWith, N2));
								decWorld.getBaseGraph().add(Triple.create(N2, disagreesWith, N1));
								if (W1.getDecType().equals("reality")) {
									decWorld.getBaseGraph().add(Triple.create(N2, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
								} else if (W2.getDecType().equals("reality")) {
									decWorld.getBaseGraph().add(Triple.create(N1, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
								}

								if (debug >= 1 && !DecUtils.isTrivial(t)) out("Inconsistency between ", N1, " and ", N2, " for predicate ", p, "\n subjects1:", subjects1, "\n subjects2:", subjects2, 8);
							}
						}
					}
					
				}

			}
		
			if (debug >= 1) out("DecWorld", decWorld.getPermeatedGraph(), false, 8);
	}

	
	public void verifyInconsistencies() {
		if (debug >= 1) out("verifyInconsistencies", 4);

		if (!checkInconsistencies) return;

		DecWorld decWorld = worlds.get(NodeFactory.createURI(decStatementWorldS));

		Node disagreesWith = NodeFactory.createURI(disagreesWithS);
		List<Node> worldList = new ArrayList<>(worlds.keySet());
		for (int i = 0; i < worldList.size(); i++) {
			for (int j = i + 1; j < worldList.size(); j++) {
				Node X = worldList.get(i);
				Node Y = worldList.get(j);
				
				DecWorld worldX = worlds.get(X);
				DecWorld worldY = worlds.get(Y);
				String displayX = worldX.getName();
				String displayY = worldY.getName();
//				Node namedGraphUri = NodeFactory.createURI(decPrefix + displayX + "_" + displayY);


				if ("special".equals(worldX.getDecType()) || "special".equals(worldY.getDecType())) continue;
				if ("shared".equals(worldX.getDecType())  || "shared".equals(worldY.getDecType()))  continue;

				if (debug >= 3) out("Checking disagreements between ", displayX, " and ", displayY, 4);
				
				InfModel xModel = worldX.getInfModel();
				InfModel yModel = worldY.getInfModel();
				Model unionModel = ModelFactory.createUnion(xModel, yModel);


				InfModel permeatedModel = ModelFactory.createInfModel(dataset.getBaseReasoner(), unionModel);

				ValidityReport validityReport = permeatedModel.validate();

				if (!validityReport.isValid()) {
					if (debug >= 3) out("FOUND DISAGREEMENTS BETWEEN ", displayX, " and ", displayY);
					if (generateReportAnalysis) analyzeReports(validityReport, displayX, displayY);
					decWorld.getBaseGraph().add(Triple.create(X, disagreesWith, Y));
					decWorld.getBaseGraph().add(Triple.create(Y, disagreesWith, X));
					if (worldX.getDecType().equals("reality")) {
						decWorld.getBaseGraph().add(Triple.create(Y, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
					} else if (worldY.getDecType().equals("reality")) {
						decWorld.getBaseGraph().add(Triple.create(X, NodeFactory.createURI(RDF_TYPE_URI), NodeFactory.createURI(delusionalWorld)));
					}

				}
			}
		}
	}

	public void analyzeReports(ValidityReport validity, String Xname, String Yname) {
		Node disagreementGraphNode = NodeFactory.createURI(
			disagreementsPrefix + Xname + "_" + Yname
		);
		// Create disagreement world and add type triple
//		DecWorld a = decDataset.AddWorld(disagreementGraphNode, "special");

		dataset.getDefaultGraph().add(Triple.create(
			disagreementGraphNode,
			NodeFactory.createURI(RDF_TYPE_URI),
			NodeFactory.createURI(disagreementType)
		));

		final Graph disagreementGraph;
		if (dataset.getDatasetGraph().getGraph(disagreementGraphNode) == null) {
			disagreementGraph = GraphFactory.createDefaultGraph();
			dataset.getDatasetGraph().addGraph(disagreementGraphNode, disagreementGraph);
		} else {
			disagreementGraph = dataset.getDatasetGraph().getGraph(disagreementGraphNode);
		}

		Iterator<ValidityReport.Report> reports = validity.getReports();
		int errorCount = 0;
		int warningCount = 0;
		while (reports.hasNext()) {
			ValidityReport.Report report = reports.next();
			if (report.isError()) {
				analyzeReport(report, disagreementGraph, errorCount++);
			} else {
				analyzeReport(report, disagreementGraph, warningCount++);
			}
		}
	}

	public void analyzeReport(ValidityReport.Report report, Graph disagreementGraph, int reportId) {
		// Collect all information first
		String description = report.getDescription();
		if (description != null) {
			int firstQuote = description.indexOf('"');
			int lastQuote = description.lastIndexOf('"');
			if (firstQuote >= 0 && lastQuote > firstQuote) {
				description = description.substring(firstQuote + 1, lastQuote);
			}
		}
		String type = report.getType();
		String severity = report.isError() ? "Error" : "Warning";
		String scope = report.getExtension() != null ? report.getExtension().toString() : null;
		String subject = null;
		List<String> implicatedNodes = new ArrayList<>();

		// Parse full description for additional information
		String fullDescription = report.toString();
		if (fullDescription != null) {
			String[] lines = fullDescription.split("\n");
			for (String line : lines) {
				if (line == null) continue;
				line = line.trim();
				if (line.startsWith("Culprit =")) {
					subject = line.substring("Culprit =".length()).trim();
					if (subject.length() > 2) {
						subject = subject.substring(1, subject.length()-1);  // Remove < >
					}
				} else if (line.startsWith("Implicated node:")) {
					String node = line.substring("Implicated node:".length()).trim();
					if (node.length() > 2) {
						node = node.substring(1, node.length()-1);  // Remove < >
					}
					implicatedNodes.add(node);
				}
			}
		}

		// Create report URI based on collected information
		String reportUri = disagreementsPrefix + severity + String.format("%03d", reportId + 1);
		Node reportNode = NodeFactory.createURI(reportUri);

		disagreementGraph.add(Triple.create(
			reportNode,
			NodeFactory.createURI(RDF_TYPE_URI),
			NodeFactory.createURI(disagreementsPrefix + severity)
		));

		if (subject != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(disagreementsPrefix + "subject"),
				NodeFactory.createURI(subject)
			));
		}

		if (type != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(disagreementsPrefix + "rule"),
				NodeFactory.createLiteral(type)
			));
		}

		if (description != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(disagreementsPrefix + "description"),
				NodeFactory.createLiteral(description)
			));
		}

		if (scope != null && !scope.equals(subject)) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(disagreementsPrefix + "scope"),
				NodeFactory.createURI(scope)
			));
		}

		for (String node : implicatedNodes) {
			if (node.equals(subject)) continue;
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(disagreementsPrefix + "implicates"),
				NodeFactory.createURI(node)
			));
		}
	}

	public void evaluatePointsOfView() {
		// we do not evaluate points of view if we are verifying disagreements since they are already included in the validation
		if (!checkInconsistencies) return; 

		if (debug >= 1) out("   evaluatePointsOfView()");
		Node disagreesWith = NodeFactory.createURI(disagreesWithS);
		List<DecWorld> worldList = new ArrayList<>(worlds.values());
		for (DecWorld povWorld : worldList) {
			if (povWorld.isPointOfView()) {
				String povName = povWorld.getName();
				for (DecWorld otherWorld : worldList) {
					if (povWorld == otherWorld) continue;
					if ("shared".equals(otherWorld.getDecType())) continue;
					String otherName = otherWorld.getName();
					InfModel povModel = povWorld.getInfModel();
					InfModel otherModel = otherWorld.getInfModel();
					Model unionModel = ModelFactory.createUnion(povModel, otherModel);
					InfModel permeatedModel = ModelFactory.createInfModel(dataset.getReasoner(), unionModel);
					ValidityReport validityReport = permeatedModel.validate();
					if (!validityReport.isValid()) {
						Node povNode = NodeFactory.createURI(povName);
						Node otherNode = NodeFactory.createURI(otherName);
						dataset.getDefaultGraph().add(Triple.create(povNode, disagreesWith, otherNode));
						if (true || generateReportAnalysis) analyzeReports(validityReport, povName, otherName);
						if (debug >= 1) out("      Point of view " + povName + " disagrees with " + otherName);
					}
				}
			}
		}
	}

	private boolean isSameEntity(Node x, Node y) {
		if (x == null || y == null) return false;
		if (x.equals(y)) return true;
	
		Node realityNode = NodeFactory.createURI(realityWorldS);
		DecWorld reality = worlds.get(realityNode);
		if (reality == null) return false;
	
		Graph g = reality.getPermeatedGraph();
		Node sameAs = NodeFactory.createURI(OWL_SAME_AS_URI);
	
		boolean result = g.contains(x, sameAs, y) || g.contains(y, sameAs, x);
		if (debug >= 3) out("   isSameEntity(" + x + ", " + y + "): " + result);
		return result;
	}

	private void out(Object... args) {
		DecUtils.out(args);
	}

}
