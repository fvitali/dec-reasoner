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

 package org.w3id.conjectures;

import org.w3id.conjectures.handler.*;

import java.util.*; 
import java.util.stream.Stream;
import java.util.function.Supplier;
import org.apache.jena.graph.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.graph.GraphFactory; 
import org.apache.jena.sparql.util.Context;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.sparql.core.Transactional;
import java.util.stream.StreamSupport;
import java.util.Spliterators;
import java.util.Spliterator;
import org.apache.jena.reasoner.ValidityReport;

@SuppressWarnings("unused")
public class DecDataset implements DatasetGraph {
	private static int debug = 1;
	private static final String DEFAULT_LOCATION = "/tmp/dec-dataset";

	private static DecDataset instance;	
	private final DatasetGraph datasetGraph;
	private final Reasoner baseReasoner;
	private final DecReasoner reasoner;

	private final DefaultGraphHandler dgh;
	private final NamedGraphHandler ngh;
	private final RDFStarHandler rsh;
	private final ReificationHandler reh;
	private final DecStatementHandler dsh;

	private final boolean unionDefaultGraph;
	private final boolean allowUpdate;
	private final int queryTimeout;	


	final Map<Node, DecWorld>	worlds 			= new HashMap<>();
	final Map<Quad, String> 		rdfStarTriples		= new HashMap<>();

	private boolean needsPermeating = false;
	public final Object permeatingLock = new Object();
	public volatile boolean isPermeating = false;
	public volatile boolean inferencesPending = false;  

	/**
	 * Constructs a DecDataset with the specified parameters.
	 *
	 * @param location the location of the dataset
	 * @param useTDB2 whether to use TDB2
	 * @param unionDefaultGraph whether to union the default graph
	 * @param allowUpdate whether updates are allowed
	 * @param queryTimeout the query timeout in milliseconds
	 * @param baseReasoner the base reasoner to use
	 */
	public DecDataset(
			String location, 
			boolean useTDB2, 
			boolean unionDefaultGraph, 
			boolean allowUpdate, 
			int queryTimeout, 
			Reasoner baseReasoner, 
			String levels) {

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

		DecUtils.setDebugLevel(levels);
		this.debug = DecUtils.getDebugLevel(1); // Position 1 for DecDataset

		this.dgh = new DefaultGraphHandler(this);
		this.ngh = new NamedGraphHandler(this);
		this.rsh = new RDFStarHandler(this);
		this.reh = new ReificationHandler(this);
		this.dsh = new DecStatementHandler(this);

		this.reasoner = new DecReasoner(this);

	}
	
	/**
	 * Checks if updates are allowed on the dataset.
	 *
	 * @return true if updates are allowed, false otherwise
	 */
	public boolean isAllowUpdate() { return allowUpdate; }

	/**
	 * Gets the query timeout value.
	 *
	 * @return the query timeout in milliseconds
	 */
	public int getQueryTimeout() { return queryTimeout; }

	/**
	 * Gets the singleton instance of DecDataset.
	 *
	 * @return the singleton instance
	 */
	public static DecDataset getInstance() { return instance; }

	/**
	 * Gets the map of worlds in the dataset.
	 *
	 * @return the map of worlds
	 */
	public Map<Node, DecWorld> getWorlds() { return worlds; }

	/**
	 * Gets the base reasoner used by the dataset.
	 *
	 * @return the base reasoner
	 */
	public Reasoner getBaseReasoner() { return baseReasoner; }

	/**
	 * Gets the underlying dataset graph.
	 *
	 * @return the dataset graph
	 */
	public DatasetGraph getDatasetGraph() { return datasetGraph; }

	/**
	 * Gets the custom reasoner used by the dataset.
	 *
	 * @return the custom reasoner
	 */
	public DecReasoner getReasoner() { return reasoner; }

	// These will go soon. 
	public Map<Node, Node[]> getReifications() { return reh.getReifications(); }
	public Map<Node, Triple> getDecStatements() { return dsh.getDecStatements(); }
	public RDFStarHandler getRDFStarHandler() { return rsh; }
	public ReificationHandler getReificationHandler() { return reh; }
	public DecStatementHandler getDecStatementHandler() { return dsh; }
	public DefaultGraphHandler getDefaultGraphHandler() { return dgh; }
	public NamedGraphHandler getNamedGraphHandler() { return ngh; }
	// these will go soon. 

	/**
	 * Marks a specific graph as not ready.
	 *
	 * @param graphName the name of the graph to mark as not ready
	 */
	private void markNotReady(Node graphName) {
		DecWorld world = worlds.get(graphName);
		if (world != null) world.markNotReady();
	}

	/**
	 * Marks all graphs as not ready.
	 */
	private void markAllAsNotReady() {
		worlds.values().forEach(DecWorld::markNotReady); 
	}

	/**
	 * Handles the permeation process for the dataset.
	 */
	public void handlePermeation() {
		if (debug >= 1) DecUtils.out("\npermeating");
		if (debug >= 4) DecUtils.out(worlds);
		isPermeating = true;
		synchronized(permeatingLock) {
			try {
				inferencesPending = false;
				inferencesPending = worlds.values().stream().anyMatch(DecWorld::isNotReady);
				if (!inferencesPending) return;
		
				dsh.clearDecData();
				reh.handleReifications();
				dsh.assignDecTypes();
				dsh.assignDecPermeationsAndInferenceSettings();

				worlds.values().forEach(world -> {
					if (debug >= 1) DecUtils.out("getting PermeatedGraph for", world.getName(), 4); 
					world.getPermeatedGraph();
				});
				dsh.verifyClosedFor();
				dsh.verifyInconsistencies();
				
				prepareRestore();
				reh.restoreReifications();
				rsh.restoreRdfStarTriples();
				dgh.restoreDefaultGraph();
//				checkConsistency();
			} finally {
				isPermeating = false;
			}
		}
		if (debug >= 1) DecUtils.out("Finished permeating\n");
		if (debug >= 4) DecUtils.out(worlds);
	}


	/**
	 * Checks the consistency of the dataset.
	 */
	public void checkConsistency() {

		if (debug >= 1) DecUtils.out("checkConsistency",4);
	worlds.values().forEach(world -> {
		InfModel infModel = world.getInfModel();
		ValidityReport validityReport = infModel.validate();
		if (debug >= 1) {
			DecUtils.out("Validity report for world: " + world.getName(), validityReport.isValid() ? "Valid" : "Invalid", 4);
		}
	});
	}

	/**
	 * Prepares the dataset for restoration.
	 */
	public void prepareRestore() {
		if (debug >= 1) DecUtils.out("prepareRestore",4);
		datasetGraph.getDefaultGraph().clear();
		DecWorld defaultWorld = worlds.computeIfAbsent(Quad.defaultGraphNodeGenerated,
		key -> new DecWorld(Quad.defaultGraphNodeGenerated.getURI(), "default", GraphFactory.createDefaultGraph(), instance));
	}


	/**
	 * Checks if a graph is the default graph.
	 *
	 * @param graphName the name of the graph to check
	 * @return true if the graph is the default graph, false otherwise
	 */
	public boolean isDefaultGraph(Node graphName) {
		return dgh.isRelevant(graphName);
	}

	/**
	 * Gets the default graph of the dataset.
	 *
	 * @return the default graph
	 */
	@Override public Graph getDefaultGraph() {		
		if (needsPermeating) {
			if (!isPermeating) {
					handlePermeation();
					needsPermeating = false;
			}
		}		
		return datasetGraph.getDefaultGraph();
	}

	/**
	 * Gets a specific graph from the dataset.
	 *
	 * @param graphNode the node representing the graph
	 * @return the graph corresponding to the node
	 */
	@Override public Graph getGraph(Node graphNode) {
		if (debug >= 2) DecUtils.out("getGraph", graphNode, needsPermeating?"needsPermeating": " ", 4);
		
		if (needsPermeating) {
			if (!isPermeating) {
					handlePermeation();
					needsPermeating = false;
			}
		}
		if (graphNode == null) {
			return datasetGraph.getDefaultGraph();
		}

		DecWorld world = worlds.get(graphNode);
				
		Graph graph = world != null && world.isModelValid() ? world.getPermeatedGraph() : null;
		return graph;

	}


	/**
	 * Adds a quad to the dataset.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 */
	@Override public void add(Node g, Node s, Node p, Node o) { 
		if (debug >= 2) DecUtils.out("Add", Quad.create(g, s, p, o), 4, true);


		// 1. Handle DEC statements
		if (dsh.isRelevant(g, s, p, o)) {
			Node decStatementNode = dsh.add(g, s, p, o);
			markNotReady(decStatementNode);
			needsPermeating = true;
			return;
		}

		// 2. Handle RDF-star triples in default graph
		if (rsh.isRelevant(g, s, p, o)) {
			Node rdfStarNode = rsh.add(g, s, p, o);
			markNotReady(rdfStarNode);
			needsPermeating = true;
			return; 
		}
 
		// 3. Handle reifications
		if (reh.isRelevant(g, s, p, o)) {
			reh.add(g, s, p, o);
			needsPermeating = true;
			return;
		}

		// 4. Handle default graph's triples
		if (dgh.isRelevant(g, s, p, o)) {
			Node defaultNode = dgh.add(g, s, p, o);
			markNotReady(defaultNode);
			needsPermeating = true;
			return;
		}

		// Otherwise, handle named graphs' triples
		Node namedNode = ngh.add(g, s, p, o);
		markNotReady(namedNode);
		needsPermeating = true;
		return;

	}

	/**
	 * Adds a quad to the dataset.
	 *
	 * @param quad the quad to add
	 */
	@Override public void add(Quad quad) { 
		add(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	/**
	 * Adds all quads from another dataset graph to this dataset.
	 *
	 * @param src the source dataset graph
	 */
	@Override public void addAll(DatasetGraph src) { 
		if (debug >= 2) DecUtils.out("\n   addAll(src)");
		src.find().forEachRemaining(this::add);
		markAllAsNotReady();
		needsPermeating = true;
	}

	/**
	 * Adds a graph to the dataset.
	 *
	 * @param graphName the name of the graph
	 * @param graph the graph to add
	 */
	@Override public void addGraph(Node graphName, Graph graph) { 
		if (debug >= 2) DecUtils.out("\n   addGraph: " + graphName);
		Node defaultGraphNode = Quad.defaultGraphNodeGenerated;
		if (!worlds.containsKey(defaultGraphNode)) {
			DecWorld defaultWorld = new DecWorld(defaultGraphNode.getURI(), "special", datasetGraph.getDefaultGraph(), instance);
			worlds.put(defaultGraphNode, defaultWorld);
		}

		if (graphName.equals(Quad.defaultGraphNodeGenerated)) {
			graph.find().forEachRemaining(triple -> add(Quad.defaultGraphNodeGenerated, triple.getSubject(), triple.getPredicate(), triple.getObject()));
		} else {
			if (debug >= 3) DecUtils.out("   adding named graph " + graphName);
			datasetGraph.addGraph(graphName, graph);
			DecWorld world = worlds.computeIfAbsent(graphName, 
				key -> new DecWorld(graphName.getURI(), "named", graph, instance));
		}			
		markNotReady(graphName);
		needsPermeating = true;
	}



	/**
	 * Clears all data from the dataset.
	 */
	@Override public void clear() { 
		if (debug >= 2) DecUtils.out("clear()");
		try {
			// Wait for permeating to complete
			while (isPermeating) {
				try {
					Thread.sleep(10); // Small delay to prevent busy waiting
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Clear operation interrupted while waiting for permeating", e);
				}
			}
			
			// First clear all worlds to release rule engine locks
			// Use a copy of the values to avoid ConcurrentModificationException
			List<DecWorld> worldList = new ArrayList<>(worlds.values());
			for (DecWorld world : worldList) {
				Graph graph = world.getPermeatedGraph();
				if (graph != null) graph.clear();
			}
			
			// Clear all data structures
			if (debug >= 1) DecUtils.out("Clearing data structures");
			datasetGraph.clear();
			worlds.clear();
			dgh.clear();
			ngh.clear();
			rsh.clear();
			reh.clear();
			dsh.clear();
	
			needsPermeating = true;
		} catch (Exception e) {
			DecUtils.out("Error in clear(): " + e.getMessage());
			throw e;
		}
	}

	
	/**
	 * Deletes a quad from the dataset.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 */
	@Override public void delete(Node g, Node s, Node p, Node o) { 
		if (debug >= 3) DecUtils.out("delete( g,  s,  p,  o)");
		try {
			Quad quad = Quad.create(g, s, p, o);
			// First clear any inference models that might be affected
			DecWorld world = worlds.get(g);
			if (world != null && world.getPermeatedGraph() != null) {
				world.getPermeatedGraph().delete(s, p, o);
			}
			datasetGraph.delete(quad);
			markNotReady(g);
		} catch (Exception e) {
			DecUtils.out("Error in delete: " + e.getMessage());
			throw e;
		}
	}

	/**
	 * Deletes a quad from the dataset.
	 *
	 * @param quad the quad to delete
	 */
	@Override public void delete(Quad quad) { 
		delete(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	/**
	 * Deletes any quads matching the given pattern from the dataset.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 */
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
	
	/**
	 * Finds quads in the dataset.
	 *
	 * @return an iterator over the quads
	 */
	@Override public Iterator<Quad> find() {
		return findInAllWorlds(null, null, null);
	}

	/**
	 * Finds quads in the dataset matching the given pattern.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return an iterator over the matching quads
	 */
	@Override public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
		if (debug >= 2) DecUtils.out("find(Node g, Node s, Node p, Node o): " + g + " " + s + " " + p + " " + o);
		Iterator<Quad> result;
		if (g == Node.ANY) {
			result = findInAllWorlds(s, p, o);
		} else if (g == null || g.equals(Quad.defaultGraphNodeGenerated)) {
			result = findInWorld(Quad.defaultGraphNodeGenerated, s, p, o);
		} else {
			result = findInWorld(g, s, p, o);
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

	/**
	 * Finds quads in the dataset matching the given quad pattern.
	 *
	 * @param quad the quad pattern
	 * @return an iterator over the matching quads
	 */
	@Override public Iterator<Quad> find(Quad quad) {
		return find(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	/**
	 * Finds triples in all worlds matching the given pattern.
	 *
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return an iterator over the matching quads
	 */
	private Iterator<Quad> findInAllWorlds(Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("findInAllWorlds() " , s , p , o, 4);
		List<Iterator<Quad>> iterators = new ArrayList<>();
		iterators.add(findInWorld(Quad.defaultGraphNodeGenerated, s, p, o));
		for (DecWorld world : worlds.values()) {
			iterators.add(findInWorld(NodeFactory.createURI(world.getName()), s, p, o));
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

	/**
	 * Finds triples in a specific world matching the given pattern.
	 *
	 * @param graphName the name of the graph
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return an iterator over the matching quads
	 */
	private Iterator<Quad> findInWorld(Node graphName, Node s, Node p, Node o) {
		// Map both default graph nodes to defaultGraphNodeGenerated
		if (debug >= 3) DecUtils.out("findInWorld: ",graphName , s , p , o, 8);

		if (needsPermeating) {
			if (!isPermeating) {
					handlePermeation();
					needsPermeating = false;
			}
		}		

		while (isPermeating) {
			try {
				Thread.sleep(10); // Small delay to prevent busy waiting
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Collections.emptyIterator();
			}
		}
		
		Graph graph;
		Iterator<Triple> newTriples;


		try {
			if (dgh.isRelevant(graphName)) {
				graph = getDefaultGraph();
			} else {
				DecWorld world = worlds.get(graphName);
				if (world == null) {
					if (debug >= 3) DecUtils.out("findInWorld: world " + graphName + " not found");
					return Collections.emptyIterator();
				}
		
				graph = world.getPermeatedGraph() ;
			}
			
			Iterator<Triple> triples = graph.find(s, p, o);
			List<Triple> list = new ArrayList<>();
			triples.forEachRemaining(list::add);

			newTriples = list.iterator();

		} catch (Exception e) {
			DecUtils.out("Error in findInWorld: " + e.getMessage());
			throw e;
		}

		return new Iterator<Quad>() {
			@Override
			public boolean hasNext() {
				return newTriples.hasNext();
			}
			@Override
			public Quad next() {
				Triple t = newTriples.next();
				return Quad.create(graphName, t.getSubject(), t.getPredicate(), t.getObject());
			}
		};
	}

	/**
	 * Finds quads in the dataset matching the given pattern, excluding the default graph.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return an iterator over the matching quads
	 */
	@Override public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) { return datasetGraph.findNG(g, s, p, o); }
	/**
	 * Gets the prefix map for the dataset.
	 *
	 * @return the prefix map
	 */
	@Override public PrefixMap prefixes() { return datasetGraph.prefixes(); }
	/**
	 * Aborts the current transaction.
	 */
	@Override public void abort() { 
		if (debug >= 3) DecUtils.out("abort()");
		datasetGraph.abort(); 
	}
	/**
	 * Streams all quads in the dataset.
	 *
	 * @return a stream of quads
	 */
	@Override public Stream<Quad> stream() {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(find(), Spliterator.ORDERED), false);
	}

	/**
	 * Streams quads in the dataset matching the given pattern.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return a stream of matching quads
	 */
	@Override public Stream<Quad> stream(Node g, Node s, Node p, Node o) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(find(g, s, p, o), Spliterator.ORDERED), false);
	}
	 /**
	 * Begins a transaction with the specified read/write mode.
	 *
	 * @param readWrite the read/write mode
	 */
	 @Override public void begin(ReadWrite readWrite) { 
			datasetGraph.begin(readWrite);
	}
	/**
	 * Begins a transaction with the specified transaction type.
	 *
	 * @param type the transaction type
	 */
	@Override public void begin(TxnType type) { 		
		try {
			if (isInTransaction()) {
				DecUtils.out("Already in transaction, aborting previous");
				abort();
			}
			datasetGraph.begin(type);
		} catch (Exception e) {
			DecUtils.out("Error in begin(" + type + "): " + e.getMessage());
			throw e;
		}
	}
	/**
	 * Executes a calculation within a transaction.
	 *
	 * @param txnType the transaction type
	 * @param action the action to execute
	 * @return the result of the calculation
	 */
	@Override public <T> T calc(TxnType txnType, Supplier<T> action) { return datasetGraph.calc(txnType, action); }
	/**
	 * Closes the dataset.
	 */
	@Override public void close() { datasetGraph.close(); }
	/**
	 * Commits the current transaction.
	 */
	@Override public void commit() { 
		if (needsPermeating) {
			handlePermeation();
			needsPermeating = false;
		}
		datasetGraph.commit(); 		
		if (datasetGraph.isEmpty()) {
			if (debug >= 1) DecUtils.out("Dataset is empty after commit, calling clear()");
			clear();
		}
	}
	/**
	 * Ends the current transaction.
	 */
	@Override public void end() { datasetGraph.end(); }
	/**
	 * Executes a runnable within a transaction.
	 *
	 * @param txnType the transaction type
	 * @param action the action to execute
	 */
	@Override public void exec(TxnType txnType, Runnable action) { datasetGraph.exec(txnType, action); }
	/**
	 * Executes a runnable.
	 *
	 * @param r the runnable to execute
	 */
	@Override public void execute(Runnable r) { datasetGraph.execute(r); }
	/**
	 * Executes a runnable in read mode.
	 *
	 * @param r the runnable to execute
	 */
	@Override public void executeRead(Runnable r) { datasetGraph.executeRead(r); }
	/**
	 * Executes a runnable in write mode.
	 *
	 * @param r the runnable to execute
	 */
	@Override public void executeWrite(Runnable r) { datasetGraph.executeWrite(r); }
	/**
	 * Promotes the current transaction.
	 *
	 * @return true if the promotion was successful, false otherwise
	 */
	@Override public boolean promote() { return datasetGraph.promote(); }
	/**
	 * Promotes the current transaction with the specified mode.
	 *
	 * @param mode the promotion mode
	 * @return true if the promotion was successful, false otherwise
	 */
	@Override public boolean promote(Transactional.Promote mode) { return datasetGraph.promote(mode); }
	/**
	 * Gets the context for the dataset.
	 *
	 * @return the context
	 */
	@Override public Context getContext() { return datasetGraph.getContext(); }
	/**
	 * Gets the union graph of the dataset.
	 *
	 * @return the union graph
	 */
	@Override public Graph getUnionGraph() { return datasetGraph.getUnionGraph(); }
	/**
	 * Gets the lock for the dataset.
	 *
	 * @return the lock
	 */
	@Override public Lock getLock() { return datasetGraph.getLock(); }
	/**
	 * Lists the graph nodes in the dataset.
	 *
	 * @return an iterator over the graph nodes
	 */
	@Override public Iterator<Node> listGraphNodes() { return datasetGraph.listGraphNodes(); }
	/**
	 * Removes a graph from the dataset.
	 *
	 * @param graphName the name of the graph to remove
	 */
	@Override public void removeGraph(Node graphName) { if (allowUpdate) { datasetGraph.removeGraph(graphName); } }
	/**
	 * Sets the default graph for the dataset.
	 *
	 * @param graph the graph to set as default
	 */
	@Override @Deprecated public void setDefaultGraph(Graph graph) { datasetGraph.setDefaultGraph(graph); }
	/**
	 * Checks if the dataset supports transactions.
	 *
	 * @return true if transactions are supported, false otherwise
	 */
	@Override public boolean supportsTransactions() { return datasetGraph.supportsTransactions(); }
	/**
	 * Checks if the dataset supports transaction abort.
	 *
	 * @return true if transaction abort is supported, false otherwise
	 */
	@Override public boolean supportsTransactionAbort() { return datasetGraph.supportsTransactionAbort(); }
	/**
	 * Gets the size of the dataset.
	 *
	 * @return the size of the dataset
	 */
	@Override public long size() { return datasetGraph.size(); }
	/**
	 * Checks if the dataset is empty.
	 *
	 * @return true if the dataset is empty, false otherwise
	 */
	@Override public boolean isEmpty() { return datasetGraph.isEmpty(); }
	/**
	 * Checks if a transaction is in progress.
	 *
	 * @return true if a transaction is in progress, false otherwise
	 */
	@Override public boolean isInTransaction() { return datasetGraph.isInTransaction(); }
	/**
	 * Gets the transaction mode of the dataset.
	 *
	 * @return the transaction mode
	 */
	@Override public ReadWrite transactionMode() { return datasetGraph.transactionMode(); }
	/**
	 * Gets the transaction type of the dataset.
	 *
	 * @return the transaction type
	 */
	@Override public TxnType transactionType() { return datasetGraph.transactionType(); }
	
	/**
	 * Checks if the dataset contains a specific graph.
	 *
	 * @param graphNode the node representing the graph
	 * @return true if the graph is contained in the dataset, false otherwise
	 */
	@Override public boolean containsGraph(Node graphNode) { 
		if (debug >= 3) DecUtils.out("containsGraph: ", graphNode, 4);
//		DecUtils.printStack(25);
		return datasetGraph.containsGraph(graphNode); 
	}
	/**
	 * Checks if the dataset contains a specific quad.
	 *
	 * @param g the graph node
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return true if the quad is contained in the dataset, false otherwise
	 */
	@Override public boolean contains(Node g, Node s, Node p, Node o) { 
		return contains(Quad.create(g, s, p, o));
	}
	/**
	 * Checks if the dataset contains a specific quad.
	 *
	 * @param quad the quad to check
	 * @return true if the quad is contained in the dataset, false otherwise
	 */
	@Override public boolean contains(Quad quad) {	
		if (debug >= 3) DecUtils.out("contains: " + quad);
		Iterator<Quad> it = find(quad); 
		boolean result = it.hasNext(); 
		if (debug >= 3) DecUtils.out("contains: " + quad + " " + result);
		return result; 
	}







	/**
	 * WE PROBABLY NEED NONE OF THE FOLLOWING FUNCTIONS
	 */




	/**
	 * Optimized method for checking if a triple exists in a specific graph.
	 * This is much faster than contains() for bulk operations as it avoids iterator creation.
	 *
	 * @param graphName the name of the graph
	 * @param s the subject node
	 * @param p the predicate node
	 * @param o the object node
	 * @return true if the triple exists in the graph, false otherwise
	 */
	public boolean containsTripleInGraph(Node graphName, Node s, Node p, Node o) {
		if (debug >= 3) DecUtils.out("containsTripleInGraph: " + graphName + " " + s + " " + p + " " + o);
		
		// Map default graph nodes
		final Node finalGraphName = graphName.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated 
			: graphName;
		
		DecWorld world = worlds.get(finalGraphName);
		if (world == null) {
			return false;
		}
		
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		
		return world.getPermeatedGraph().contains(s, p, o);
	}

	/**
	 * Bulk check for triple membership in a third graph.
	 * Returns a map of triples to their membership status.
	 *
	 * @param targetGraph the graph to check
	 * @param triples the collection of triples to check
	 * @return a map of triples to their membership status
	 */
	public Map<Triple, Boolean> bulkCheckTripleMembership(Node targetGraph, Collection<Triple> triples) {
		if (debug >= 2) DecUtils.out("bulkCheckTripleMembership: checking " + triples.size() + " triples in " + targetGraph);
		
		Map<Triple, Boolean> results = new HashMap<>();
		
		// Map default graph nodes
		final Node finalGraphName = targetGraph.toString().equals("urn:x-arq:DefaultGraph") 
			? Quad.defaultGraphNodeGenerated 
			: targetGraph;
		
		DecWorld world = worlds.get(finalGraphName);
		if (world == null) {
			// If world doesn't exist, no triples can be in it
			triples.forEach(t -> results.put(t, false));
			return results;
		}
		
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				triples.forEach(t -> results.put(t, false));
				return results;
			}
		}
		
		Graph graph = world.getPermeatedGraph();
		for (Triple triple : triples) {
			results.put(triple, graph.contains(triple.getSubject(), triple.getPredicate(), triple.getObject()));
		}
		
		return results;
	}

	/**
	 * Efficient method to move triples from source to target graph, 
	 * checking membership in a third graph during the process.
	 *
	 * @param sourceGraph the source graph
	 * @param targetGraph the target graph
	 * @param checkGraph the graph to check membership
	 * @param triples the collection of triples to move
	 * @param skipIfInCheckGraph whether to skip triples if they exist in the check graph
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
		
		// Get worlds
		DecWorld sourceWorld = worlds.get(finalSourceGraph);
		DecWorld targetWorld = worlds.get(finalTargetGraph);
		DecWorld checkWorld = worlds.get(finalCheckGraph);
		
		if (sourceWorld == null || targetWorld == null) {
			if (debug >= 1) DecUtils.out("Source or target world not found");
			return;
		}
		
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		Graph sourceGraphObj = sourceWorld.getPermeatedGraph();
		Graph targetGraphObj = targetWorld.getPermeatedGraph();
		Graph checkGraphObj = checkWorld != null ? checkWorld.getPermeatedGraph() : null;
		
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
		
		// Mark graphs as needing permeating
		markNotReady(finalSourceGraph);
		markNotReady(finalTargetGraph);
		needsPermeating = true;
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
		
		// Get worlds
		DecWorld world1 = worlds.get(finalGraph1);
		DecWorld world2 = worlds.get(finalGraph2);
		
		if (world1 == null) {
			if (debug >= 1) DecUtils.out("Graph1 world not found");
			return 0;
		}
		
		if (world2 == null) {
			if (debug >= 1) DecUtils.out("Graph2 world not found, nothing to remove");
			return 0;
		}
	
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			}
		}
		
		Graph graph1Obj = world1.getPermeatedGraph();
		Graph graph2Obj = world2.getPermeatedGraph();
		
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
		
		// Mark graph as needing permeating
		markNotReady(finalGraph1);
		needsPermeating = true;
		
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
		
		// Get worlds
		DecWorld world1 = worlds.get(finalGraph1);
		DecWorld world2 = worlds.get(finalGraph2);
		
		if (world1 == null) {
			if (debug >= 1) DecUtils.out("Graph1 world not found, returning empty graph");
			return GraphFactory.createDefaultGraph();
		}
		
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return GraphFactory.createDefaultGraph();
			}
		}
		
		Graph graph1Obj = world1.getPermeatedGraph();
		Graph graph2Obj = world2 != null ? world2.getPermeatedGraph() : GraphFactory.createDefaultGraph();
		
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
		
		// Get worlds
		DecWorld world1 = worlds.get(finalGraph1);
		DecWorld world2 = worlds.get(finalGraph2);
		
		if (world1 == null && world2 == null) {
			if (debug >= 1) DecUtils.out("Both worlds not found, returning empty graph");
			return GraphFactory.createDefaultGraph();
		}
		
		// Wait if permeating is in progress
		while (isPermeating) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return GraphFactory.createDefaultGraph();
			}
		}
		
		Graph graph1Obj = world1 != null ? world1.getPermeatedGraph() : GraphFactory.createDefaultGraph();
		Graph graph2Obj = world2 != null ? world2.getPermeatedGraph() : GraphFactory.createDefaultGraph();
		
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