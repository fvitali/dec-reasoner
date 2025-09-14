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
import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;

import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.reasoner.ValidityReport;



import java.util.*;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.File;

@SuppressWarnings("unused")
public class DecUtils {
	private static int debug = 0;
	private static int countTrivial = 0;
	/**
	 * Private constructor to prevent instantiation of the utility class.
	 */

	public static final String decPrefix = "http://w3id.org/conjectures/";
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

	public static final String delusionalWorld = decPrefix + "delusionalWorld";

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
	public static final String OWL_SAME_AS_URI = "http://www.w3.org/2002/07/owl#sameAs";
	
	// Schema Graph URI
	public static final String SCHEMA_GRAPH_URI = decPrefix + "schemaGraph";

	public static final List<String> decCategories = List.of(
		"verbatim", "doxastic", "epistemic", "conjectural",
		"nirvana", "shared", "reality", "union", "colored", "error", "special"
	);

	public static final String CLOSED_FOR = decPrefix + "closedFor";

	public static final String POINT_OF_VIEW = decPrefix + "pointofview";
	public static final String POINT_OF_VIEW_PREDICATE = decPrefix + "pointofviewPredicate";
	public static final String POINT_OF_VIEW_REVERSE_PREDICATE = decPrefix + "pointofviewReversePredicate";

	public static final Map<String, String> DECtypes = new HashMap<>();
	public static final Map<String, String> DECpredicateTypes = new HashMap<>();
	public static final Map<String, String> DECReversePredicateTypes = new HashMap<>();

	private static int[] loggingLevels;
	private static final int DEFAULT_LOG_LEVEL = 1;
	private static final int LOGGING_LEVELS_SIZE = 11;

	static {
		for (String category : decCategories) {
			DECtypes.put(decPrefix + category + "World", category);
			DECpredicateTypes.put(decPrefix + category + "Predicate", category);
			DECReversePredicateTypes.put(decPrefix + category + "ReversePredicate", category);
		}

		// Set permeations
		   // No permeations for VERBATIM worlds
		   DecInferenceSetting[VERBATIM] = false;

		   // Permeations for DOXASTIC worlds
		   DecInferenceSetting[DOXASTIC] = true;
		   DecPermeations[DOXASTIC][SHARED] = true;

		   // Permeations for EPISTEMIC worlds
		   DecInferenceSetting[EPISTEMIC] = true;
		   DecPermeations[EPISTEMIC][SHARED] = true;

		   // Permeations for CONJECTURAL worlds
		   DecInferenceSetting[CONJECTURAL] = true;
		   DecPermeations[CONJECTURAL][SHARED] = true;
		   DecPermeations[CONJECTURAL][REALITY] = true;

		   // Permeations for NIRVANA worlds
		   DecInferenceSetting[NIRVANA] = true;
		   DecPermeations[NIRVANA][SHARED] = true;
		   DecPermeations[NIRVANA][REALITY] = true ;

		   // Permeations for SHARED worlds	
		   DecInferenceSetting[SHARED] = true;
		   DecPermeations[SHARED][SHARED] = true;

		   // Permeations for REALITY worlds	
		   DecInferenceSetting[REALITY] = true;
		   DecPermeations[REALITY][REALITY] = true;
		   DecPermeations[REALITY][SHARED] = true;
		   DecPermeations[REALITY][EPISTEMIC] = true;
		   DecPermeations[REALITY][NIRVANA] = true;
		   DecPermeations[REALITY][UNION] = true;

		   // No permeations for UNION worlds
		   DecInferenceSetting[UNION] = false;
		   
		   // Permeations for COLORED worlds
		   DecInferenceSetting[COLORED] = true;
		   DecPermeations[COLORED][SHARED] = true;

		   // No permeations for ERROR worlds
		   DecInferenceSetting[ERROR] = false;

		   // No permeations for SPECIAL worlds
		   DecInferenceSetting[SPECIAL] = false;
	}

	/**
	 * Computes a hash for the given subject, predicate, and object with a prefix.
	 *
	 * @param s the subject
	 * @param p the predicate
	 * @param o the object
	 * @param prefix the prefix to use
	 * @return the computed hash
	 */
	public static String hash(String s, String p, String o, String prefix) {
		String combined = String.join("_", 
			s != null ? replacePrefixes(s) : "",
			p != null ? replacePrefixes(p) : "",
			o != null ? replacePrefixes(o) : ""
		);
		return prefix + Integer.toHexString(combined.hashCode())+ "#" + (s=="TRIPLES"? o: s);
//		return combined;
	}

	/**
	 * Prints the stack trace up to a specified limit.
	 *
	 * @param limit the maximum number of stack trace elements to print
	 */
	public static void printStack(int limit) {
		Exception e = new Exception();
		StackTraceElement[] stackTrace = e.getStackTrace();
		limit = Math.min(stackTrace.length, limit);
		for (int i = 0; i < limit; i++) {
			DecUtils.out(stackTrace[i].toString());
		}
	}

	/**
	 * Dumps the given content to a file.
	 *
	 * @param filename the name of the file
	 * @param content the content to write
	 */
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

	/**
	 * Dumps the given model to a file in N-Quads format.
	 *
	 * @param filename the name of the file
	 * @param model the model to dump
	 */
	public static void dump(String filename, Model model) {
		StringWriter writer = new StringWriter();
		RDFDataMgr.write(writer, model.getGraph(), Lang.NQUADS);
		dump(filename, writer.toString());
	}

	/**
	 * Dumps the given inference model to a file in N-Quads format.
	 *
	 * @param filename the name of the file
	 * @param model the inference model to dump
	 */
	public static void dump(String filename, InfModel model) {
		StringWriter writer = new StringWriter();
		RDFDataMgr.write(writer, model.getGraph(), Lang.NQUADS);
		dump(filename, writer.toString());
	}

	/**
	 * Counts the number of triples in a graph.
	 *
	 * @param g the graph to count triples in
	 * @return the number of triples
	 */
	public static int countTriples(Graph g) {
		if (g.isEmpty()) return 0;
		ExtendedIterator<Triple> it = g.find();
		int count = 0;
		while (it.hasNext()) {
			Triple t = it.next();
			count++;
		}
		return count;
	}

	/**
	 * Counts the number of triples in a dataset graph.
	 *
	 * @param dsg the dataset graph to count triples in
	 * @return the number of triples
	 */
	public static int countTriples(DatasetGraph dsg) {
		return countTriples(dsg, 10);
	}

	/**
	 * Counts the number of triples in a dataset graph, printing them if below a threshold.
	 *
	 * @param dsg the dataset graph to count triples in
	 * @param printIfLess the threshold for printing triples
	 * @return the number of triples
	 */
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

	/**
	 * Checks if a dataset graph is empty.
	 *
	 * @param dsg the dataset graph to check
	 * @return true if the dataset graph is empty, false otherwise
	 */
	public static boolean isEmpty(DatasetGraph dsg) {
		if (!dsg.getDefaultGraph().isEmpty()) return false;
		Iterator<Node> graphNodes = dsg.listGraphNodes();
		while (graphNodes.hasNext()) {
			if (!dsg.getGraph(graphNodes.next()).isEmpty()) return false;
		}
		return true;
	}

	/**
	 * Replaces prefixes in a string with their corresponding short forms.
	 *
	 * @param s the string to replace prefixes in
	 * @param hard whether to perform a hard replacement
	 * @return the string with replaced prefixes
	 */
	public static String replacePrefixes(String s, boolean hard) {
		String t = s;
		String[] namespaces = {
			"urn:x-arq:",
			"http://www.w3.org/1999/02/22-rdf-syntax-ns#",
			"http://www.w3.org/2000/01/rdf-schema#",
			"http://www.w3.org/2002/07/owl#",
			"http://www.w3.org/2001/XMLSchema#",
			"http://w3id.org/conjectures/",
			"https://www.dublincore.org/",
			"http://www.w3.org/ns/prov#",
			"http://example.org/"
		};
		String[] prefixes = {
			"",
			"rdf:",
			"rdfs:",
			"owl:",	
			"xsd:",
			"dec:",
			"dc:",
			"prov:",
			"ex:"
		};	
		boolean done = false;
		for (int i = 0; i < namespaces.length; i++) {
			t = t.replace(namespaces[i], prefixes[i]);
			done = true;
		}
		if (!done && hard) {
			int lastHash = s.lastIndexOf('#');
			int lastSlash = s.lastIndexOf('/');
			if (lastHash != -1) {
				s = s.substring(lastHash + 1);
			} else if (lastSlash != -1) {
				s = s.substring(lastSlash + 1);
			}
			out("Warning: replacePrefixes: " + s + " not found in namespaces");
		}
		return t;
	}

	/**
	 * Replaces prefixes in a string with their corresponding short forms.
	 *
	 * @param s the string to replace prefixes in
	 * @return the string with replaced prefixes
	 */
	public static String replacePrefixes(String s) {	
		return replacePrefixes(s, false);
	}

	/**
	 * Replaces prefixes in a node with their corresponding short forms.
	 *
	 * @param n the node to replace prefixes in
	 * @param hard whether to perform a hard replacement
	 * @return the node with replaced prefixes
	 */
	public static String replacePrefixes(Node n, boolean hard) {
		return n.isNodeTriple() ? "<<" + replacePrefixes(n.getTriple(), hard) + ">>" : replacePrefixes(n.toString(), hard);
	}
	/**
	 * Replaces prefixes in a node with their corresponding short forms.
	 *
	 * @param n the node to replace prefixes in
	 * @return the node with replaced prefixes
	 */
	public static String replacePrefixes(Node n) {
		return replacePrefixes(n, false);
	}

	/**
	 * Replaces prefixes in a triple with their corresponding short forms.
	 *
	 * @param t the triple to replace prefixes in
	 * @param hard whether to perform a hard replacement
	 * @return the triple with replaced prefixes
	 */
	public static String replacePrefixes(Triple t, boolean hard) {
		return replacePrefixes(t.getSubject()) + " " + replacePrefixes(t.getPredicate()) + " " + replacePrefixes(t.getObject());
	}

	/**
	 * Replaces prefixes in a triple with their corresponding short forms.
	 *
	 * @param t the triple to replace prefixes in
	 * @return the triple with replaced prefixes
	 */
	public static String replacePrefixes(Triple t) {
		return replacePrefixes(t, false);
	}

	/**
	 * Replaces prefixes in a quad with their corresponding short forms.
	 *
	 * @param q the quad to replace prefixes in
	 * @param hard whether to perform a hard replacement
	 * @return the quad with replaced prefixes
	 */
	public static String replacePrefixes(Quad q, boolean hard) {
		return replacePrefixes(q.getGraph()) + " " + replacePrefixes(q.getSubject()) + " " + replacePrefixes(q.getPredicate()) + " " + replacePrefixes(q.getObject(), hard);
	}

	/**
	 * Replaces prefixes in a quad with their corresponding short forms.
	 *
	 * @param q the quad to replace prefixes in
	 * @return the quad with replaced prefixes
	 */
	public static String replacePrefixes(Quad q) {
		return replacePrefixes(q, false);
	}


	/**
	 * Checks if a triple is trivial based on its subject, predicate, and object.
	 *
	 * @param s the subject
	 * @param p the predicate
	 * @param o the object
	 * @return true if the triple is trivial, false otherwise
	 */
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

		return s != null && 
       		!s.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#subject") && 
       		!s.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#object") && 
       		Arrays.stream(trivial).anyMatch(ns -> s.startsWith(ns));
	}

	/**
	 * Checks if a triple is trivial.
	 *
	 * @param t the triple to check
	 * @return true if the triple is trivial, false otherwise
	 */
	public static boolean isTrivial(Triple t) {
		return isTrivial(t.getSubject().toString(), t.getPredicate().toString(), t.getObject().toString());
	}

	/**
	 * Checks if a quad is trivial.
	 *
	 * @param q the quad to check
	 * @return true if the quad is trivial, false otherwise
	 */
	public static boolean isTrivial(Quad q) {
		return isTrivial(Triple.create(q.getSubject(), q.getPredicate(), q.getObject()));
	}


	/**
	 * Outputs a formatted string representation of a graph, subject, predicate, and object.
	 *
	 * @param g the graph
	 * @param s the subject
	 * @param p the predicate
	 * @param o the object
	 */
	public static void outS(String g, String s, String p, String o) {
		out(replacePrefixes(g) + " { " + replacePrefixes(s) + " " + replacePrefixes(p) + " " + replacePrefixes(o) + " }");
	}

	/**
	 * Outputs the given arguments to the console.
	 *
	 * @param args the arguments to output
	 */
	public static void out(Object... args) {
		List<String> list = collect(true, args);
		for (String s : list) System.out.print(s);
	}


	/**
	 * Collects a list of strings from the given arguments.
	 *
	 * @param first whether this is the first call in a sequence
	 * @param args the arguments to collect
	 * @return a list of collected strings
	 */
	public static List<String> collect(Boolean first, Object... args) {
		String NL = "\n" ;
		List<String> list = new ArrayList<>();
		String prefix = "";
		int maxLength = 16;
		boolean singleLine = true;

		for (Object arg : args) {
			if (arg instanceof String) {
				list.add((String) arg);
				list.add(NL) ; 
			} else if (arg instanceof Integer) {
				if (prefix.isEmpty()) {
					prefix = " ".repeat( (Integer) arg);
				} else {
					maxLength = (Integer) arg;
				}
			} else if (arg instanceof Boolean) {
				singleLine = (Boolean) arg;
			} else if (arg instanceof Node) {
				Node n = (Node) arg;
				list.add(n.toString());
				list.add(NL) ; 
			} else if (arg instanceof Quad) {
				Quad t = (Quad) arg;
				if (!isTrivial(t)) {
					list.add(t.getGraph()==null ? "" : t.getGraph().toString());
					list.add(t.getSubject().toString());
					list.add(t.getPredicate().toString());
					list.add(t.getObject().toString());
					list.add(NL) ; 
				}
			} else if (arg instanceof Triple) {
				Triple t = (Triple) arg;
				if (!isTrivial(t)) {
					list.add(t.getSubject().toString());
					list.add(t.getPredicate().toString());
					list.add(t.getObject().toString());
					list.add(NL) ; 
				}
			} else if (arg instanceof ArrayList) {
				ArrayList<String> a = (ArrayList<String>) arg;
				for (String s : a) {
					list.add(s);
					list.add(NL) ; 
				}
			} else if (arg instanceof Map) {
				Map<?, ?> map = (Map<?, ?>) arg;
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					Object key = entry.getKey();
					Object value = entry.getValue();
		
					List<String> a = collect(false, value);
					list.add(key.toString());
					list.addAll(a);
					list.add(NL) ; 
				}
			} else if (arg instanceof HashSet) {
				HashSet<?> set = (HashSet<?>) arg;
				for (Object entry : set) {
					List<String> a = collect(false, entry);
					list.addAll(a);
				}
			} else if (arg instanceof Graph) {
				Graph g = (Graph) arg;
				g.find().forEachRemaining(triple -> {
					if (isTrivial(triple)) return;
					list.addAll(collect(false,triple));
				});		
				list.add(NL) ; 
			} else if (arg instanceof ResultSetRewindable) {
				ResultSetRewindable rewindable = (ResultSetRewindable) arg;
				rewindable.reset(); // Reset to the beginning
				countTrivial = 0;
				while (rewindable.hasNext()) {
					QuerySolution solution = rewindable.next();
					List<String> a = collect(false, solution);
					list.addAll(a);
//					list.add(NL) ; 
				}
				if (countTrivial > 0) {
					list.add("plus " + countTrivial + " trivial triples") ; 
					list.add(NL) ; 
				}
		} else if (arg instanceof QuerySolution) {
					QuerySolution solution = (QuerySolution) arg;
					// Try to gather g, s, p, o in order
					String[] vars = {"g", "s", "p", "o"};
					Node[] nodes = new Node[4];
					for (int i = 0; i < vars.length; i++) {
						if (solution.contains(vars[i])) {
							Node n = solution.get(vars[i]).asNode();
							nodes[i] = n;
						}
					}	
					Quad t = new Quad(nodes[0], nodes[1], nodes[2], nodes[3]);
					if (!isTrivial(t)) {
						list.add(t.getGraph()==null ? "" : t.getGraph().toString());
						list.add(t.getSubject().toString());
						list.add(t.getPredicate().toString());
						list.add(t.getObject().toString());
						list.add(NL) ; 
					} else {
						countTrivial++;
					}

					// Gather other variables
					List<Object> values = new ArrayList<>();
					for (Iterator<String> it = solution.varNames(); it.hasNext();) {
						String var = it.next();
						if (!Arrays.asList(vars).contains(var)) {
							Node n = solution.get(var).asNode();
							if (n != null) {
								values.add(var + ": "+ n);
							}
						}
					}
					// Collect on the values
					List<String> valuesList = collect(false, values.toArray());
					if(valuesList.size()>0) {
						list.add(valuesList.get(0));
						list.add(NL);
					}
			} else {
				// Handle unknown type
				list.add(arg.getClass().getName());
				list.add(NL);
				list.add(arg.toString());
			}
		}

		if (first) {
			for (int i = 0; i < list.size(); i++) {
				String s = list.get(i);
				if (i==0) {
					s = prefix + s;
				}
				if (s.equals(NL)) {
					if (singleLine) {
						s = "";
					} else {
						s += prefix;
					}
				} else {
					String s1 = replacePrefixes(s);
					String padding = " ".repeat(maxLength);
					int blocks = (s1.length() / maxLength) + 1;
					s = (s1 + padding).substring(0, maxLength * blocks);
				}
				list.set(i, s); // Update the list with the modified string
			}
			if (!singleLine) {
				list.add(0, NL);
			}
			list.add(NL);
		}
		return list;
	}


	public static void setDebugLevel(String levels) {
		String[] levelStrings = levels.split(" ");
		loggingLevels = new int[LOGGING_LEVELS_SIZE];
		if (levelStrings.length == 1) {
			try {
				int level = Integer.parseInt(levelStrings[0]);
				Arrays.fill(loggingLevels, level);
			} catch (NumberFormatException e) {
				Arrays.fill(loggingLevels, DEFAULT_LOG_LEVEL);
			}
		} else {
			for (int i = 0; i < LOGGING_LEVELS_SIZE; i++) {
				try {
					if (i < levelStrings.length) {
						loggingLevels[i] = Integer.parseInt(levelStrings[i]);
					} else {
						loggingLevels[i] = DEFAULT_LOG_LEVEL;
					}
				} catch (NumberFormatException e) {
					loggingLevels[i] = DEFAULT_LOG_LEVEL;
				}
			}
		}
		debug = loggingLevels[4]; // The first level is the DecUtils level
		DecUtils.out("DecUtils: debug level (4): " + debug);

	}
	public static int getDebugLevel(int n) {
		if (n >= 0 && n < loggingLevels.length) {
			return loggingLevels[n];
		}
		return 1; // Default level if out of bounds
	}
	

}
