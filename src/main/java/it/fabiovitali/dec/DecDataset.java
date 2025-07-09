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
import java.util.stream.Stream;
import java.util.function.Supplier;
import org.apache.jena.graph.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.graph.GraphFactory; 
import org.apache.jena.sparql.util.Context;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.sparql.core.Transactional;
import java.util.stream.StreamSupport;
import java.util.Spliterators;
import java.util.Spliterator;

@SuppressWarnings("unused")
public class DecDataset implements DatasetGraph {
	private static final int debug = 0;
	private static final String DEFAULT_LOCATION = "/tmp/dec-dataset";
	private static final boolean DEBUG_SHUTDOWN_HOOK = false;  // Toggle this to enable/disable shutdown hook

	private static DecDataset instance;



	
	private final DatasetGraph datasetGraph;
	final DecReasoner reasoner;
	private final Reasoner baseReasoner;

	private final boolean unionDefaultGraph;
	private final boolean allowUpdate;
	private final int queryTimeout;	

	final Map<Node, DecUniverse>	universes 			= new HashMap<>();
	final Map<Node, Triple>			DecStatements		= new HashMap<>();
	final Map<Node, Node[]>			reifications		= new HashMap<>();
	final Map<Quad, String> 		rdfStarTriples		= new HashMap<>();

	private boolean needsReasoning = false;
	public final Object reasoningLock = new Object();
	public volatile boolean isReasoning = false;

	private boolean verifyDisagreements = false;
	private boolean generateReportAnalysis = false;
  

	public DecDataset(
			String location, 
			boolean useTDB2, 
			boolean unionDefaultGraph, 
			boolean allowUpdate, 
			int queryTimeout, 
			Reasoner baseReasoner) {

		DecDataset.instance = this;
		Dataset dataset;
		String datasetLocation = (location != null) ? location : DEFAULT_LOCATION;
		if (useTDB2) {
			if (debug >= 2) DecUtils.out("   accessing TDB2 dataset");
			dataset = TDB2Factory.connectDataset(datasetLocation);
		} else {
			if (debug >= 2) DecUtils.out("   created in-memory dataset");
			dataset = DatasetFactory.create();
		}
		this.datasetGraph = dataset.asDatasetGraph();

		this.unionDefaultGraph = unionDefaultGraph;
		this.allowUpdate = allowUpdate;
		this.queryTimeout = queryTimeout;
		this.baseReasoner = baseReasoner;
		this.reasoner = new DecReasoner(this);


		// Add shutdown hook for CTRL+C if enabled
		if (DEBUG_SHUTDOWN_HOOK) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				DecUtils.out("\nShutdown hook triggered. Printing stack traces:");
				Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
				stacks.forEach((thread, stack) -> {
					DecUtils.out("\nThread: " + thread.getName() + " (" + thread.getState() + ")");
					for (StackTraceElement element : stack) {
						DecUtils.out("  at " + element);
					}
				});
			}));
		}
	}
	
	@Override public void add(Quad quad) { 
		add(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	@Override public void add(Node g, Node s, Node p, Node o) { 
		if (debug >= 2) DecUtils.out("\n   add(g,s,p,o): ");

		/*
			Always add the default graph, just in case
		*/
		Node defaultGraphNode = Quad.defaultGraphNodeGenerated;
		if (!universes.containsKey(defaultGraphNode)) {
			DecUniverse defaultUniverse = new DecUniverse(defaultGraphNode.getURI(), "special", datasetGraph.getDefaultGraph(), baseReasoner);
			universes.put(defaultGraphNode, defaultUniverse);
		}

		// 1. Handle non-default graphs
		if (g != null && !g.equals(Node.ANY) && !g.equals(Quad.defaultGraphIRI) && !g.equals(Quad.defaultGraphNodeGenerated)) {
			if (debug >= 3) DecUtils.out("   adding named graph " + g + " " + s + " " + p + " " + o);
			DecUniverse universe = universes.computeIfAbsent(g, 
				key -> new DecUniverse(g.getURI(), "named", datasetGraph.getGraph(g), baseReasoner));
			datasetGraph.add(new Quad(g, s, p, o));
			markNotReady(g);
			needsReasoning = true;
			return;
		}

		// 2. Handle RDF-star triples in default graph
		if (o.isNodeTriple() || s.isNodeTriple()) {
			if (debug >= 3) DecUtils.out("   adding RDF-star triple ");
			String hash;
			Node s1, p1, o1;
			Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
			DecUniverse realityUniverse = universes.computeIfAbsent(realityNode,
				key -> new DecUniverse(realityNode.getURI(), "default", GraphFactory.createDefaultGraph(), baseReasoner));

			if (o.isNodeTriple()) {
				hash = DecUtils.hash("TRIPLE", p.getURI(), o.toString(), DecUtils.decPrefix + "rdfstar/");
				s1 = o.getTriple().getSubject();
				p1 = o.getTriple().getPredicate();
				o1 = o.getTriple().getObject();
				realityUniverse.getBaseGraph().add(s, p, NodeFactory.createURI(hash));
			} else {
				hash = DecUtils.hash(s.toString(), p.getURI(), "TRIPLE", DecUtils.decPrefix + "rdfstar/");
				s1 = s.getTriple().getSubject();
				p1 = s.getTriple().getPredicate();
				o1 = s.getTriple().getObject();
				realityUniverse.getBaseGraph().add(NodeFactory.createURI(hash), p, o);
			}
			Node hashNode = NodeFactory.createURI(hash);
			DecUniverse hashUniverse = universes.computeIfAbsent(hashNode,
				key -> {
					Graph hashGraph = GraphFactory.createDefaultGraph();
					return new DecUniverse(hash, "rdf-star", hashGraph, baseReasoner);
				});
			hashUniverse.getBaseGraph().add(s1, p1, o1);

//			datasetGraph.add(new Quad(g, s, p, o));
			markNotReady(hashNode);
			needsReasoning = true;
			return;
		}

		// 3. Handle RDF statement parts
		if (p.equals(NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI)) ||
			p.equals(NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI)) ||
			p.equals(NodeFactory.createURI(DecUtils.RDF_OBJECT_URI)) ||
			o.equals(NodeFactory.createURI(DecUtils.RDF_STATEMENT_URI))) {
			if (debug >= 3) DecUtils.out("   adding reification " + g + " " + s + " " + p + " " + o);
			
			Node[] reification = reifications.computeIfAbsent(s, key -> new Node[3]);
			if (p.equals(NodeFactory.createURI(DecUtils.RDF_SUBJECT_URI))) {
				reification[0] = o;	
			} else if (p.equals(NodeFactory.createURI(DecUtils.RDF_PREDICATE_URI))) {
				reification[1] = o;
			} else if (p.equals(NodeFactory.createURI(DecUtils.RDF_OBJECT_URI))) {
				reification[2] = o;
			}
			needsReasoning = true;
			return;
		}

		// 4. Handle DEC statements
		if (s.toString().startsWith(DecUtils.decPrefix) || 
			p.toString().startsWith(DecUtils.decPrefix) || 
			o.toString().startsWith(DecUtils.decPrefix)) {
			if (debug >= 3) DecUtils.out("   adding DEC statement " + g + " " + s + " " + p + " " + o);
			
			Node decStatementGraph = NodeFactory.createURI(DecUtils.decStatementGraph);
			DecUniverse decStatementUniverse = universes.computeIfAbsent(decStatementGraph,
				key -> new DecUniverse(decStatementGraph.getURI(), "special", GraphFactory.createDefaultGraph(), baseReasoner));

			decStatementUniverse.getBaseGraph().add(s, p, o);
			datasetGraph.addGraph(decStatementGraph, decStatementUniverse.getBaseGraph());
			DecStatements.put(s, Triple.create(s, p, o));
			needsReasoning = true;
			return;
		}

		// 5. Handle reality triples
		Node realityNode = NodeFactory.createURI(DecUtils.realityGraph);
		DecUniverse realityUniverse = universes.computeIfAbsent(realityNode,
			key -> new DecUniverse(realityNode.getURI(), "default", GraphFactory.createDefaultGraph(), baseReasoner));
		if(debug >= 3) DecUtils.out("   adding normal triple to realityUniverse: " + s + " " + p + " " + o);
		realityUniverse.getBaseGraph().add(s, p, o);
		datasetGraph.add(g, s, p, o);
		markNotReady(realityNode);
		needsReasoning = true;
		return;
	}


	@Override public void addAll(DatasetGraph src) { 
		if (debug >= 1) DecUtils.out("\n   addAll(src)");
		src.find().forEachRemaining(this::add);
		markAllAsNotReady();
		reasoner.reason(); // Update inferences after bulk add
		needsReasoning = false;
	}

	@Override public void addGraph(Node graphName, Graph graph) { 
		if (debug >= 2) DecUtils.out("\n   addGraph: " + graphName);
		if (!allowUpdate) return;
		Node defaultGraphNode = Quad.defaultGraphNodeGenerated;
		if (!universes.containsKey(defaultGraphNode)) {
			DecUniverse defaultUniverse = new DecUniverse(defaultGraphNode.getURI(), "special", datasetGraph.getDefaultGraph(), baseReasoner);
			universes.put(defaultGraphNode, defaultUniverse);
		}

		if (graphName.equals(Quad.defaultGraphNodeGenerated)) {
			graph.find().forEachRemaining(triple -> add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject()));
		} else {
			if (debug >= 3) DecUtils.out("   adding named graph " + graphName);
			datasetGraph.addGraph(graphName, graph);
			DecUniverse universe = universes.computeIfAbsent(graphName, 
				key -> new DecUniverse(graphName.getURI(), "named", graph, baseReasoner));
		}			
		markNotReady(graphName);
		needsReasoning = true;
	}

	private void processQuadBeforeDeletion(Quad quad) {
		if (debug >= 2) DecUtils.out("processQuadBeforeDeletion(Quad quad): " + quad);
		if (isRdfStarTriple(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject())) {
			handleRdfStar(quad);
		} else if (isReifiedStatement(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject())) {
			handleReification(quad);
		} else if (isDecStatement(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject())) {
			handleDecStatement(quad);
		}
	}

	public DecUniverse AddUniverse(Node graphName, String type) {
		if (debug >= 2) DecUtils.out("   AddUniverse: " + graphName + " " + type + " baseReasoner: " + baseReasoner);
		return universes.computeIfAbsent(
			graphName, 
			key -> {
				Graph graph = datasetGraph.getGraph(graphName);
				if (graph == null) {
					graph = GraphFactory.createDefaultGraph();
					datasetGraph.addGraph(graphName, graph);
				}
				DecUniverse universe = new DecUniverse(graphName.getURI(), type, graph, baseReasoner);
				universe.markReady(); // Mark as ready to avoid infinite recursion
				return universe;
			}
		);
	}

	private void handleRdfStar(Quad quad) {
	}

	private void handleReification(Quad quad) {
	}

	private void handleDecStatement(Quad quad) {
	}

	private void markNotReady(Node graphName) {
		if (debug >= 3) DecUtils.out("markNotReady: " + graphName);
		DecUniverse universe = universes.get(graphName);
		if (universe != null) {
			universe.markNotReady();
		}
	}

	private void markAllAsNotReady() { universes.values().forEach(DecUniverse::markNotReady); }

	@Override public void clear() { 
		if (debug >= 2) DecUtils.out("clear()");
		try {
			// Wait for reasoning to complete
			while (isReasoning) {
				try {
					Thread.sleep(10); // Small delay to prevent busy waiting
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Clear operation interrupted while waiting for reasoning", e);
				}
			}
			
			// First clear all universes to release rule engine locks
			// Use a copy of the values to avoid ConcurrentModificationException
			List<DecUniverse> universeList = new ArrayList<>(universes.values());
			for (DecUniverse universe : universeList) {
				if (universe.getInfModel() != null) {
					InfModel infModel = universe.getInfModel();
					if (infModel.getGraph() instanceof InfGraph) {
						((InfGraph)infModel.getGraph()).getRawGraph().clear();
					}
				}
			}
			
			// Then clear the dataset
			DecUtils.out("Clearing datasetGraph");
			datasetGraph.clear();
			DecUtils.out("Clearing universes");
			universes.clear();
			DecUtils.out("Clearing reifications");
			reifications.clear();
			DecUtils.out("Clearing DecStatements");
			DecStatements.clear();
			needsReasoning = true;
		} catch (Exception e) {
			DecUtils.out("Error in clear(): " + e.getMessage());
			throw e;
		}
	}

	
	@Override public void delete(Node g, Node s, Node p, Node o) { 
		if (debug >= 1) DecUtils.out("delete(Node g, Node s, Node p, Node o): " + g + " " + s + " " + p + " " + o);
		try {
			Quad quad = Quad.create(g, s, p, o);
			processQuadBeforeDeletion(quad);
			// First clear any inference models that might be affected
			DecUniverse universe = universes.get(g);
			if (universe != null && universe.getInfModel() != null) {
				universe.getInfModel().getGraph().delete(s, p, o);
			}
			datasetGraph.delete(quad);
			markNotReady(g);
		} catch (Exception e) {
			DecUtils.out("Error in delete: " + e.getMessage());
			throw e;
		}
	}

	@Override public void delete(Quad quad) { 
		if (debug >= 3) DecUtils.out("delete(): " + quad);
		delete(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	@Override public void deleteAny(Node g, Node s, Node p, Node o) {
		// If all parameters are null, this is a CLEAR ALL operation
		if (debug >= 3) DecUtils.out("deleteAny(): " + g + " " + s + " " + p + " " + o);
		if (g == null && s == null && p == null && o == null) {
			clear();
		} else {
			// Otherwise, proceed with normal deleteAny operation
			datasetGraph.deleteAny(g, s, p, o);
			markNotReady(g);
		}
	}
	
	@Override public Iterator<Quad> find() {
		return findInAllUniverses(null, null, null);
	}

	@Override public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
		if (debug >= 2) DecUtils.out("find(Node g, Node s, Node p, Node o): " + g + " " + s + " " + p + " " + o);
		Iterator<Quad> result;
		if (g == Node.ANY) {
			result = findInAllUniverses(s, p, o);
		} else if (g == null || g.equals(Quad.defaultGraphNodeGenerated)) {
			result = findInUniverse(Quad.defaultGraphNodeGenerated, s, p, o);
		} else {
			result = findInUniverse(g, s, p, o);
		}
		
		// Debug output using a copy
		if (debug >= 4) {
			List<Quad> debugCopy = new ArrayList<>();
			result.forEachRemaining(debugCopy::add);
			DecUtils.out("\n\n\nContent of the result: ");
			debugCopy.stream().limit(10).forEach(q -> DecUtils.out("Quad: " + q));
			// Create fresh iterator to return
			return debugCopy.iterator();
		}
		return result;
	}

	@Override public Iterator<Quad> find(Quad quad) {
		return find(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	@Override public Stream<Quad> stream() {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(find(), Spliterator.ORDERED), false);
	}

	@Override public Stream<Quad> stream(Node g, Node s, Node p, Node o) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(find(g, s, p, o), Spliterator.ORDERED), false);
	}

	/** Finds triples in all universes */
	private Iterator<Quad> findInAllUniverses(Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("findInAllUniverses(Node s, Node p, Node o): " + s + " " + p + " " + o);
		List<Iterator<Quad>> iterators = new ArrayList<>();
		for (DecUniverse universe : universes.values()) {
			iterators.add(findInUniverse(NodeFactory.createURI(universe.getName()), s, p, o));
		}
		return new Iterator<Quad>() {
			private Iterator<Quad> current = iterators.isEmpty() ? Collections.emptyIterator() : iterators.get(0);
			private int index = 0;

			@Override
			public boolean hasNext() {
				while (!current.hasNext() && index < iterators.size() - 1) {
					current = iterators.get(++index);
				}
				return current.hasNext();
			}

			@Override
			public Quad next() {
				if (!hasNext()) throw new NoSuchElementException();
				return current.next();
			}
		};
	}

	/** Finds triples in a specific universe */
	private Iterator<Quad> findInUniverse(Node graphName, Node s, Node p, Node o) {
		// Map both default graph nodes to defaultGraphNodeGenerated
		if (debug >= 2) DecUtils.out("findInUniverse: " + graphName + " " + s + " " + p + " " + o);
		final Node finalGraphName = graphName.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated 
			: graphName;
		
		// If dataset is empty, return empty iterator
		if (datasetGraph.isEmpty()) {
			if (debug >= 4) DecUtils.out("findInUniverse: datasetGraph is empty");
			return Collections.emptyIterator();
		}
		
		DecUniverse universe = universes.get(finalGraphName);
		if (universe == null) {
			return Collections.emptyIterator();
		}

		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10); // Small delay to prevent busy waiting
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Collections.emptyIterator();
			}
		}

		Iterator<Triple> triples = universe.getInfModel().getGraph().find(s, p, o);
		return new Iterator<Quad>() {
			@Override
			public boolean hasNext() {
				return triples.hasNext();
			}
			@Override
			public Quad next() {
				Triple t = triples.next();
				return Quad.create(finalGraphName, t.getSubject(), t.getPredicate(), t.getObject());
			}
		};
	}

	@Override public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) { return datasetGraph.findNG(g, s, p, o); }
	@Override public PrefixMap prefixes() { return datasetGraph.prefixes(); }
	@Override public void abort() { 
		if (debug >= 3) DecUtils.out("abort()");
		datasetGraph.abort(); 
	}
	 @Override public void begin(ReadWrite readWrite) { 
		if (debug >= 3) DecUtils.out("begin(" + readWrite + "): needsReasoning: " + needsReasoning);
			datasetGraph.begin(readWrite);
			DecUtils.out("completed begin(" + readWrite + "): needsReasoning: " + needsReasoning);
	}
	@Override public void begin(TxnType type) { 
		if (debug >= 3) DecUtils.out("begin(" + type + "): needsReasoning: " + needsReasoning);
		try {
			if (isInTransaction()) {
				DecUtils.out("Already in transaction, aborting previous");
				abort();
			}
			datasetGraph.begin(type);
			DecUtils.out("completed begin(" + type + "): needsReasoning: " + needsReasoning);
		} catch (Exception e) {
			DecUtils.out("Error in begin(" + type + "): " + e.getMessage());
			throw e;
		}
	}
	@Override public <T> T calc(TxnType txnType, Supplier<T> action) { return datasetGraph.calc(txnType, action); }
	@Override public void close() { datasetGraph.close(); }
	@Override public void commit() { 
		if (debug >= 3) {
			DecUtils.out("commit()");
		}
		if (needsReasoning) {
			reasoner.reason();
			needsReasoning = false;
		}
		datasetGraph.commit(); 		
		if (datasetGraph.isEmpty()) {
			if (debug >= 1) DecUtils.out("Dataset is empty after commit, calling clear()");
			clear();
		}
	}
	@Override public void end() { 
		if (debug >= 3) DecUtils.out("end()");
		datasetGraph.end(); 
	}
	@Override public void exec(TxnType txnType, Runnable action) { datasetGraph.exec(txnType, action); }
	@Override public void execute(Runnable r) { datasetGraph.execute(r); }
	@Override public void executeRead(Runnable r) { datasetGraph.executeRead(r); }
	@Override public void executeWrite(Runnable r) { datasetGraph.executeWrite(r); }
	@Override public boolean promote() { return datasetGraph.promote(); }
	@Override public boolean promote(Transactional.Promote mode) { return datasetGraph.promote(mode); }
	@Override public Context getContext() { return datasetGraph.getContext(); }
	@Override public Graph getUnionGraph() { return datasetGraph.getUnionGraph(); }
	@Override public Lock getLock() { return datasetGraph.getLock(); }
	@Override public Iterator<Node> listGraphNodes() { return datasetGraph.listGraphNodes(); }
	@Override public void removeGraph(Node graphName) { if (allowUpdate) { datasetGraph.removeGraph(graphName); } }
	@Override @Deprecated public void setDefaultGraph(Graph graph) { datasetGraph.setDefaultGraph(graph); }
	@Override public boolean supportsTransactions() { return datasetGraph.supportsTransactions(); }
	@Override public boolean supportsTransactionAbort() { return datasetGraph.supportsTransactionAbort(); }
	@Override public long size() { return datasetGraph.size(); }
	@Override public boolean isEmpty() { return datasetGraph.isEmpty(); }
	@Override public boolean isInTransaction() { return datasetGraph.isInTransaction(); }
	@Override public ReadWrite transactionMode() { return datasetGraph.transactionMode(); }
	@Override public TxnType transactionType() { return datasetGraph.transactionType(); }
	
	public boolean isAllowUpdate() { return allowUpdate; }
	public int getQueryTimeout() { return queryTimeout; }
	public static DecDataset getInstance() { return instance; }

	public Map<Node, DecUniverse> getUniverses() { return universes; }
	public Map<Node, Node[]> getReifications() { return reifications; }
	public Map<Node, Triple> getDecStatements() { return DecStatements; }
	public Reasoner getBaseReasoner() { return baseReasoner; }
	public DecReasoner getReasoner() { return reasoner; }
	public DatasetGraph getDatasetGraph() { return datasetGraph; }

	@Override public boolean containsGraph(Node graphNode) {
		if (debug >= 3) DecUtils.out("containsGraph: " + graphNode);
		return datasetGraph.containsGraph(graphNode);
	}

	@Override public boolean contains(Node g, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("contains: " + g + " " + s + " " + p + " " + o);
		return find(g, s, p, o).hasNext();
	}

	@Override public boolean contains(Quad quad) {
		if (debug >= 3) DecUtils.out("contains: " + quad);
		return find(quad).hasNext();
	}

	@Override public Graph getDefaultGraph() {
		if (debug >= 2) DecUtils.out("getDefaultGraph()");
		return getGraph(Quad.defaultGraphNodeGenerated);
	}

	@Override public Graph getGraph(Node graphNode) {
		if (debug >= 2) DecUtils.out("getGraph(Node graphNode): " + graphNode + " needsReasoning: " + needsReasoning);
		
		if (needsReasoning) {
			// Start reasoning in background if not already running
			if (!isReasoning) {
					reasoner.reason();
					needsReasoning = false;
			}
		}
		
		Node targetNode = (graphNode == null) ? Quad.defaultGraphNodeGenerated : graphNode;
		if (debug >= 3) DecUtils.out("About to getGraph from reasoner: " );
		Graph graph = reasoner.getGraph(targetNode);
		if (graph == null) {
			graph = GraphFactory.createDefaultGraph();
			datasetGraph.addGraph(targetNode, graph);
		}
				
		return graph;
	}

	private boolean isRdfStarTriple(Node g, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("isRdfStarTriple(): " + g + s + p + o);
		return s.isNodeTriple() || o.isNodeTriple();
	}

	private boolean isReifiedStatement(Node g, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("isReifiedStatement(): " + g + s + p + o);
		return p.equals(RDF.subject.asNode()) ||
			   p.equals(RDF.predicate.asNode()) ||
			   p.equals(RDF.object.asNode());
	}

	private boolean isDecStatement(Node g, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("isDecStatement(): " + g + s + p + o);
		return (s.isURI() && s.getURI().startsWith(DecUtils.decPrefix)) ||
			   (p.isURI() && p.getURI().startsWith(DecUtils.decPrefix)) ||
			   (o.isURI() && o.getURI().startsWith(DecUtils.decPrefix));
	}

	public Boolean getVerifyDisagreements() {
		return this.verifyDisagreements;
	}

	public Boolean getGenerateReportAnalysis() {
		return this.generateReportAnalysis;
	}

	/**
	 * Optimized method for checking if a triple exists in a specific graph.
	 * This is much faster than contains() for bulk operations as it avoids iterator creation.
	 */
	public boolean containsTripleInGraph(Node graphName, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("containsTripleInGraph: " + graphName + " " + s + " " + p + " " + o);
		
		// Map default graph nodes
		final Node finalGraphName = graphName.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated 
			: graphName;
		
		DecUniverse universe = universes.get(finalGraphName);
		if (universe == null) {
			return false;
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		
		return universe.getInfModel().getGraph().contains(s, p, o);
	}

	/**
	 * Bulk check for triple membership in a third graph.
	 * Returns a map of triples to their membership status.
	 */
	public Map<Triple, Boolean> bulkCheckTripleMembership(Node targetGraph, Collection<Triple> triples) {
		if (debug >= 2) DecUtils.out("bulkCheckTripleMembership: checking " + triples.size() + " triples in " + targetGraph);
		
		Map<Triple, Boolean> results = new HashMap<>();
		
		// Map default graph nodes
		final Node finalGraphName = targetGraph.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated 
			: targetGraph;
		
		DecUniverse universe = universes.get(finalGraphName);
		if (universe == null) {
			// If universe doesn't exist, no triples can be in it
			triples.forEach(t -> results.put(t, false));
			return results;
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				triples.forEach(t -> results.put(t, false));
				return results;
			}
		}
		
		Graph graph = universe.getInfModel().getGraph();
		for (Triple triple : triples) {
			results.put(triple, graph.contains(triple.getSubject(), triple.getPredicate(), triple.getObject()));
		}
		
		return results;
	}

	/**
	 * Efficient method to move triples from source to target graph, 
	 * checking membership in a third graph during the process.
	 */
	public void moveTriplesWithThirdGraphCheck(Node sourceGraph, Node targetGraph, Node checkGraph, 
											  Collection<Triple> triples, boolean skipIfInCheckGraph) {
		if (debug >= 1) DecUtils.out("moveTriplesWithThirdGraphCheck: moving " + triples.size() + 
									" triples from " + sourceGraph + " to " + targetGraph + 
									", checking " + checkGraph);
		
		// Map graph names
		final Node finalSourceGraph = sourceGraph.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : sourceGraph;
		final Node finalTargetGraph = targetGraph.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : targetGraph;
		final Node finalCheckGraph = checkGraph.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : checkGraph;
		
		// Get universes
		DecUniverse sourceUniverse = universes.get(finalSourceGraph);
		DecUniverse targetUniverse = universes.get(finalTargetGraph);
		DecUniverse checkUniverse = universes.get(finalCheckGraph);
		
		if (sourceUniverse == null || targetUniverse == null) {
			if (debug >= 1) DecUtils.out("Source or target universe not found");
			return;
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		Graph sourceGraphObj = sourceUniverse.getInfModel().getGraph();
		Graph targetGraphObj = targetUniverse.getInfModel().getGraph();
		Graph checkGraphObj = checkUniverse != null ? checkUniverse.getInfModel().getGraph() : null;
		
		int moved = 0;
		int skipped = 0;
		
		for (Triple triple : triples) {
			// Check if triple exists in source graph
			if (!sourceGraphObj.contains(triple.getSubject(), triple.getPredicate(), triple.getObject())) {
				continue;
			}
			
			// Check third graph membership if needed
			if (checkGraphObj != null && checkGraphObj.contains(triple.getSubject(), triple.getPredicate(), triple.getObject())) {
				if (skipIfInCheckGraph) {
					skipped++;
					continue;
				}
			}
			
			// Move the triple
			sourceGraphObj.delete(triple.getSubject(), triple.getPredicate(), triple.getObject());
			targetGraphObj.add(triple.getSubject(), triple.getPredicate(), triple.getObject());
			moved++;
		}
		
		if (debug >= 1) DecUtils.out("Moved " + moved + " triples, skipped " + skipped);
		
		// Mark graphs as needing reasoning
		markNotReady(finalSourceGraph);
		markNotReady(finalTargetGraph);
		needsReasoning = true;
	}

	/**
	 * VERY FAST difference operation: removes from graph1 all triples that exist in graph2.
	 * This is much faster than checking triples individually as it uses HashSet for fast membership checking.
	 * 
	 * @param graph1 The graph to remove triples from
	 * @param graph2 The graph containing triples to remove from graph1
	 * @return Number of triples removed
	 */
	public long removeGraph(Node graph1, Node graph2) {
		if (debug >= 1) DecUtils.out("removeGraph: removing triples from " + graph1 + " that exist in " + graph2);
		
		// Map graph names
		final Node finalGraph1 = graph1.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph1;
		final Node finalGraph2 = graph2.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph2;
		
		// Get universes
		DecUniverse universe1 = universes.get(finalGraph1);
		DecUniverse universe2 = universes.get(finalGraph2);
		
		if (universe1 == null) {
			if (debug >= 1) DecUtils.out("Graph1 universe not found");
			return 0;
		}
		
		if (universe2 == null) {
			if (debug >= 1) DecUtils.out("Graph2 universe not found, nothing to remove");
			return 0;
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			}
		}
		
		Graph graph1Obj = universe1.getInfModel().getGraph();
		Graph graph2Obj = universe2.getInfModel().getGraph();
		
		long initialSize = graph1Obj.size();
		
		// Build HashSet of triples from graph2 for fast membership checking
		Set<Triple> graph2Triples = new HashSet<>();
		graph2Obj.find().forEachRemaining(graph2Triples::add);
		
		// Find triples in graph1 that are not in graph2
		List<Triple> triplesToKeep = new ArrayList<>();
		graph1Obj.find().forEachRemaining(triple -> {
			if (!graph2Triples.contains(triple)) {
				triplesToKeep.add(triple);
			}
		});
		
		// Replace graph1 with the filtered triples
		graph1Obj.clear();
		triplesToKeep.forEach(triple -> 
			graph1Obj.add(triple.getSubject(), triple.getPredicate(), triple.getObject())
		);
		
		long finalSize = graph1Obj.size();
		long removed = initialSize - finalSize;
		
		if (debug >= 1) DecUtils.out("Removed " + removed + " triples from " + graph1);
		
		// Mark graph as needing reasoning
		markNotReady(finalGraph1);
		needsReasoning = true;
		
		return removed;
	}

	/**
	 * VERY FAST difference operation: returns a new graph containing triples from graph1 
	 * that do not exist in graph2. Original graphs are not modified.
	 * Uses HashSet for fast membership checking instead of slow contains() calls.
	 * 
	 * @param graph1 The source graph
	 * @param graph2 The graph to subtract from graph1
	 * @return A new graph containing the difference (graph1 - graph2)
	 */
	public Graph difference(Node graph1, Node graph2) {
		if (debug >= 1) DecUtils.out("difference: computing " + graph1 + " - " + graph2);
		
		// Map graph names
		final Node finalGraph1 = graph1.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph1;
		final Node finalGraph2 = graph2.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph2;
		
		// Get universes
		DecUniverse universe1 = universes.get(finalGraph1);
		DecUniverse universe2 = universes.get(finalGraph2);
		
		if (universe1 == null) {
			if (debug >= 1) DecUtils.out("Graph1 universe not found, returning empty graph");
			return GraphFactory.createDefaultGraph();
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return GraphFactory.createDefaultGraph();
			}
		}
		
		Graph graph1Obj = universe1.getInfModel().getGraph();
		Graph graph2Obj = universe2 != null ? universe2.getInfModel().getGraph() : GraphFactory.createDefaultGraph();
		
		// Build HashSet of triples from graph2 for fast membership checking
		Set<Triple> graph2Triples = new HashSet<>();
		graph2Obj.find().forEachRemaining(graph2Triples::add);
		
		// Create result graph with triples from graph1 that are not in graph2
		Graph result = GraphFactory.createDefaultGraph();
		graph1Obj.find().forEachRemaining(triple -> {
			if (!graph2Triples.contains(triple)) {
				result.add(triple);
			}
		});
		
		if (debug >= 1) DecUtils.out("Difference contains " + result.size() + " triples");
		
		return result;
	}

	/**
	 * VERY FAST symmetric difference: returns triples that are in either graph1 or graph2, 
	 * but not in both (XOR operation).
	 * Uses HashSet for fast membership checking instead of slow contains() calls.
	 * 
	 * @param graph1 First graph
	 * @param graph2 Second graph  
	 * @return A new graph containing the symmetric difference
	 */
	public Graph symmetricDifference(Node graph1, Node graph2) {
		if (debug >= 1) DecUtils.out("symmetricDifference: computing " + graph1 + " XOR " + graph2);
		
		// Map graph names
		final Node finalGraph1 = graph1.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph1;
		final Node finalGraph2 = graph2.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated : graph2;
		
		// Get universes
		DecUniverse universe1 = universes.get(finalGraph1);
		DecUniverse universe2 = universes.get(finalGraph2);
		
		if (universe1 == null && universe2 == null) {
			if (debug >= 1) DecUtils.out("Both universes not found, returning empty graph");
			return GraphFactory.createDefaultGraph();
		}
		
		// Wait if reasoning is in progress
		while (isReasoning) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return GraphFactory.createDefaultGraph();
			}
		}
		
		Graph graph1Obj = universe1 != null ? universe1.getInfModel().getGraph() : GraphFactory.createDefaultGraph();
		Graph graph2Obj = universe2 != null ? universe2.getInfModel().getGraph() : GraphFactory.createDefaultGraph();
		
		// Build HashSets for fast membership checking
		Set<Triple> graph1Triples = new HashSet<>();
		Set<Triple> graph2Triples = new HashSet<>();
		graph1Obj.find().forEachRemaining(graph1Triples::add);
		graph2Obj.find().forEachRemaining(graph2Triples::add);
		
		// Create result graph with symmetric difference
		Graph result = GraphFactory.createDefaultGraph();
		
		// Add triples from graph1 that are not in graph2
		graph1Triples.forEach(triple -> {
			if (!graph2Triples.contains(triple)) {
				result.add(triple);
			}
		});
		
		// Add triples from graph2 that are not in graph1
		graph2Triples.forEach(triple -> {
			if (!graph1Triples.contains(triple)) {
				result.add(triple);
			}
		});
		
		if (debug >= 1) DecUtils.out("Symmetric difference contains " + result.size() + " triples");
		
		return result;
	}
}