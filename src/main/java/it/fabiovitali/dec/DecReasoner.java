/*
 * Copyright (c) 2025 Fabio Vitali
 * 
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

 package it.fabiovitali.dec;

import java.util.*;
import java.util.Optional;

import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.reasoner.rulesys.FBRuleReasoner;
import org.apache.jena.shared.Lock;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class DecReasoner implements Reasoner {
	private static final int debug = 1;
	private final Graph						defaultGraph;
	private final Reasoner					baseReasoner;
	private DatasetGraph					datasetGraph;
	private final DecDataset				decDataset;

	private final Map<Node, DecUniverse>	universes;
	private final Map<Node, Node[]>			reifications;
	private final Map<Node, Quad>			reificationMapping;
	private final Map<Node, Triple>			DecStatements;
	final Map<Node, String>					DecPredicates = new HashMap<>();
	final Map<Node, String>					DecReversePredicates = new HashMap<>();
	final List<Node>						DecPointOfViewPredicates = new ArrayList<>();
	final List<Node>						DecPointOfViewReversePredicates = new ArrayList<>();

	private boolean							inferencesPending = true;
	private String 							defaultGraphDecType = DecUtils.DEFAULT_GRAPH_DEC_TYPE; 
	private String 							defaultNamedGraphDecType = DecUtils.DEFAULT_NAMED_GRAPH_DEC_TYPE;
	private String 							defaultReificationDecType = DecUtils.DEFAULT_REIFICATION_DEC_TYPE;
	private String 							defaultRdfStarDecType = DecUtils.DEFAULT_RDF_STAR_DEC_TYPE;
	private Boolean							verifyDisagreements = true;
	private Boolean							generateReportAnalysis = true;
		
	private static final ThreadLocal<Integer> bindDepth = ThreadLocal.withInitial(() -> 0);

	private List<Model> realityModels = new ArrayList<>();
	private List<Model> sharedModels = new ArrayList<>();
//	private Model unionReality = ModelFactory.createDefaultModel();
//	private Model unionShared = ModelFactory.createDefaultModel();

	public DecReasoner(DecDataset decDataset) {
		this.decDataset = decDataset;
		this.baseReasoner = decDataset.getBaseReasoner();
		this.datasetGraph = decDataset.getDatasetGraph();
		this.defaultGraph = datasetGraph.getDefaultGraph();
		this.verifyDisagreements = decDataset.getVerifyDisagreements();
		this.generateReportAnalysis = decDataset.getGenerateReportAnalysis();
		
		// Initialize maps with empty collections if not available yet
		this.universes = decDataset.getUniverses() != null ? decDataset.getUniverses() : new HashMap<>();
		this.reifications = decDataset.getReifications() != null ? decDataset.getReifications() : new HashMap<>();
		this.reificationMapping = new HashMap<>();
		this.DecStatements = decDataset.getDecStatements() != null ? decDataset.getDecStatements() : new HashMap<>();

		if (debug >= 3) DecUtils.out("DecReasoner constructor: baseReasoner: " + baseReasoner + " datasetGraph: " + datasetGraph + " defaultGraph: " + defaultGraph);
	}

	public void reason() {
		if (debug >= 1) DecUtils.out("\nReasoning");
		if (debug >= 4) DecUtils.out(universes);
		if (debug >= 2) DecUtils.out(decDataset, true, false);
		decDataset.isReasoning = true;
		synchronized(decDataset.reasoningLock) {
			try {
				inferencesPending = false;
				inferencesPending = universes.values().stream().anyMatch(DecUniverse::isNotReady);
				if (!inferencesPending) return;
				if (debug >= 3) DecUtils.out("   Starting reasoning");
				clearDataForReasoning();
				handleReifications();
				assignDecTypes();
				assignDecPermeationsAndInferenceSettings();

				if (debug >= 3) DecUtils.out("Before inference");
				if (debug >= 3) DecUtils.out(decDataset, true, false);
				universes.values().forEach(universe -> {
					universe.getInfModel();
				});
				evaluatePointsOfView();
				if (verifyDisagreements) generateDisagreements();
				prepareRestore();
				restoreReifications();
				restoreRdfStarTriples();
				restoreDefaultGraph();
			} finally {
				decDataset.isReasoning = false;
			}
		}
		if (debug >= 1) DecUtils.out("Finished reasoning\n");
		if (debug >= 4) DecUtils.out(universes);
		if (debug >= 3) DecUtils.out(decDataset, true, false);
	}

	private void clearDataForReasoning() {
		if (debug >= 1) DecUtils.out("   clearDataForReasoning");
		if (debug >= 2) DecUtils.out(decDataset, true, true);

		// 1. Clear all special universes
		Iterator<Node> graphNodes = datasetGraph.listGraphNodes();
		while (graphNodes.hasNext()) {
			Node graphNode = graphNodes.next();
			DecUniverse universe = universes.get(graphNode);
			if (universe != null && "special".equals(universe.getGraphType())) {
				Graph g = datasetGraph.getGraph(graphNode);
				if (g != null) {
					g.clear();
				}
			}
		}

		// 2. Create filtered graph for default graph
		Graph defaultGraph = datasetGraph.getDefaultGraph();
		Graph filteredGraph = GraphFactory.createDefaultGraph();
		ExtendedIterator<Triple> it = defaultGraph.find();
		while (it.hasNext()) {
			Triple t = it.next();
			Node p = t.getPredicate();
			Node o = t.getObject();
			Node s = t.getSubject();
			if (!(
				p.toString().startsWith(DecUtils.decPrefix) ||
				o.isNodeTriple() ||
				s.isNodeTriple() ||
				p.toString().equals(DecUtils.RDF_SUBJECT_URI) || 
				p.toString().equals(DecUtils.RDF_PREDICATE_URI) || 
				p.toString().equals(DecUtils.RDF_OBJECT_URI)
			 )) {
				filteredGraph.add(t);
			}
		}

		// 3. Set filtered graph as base graph for default universe
		DecUniverse defaultUniverse = universes.get(Node.ANY);
		if (defaultUniverse != null) {
			defaultUniverse.setBaseGraph(filteredGraph);
			defaultUniverse.markNotReady();
		}

		// 4. Clear predicate maps
		DecPredicates.clear();
		DecReversePredicates.clear();
		DecPointOfViewPredicates.clear();
		DecPointOfViewReversePredicates.clear();

		// 5. Reset flags
		this.verifyDisagreements = decDataset.getVerifyDisagreements();
		this.generateReportAnalysis = decDataset.getGenerateReportAnalysis();
	}

	private boolean isDecStatement(Triple t) {
		return t.getPredicate().getURI().startsWith(DecUtils.decPrefix);
	}

	private boolean isRdfStarTriple(Triple t) {
		return t.getObject().isNodeTriple();
	}

	private boolean isPartOfRdfStatement(Triple t) {
		String p = t.getPredicate().getURI();
		return p.equals(DecUtils.RDF_SUBJECT_URI) || 
		       p.equals(DecUtils.RDF_PREDICATE_URI) || 
		       p.equals(DecUtils.RDF_OBJECT_URI);
	}

	private void handleReifications() {
		if (debug >= 1) DecUtils.out("   handleReifications");
		
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecUniverse reality = universes.computeIfAbsent(realityNode, key -> new DecUniverse(key.getURI(), "default", GraphFactory.createDefaultGraph(), this));
				
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
				
				DecUniverse reificationUniverse = universes.computeIfAbsent(hashNode, key -> new DecUniverse(hash, "reification", GraphFactory.createDefaultGraph(), baseReasoner));
				reificationUniverse.getBaseGraph().add(components[0], components[1], components[2]);
				reality.getBaseGraph().add(t.getSubject(), t.getPredicate(), hashNode);
				reality.getBaseGraph().delete(t.getSubject(), t.getPredicate(), reificationNode);
				reificationMapping.put(hashNode, new Quad(reificationNode, components[0], components[1], components[2]));
				defaultGraph.delete(t.getSubject(), t.getPredicate(), reificationNode);
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
				
				DecUniverse reificationUniverse = universes.computeIfAbsent(hashNode, key -> new DecUniverse(hash, "reification", GraphFactory.createDefaultGraph(), baseReasoner));
				reificationUniverse.getBaseGraph().add(components[0], components[1], components[2]);				
				reality.getBaseGraph().add(hashNode, t.getPredicate(), t.getObject());
				reality.getBaseGraph().delete(reificationNode, t.getPredicate(), t.getObject());
				defaultGraph.delete(reificationNode, t.getPredicate(), t.getObject());
			}
		}
	}

	private void assignDecTypes() {
		Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
		String rdftype = DecUtils.RDF_TYPE_URI;
		String rdfsrange = DecUtils.RDFS_RANGE_URI;
		String owlrange = DecUtils.OWL_RANGE_URI;
		var DECtypes = DecUtils.DECtypes;
		var DECpredicateTypes = DecUtils.DECpredicateTypes;
		var DECReversePredicateTypes = DecUtils.DECReversePredicateTypes;
		var pov = DecUtils.POINT_OF_VIEW;
		var povPredicate = DecUtils.POINT_OF_VIEW_PREDICATE;
		var povReversePredicate = DecUtils.POINT_OF_VIEW_REVERSE_PREDICATE;

		DecUniverse reality = universes.get(NodeFactory.createURI(DecUtils.realityGraph));
		DecUniverse decUniverse = universes.get(NodeFactory.createURI(DecUtils.decStatementGraph));

		if (debug >= 1) DecUtils.out("   assignDecTypes");
		// Assign dec types from DecStatements
		for (Map.Entry<Node, Triple> entry : DecStatements.entrySet()) {
			Node s = entry.getValue().getSubject();
			Node p = entry.getValue().getPredicate();
			Node o = entry.getValue().getObject();

			String sId = s.isURI() ? s.getURI() : (s.isLiteral() ? s.getLiteralLexicalForm() : s.toString());
			String pId = p.isURI() ? p.getURI() : (p.isLiteral() ? p.getLiteralLexicalForm() : p.toString());
			String oId = o.isURI() ? o.getURI() : (o.isLiteral() ? o.getLiteralLexicalForm() : o.toString());	

			if (debug >= 4) DecUtils.out("   " + sId + " - " + pId + " - " + oId);
			if (pId.equals(rdftype) && DECtypes.containsKey(oId)) {
				// Case 1: :g1 rdf:type dec:epistemicUniverse
				// Sets the decType of the universe :g1 to epistemic, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 1: " + sId + " - " + pId + " - " + oId);
				String decType = DECtypes.get(oId);
				DecUniverse universe = universes.computeIfAbsent(
					s, key -> new DecUniverse(sId, "named", datasetGraph.getGraph(s), this)
				);
				universe.setDecType(decType);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if (pId.equals(rdftype) && oId.equals(pov)) {
				// Case 1-pov: :g1 rdf:type dec:pointOfViewUniverse
				// Sets the pointOfView of the universe ex:g1, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 1-pov: " + sId + " - " + pId + " - " + oId);
				DecUniverse universe = universes.computeIfAbsent(
					s, key -> new DecUniverse(sId, "named", datasetGraph.getGraph(s), this)
				);
				universe.setPointOfView(true);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if ( (pId.equals(rdfsrange) && DECtypes.containsKey(oId)) || 
						(pId.equals(owlrange)  && DECtypes.containsKey(oId))) {
				// Case 2: :knows rdfs:range dec:epistemicUniverse
				// Adds the predicate :knows to the DecPredicates map as epistemic, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 2: " + sId + " - " + pId + " - " + oId);
				String decType = DECtypes.get(oId);
				DecPredicates.put(s, decType);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if ( (pId.equals(rdfsrange) && oId.equals(pov)) || 
						(pId.equals(owlrange)  && oId.equals(pov))) {
				// Case 2-pov: ex:predicate rdfs:range dec:pointOfView
				// Adds the predicate ex:predicate to the DecPointOfViewPredicates map, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 2-pov: " + sId + " - " + pId + " - " + oId);
				DecPointOfViewPredicates.add(s);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if (pId.equals(rdftype) && DECpredicateTypes.containsKey(oId)) {
				// Case 3: ex:predicate rdf:type dec:epistemicPredicate
				// Adds the predicate ex:predicate to the DecPredicates map as epistemic, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 3: " + sId + " - " + pId + " - " + oId);
				String decType = DECpredicateTypes.get(oId);
				DecPredicates.put(s, decType);
				datasetGraph.add(new Quad(decStatementGraph, s, p, o));

			} else if (pId.equals(rdftype) && oId.equals(povPredicate)) {
				// Case 3-pov: ex:predicate rdf:type dec:pointOfViewPredicate
				// Adds the predicate ex:predicate to the DecPointOfViewPredicates map, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 3-pov: " + sId + " - " + pId + " - " + oId);
				DecPointOfViewPredicates.add(s);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if (pId.equals(rdftype) && DECReversePredicateTypes.containsKey(oId)) {
				// Case 4: ex:predicate rdf:type dec:epistemicReversePredicate
				// Adds the predicate ex:predicate to the DecPredicates map as epistemic, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 4: " + sId + " - " + pId + " - " + oId);
				String decType = DECReversePredicateTypes.get(oId);
				DecReversePredicates.put(s, decType);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if (pId.equals(rdftype) && oId.equals(povReversePredicate)) {
				// Case 4-pov: ex:predicate rdf:type dec:pointOfViewReversePredicate
				// Adds the predicate ex:predicate to the DecPredicates map as epistemic, and places the relevant triple in the decStatementGraph
				if (debug >= 2) DecUtils.out("   Case 4-pov: " + sId + " - " + pId + " - " + oId);
				DecPointOfViewReversePredicates.add(s);
				decUniverse.getBaseGraph().add(Triple.create(s, p, o));

			} else if  (pId.equals(defaultGraphDecType) && DECtypes.containsKey(oId)) {
				// Case 5: Set a default DEC type for the default graph
				// [] dec:defaultGraphType dec:reality
				if (debug >= 2) DecUtils.out("   Case 5: " + sId + " - " + pId + " - " + oId);
				defaultGraphDecType = DECtypes.get(oId);
			} else if  (pId.equals(defaultNamedGraphDecType) && DECtypes.containsKey(oId)) {
				// Case 6: Set a default DEC type for all named graphs
					// [] dec:defaultNamedGraphType dec:verbatim
				if (debug >= 2) DecUtils.out("   Case 6: " + sId + " - " + pId + " - " + oId);
				defaultNamedGraphDecType = DECtypes.get(oId);
			} else if  (pId.equals(defaultReificationDecType) && DECtypes.containsKey(oId)) {
				// Case 7: Set a default DEC type for all reifications
				// [] dec:defaultReificationType dec:verbatim
				if (debug >= 2) DecUtils.out("   Case 7: " + sId + " - " + pId + " - " + oId);
				defaultReificationDecType = DECtypes.get(oId);
			} else if  (pId.equals(defaultRdfStarDecType) && DECtypes.containsKey(oId)) {
				// Case 8: Set a default DEC type for all rdf-star triples
				// [] dec:defaultRdfStarType dec:verbatim
				if (debug >= 2) DecUtils.out("   Case 8: " + sId + " - " + pId + " - " + oId);
				defaultRdfStarDecType = DECtypes.get(oId);
			} else if (pId.equals(DecUtils.VERIFY_DISAGREEMENTS)) { 
				// Case 9: Activate or deactive the verification of disagreements
				// [] dec:verifyDisagreements true
				if (debug >= 2) DecUtils.out("   Case 9: " + sId + " - " + pId + " - " + oId);
				verifyDisagreements = !oId.isEmpty() && !oId.equals("false");
			} else if (p.getURI().equals(DecUtils.GENERATE_REPORT_ANALYSIS)) { 
				// Case 10: Activate or deactive the generation of report analysis
				// [] dec:generateReportAnalysis true
				if (debug >= 2) DecUtils.out("   Case 10: " + sId + " - " + pId + " - " + oId);
				generateReportAnalysis = !oId.isEmpty() && !oId.equals("false");
			} else {
				// Case 11: Unhandled dec statement
				DecUtils.out("   Unhandled dec statement: " + entry);
			}
		}
		
		// Assign dec types from DecPredicates
		// e.g.: :alice :knows :G1
		// :knows rdf:type dec:epistemicPredicate
		for (Map.Entry<Node, String> entry : DecPredicates.entrySet()) {
			// For every predicate e.g., :knows such that :knows rdf:type dec:epistemicPredicate
			Node predicate = entry.getKey();
			String assignedDecType = entry.getValue();
			Iterator<Triple> triples = reality.getBaseGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :alice :knows :G1 
				if (debug >= 2) DecUtils.out("   assigning dec type: " + assignedDecType + " to " + triple);
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecUniverse universe = universes.computeIfAbsent(
					o,
					key -> new DecUniverse(o.getURI(), "named", datasetGraph.getGraph(o), this)
				);

				universe.setDecType(assignedDecType);
				String decType = DecUtils.decPrefix + assignedDecType + "Universe";
				// Adds the type triple for the universe
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(universe.getName()),
					NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
					NodeFactory.createURI(decType)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decUniverse.getBaseGraph().add(typeTriple);
				decUniverse.getBaseGraph().add(originalTriple);

			});
		}
		// Assign dec types from DecReversePredicates
		// e.g.: :G1 :knownBy :alice
		// :knowBy rdf:type dec:epistemicReversePredicate
		for (Map.Entry<Node, String> entry : DecReversePredicates.entrySet()) {
			// For every predicate e.g., :knownBy such that :knownBy rdf:type dec:epistemicReversePredicate
			Node predicate = entry.getKey();
			String assignedDecType = entry.getValue();
			Iterator<Triple> triples = reality.getBaseGraph().find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :G1 :knownBy :alice
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecUniverse universe = universes.computeIfAbsent(
					s,
					key -> new DecUniverse(s.getURI(), "named", datasetGraph.getGraph(s), this)
				);
				universe.setDecType(assignedDecType);
				String decType = DecUtils.decPrefix + assignedDecType + "Universe";
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(universe.getName()),
					NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
					NodeFactory.createURI(decType)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decUniverse.getBaseGraph().add(typeTriple);
				decUniverse.getBaseGraph().add(originalTriple);
			});
		}

		// Assign dec types from DecPointOfViewPredicates
		// e.g.: :knows rdf:type dec:pointOfViewPredicate	
		for (Node predicate : DecPointOfViewPredicates) {
			// For every predicate e.g., :knows such that :knows rdf:type dec:pointOfViewPredicate
			Iterator<Triple> triples = defaultGraph.find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :alice :knows :G1
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecUniverse universe = universes.computeIfAbsent(
					o,
					key -> new DecUniverse(o.getURI(), "named", datasetGraph.getGraph(o), this)
				);
				universe.setPointOfView(true);
				// Adds the type triple for the universe
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(universe.getName()),
					NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
					NodeFactory.createURI(DecUtils.POINT_OF_VIEW)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decUniverse.getBaseGraph().add(typeTriple);
				decUniverse.getBaseGraph().add(originalTriple);
			});
		}


		// Assign dec types from DecPointOfViewReversePredicates
		// e.g.: :G1 :knownBy :alice
		// :knowBy rdf:type dec:pointOfViewReversePredicate
		for (Node predicate : DecPointOfViewReversePredicates) {
			// For every predicate e.g., :knownBy such that :knownBy rdf:type dec:pointOfViewReversePredicate
			Iterator<Triple> triples = defaultGraph.find(Node.ANY, predicate, Node.ANY);
			triples.forEachRemaining(triple -> {
				// For every triple e.g., :alice :knows :G1
				Node s = triple.getSubject();
				Node p = triple.getPredicate();
				Node o = triple.getObject();
				DecUniverse universe = universes.computeIfAbsent(
					s,
					key -> new DecUniverse(o.getURI(), "named", datasetGraph.getGraph(o), this)
				);
				universe.setPointOfView(true);
				// Adds the type triple for the universe
				Triple typeTriple = Triple.create(
					NodeFactory.createURI(universe.getName()),
					NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
					NodeFactory.createURI(DecUtils.POINT_OF_VIEW)
				);
				Triple originalTriple = Triple.create(s, p, o);
				decUniverse.getBaseGraph().add(typeTriple);
				decUniverse.getBaseGraph().add(originalTriple);
			});
		}
		if (debug >= 4) DecUtils.out(decDataset, true, true);
	}


	private void assignDecPermeationsAndInferenceSettings() {
		if (debug >= 1) DecUtils.out("   assignDecPermeations and inference settings");
//		realityModels.clear();
//		sharedModels.clear();
		universes.values().forEach(u -> {
			int decTypeIndex = DecUtils.decCategories.indexOf(u.getDecType());
			Boolean inferenceSetting = DecUtils.DecInferenceSetting[decTypeIndex];
			List<DecUniverse> permeations = new ArrayList<>();
			universes.values().forEach(other -> {
				// DecUtils.out("   I am " + u.getDecType() + " and the other is " + other.getDecType() );
				int otherDecTypeIndex = DecUtils.decCategories.indexOf(other.getDecType());
				if (other != u && DecUtils.DecPermeations[decTypeIndex][otherDecTypeIndex]) {
					permeations.add(other);
				}
			}); 
			u.setPermeations(permeations);
			u.setEnableInference(inferenceSetting);

			// Raccogli i modelli per le unioni
/* 
			if ("shared".equals(u.getDecType())) {
				sharedModels.add(ModelFactory.createModelForGraph(u.getBaseGraph()));
			}
			if ("reality".equals(u.getDecType())) {
				realityModels.add(ModelFactory.createModelForGraph(u.getBaseGraph()));
			}
*/
			});
		// Crea le union dinamiche
//		unionReality = createDynamicUnion(realityModels);
//		unionShared = createDynamicUnion(sharedModels);
	}

	private Model createDynamicUnion(List<Model> models) {
		if (models.isEmpty()) return ModelFactory.createDefaultModel();
		Model union = models.get(0);
		for (int i = 1; i < models.size(); i++) {
			union = ModelFactory.createUnion(union, models.get(i));
		}
		return union;
	}

	public boolean isUnionModelValid(InfModel unionInfModel) {
		try {
			unionInfModel.getGraph().size();
			return true;
		} catch (NullPointerException | IllegalStateException e) {
			return false;
		}
	}

	private void generateDisagreements() {
		if (debug >= 1) DecUtils.out("   generate Disagreements");
		DecUniverse decUniverse = universes.get(NodeFactory.createURI(DecUtils.decStatementGraph));

		Node disagreesWith = NodeFactory.createURI(DecUtils.disagreesWith);
		List<Node> universeList = new ArrayList<>(universes.keySet());
		for (int i = 0; i < universeList.size(); i++) {
			for (int j = i + 1; j < universeList.size(); j++) {
				Node X = universeList.get(i);
				Node Y = universeList.get(j);
				
				DecUniverse universeX = universes.get(X);
				DecUniverse universeY = universes.get(Y);
				String displayX = universeX.getName().contains("DefaultGraph") ? "default" : universeX.getName();
				String displayY = universeY.getName().contains("DefaultGraph") ? "default" : universeY.getName();
				Node namedGraphUri = NodeFactory.createURI(DecUtils.decPrefix + displayX + "_" + displayY);

				if ("special".equals(universeX.getGraphType()) || "special".equals(universeY.getGraphType())) {
					continue;
				}
				DecUtils.out("    Checking disagreements between " + displayX + " and " + displayY);
				
				long startTime = System.currentTimeMillis();
				InfModel xModel = universeX.getInfModel();
				InfModel yModel = universeY.getInfModel();
				Model unionModel = ModelFactory.createUnion(xModel, yModel);
				InfModel permeatedModel = ModelFactory.createInfModel(baseReasoner, unionModel);
				ValidityReport validityReport = permeatedModel.validate();
				long endTime = System.currentTimeMillis();
				DecUtils.out("    Validation time for union of " + displayX + " and " + displayY + ": " + (endTime - startTime) + " ms");

				DecUtils.out("    Checked ");
				if (!validityReport.isValid()) {
					if (generateReportAnalysis) analyzeReports(validityReport, displayX, displayY);
					decUniverse.getBaseGraph().add(Triple.create(X, disagreesWith, Y));
					decUniverse.getBaseGraph().add(Triple.create(Y, disagreesWith, X));
				}
			}
		}
	}

	private void analyzeReports(ValidityReport validity, String Xname, String Yname) {
		Node disagreementGraphNode = NodeFactory.createURI(
			DecUtils.disagreementsPrefix + Xname + "_" + Yname
		);
		// Create disagreement universe and add type triple
		DecUniverse a = decDataset.AddUniverse(disagreementGraphNode, "special");

		defaultGraph.add(Triple.create(
			disagreementGraphNode,
			NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
			NodeFactory.createURI(DecUtils.disagreementType)
		));

		final Graph disagreementGraph;
		if (datasetGraph.getGraph(disagreementGraphNode) == null) {
			disagreementGraph = GraphFactory.createDefaultGraph();
			datasetGraph.addGraph(disagreementGraphNode, disagreementGraph);
		} else {
			disagreementGraph = datasetGraph.getGraph(disagreementGraphNode);
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

	private void analyzeReport(ValidityReport.Report report, Graph disagreementGraph, int reportId) {
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
		String reportUri = DecUtils.disagreementsPrefix + severity + String.format("%03d", reportId + 1);
		Node reportNode = NodeFactory.createURI(reportUri);

		disagreementGraph.add(Triple.create(
			reportNode,
			NodeFactory.createURI(DecUtils.RDF_TYPE_URI),
			NodeFactory.createURI(DecUtils.disagreementsPrefix + severity)
		));

		if (subject != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(DecUtils.disagreementsPrefix + "subject"),
				NodeFactory.createURI(subject)
			));
		}

		if (type != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(DecUtils.disagreementsPrefix + "rule"),
				NodeFactory.createLiteral(type)
			));
		}

		if (description != null) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(DecUtils.disagreementsPrefix + "description"),
				NodeFactory.createLiteral(description)
			));
		}

		if (scope != null && !scope.equals(subject)) {
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(DecUtils.disagreementsPrefix + "scope"),
				NodeFactory.createURI(scope)
			));
		}

		for (String node : implicatedNodes) {
			if (node.equals(subject)) continue;
			disagreementGraph.add(Triple.create(
				reportNode,
				NodeFactory.createURI(DecUtils.disagreementsPrefix + "implicates"),
				NodeFactory.createURI(node)
			));
		}
	}

	private void evaluatePointsOfView() {
		// we do not evaluate points of view if we are verifying disagreements since they are already included in the validation
		if (verifyDisagreements) return; 

		if (debug >= 1) DecUtils.out("   evaluatePointsOfView()");
		Node disagreesWith = NodeFactory.createURI(DecUtils.disagreesWith);
		List<DecUniverse> universeList = new ArrayList<>(universes.values());
		for (DecUniverse povUniverse : universeList) {
			if (povUniverse.isPointOfView()) {
				String povName = povUniverse.getName();
				for (DecUniverse otherUniverse : universeList) {
					if (povUniverse == otherUniverse) continue;
					if ("shared".equals(otherUniverse.getDecType())) continue;
					String otherName = otherUniverse.getName();
					InfModel povModel = povUniverse.getInfModel();
					InfModel otherModel = otherUniverse.getInfModel();
					Model unionModel = ModelFactory.createUnion(povModel, otherModel);
					InfModel permeatedModel = ModelFactory.createInfModel(baseReasoner, unionModel);
					ValidityReport validityReport = permeatedModel.validate();
					if (!validityReport.isValid()) {
						Node povNode = NodeFactory.createURI(povName);
						Node otherNode = NodeFactory.createURI(otherName);
						defaultGraph.add(Triple.create(povNode, disagreesWith, otherNode));
						if (true || generateReportAnalysis) analyzeReports(validityReport, povName, otherName);
						if (debug >= 1) DecUtils.out("      Point of view " + povName + " disagrees with " + otherName);
					}
				}
			}
		}
	}

	private void prepareRestore() {
		if (debug >= 1) DecUtils.out("   prepareRestore");
		datasetGraph.getDefaultGraph().clear();
	}

	private void restoreDefaultGraph() {
		if (debug >= 1) DecUtils.out("   restoreDefaultGraph");
		
		// Add all triples from decStatement graph
		Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
		DecUniverse decStatementUniverse = universes.get(decStatementGraph);
		if (decStatementUniverse != null) {
			Graph decStatementGraphGraph = decStatementUniverse.getBaseGraph();
			decStatementGraphGraph.find().forEachRemaining(triple -> {
				if (debug >= 1 && !DecUtils.isTrivial(triple)) DecUtils.out("   Restoring triple: " + triple);
				datasetGraph.add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject());
			});
		}
		
		// Add all triples from reality graph
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecUniverse reality = universes.get(realityNode);
		if (reality != null) {
			reality.getInfModel().getGraph().find().forEachRemaining(triple -> {
				if (debug >= 3 && !DecUtils.isTrivial(triple)) DecUtils.out("   Restoring triple: " + triple);
				datasetGraph.add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject());
			});
		}
		
		// Remove useless graphs and universes
		datasetGraph.removeGraph(decStatementGraph);
		datasetGraph.removeGraph(realityNode);
		universes.remove(realityNode);
	}

	
	private void restoreReifications() {
		if (debug >= 1) DecUtils.out("   restoreReifications");
		if (debug >= 2) DecUtils.out(universes);
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecUniverse realityUniverse = decDataset.getUniverses().get(realityNode);
		if (realityUniverse == null) {
			if (debug >= 1) DecUtils.out("   No reality universe found");
			return;
		}
		Graph realityGraph = realityUniverse.getInfModel().getGraph();

		int statementsCount = 0;
		Graph defaultGraph = datasetGraph.getDefaultGraph();
		Set<Node> universesToRemove = new HashSet<>();
		boolean addAllTriples = false;
		for (Map.Entry<Node, DecUniverse> entry : universes.entrySet()) {
			Node n = entry.getKey();
			DecUniverse universe = entry.getValue();
			if (!universe.getGraphType().equals("reification")) continue;
			if (debug >= 3) DecUtils.out("   Processing reification universe: " + n);

			Quad originalQuad = reificationMapping.get(n);

			// Find triple with n as object
			Iterator<Triple> it = realityUniverse.getInfModel().getGraph().find(Node.ANY, Node.ANY, n);
			if (it.hasNext()) {
				Triple t = it.next();
				Node s = t.getSubject();
				Node p = t.getPredicate();
				if (debug >= 3) DecUtils.out("   Found triple with n as object: " + t);
				
				// Convert each triple in universe to reification statements
				Iterator<Triple> universeTriples = universe.getInfModel().getGraph().find();
				while (universeTriples.hasNext()) {
					Triple reifiedTriple = universeTriples.next();
					if (debug >= 3 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + reifiedTriple);
					Boolean isOrigTriple =	reifiedTriple.getSubject().equals(originalQuad.getSubject()) && 
											reifiedTriple.getPredicate().equals(originalQuad.getPredicate()) && 
											reifiedTriple.getObject().equals(originalQuad.getObject());
					String counter = isOrigTriple ? "" : "-inf" + statementsCount++;
					
					if (DecUtils.isTrivial(reifiedTriple) && !addAllTriples) continue;
					if (debug >= 1 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + reifiedTriple);
					Node name = NodeFactory.createURI(originalQuad.getGraph().getURI() + counter);
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_TYPE_URI), NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI));
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI), reifiedTriple.getSubject());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI), reifiedTriple.getPredicate());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_OBJECT_URI), reifiedTriple.getObject());
					realityGraph.add(s, p, name);
					if (debug >= 3) DecUtils.out("   Added reification triple " + name + " to default graph: " + reifiedTriple.getSubject() + " " + reifiedTriple.getPredicate() + " " + reifiedTriple.getObject());
				}
				
				// Remove original triple from reality graph
				realityUniverse.getBaseGraph().delete(t);
				if (debug >= 3) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			// Find triple with n as subject
			it = realityUniverse.getInfModel().getGraph().find(n, Node.ANY, Node.ANY);
			if (it.hasNext()) {
				Triple t = it.next();
				Node p = t.getPredicate();
				Node o = t.getObject();
				if (debug >= 3) DecUtils.out("   Found triple with n as subject: " + t);
				
				Iterator<Triple> universeTriples = universe.getInfModel().getGraph().find();
				while (universeTriples.hasNext()) {
					Triple reifiedTriple = universeTriples.next();
					if (DecUtils.isTrivial(reifiedTriple) && !addAllTriples) continue;
					if (debug >= 3 && !DecUtils.isTrivial(reifiedTriple)) DecUtils.out("   Processing triple: " + reifiedTriple);
					Node name = NodeFactory.createURI(n.getURI() + "-" + statementsCount++);
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_TYPE_URI), NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI));
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI), reifiedTriple.getSubject());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI), reifiedTriple.getPredicate());
					realityGraph.add(name, NodeFactory.createURI(DecUtils.RDF_OBJECT_URI), reifiedTriple.getObject());
					realityGraph.add(name, p, o);
					if (debug >= 3) DecUtils.out("   Added reification triple " + name + " to default graph: " + reifiedTriple.getSubject() + " " + reifiedTriple.getPredicate() + " " + reifiedTriple.getObject());
				}
				
				// Remove original triple from reality graph
				realityUniverse.getBaseGraph().delete(t);
				if (debug >= 3) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			universesToRemove.add(n);
			if (debug >= 3) DecUtils.out("   Marked universe for removal: " + n);
		}

			// Remove universes after iteration
			for (Node n : universesToRemove) {
				universes.remove(n);
				if (debug >= 3) DecUtils.out("   Removed universe: " + n);
			}
	}

	private void restoreRdfStarTriples() {
		if (debug >= 1) DecUtils.out("   restoreRdfTriples");
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecUniverse realityUniverse = decDataset.getUniverses().get(realityNode);
		if (realityUniverse == null) {
			if (debug >= 1) DecUtils.out("   No reality universe found");
			return;
		}

		Graph defaultGraph = datasetGraph.getDefaultGraph();
		Set<Node> universesToRemove = new HashSet<>();
		boolean addAllTriples = false;
		// XXX MAKE THIS A PARAMETER

		for (Map.Entry<Node, DecUniverse> entry : universes.entrySet()) {
			Node n = entry.getKey();
			DecUniverse universe = entry.getValue();
			if (!universe.getGraphType().equals("rdf-star")) continue;
			if (debug >= 1) DecUtils.out("   Processing RDF-star universe: " + n + " " + universe.getName());

			// Find triple with n as object
			Iterator<Triple> it = realityUniverse.getInfModel().getGraph().find(Node.ANY, Node.ANY, n);
			if (it.hasNext()) {
				Triple t = it.next();
				Node s = t.getSubject();
				Node p = t.getPredicate();
				if (debug >= 3) DecUtils.out("   Found triple with n as object: " + t);
				
				// Convert each triple in universe to RDF-star quad
				Iterator<Triple> universeTriples = universe.getInfModel().getGraph().find();
				while (universeTriples.hasNext()) {
					Triple T = universeTriples.next();
					Node starNode = NodeFactory.createTripleNode(T);
					if (!DecUtils.isTrivial(T) || addAllTriples) {
						realityUniverse.getInfModel().getGraph().add(s, p, starNode);
						if (debug >= 3 && !DecUtils.isTrivial(T)) DecUtils.out("   Added RDF-star triple to reality graph: " + s + " " + p + " " + starNode);
					}
				}
				// Remove original triple from reality graph
				realityUniverse.getBaseGraph().delete(t);
				if (debug >= 1) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			// Find triple with n as subject
			it = realityUniverse.getInfModel().getGraph().find(n, Node.ANY, Node.ANY);
			if (it.hasNext()) {
				Triple t = it.next();
				Node p = t.getPredicate();
				Node o = t.getObject();
				if (debug >= 3) DecUtils.out("   Found triple with n as subject: " + t);
				
				// Convert each triple in universe to RDF-star quad
				Iterator<Triple> universeTriples = universe.getInfModel().getGraph().find();
				while (universeTriples.hasNext()) {
					Triple T = universeTriples.next();
					Node starNode = NodeFactory.createTripleNode(T);
					if (!DecUtils.isTrivial(T) || addAllTriples) {
						realityUniverse.getInfModel().getGraph().add(starNode, p, o);
						if (debug >= 1 && !DecUtils.isTrivial(T)) DecUtils.out("   Added RDF-star triple to default graph: " + starNode + " " + p + " " + o);
					}
				}
				
				// Remove original triple from reality graph
				realityUniverse.getBaseGraph().delete(t);
				if (debug >= 1) DecUtils.out("   Removed original triple from reality graph: " + t);
			}

			universesToRemove.add(n);
			if (debug >= 3) DecUtils.out("   Marked universe for removal: " + n);
			
			// Search and delete triples from DecStatementGraph that have n as subject or object
			Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
			DecUniverse decStatementUniverse = universes.get(decStatementGraph);
			if (decStatementUniverse != null) {
				Graph decStatementGraphGraph = decStatementUniverse.getBaseGraph();
				
				// Collect triples to delete (to avoid ConcurrentModificationException)
				List<Triple> triplesToDelete = new ArrayList<>();
				
				// Find triples with n as subject
				Iterator<Triple> subjectTriples = decStatementGraphGraph.find(n, Node.ANY, Node.ANY);
				while (subjectTriples.hasNext()) {
					Triple t = subjectTriples.next();
					triplesToDelete.add(t);
					if (debug >= 3) DecUtils.out("   Found triple to delete from DecStatementGraph (subject): " + t);
				}
				
				// Find triples with n as object
				Iterator<Triple> objectTriples = decStatementGraphGraph.find(Node.ANY, Node.ANY, n);
				while (objectTriples.hasNext()) {
					Triple t = objectTriples.next();
					triplesToDelete.add(t);
					if (debug >= 3) DecUtils.out("   Found triple to delete from DecStatementGraph (object): " + t);
				}
				
				// Delete all collected triples
				for (Triple t : triplesToDelete) {
					decStatementGraphGraph.delete(t);
					if (debug >= 3) DecUtils.out("   Deleted triple from DecStatementGraph: " + t);
				}
			}
		}

			// Remove universes after iteration
			for (Node n : universesToRemove) {
				universes.remove(n);
				if (debug >= 3) DecUtils.out("   Removed universe: " + n);
			}
	}

	public Graph getGraph(Node graphName) {    
		if (debug >= 3) DecUtils.out("Dec Reasoner getGraph: " + graphName);
		DecUniverse universe = universes.get(graphName);
				
		Graph graph = universe != null && universe.isModelValid() ? universe.getInfModel().getGraph() : null;
		return graph;
	}

	@Override public InfGraph bind(Graph data) {
		if (debug >= 3) DecUtils.out("bind");
		int depth = bindDepth.get() + 1;
		bindDepth.set(depth);
		try {
			if (depth > 10) {
				throw new IllegalStateException("DecReasoner.bind() called recursively more than 10 times");
			}
			Node graphNode = null;
			// Find the graph node in universes
			for (Map.Entry<Node, DecUniverse> entry : universes.entrySet()) {
				if (entry.getValue().getBaseGraph().equals(data)) {
					graphNode = entry.getKey();
					break;
				}
			}
			// If not found, add graph
			if (graphNode == null) {
				graphNode = Quad.defaultGraphNodeGenerated; // or determine the correct node
				decDataset.addGraph(graphNode, data);
			}
			// Return the infGraph of the infModel
			if (debug >= 3) DecUtils.out("bind: " + universes.get(graphNode).getName());
			return (InfGraph) universes.get(graphNode).getInfModel().getGraph();
		} finally {
			bindDepth.set(depth - 1);
		}
	}	

	@Override public Reasoner bindSchema(Model model) {
		bindSchema(model.getGraph());
		return this;
	}

	@Override public Reasoner bindSchema(Graph tbox) {  
		Node schemaGraphNode = NodeFactory.createURI(DecUtils.SCHEMA_GRAPH_URI);
		DecUniverse schemaUniverse = universes.computeIfAbsent(
			schemaGraphNode, key -> new DecUniverse(schemaGraphNode.getURI(), "named", tbox, baseReasoner)
		);
		schemaUniverse.getInfModel();
		return this;
	}

	@Override public void addDescription(Model configSpec, Resource base) {  baseReasoner.addDescription(configSpec, base); }	
	@Override public Capabilities getGraphCapabilities() { return baseReasoner.getGraphCapabilities(); }
	@Override public Model getReasonerCapabilities() { return baseReasoner.getReasonerCapabilities(); }
	@Override public void setDerivationLogging(boolean logOn) { baseReasoner.setDerivationLogging(logOn); }
	@Override public void setParameter(Property parameterUri, Object value) { baseReasoner.setParameter(parameterUri, value); }
	@Override public boolean supportsProperty(Property property) { return baseReasoner.supportsProperty(property); }

	public Map<Node, String> getDecPredicates() {
		return DecPredicates;
	}

	public List<Node> getDecPointOfViewPredicates() {
		return DecPointOfViewPredicates;
	}

	public List<Node> getDecPointOfViewReversePredicates() {
		return DecPointOfViewReversePredicates;
	}

}

