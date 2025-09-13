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

import java.util.Map;
import java.util.Map.Entry;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Capabilities;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.sparql.core.Quad;

/**
 * DecReasoner is a custom implementation of the Reasoner interface.
 * It binds data and schema graphs to inference models and manages worlds.
 * This class seems to be doing nothing useful except for bind(), yet without it nothing works. 
 */

@SuppressWarnings("unused")
public class DecReasoner implements Reasoner {


	private static int debug = 1;
	private final Reasoner					baseReasoner;
	private final DecDataset				decDataset;

	private final Map<Node, DecWorld>	worlds;
		
	private static final ThreadLocal<Integer> bindDepth = ThreadLocal.withInitial(() -> 0);


	/**
	 * Constructs a DecReasoner with the specified DecDataset.
	 *
	 * @param decDataset the DecDataset containing base reasoner and worlds
	 */
	public DecReasoner(DecDataset decDataset) {
		this.decDataset = decDataset;
		this.baseReasoner = decDataset.getBaseReasoner();		
		this.worlds = decDataset.getWorlds();
		int debugLevel = DecUtils.getDebugLevel(2); // Position 2 for DecReasoner
		// Set the debug level for this class
		this.debug = debugLevel;
	}

	/**
	 * Binds the given graph to this reasoner, creating an inference graph.
	 *
	 * @param data the graph to bind
	 * @return the inference graph
	 */
	@Override
	public InfGraph bind(Graph data) {
		if (debug >= 3) DecUtils.out("DecReasoner: bind");
		int depth = bindDepth.get() + 1;
		bindDepth.set(depth);
		try {
			if (depth > 10) {
				throw new IllegalStateException("DecReasoner.bind() called recursively more than 10 times");
			}
			Node graphNode = null;
			// Find the graph node in worlds
			for (Map.Entry<Node, DecWorld> entry : worlds.entrySet()) {
				if (entry.getValue().getBaseGraph().equals(data)) {
					graphNode = entry.getKey();
					break;
				}
			}
			if (graphNode == null) {
				graphNode = Quad.defaultGraphNodeGenerated; 
				decDataset.addGraph(graphNode, data);
			}
			return (InfGraph) worlds.get(graphNode).getPermeatedGraph();
		} finally {
			bindDepth.set(depth - 1);
		}
	}	

	/**
	 * Binds the given schema model to the reasoner.
	 *
	 * @param model the schema model to bind
	 * @return the reasoner with the schema bound
	 */
	@Override public Reasoner bindSchema(Model model) {
		if (debug >= 3) DecUtils.out("DecReasoner: bindSchema(model)");
		bindSchema(model.getGraph());
		return this;
	}

	/**
	 * Binds the given schema graph to the reasoner.
	 *
	 * @param tbox the schema graph to bind
	 * @return the reasoner with the schema bound
	 */
	@Override public Reasoner bindSchema(Graph tbox) {  
		if (debug >= 3) DecUtils.out("DecReasoner: bindSchema(Graph)");
		Node schemaGraphNode = NodeFactory.createURI(DecUtils.SCHEMA_GRAPH_URI);
		DecWorld schemaWorld = worlds.computeIfAbsent(
			schemaGraphNode, key -> new DecWorld(schemaGraphNode.getURI(), "named", tbox, decDataset)
		);
		schemaWorld.getPermeatedGraph();
		return this;
	}

	/**
	 * Adds a description to the configuration specification.
	 *
	 * @param configSpec the configuration specification model
	 * @param base the base resource
	 */
	@Override public void addDescription(Model configSpec, Resource base) {  baseReasoner.addDescription(configSpec, base); }	
	
	/**
	 * Gets the graph capabilities of the reasoner.
	 *
	 * @return the graph capabilities
	 */
	@Override public Capabilities getGraphCapabilities() { return baseReasoner.getGraphCapabilities(); }
	
	/**
	 * Returns the reasoner capabilities in RDF format.
	 *
	 * @return the capabilities model
	 */
	@Override
	public Model getReasonerCapabilities() {
		return baseReasoner.getReasonerCapabilities();
	}
	
	/**
	 * Sets the derivation logging state.
	 *
	 * @param logOn true to enable logging, false to disable
	 */
	@Override public void setDerivationLogging(boolean logOn) { baseReasoner.setDerivationLogging(logOn); }
	
	/**
	 * Sets a parameter for the reasoner.
	 *
	 * @param parameterUri the URI of the parameter
	 * @param value the value to set
	 */
	@Override public void setParameter(Property parameterUri, Object value) { baseReasoner.setParameter(parameterUri, value); }

	/**
	 * Checks if the reasoner supports a given property.
	 *
	 * @param property the property to check
	 * @return true if the property is supported, false otherwise
	 */
	@Override public boolean supportsProperty(Property property) { return baseReasoner.supportsProperty(property); }

}