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

import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.graph.Triple;


import java.util.*;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.File;

@SuppressWarnings("unused")
public class DecUtils {
	private static final int debug = 0;
	private DecUtils() {} // Private constructor to prevent instantiation

	public static final String decPrefix = "http://dec.fabiovitali.it/";
//	public static final String disagreementsPrefix = "http://disagreements.fabiovitali.it/";
	public static final String disagreementsPrefix = decPrefix + "disagreements/";
	public static final String decStatementGraph = decPrefix + "statements" ;
	public static final String disagreesWith = decPrefix + "disagreesWith";
	public static final String disagreementType = decPrefix + "DisagreementReport";
	public static final String realityGraph = decPrefix + "reality";

	public static final String DEFAULT_LOCATION = "/tmp/dec-dataset";
	public static final String SERVICE_NAME = "dec";

	public static final String DEFAULT_GRAPH_DEC_TYPE = "reality" ;
	public static final String DEFAULT_NAMED_GRAPH_DEC_TYPE = "verbatim" ;
	public static final String DEFAULT_REIFICATION_DEC_TYPE = "verbatim" ;
	public static final String DEFAULT_RDF_STAR_DEC_TYPE = "verbatim" ;
	public static final String DEFAULT_SPECIAL_DEC_TYPE = "special" ;

	public static final boolean DEFAULT_GRAPH_ENABLED = true ;
	public static final boolean DEFAULT_NAMED_GRAPH_ENABLED = false ;
	public static final boolean DEFAULT_REIFICATION_ENABLED = false ;
	public static final boolean DEFAULT_RDF_STAR_ENABLED = false ;
	public static final boolean DEFAULT_SPECIAL_ENABLED = false ;

	public static final String VERIFY_DISAGREEMENTS = decPrefix + "verifyDisagreements" ;
	public static final String GENERATE_REPORT_ANALYSIS = decPrefix + "generateReportAnalysis" ;
	
	// DEC type constants
	public static final int VERBATIM = 0;
	public static final int DOXASTIC = 1;
	public static final int EPISTEMIC = 2;
	public static final int CONJECTURAL = 3;
	public static final int NIRVANA = 4;
	public static final int SHARED = 5;
	public static final int REALITY = 6;
	public static final int UNION = 7;
	public static final int COLORED = 8;
	public static final int SPECIAL = 9;
	public static final int ERROR = 10;

	// Permeation matrix
	static int numOfDecTypes = 11;
	public static final boolean[][] DecPermeations = new boolean[numOfDecTypes][numOfDecTypes];
	public static final boolean[]   DecInferenceSetting = new boolean[numOfDecTypes];

	// RDF Vocabulary URIs
	public static final String RDF_TYPE_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" ;
	public static final String RDFS_RANGE_URI = "http://www.w3.org/2000/01/rdf-schema#range" ;
	public static final String OWL_RANGE_URI = "http://www.w3.org/2002/07/owl#range" ;
	public static final String RDF_SUBJECT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject";
	public static final String RDF_PREDICATE_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate";
	public static final String RDF_OBJECT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object";
	public static final String RDF_STATEMENT_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement";

	// Schema Graph URI
	public static final String SCHEMA_GRAPH_URI = decPrefix + "schemaGraph";

	public static final List<String> decCategories = List.of(
		"verbatim", "doxastic", "epistemic", "conjectural",
		"nirvana", "shared", "reality", "union", "colored", "error", "special"
	);

	public static final String POINT_OF_VIEW = decPrefix + "pointofview";
	public static final String POINT_OF_VIEW_PREDICATE = decPrefix + "pointofviewPredicate";
	public static final String POINT_OF_VIEW_REVERSE_PREDICATE = decPrefix + "pointofviewReversePredicate";

	public static final Map<String, String> DECtypes = new HashMap<>();
	public static final Map<String, String> DECpredicateTypes = new HashMap<>();
	public static final Map<String, String> DECReversePredicateTypes = new HashMap<>();
	static {
		for (String category : decCategories) {
			DECtypes.put(decPrefix + category + "Universe", category);
			DECpredicateTypes.put(decPrefix + category + "Predicate", category);
			DECReversePredicateTypes.put(decPrefix + category + "ReversePredicate", category);
		}

		// Set permeations
		   // No permeations for VERBATIM universes
		   DecInferenceSetting[VERBATIM] = false;

		   // Permeations for DOXASTIC universes
		   DecInferenceSetting[DOXASTIC] = true;
		   DecPermeations[DOXASTIC][SHARED] = true;

		   // Permeations for EPISTEMIC universes
		   DecInferenceSetting[EPISTEMIC] = true;
		   DecPermeations[EPISTEMIC][SHARED] = true;

		   // Permeations for CONJECTURAL universes
		   DecInferenceSetting[CONJECTURAL] = true;
		   DecPermeations[CONJECTURAL][SHARED] = true;
		   DecPermeations[CONJECTURAL][REALITY] = true;

		   // Permeations for NIRVANA universes
		   DecInferenceSetting[NIRVANA] = true;
		   DecPermeations[NIRVANA][SHARED] = true;
		   DecPermeations[NIRVANA][REALITY] = true ;

		   // Permeations for SHARED universes	
		   DecInferenceSetting[SHARED] = true;
		   DecPermeations[SHARED][SHARED] = true;

		   // Permeations for REALITY universes	
		   DecInferenceSetting[REALITY] = true;
		   DecPermeations[REALITY][REALITY] = true;
		   DecPermeations[REALITY][SHARED] = true;
		   DecPermeations[REALITY][EPISTEMIC] = true;
		   DecPermeations[REALITY][NIRVANA] = true;
		   DecPermeations[REALITY][UNION] = true;

		   // No permeations for UNION universes
		   DecInferenceSetting[UNION] = false;
		   
		   // Permeations for COLORED universes
		   DecInferenceSetting[COLORED] = true;
		   DecPermeations[COLORED][SHARED] = true;

		   // No permeations for ERROR universes
		   DecInferenceSetting[ERROR] = false;

		   // No permeations for SPECIAL universes
		   DecInferenceSetting[SPECIAL] = false;
	}

	public static String hash(String s, String p, String o, String prefix) {
		String combined = String.join("\n", 
			prefix,
			s != null ? s : "",
			p != null ? p : "",
			o != null ? o : ""
		);
		return prefix + Integer.toHexString(combined.hashCode());
	}

	
	public static void out(Object s) {
		System.out.println(s);
	}

	public static <K, V> String printMap(Map<K, V> map) {
		return map.entrySet().stream()
			.map(e -> e.getKey() + " -> " + e.getValue())
			.collect(Collectors.joining(",\n     ", "     ", "\n"));
	}

	public static <K, V> void out(Map<K, V> map) {
		System.out.println(printMap(map));
	}

	public static <T> String out(List<T> list, String delimiter) {
		return list.stream()
			.map(Object::toString)
			.collect(Collectors.joining(delimiter));
	}

	
	public static void outRelevantQuads(Iterator<Quad> quads) {
		quads.forEachRemaining(quad -> {
			Node g = quad.getGraph();
			Node s = quad.getSubject();
			Node p = quad.getPredicate();
			Node o = quad.getObject();
			
			if (!g.isURI() || !s.isURI() || !p.isURI() || !o.isURI()) return;
			if (DecUtils.isTrivial(quad)) return;
			
			String gUri = g.getURI();
			String sUri = s.getURI();
			String pUri = p.getURI();
			String oUri = o.getURI();
						
			// Extract last part of URIs after last # or /
			String gStr = gUri.contains("Default") ? "<default>" : gUri.substring(Math.max(gUri.lastIndexOf("#"), gUri.lastIndexOf("/")) + 1 );
			String sStr = sUri.substring(Math.max(sUri.lastIndexOf("#"), sUri.lastIndexOf("/")) + 1);
			String pStr = pUri.substring(Math.max(pUri.lastIndexOf("#"), pUri.lastIndexOf("/")) + 1);
			String oStr = oUri.substring(Math.max(oUri.lastIndexOf("#"), oUri.lastIndexOf("/")) + 1);
			
			out(gStr + ": " + sStr + " " + pStr + " " + oStr);
		});
	}

	public static void printStack() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		for (int i = 2; i < stackTrace.length; i++) { // Skip getStackTrace and printStack
			StackTraceElement element = stackTrace[i];
			out("  at " + element.getClassName() + "." + element.getMethodName() + 
				"(" + element.getFileName() + ":" + element.getLineNumber() + ")");
		}
	}

	public static void dump(String filename, String content) {
		try {
			File file = new File(filename);
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			try (FileWriter writer = new FileWriter(file)) {
				writer.write(content);
			}
		} catch (IOException e) {
			out("Error writing to file " + filename + ": " + e.getMessage());
		}
	}

	public static void dump(String filename, Model model) {
		StringWriter writer = new StringWriter();
		RDFDataMgr.write(writer, model.getGraph(), Lang.NQUADS);
		dump(filename, writer.toString());
	}

	public static void dump(String filename, InfModel model) {
		StringWriter writer = new StringWriter();
		RDFDataMgr.write(writer, model.getGraph(), Lang.NQUADS);
		dump(filename, writer.toString());
	}

	public static int countTriples(Graph g) {
		if (g.isEmpty()) return 0;
		ExtendedIterator<Triple> it = g.find();
		int count = 0;
		while (it.hasNext()) {
			Triple t = it.next();
			if (debug >= 1) DecUtils.out("  triple: " + t.getSubject() + " " + t.getPredicate() + " " + t.getObject());
			count++;
		}
		return count;
	}

	public static int countTriples(DatasetGraph dsg) {
		return countTriples(dsg, 10);
	}

	public static int countTriples(DatasetGraph dsg, int printIfLess) {
		int total = dsg.getDefaultGraph().isEmpty() ? 0 : countTriples(dsg.getDefaultGraph());
		Iterator<Node> graphNodes = dsg.listGraphNodes();
		while (graphNodes.hasNext()) {
			Node graphNode = graphNodes.next();
			Graph g = dsg.getGraph(graphNode);
			if (!g.isEmpty()) {
				total += countTriples(g);
			}
		}
		
		if (printIfLess >= 0 && total <= printIfLess) {
			out("\nAll quads in dataset (" + total + " total):");
			dsg.find().forEachRemaining(quad -> {
				out("  " + quad.getGraph() + " { " + 
					quad.getSubject() + " " + 
					quad.getPredicate() + " " + 
					quad.getObject() + " }");
			});
		}
		
		return total;
	}

	public static boolean isEmpty(DatasetGraph dsg) {
		if (!dsg.getDefaultGraph().isEmpty()) return false;
		Iterator<Node> graphNodes = dsg.listGraphNodes();
		while (graphNodes.hasNext()) {
			if (!dsg.getGraph(graphNodes.next()).isEmpty()) return false;
		}
		return true;
	}

	public static void out(DecDataset dataset, boolean complete, boolean ignoreReasoning) {
		// Wait if reasoning is in progress

		if (dataset.isReasoning && !ignoreReasoning) {
			out("Reasoning on dataset is in progress; returning. ");
		}

		Map<Node, DecUniverse> universes = dataset.getUniverses();
		Map<Node, Node[]> reifications = dataset.getReifications();
		Map<Node, Triple> decStatements = dataset.getDecStatements();

		if (!universes.isEmpty()) {
			out("\nUniverses:");
			out(universes);
		}
		
		if (!reifications.isEmpty()) {
			out("\nReifications:");
			for (Map.Entry<Node, Node[]> entry : reifications.entrySet()) {
				Node[] components = entry.getValue();
				if (components[0] != null && components[1] != null && components[2] != null) {
					out("  " + entry.getKey() + " -> " + Triple.create(components[0], components[1], components[2]));
				}
			}
		}
		
		if (!decStatements.isEmpty()) {
			out("\nDecStatements:");
			out(decStatements);
		}

		var decPredicates = dataset.getReasoner().getDecPredicates();
		if (!decPredicates.isEmpty()) {
			out("\nDecPredicates:");
			out(decPredicates);
		}

		var decPointOfViewPredicates = dataset.getReasoner().getDecPointOfViewPredicates();
		if (!decPointOfViewPredicates.isEmpty()) {
			out("\nDecPointOfViewPredicates:");
			out(decPointOfViewPredicates);
		}
		
		var decPointOfViewReversePredicates = dataset.getReasoner().getDecPointOfViewReversePredicates();
		if (!decPointOfViewReversePredicates.isEmpty()) {
			out("\nDecPointOfViewReversePredicates:");
			out(decPointOfViewReversePredicates);
		}

		if (complete) {
			// Create list of all graphs
			List<Graph> graphs = new ArrayList<>();
			List<String> graphNames = new ArrayList<>();
			
			dataset.getUniverses().forEach((graphNode, universe) -> {
				if (universe.isReady()) {
					graphs.add(universe.getInfModel().getGraph());
					graphNames.add(graphNode.toString());
				} else {
					graphs.add(universe.getBaseGraph());
					graphNames.add(graphNode.toString() + " - only graph");
				}
			});
			dataset.getDatasetGraph().listGraphNodes().forEachRemaining(graphNode -> {
				if (!dataset.getUniverses().containsKey(graphNode)) {
					graphs.add(dataset.getDatasetGraph().getGraph(graphNode));
					graphNames.add(graphNode.toString() + " - not a universe");
				}
			});
			
			// Process all graphs
			for (int i = 0; i < graphs.size(); i++) {
				Graph g = graphs.get(i);
				String name = graphNames.get(i);
				out(g, name);
			}

			out("\n\n") ; 
		}
	}

	public static void out(Graph g, String name) {
		boolean filterTrivialTriples = true;

		out("\nContent of the graph: " + name);
		if (!g.isEmpty()) {
			int count = 0;
			Iterator<Triple> it = g.find();
			while (it.hasNext() && count < 100) {
				Triple triple = it.next();
				boolean isTrivial = false;
				if (debug >= 1) DecUtils.out("   Triple: " + triple);
				
				// Check subject
				if (triple.getSubject().isNodeTriple()) {
					Triple embedded = triple.getSubject().getTriple();
					isTrivial = DecUtils.isTrivial(embedded);
					if (debug >= 1 && !isTrivial) DecUtils.out("   Subject is not trivial: " + embedded);
				} else {
					isTrivial = DecUtils.isTrivial(triple);
				}
				
				// Check object
				if (!isTrivial && triple.getObject().isNodeTriple()) {
					Triple embedded = triple.getObject().getTriple();
					isTrivial = DecUtils.isTrivial(embedded);
					if (debug >= 1 && !isTrivial) DecUtils.out("   Object is not trivial: " + embedded);
				} else if (!isTrivial) {
					isTrivial = DecUtils.isTrivial(triple);
				}

				if (!filterTrivialTriples || !isTrivial) {
					out(replacePrefixes(triple.getSubject()) + " " + replacePrefixes(triple.getPredicate()) + " " + replacePrefixes(triple.getObject()));
					count++;
				}
			}
		} else {
			out("  (empty)");
		}
	}


	public static void out(ValidityReport report) {
		if (report.isValid()) return;

		out("Model is invalid. Issues:");
		Iterator<ValidityReport.Report> reports = report.getReports();
		while (reports.hasNext()) {
			ValidityReport.Report r = reports.next();
			out("  - " + r.getDescription());
			out("    Type: " + r.getType());
			out("    Error: " + r.isError());
			Object ext = r.getExtension();
			if (ext != null) {
				out("    Extension: " + ext);
			}
		}
	}

	public static void out(Quad q) {
		out(q.getGraph(), q.getSubject(), q.getPredicate(), q.getObject());
	}

	public static void out(Node g, Node s, Node p, Node o) {
		out("  " + replacePrefixes(g) 	+ " { " + outTriple(s,p,o) + " }");
	}

	public static String outTriple(Node s, Node p, Node o) {
		return replacePrefixes(s) + " " + replacePrefixes(p) + " " + replacePrefixes(o);
	}

	public static void out(Node s, Node p, Node o) {
		out(replacePrefixes(s) + " " + replacePrefixes(p) + " " + replacePrefixes(o));
	}
	public static String replacePrefixes(String s) {
		String[] namespaces = {
			"urn:x-arq:DefaultGraphNode",
			"http://www.w3.org/1999/02/22-rdf-syntax-ns#",
			"http://www.w3.org/2000/01/rdf-schema#",
			"http://www.w3.org/2002/07/owl#",
			"http://www.w3.org/2001/XMLSchema#",
			"http://dec.fabiovitali.it/",
			"http://example.org/"
		};
		String[] prefixes = {
			"",
			"rdf:",
			"rdfs:",
			"owl:",	
			"xsd:",
			"dec:",
			"ex:"
		};	
		for (int i = 0; i < namespaces.length; i++) {
			if (s.startsWith(namespaces[i])) s = s.replace(namespaces[i], prefixes[i]);
		}
		return s;
	}

	public static String replacePrefixes(Node n) {
		return replacePrefixes(n.toString());
	}

	public static String replacePrefixes(Triple t) {
		return replacePrefixes(t.getSubject()) + " " + replacePrefixes(t.getPredicate()) + " " + replacePrefixes(t.getObject());
	}

	public static String replacePrefixes(Quad q) {
		return replacePrefixes(q.getGraph()) + " " + replacePrefixes(q.getSubject()) + " " + replacePrefixes(q.getPredicate()) + " " + replacePrefixes(q.getObject());
	}


	public static boolean isTrivial(String s, String p, String o) {
		String[] trivial = {
			"http://www.w3.org/1999/02/22-rdf-syntax-ns#",
			"http://www.w3.org/2000/01/rdf-schema#",
			"http://www.w3.org/2002/07/owl#",
			"http://www.w3.org/2001/XMLSchema#",
			"urn:x-hp-jena:rubrik/",
			"rdf",
			"rdfs",	
			"owl",
			"xsd"
		};
		return Arrays.stream(trivial).anyMatch(ns -> (s != null && s.startsWith(ns)));
	}

	public static boolean isTrivial(Triple t) {
		return isTrivial(t.getSubject().toString(), t.getPredicate().toString(), t.getObject().toString());
	}

	public static boolean isTrivial(Quad q) {
		return isTrivial(Triple.create(q.getSubject(), q.getPredicate(), q.getObject()));
	}
}
