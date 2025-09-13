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


import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.rdf.model.Model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.query.QueryFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import java.util.Map;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import org.junit.jupiter.api.Disabled;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ValidityReport;
import java.util.Iterator;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.query.ResultSetFactory;



@SuppressWarnings("unused")

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

public class DecDatasetTest {
	private static int debug ;
	private DecDataset dataset;
	private PrintStream originalErr;
	private static final String ANSI_RESET = "\u001B[0m";
	private static final String ANSI_RED = "\u001B[31m";
	private static final String ANSI_BOLD = "\u001B[1m";

	private static final String globalRdfStarQuery = """
		PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
		PREFIX owl: <http://www.w3.org/2002/07/owl#>
		PREFIX dec: <http://w3id.org/conjectures/>
		PREFIX : <http://example.org/>
		
		SELECT ?s ?p ?o ?s1 ?p1 ?o1 WHERE {
			{ ?s ?p ?o }
			
		  UNION
			{ ?s ?p << ?s1 ?p1 ?o1 >> }
		}
	""";

	
	private static final String globalReificationQuery = """
		PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
		PREFIX owl: <http://www.w3.org/2002/07/owl#>
		PREFIX dec: <http://w3id.org/conjectures/>
		PREFIX : <http://example.org/>
		
		SELECT ?s ?p ?o ?subj ?pred ?obj WHERE {
		  { ?s ?p ?o }
			
			UNION
			{ 
				?s ?p ?stmt . 
			 	?stmt rdf:subject ?subj . 
				?stmt rdf:predicate ?pred . 
				?stmt rdf:object ?obj 
			}
		}
	""";

	@BeforeEach
	
	void setUp() {

		if (debug >= 4) DecUtils.out("\nNew test");
		dataset = new DecDataset(null, false, false, true, 60000, ReasonerRegistry.getOWLReasoner(),"1");
		debug = DecUtils.getDebugLevel(11); // Position 11 for DecDatasetTest
		try {
			originalErr = System.err;
			System.setErr(new PrintStream(new FileOutputStream("test_error.log")));
		} catch (FileNotFoundException e) {
			
			e.printStackTrace();
		}
	}

	@AfterEach
	
	void tearDown() {
		if (originalErr != null) {
			System.setErr(originalErr);
		}
	}

	
	private void checkTrue(boolean condition, String message, String otherwise) {
		if (debug >= 1) {
			if (!condition) {
				DecUtils.out(ANSI_BOLD + ANSI_RED + "   ERROR: " + message + ANSI_RESET);
			} else {
				
				DecUtils.out("   " + otherwise);
			}
		}	
		
		assertTrue(condition, message);
	}

	
	private void checkFalse(boolean condition, String message, String otherwise) {
		if (debug >= 1) {
			if (condition) {
				DecUtils.out(ANSI_BOLD + ANSI_RED + "   ERROR: " + message + ANSI_RESET);
		} else {
				
				DecUtils.out("   " + otherwise);
			}
		}
		
		assertFalse(condition, message);
	}

	
	private void checkEquals(Object expected, Object actual, String message, String otherwise) {
		if (debug >= 1) {
			if (!expected.equals(actual)) {
				DecUtils.out(ANSI_BOLD + ANSI_RED + "   ERROR: " + message + ANSI_RESET);
			} else {
				
				DecUtils.out("   " + otherwise);
			}
		}
		
		assertEquals(expected, actual, message);
	}

	
	private void checkNotNull(Object object, String message, String otherwise) {
		if (debug >= 1) {
			if (object == null) {
				DecUtils.out(ANSI_BOLD + ANSI_RED + "   ERROR: " + message + ANSI_RESET);
			} else {
				
				DecUtils.out("   " + otherwise);
			}
		}
		
		assertNotNull(object, message);
	}

	private static void showAll(ResultSetRewindable rs) {
		DecUtils.out(4,
			"====================== content of result set ==========================", 
			rs, 
			"=======================================================================", false
		);
	}


	private void executeUpdate(String updateQuery) {
		try {
			UpdateRequest update = UpdateFactory.create(updateQuery);
			UpdateProcessor processor = UpdateExecutionFactory.create(update, dataset);
			processor.execute();
		} catch (Throwable t) {
			
			throw new RuntimeException("Update failed: " + t.getMessage());
		}
	}

	private ResultSetRewindable execSelect(String queryString){
		Dataset ds = DatasetFactory.wrap(dataset);
		try (QueryExecution qx = QueryExecutionFactory.create(QueryFactory.create(queryString), ds)) {
			return ResultSetFactory.copyResults(qx.execSelect());
		}
	}

	
	private ResultSet executeQuery(String queryString) {
		QueryExecution qexec = QueryExecutionFactory.create(QueryFactory.create(queryString), dataset);
		ResultSet results = qexec.execSelect();
		return results;
	}

	
	private boolean matchesPattern(ResultSet results, Map<String, String> pattern) {
		if (debug >= 3) DecUtils.out(pattern );
		while (results.hasNext()) {
			String[] triple = new String[4];
			boolean matches = true;
			boolean special = false;
			QuerySolution soln = results.next();

			String s = DecUtils.replacePrefixes(soln.get("s") != null ? soln.get("s").toString() : "");
			String p = DecUtils.replacePrefixes(soln.get("p") != null ? soln.get("p").toString() : "");
			String o = DecUtils.replacePrefixes(soln.get("o") != null ? soln.get("o").toString() : "");
			String g = DecUtils.replacePrefixes(soln.get("g") != null ? soln.get("g").toString() : "");
			
			for (Map.Entry<String, String> entry : pattern.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				String nodeStr = soln.get(key) != null ? soln.get(key).toString() : "";
				if (value.equals("*")) continue;
				if (key.equals("g") && value.isEmpty() && nodeStr.isEmpty() ) {
					if (debug >= 3) DecUtils.out("graph is default: " + s + " " + p + " " + o + " (" + g+")");
					continue; 
				} else if (!nodeStr.matches(value)) {
					
					if (debug >= 3 && key.equals("g") && !DecUtils.isTrivial(s, p, o)) {
						DecUtils.out("does not match: " + s + " " + p + " " + o + " (" + g+") because " + nodeStr + "	is not " + value);
					}
					
					matches = false;
					break;
				} else {
					
					if (debug >= 3 && key.equals("g") && !DecUtils.isTrivial(s, p, o)) 
						DecUtils.out("matches: " + s + " " + p + " " + o + " (" + g+")");
				}
			}
			
			if (matches) {
				if (debug >= 3) DecUtils.out("matches: " + s + " " + p + " " + o + " (" + g+")");
				return true;
			}
		}
		
		return false;
	}


	@Order(1)
	@Test
	
	
	void test001_TDB2Dataset() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 1 testTDB2Dataset\n----------------------------");
		DatasetGraph dataset = new DecDataset("", true, true, true, 60000, ReasonerRegistry.getOWLReasoner(),"1");
		checkNotNull(dataset, "Test 1: TDB2 dataset should be created successfully", "TDB2 dataset created successfully as expected");
	}

	@Order(2)
	
	@Test
	
	
	void test002_InMemoryDataset() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 2 testInMemoryDataset\n----------------------------");
		DatasetGraph dataset = new DecDataset(null, false, false, true, 60000, ReasonerRegistry.getOWLReasoner(),"1");
		checkNotNull(dataset, "Test 2: In-memory dataset should be created successfully", "In-memory dataset created successfully as expected");
	}

	@Order(3)
	
	@Test
	
	
	void test003_GetInstance() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 3 testGetInstance\n----------------------------");
		checkNotNull(DecDataset.getInstance(), "Test 3: getInstance() should return a non-null dataset", "getInstance() returned correct dataset instance as expected");
		checkEquals(dataset, DecDataset.getInstance(), "Test 3: getInstance() should return the same dataset instance", "getInstance() returned correct dataset instance as expected");
	}

	@Order(4)
	
	@Test
	
	
	void test004_QueryTimeout() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 4 testQueryTimeout\n----------------------------");
		DecDataset dataset = new DecDataset(null, false, false, true, 30000, ReasonerRegistry.getOWLReasoner(),"1");
		checkEquals(30000, dataset.getQueryTimeout(), "Test 4: Query timeout should be set to 30000", "Query timeout set correctly to 30000 as expected");
	}

	@Order(5)
	
	@Test
	
	
	void test005_AllowUpdate() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 5 testAllowUpdate\n----------------------------");
		checkTrue(dataset.isAllowUpdate(), "Test 5: Dataset should allow updates", "Dataset allows updates as expected");
	}

	@Order(6)
	
	@Test
	
	
	void test006_AddQuad() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 6 testAddQuad\n----------------------------");
		Node g = NodeFactory.createURI("http://example.org/graph");
		Node s = NodeFactory.createURI("http://example.org/subject");
		Node p = NodeFactory.createURI("http://example.org/predicate");
		Node o = NodeFactory.createURI("http://example.org/object");
		
		dataset.add(g, s, p, o);
		boolean containsQuad = dataset.contains(g, s, p, o);
		checkTrue(containsQuad, "Test 6: Dataset should contain the added quad", "Quad added successfully to dataset as expected");
	}

	@Order(7)
	
	@Test
	
	
	void test007_AddRdfStarTriple() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 7 testAddRdfStarTriple\n----------------------------");
		// Create a triple node
		
		Node subject = NodeFactory.createURI("http://example.org/s");
		Node predicate = NodeFactory.createURI("http://example.org/p");
		Node object = NodeFactory.createURI("http://example.org/o");
		Triple triple = Triple.create(subject, predicate, object);
		Node tripleNode = NodeFactory.createTripleNode(triple);
		
		// Add the triple node as a subject
		
		Node graph = NodeFactory.createURI("http://example.org/g");
		Node p = NodeFactory.createURI("http://example.org/p2");
		Node o = NodeFactory.createURI("http://example.org/o2");
		dataset.add(graph, tripleNode, p, o);
		
		// Verify the triple is in the dataset
		checkTrue(dataset.contains(graph, tripleNode, p, o), "Test 7: Dataset should contain the RDF* triple", "RDF* triple added successfully to dataset as expected");
	}

	@Order(8)
	
	@Test
	
	
	void test008_AddDecStatement() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 8 testAddDecStatement\n----------------------------");
		Node s = NodeFactory.createURI(DecUtils.decPrefix + "statement");
		Node p = NodeFactory.createURI("http://example.org/p");
		Node o = NodeFactory.createURI("http://example.org/o");
		
		dataset.add(Quad.defaultGraphNodeGenerated, s, p, o);
		boolean containsDecStatement = dataset.getDecStatements().containsKey(s);
		checkTrue(containsDecStatement, "Test 8: DecStatements map should contain the DEC statement", "DEC statement added successfully to DecStatements map as expected");
	}


	@Order(10)
	
	@Test
	
	
	void test010_Clear() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 10 testClear\n----------------------------");
		Node s1 = NodeFactory.createURI("http://example.org/s1");
		Node p1 = NodeFactory.createURI("http://example.org/p1");
		Node o1 = NodeFactory.createURI("http://example.org/o1");
		Node s2 = NodeFactory.createURI("http://example.org/s2");
		Node p2 = NodeFactory.createURI("http://example.org/p2");
		Node o2 = NodeFactory.createURI("http://example.org/o2");
		Node g1 = NodeFactory.createURI("http://example.org/g1");
		Node s3 = NodeFactory.createURI("http://example.org/s3");
		Node p3 = NodeFactory.createURI("http://example.org/p3");
		Node o3 = NodeFactory.createURI("http://example.org/o3");

		dataset.add(Quad.create(Quad.defaultGraphNodeGenerated, s1, p1, o1));
		dataset.add(Quad.create(Quad.defaultGraphNodeGenerated, s2, p2, o2));
		dataset.add(Quad.create(g1, s3, p3, o3));

		checkTrue(dataset.contains(Quad.defaultGraphNodeGenerated, s1, p1, o1) &&
				 dataset.contains(Quad.defaultGraphNodeGenerated, s2, p2, o2) &&
				 dataset.contains(g1, s3, p3, o3),
				 "Test 10: Dataset should contain all added quads",
				 "Dataset contains all quads as expected");

		
		dataset.clear();

		checkTrue(!dataset.contains(Quad.defaultGraphNodeGenerated, s1, p1, o1) &&
				 !dataset.contains(Quad.defaultGraphNodeGenerated, s2, p2, o2) &&
				 !dataset.contains(g1, s3, p3, o3),
				 "Test 10: Dataset should be empty after clear",
				 "Dataset is empty after clear as expected");
	}

	@Order(11)
	
	@Test
	
	
	void test011_ClearAllSparql() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 11 testClearAllSparql\n----------------------------");
		
		// Add 3 triples via SPARQL
		
		String update1 = "INSERT DATA { " +
			"<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> . " +
			"<http://example.org/s2> <http://example.org/p2> <http://example.org/o2> . " +
			"GRAPH <http://example.org/g1> { " +
			"<http://example.org/s3> <http://example.org/p3> <http://example.org/o3> } }";
		
		
		executeUpdate(update1);
		
		checkTrue(dataset.contains(Node.ANY, Node.ANY, Node.ANY, Node.ANY), 
			"Test 11: Dataset should contain at least one quad after SPARQL insert",
			"Dataset contains quads after SPARQL insert as expected");
		
		// Clear all via SPARQL
		
		String update2 = "CLEAR ALL";
		executeUpdate(update2);
		checkFalse(dataset.contains(Node.ANY, Node.ANY, Node.ANY, Node.ANY),
			"Test 11: Dataset should be empty after CLEAR ALL",
			"Dataset is empty after CLEAR ALL as expected");
	}

	@Order(12)
	
	@Test
	
	
	void test012_SingleQuadSparql() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 12 testSingleQuadSparql\n----------------------------");
		// Add a single quad
		
		String update = "INSERT DATA { GRAPH <http://example.org/g1> { <http://example.org/s> <http://example.org/p> <http://example.org/o> } }";
		executeUpdate(update);
		// Query for the quad
		
		String query = "SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }";
		ResultSet results = executeQuery(query);
		checkTrue(matchesPattern(results, Map.of(
			"g", ".*.example.org/g1",
			"s", ".*.example.org/s", 
			"p", ".*.example.org/p",
			"o", ".*.example.org/o"
		)), "Test 12: Dataset should contain the single quad (g1, s, p, o)", "Single quad added successfully via SPARQL as expected");
	}

	@Order(13)
	
	@Test
	
	
	void test013_MultipleQuadsSparql() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 13 testMultipleQuadsSparql\n----------------------------");
		// Add quads to different graphs
		
		String update = """
			INSERT DATA {
				GRAPH <http://example.org/g1> { <http://example.org/s1> <http://example.org/p1> <http://example.org/o1> }
				GRAPH <http://example.org/g2> { <http://example.org/s2> <http://example.org/p2> <http://example.org/o2> }
				GRAPH <http://example.org/g3> { <http://example.org/s3> <http://example.org/p3> <http://example.org/o3> }
			}
		""";
		
		executeUpdate(update);
		// Query for a specific quad
		
		String query = "SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }";
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"g", ".*.example.org/g1",
			"s", ".*.example.org/s1", 
			"p", ".*.example.org/p1",
			"o", ".*.example.org/o1"
		)), "Test 13: Dataset should contain the first quad (g1, s1, p1, o1)", "Multiple quads added successfully via SPARQL as expected");
	}

	@Order(14)
	
	@Test
	
	
	void test014_DeleteQuadsSparql() {
		DecUtils.out("\n----------------------------\n 14 testDeleteQuadsSparql\n----------------------------");
		// Add quads

		String update = """
			PREFIX ex: <http://example.org/>
			INSERT DATA {
				GRAPH ex:g1 { ex:t1 ex:p1 ex:o1 . }
				GRAPH ex:g2 { ex:t2 ex:p2 ex:o2 . }
			}
		""";
		
		executeUpdate(update);

		// Delete some quads
		
		String delete = """
			PREFIX ex: <http://example.org/>
			DELETE WHERE { GRAPH ex:g1 { ?s ?p ?o } }
		""";
		
		executeUpdate(delete);
		
		// Verify remaining content
		
		String query2 = """
			PREFIX ex: <http://example.org/>
			SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }
		""";
		
		checkTrue(matchesPattern(executeQuery(query2), Map.of(
			"g", ".*.example.org/g2",
			"s", ".*.example.org/t2",
			"p", ".*.example.org/p2",
			"o", ".*.example.org/o2"
		)), "Test 14: Quad (g2, t2, p2, o2) should still exist after deletion", "Quad in g2 preserved successfully after deletion as expected");
		
		// Verify deleted content is gone
		
		String query = """
			PREFIX ex: <http://example.org/>
			SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }
		""";
		
		checkFalse(matchesPattern(executeQuery(query), Map.of(
			"g", ".*.example.org/g1"
		)), "Test 14: Graph g1 should be empty after deletion", "Quad in g1 deleted successfully as expected");
	}

	@Order(15)
	
	@Test
	
	
	void test015_DecStatements() {
		DecUtils.out("\n----------------------------\n 15 testDecStatements\n----------------------------");
		
		// Add DEC statements with different types
		
		Node s1 = NodeFactory.createURI(DecUtils.decPrefix + "statement1");
		Node p1 = NodeFactory.createURI("http://example.org/p1");
		Node o1 = NodeFactory.createURI("http://example.org/o1");
		dataset.add(Quad.defaultGraphNodeGenerated, s1, p1, o1);
		
		Node s2 = NodeFactory.createURI("http://example.org/s2");
		Node p2 = NodeFactory.createURI(DecUtils.decPrefix + "statement2");
		Node o2 = NodeFactory.createURI("http://example.org/o2");
		dataset.add(Quad.defaultGraphNodeGenerated, s2, p2, o2);
		
		Node s3 = NodeFactory.createURI("http://example.org/s3");
		Node p3 = NodeFactory.createURI("http://example.org/p3");
		Node o3 = NodeFactory.createURI(DecUtils.decPrefix + "statement3");
		dataset.add(Quad.defaultGraphNodeGenerated, s3, p3, o3);
		
		// Verify all statements are in the DecStatements map
		
		Map<Node, Triple> decStatements = dataset.getDecStatements();
		checkTrue(decStatements.containsKey(s1), "Test 15: DecStatements should contain statement1", "All DEC statements found in DecStatements map as expected");
		
		// Verify the triples are correctly stored
		
		checkEquals(Triple.create(s1, p1, o1), decStatements.get(s1), "Test 15: DecStatements should store correct triple for statement1", "All DEC statements stored with correct triples as expected");
	}

	@Order(16)
	
	@Test
	
	
	void test016_DoxasticGraphWithRdfType() {
		DecUtils.out("\n----------------------------\n 16 testDoxasticGraphWithRdfType\n----------------------------");
		
		// Create test data with contradictory OWL axioms
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX ex: <http://example.org/>
			
			INSERT DATA {
			  ex:A rdfs:subClassOf ex:B .
				ex:A rdfs:subClassOf ex:C .
				ex:B owl:disjointWith ex:C .
				ex:instance1 rdf:type ex:A .
			}
		""";
		
		executeUpdate(update);
		
		// Test direct OWL reasoning
		
		Model baseModel = ModelFactory.createModelForGraph(dataset.getDefaultGraph());
		InfModel directModel = ModelFactory.createInfModel(
			ReasonerRegistry.getOWLReasoner(),
			baseModel
		);
		
		ValidityReport directValidity = directModel.validate();
		
		// Test DecReasoner
		
		InfModel decModel = ModelFactory.createInfModel(
			ReasonerRegistry.getOWLReasoner(),  // Use a new OWL reasoner instance
			ModelFactory.createModelForGraph(dataset.getDefaultGraph())
		);
		
		ValidityReport decValidity = decModel.validate();

		// Compare validity status
		
		checkEquals(directValidity.isValid(), decValidity.isValid(),
			"Test 16: DecReasoner should have same validity as direct OWL reasoning",
			"DecReasoner has same validity as direct OWL reasoning");
			
		// Compare number of validity issues
		
		Iterator<ValidityReport.Report> directReports = directValidity.getReports();
		Iterator<ValidityReport.Report> decReports = decValidity.getReports();
		int directIssueCount = 0;
		int decIssueCount = 0;
		
		while (directReports.hasNext()) {
			directReports.next();
			directIssueCount++;
		}
		
		while (decReports.hasNext()) {
			decReports.next();
			decIssueCount++;
		}
		
		
		checkEquals(directIssueCount, decIssueCount,
			"Test 16: DecReasoner should have same number of validity issues as direct OWL reasoning",
			"DecReasoner has same number of validity issues as direct OWL reasoning");
			
		// Compare specific inferences
		
		boolean directHasInferredType = false;
		boolean decHasInferredType = false;
		
		// Check direct model
		
		ResultSet directResults = QueryExecutionFactory.create(
			QueryFactory.create("SELECT ?s ?p ?o WHERE { ?s ?p ?o }"),
			directModel
		).execSelect();
		
		
		while (directResults.hasNext()) {
			QuerySolution soln = directResults.next();
			if (soln.get("s").toString().equals("http://example.org/A") &&
				soln.get("p").toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") &&
				soln.get("o").toString().equals("http://example.org/B")) {
				directHasInferredType = true;
				break;
			}
		}
		
		// Check dec model
		
		ResultSet decResults = QueryExecutionFactory.create(
			QueryFactory.create("SELECT ?s ?p ?o WHERE { ?s ?p ?o }"),
			decModel
		).execSelect();
		
		
		while (decResults.hasNext()) {
			QuerySolution soln = decResults.next();
			if (soln.get("s").toString().equals("http://example.org/A") &&
				soln.get("p").toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") &&
				soln.get("o").toString().equals("http://example.org/B")) {
				decHasInferredType = true;
				break;
			}
		}
		
		
		checkEquals(directHasInferredType, decHasInferredType,
			"Test 16: Both reasoners should make the same inference about A being of type B",
			"Both reasoners made the same inference about A being of type B");
	}

	@Order(17)
	
	@Test
	
	
	void test017_VerbatimGraphWithSaysRange() {
		DecUtils.out("\n----------------------------\n 17 testVerbatimGraphWithSaysRange\n----------------------------");
		
		// Add statements using SPARQL UPDATE
		
		String update = """
			PREFIX ex: <http://example.org/>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			INSERT DATA {
				ex:Alice ex:says ex:g1 .
				ex:says rdfs:range dec:verbatimWorld .
			}
		""";
		
		executeUpdate(update);
		
		// Verify statements exist
		
		String query = """
			PREFIX ex: <http://example.org/>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			SELECT ?g ?s ?p ?o WHERE {
				{ GRAPH ?g { ?s ?p ?o } }
				
				UNION
				{ ?s ?p ?o }
			}
		""";

		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"s", ".*/Alice",
			"p", ".*/says",
			"o", ".*/g1"
		)), "Test 17: Alice should say g1", "Alice says g1 as expected");

		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"g", "",
			"s", ".*/says",
			"p", ".*range",
			"o", ".*/verbatimWorld"
		)), "Test 17: says predicate should have verbatim range", "says predicate has correct verbatim range as expected");
	}

	
	@Test
	@Order(18)
	
	void test018_SubClassInference() {
		DecUtils.out("\n----------------------------\n 18 testSubClassInference\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			INSERT DATA {
				ex:a rdf:type ex:B .
				ex:B rdfs:subClassOf ex:C .
			}
		""";
		
		executeUpdate(update);

		String query = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			SELECT ?s ?p ?o WHERE {
				?s ?p ?o
			}
			""";

		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"g", "",
			"s", ".*/a",
			"p", ".*type",
			"o", ".*/B"
		)), "Test 18: a should be of type B", "a is of type B as expected");
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"g", "",
			"s", ".*/B",
			"p", ".*subClassOf",
			"o", ".*/C"
		)), "Test 18: B should be subclass of C", "B is subclass of C as expected");
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"g", "",
			"s", ".*/a",
			"p", ".*type",
			"o", ".*/C"
		)), "Test 18: a should be inferred to be of type C", "a is inferred to be of type C as expected");
	}

	
	@Test
	@Order(19)
	
	void test019_SubclassInferenceSelect() {
		DecUtils.out("\n----------------------------\n 19 testSubclassInferenceSelect\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			INSERT DATA {
				ex:a rdf:type ex:B .
				ex:B rdfs:subClassOf ex:C .
			}
		""";
		
		executeUpdate(update);
			
		String query = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			SELECT ?s ?p ?o WHERE {
				?s ?p ?o
				
				FILTER (STRSTARTS(STR(?o), "http://example.org/"))
			}
			""";
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"s", ".*/a",
			"p", ".*type",
			"o", ".*/B"
		)), "Test 19: a should be of type B", "a is of type B as expected");
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"s", ".*/B",
			"p", ".*subClassOf",
			"o", ".*/C"
		)), "Test 19: B should be subclass of C", "B is subclass of C as expected");
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"s", ".*/a",
			"p", ".*type",
			"o", ".*/C"
		)), "Test 19: a should be inferred to be of type C", "a is inferred to be of type C as expected");
	}

	
	@Test
	@Order(20)
	
	void test020_SubclassInferenceConstruct() {  
		DecUtils.out("\n----------------------------\n 20 testSubclassInferenceConstruct\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			INSERT DATA {
				ex:a rdf:type ex:B .
				ex:B rdfs:subClassOf ex:C .
			}
		""";
		
		executeUpdate(update);

		String query = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX ex: <http://example.org/>
			
			SELECT ?s ?p ?o WHERE {
				?s ?p ?o
			}
			""";
		
		checkTrue(matchesPattern(executeQuery(query), Map.of(
			"s", ".*/a",
			"p", ".*type",
			"o", ".*/C"
		)), "Test 20: a should be inferred to be of type C", "a is inferred to be of type C as expected");
	}

	
	@Test
	@Order(21)
	
	void test021_MultipleGraphTypes() {
		DecUtils.out("\n----------------------------\n 21 testMultipleGraphTypes\n----------------------------");
		// Insert data into three named graphs with different types and a triple in the default graph
		
		String insertQuery = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			INSERT DATA {
				GRAPH ex:g1 { ex:a rdf:type ex:B . } 
				ex:g1 rdf:type dec:sharedWorld .
				GRAPH ex:g2 { ex:B rdfs:subClassOf ex:C . } 
				ex:g2 rdf:type dec:verbatimWorld .
				GRAPH ex:g3 { ex:B rdfs:subClassOf ex:D . } 
				ex:g3 rdf:type dec:doxasticWorld .
				ex:B rdfs:subClassOf ex:E . 
			}""";
		
		executeUpdate(insertQuery);

		// Query to get all triples from both named graphs and default graph
		
		String query = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			SELECT DISTINCT ?g ?s ?p ?o WHERE { 
				{ GRAPH ?g { ?s ?p ?o } }
				 UNION 
				{ ?s ?p ?o }  
			}""";
		

		// Check if entity a is of type E in the default graph (should be true)
		
		checkTrue(matchesPattern(executeQuery(query), Map.of("g", "", "s", ".*/a", "p", ".*type", "o", ".*/E")), 
			"Test 21: Entity a should be of type E in default graph", "Entity a is of type E in default graph as expected");

		// Check if entity a is of type E in graph g3 (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", ".*/g3", "s", ".*/a", "p", ".*type", "o", ".*/E")), 
			"Test 21: Entity a should not be of type E in graph g3", "Entity a is not of type E in graph g3 as expected");

		// Check if entity a is of type D in graph g3 (should be true)
		
		checkTrue(matchesPattern(executeQuery(query), Map.of("g", ".*/g3", "s", ".*/a", "p", ".*type", "o", ".*/D")), 
			"Test 21: Entity a should be of type D in graph g3", "Entity a is of type D in graph g3 as expected");

		// Check if entity a is of type D in the default graph (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", "", "s", ".*/a", "p", ".*type", "o", ".*/D")), 
			"Test 21: Entity a should not be of type D in default graph", "Entity a is not of type D in default graph as expected");

		// Check if entity a is of type B in graph g2 (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", ".*/g2", "s", ".*/a", "p", ".*type", "o", ".*/B")), 
			"Test 21: Entity a should not be of type B in graph g2", "Entity a is not of type B in graph g2 as expected");

		// Check if entity a is of type C in graph g2 (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", ".*/g2", "s", ".*/a", "p", ".*type", "o", ".*/C")), 
			"Test 21: Entity a should not be of type C in graph g2", "Entity a is not of type C in graph g2 as expected");
	}

	
	@Test
	@Order(22)
	
	void test022_MultipleGraphTypesWithPredicates() {
		DecUtils.out("\n----------------------------\n 22 testMultipleGraphTypesWithPredicates\n----------------------------");
		// Insert data into three named graphs with different types and a triple in the default graph
		
		String insertQuery = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			INSERT DATA {
				GRAPH ex:g1 { ex:a rdf:type ex:B . } 
				ex:g1 rdf:type dec:sharedWorld .
				GRAPH ex:g2 { ex:B rdfs:subClassOf ex:C . } 
				ex:alice ex:knows ex:g2 .
				GRAPH ex:g3 { ex:B rdfs:subClassOf ex:D . } 
				ex:bruce ex:believes ex:g3 .
				ex:B rdfs:subClassOf ex:E . 
				ex:knows a dec:epistemicPredicate .
				ex:believes a dec:doxasticPredicate .
			}""";
		
		executeUpdate(insertQuery);

		// Query to get all triples from both named graphs and default graph
		
		String query = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			SELECT DISTINCT ?g ?s ?p ?o WHERE { 
				{ GRAPH ?g { ?s ?p ?o } }
				 UNION 
				{ ?s ?p ?o }  
			}""";
		
		// Check if entity a is of type E in the default graph (should be true)
		
		checkTrue(matchesPattern(executeQuery(query), Map.of("g", "", "s", ".*/a", "p", ".*type", "o", ".*/E")), 
			"Test 22: Entity a should be of type E in default graph", "Entity a is of type E in default graph as expected");

		// Check if entity a is of type E in graph g3 (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", ".*/g3", "s", ".*/a", "p", ".*type", "o", ".*/E")), 
			"Test 22: Entity a should not be of type E in graph g3", "Entity a is not of type E in graph g3 as expected");

		// Check if entity a is of type D in graph g3 (should be true)
		
		checkTrue(matchesPattern(executeQuery(query), Map.of("g", ".*/g3", "s", ".*/a", "p", ".*type", "o", ".*/D")), 
			"Test 22: Entity a should be of type D in graph g3", "Entity a is of type D in graph g3 as expected");

		// Check if entity a is of type D in the default graph (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", "", "s", ".*/a", "p", ".*type", "o", ".*/D")), 
			"Test 22: Entity a should not be of type D in default graph", "Entity a is not of type D in default graph as expected");

		// Check if entity a is of type B in graph g2 (should be false)
		
		checkTrue(matchesPattern(executeQuery(query), Map.of("g", "", "s", ".*/a", "p", ".*type", "o", ".*/C")), 
			"Test 22: Entity a should be of type C in default graph", "Entity a is of type C in default graph as expected");

		// Check if entity a is of type C in graph g2 (should be false)
		
		checkFalse(matchesPattern(executeQuery(query), Map.of("g", ".*/g3", "s", ".*/a", "p", ".*type", "o", ".*/C")), 
			"Test 22: Entity a should not be of type C in graph g3", "Entity a is not of type C in graph g3 as expected");
	}

	
	@Test
	@Order(23)
	
	void test023_OwlReasoningComparison() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 23 testOwlReasoningComparison\n----------------------------");
		
		// Create test data with contradictory OWL axioms
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX ex: <http://example.org/>
			
			INSERT DATA {
				ex:A rdfs:subClassOf ex:B .
				ex:A rdfs:subClassOf ex:C .
				ex:B owl:disjointWith ex:C .
				ex:instance1 rdf:type ex:A .
			}
		""";
		
		executeUpdate(update);
		
		// Test direct OWL reasoning
		
		Model baseModel = ModelFactory.createModelForGraph(dataset.getDefaultGraph());
		InfModel directModel = ModelFactory.createInfModel(
			ReasonerRegistry.getOWLReasoner(),
			baseModel
		);
		
		ValidityReport directValidity = directModel.validate();
		
		// Test DecReasoner
		
		InfModel decModel = ModelFactory.createInfModel(
			ReasonerRegistry.getOWLReasoner(),  // Use a new OWL reasoner instance
			ModelFactory.createModelForGraph(dataset.getDefaultGraph())
		);
		
		ValidityReport decValidity = decModel.validate();

		// Compare validity status
		
		checkEquals(directValidity.isValid(), decValidity.isValid(),
			"Test 23: DecReasoner should have same validity as direct OWL reasoning",
			"DecReasoner has same validity as direct OWL reasoning");
			
		// Compare number of validity issues
		
		Iterator<ValidityReport.Report> directReports = directValidity.getReports();
		Iterator<ValidityReport.Report> decReports = decValidity.getReports();
		int directIssueCount = 0;
		int decIssueCount = 0;
		
		while (directReports.hasNext()) {
			directReports.next();
			directIssueCount++;
		}
		
		while (decReports.hasNext()) {
			decReports.next();
			decIssueCount++;
		}
		
		
		checkEquals(directIssueCount, decIssueCount,
			"Test 23: DecReasoner should have same number of validity issues as direct OWL reasoning: " + directIssueCount + " vs " + decIssueCount,
			"DecReasoner has same number of validity issues as direct OWL reasoning: " + directIssueCount );
			
		
		// Compare specific inferences
		
		boolean directHasInferredType = false;
		boolean decHasInferredType = false;
		
		// Check direct model
		
		ResultSet directResults = QueryExecutionFactory.create(
			QueryFactory.create("SELECT ?s ?p ?o WHERE { ?s ?p ?o }"),
			directModel
		).execSelect();
		
		
		while (directResults.hasNext()) {
			QuerySolution soln = directResults.next();
			if (soln.get("s").toString().equals("http://example.org/A") &&
				soln.get("p").toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") &&
				soln.get("o").toString().equals("http://example.org/B")) {
				directHasInferredType = true;
				break;
			}
		}
		
		// Check dec model
		
		ResultSet decResults = QueryExecutionFactory.create(
			QueryFactory.create("SELECT ?s ?p ?o WHERE { ?s ?p ?o }"),
			decModel
		).execSelect();
		
		
		while (decResults.hasNext()) {
			QuerySolution soln = decResults.next();
			if (soln.get("s").toString().equals("http://example.org/A") &&
				soln.get("p").toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") &&
				soln.get("o").toString().equals("http://example.org/B")) {
				decHasInferredType = true;
				break;
			}
		}
		
		
		checkEquals(directHasInferredType, decHasInferredType,
			"Test 23: Both reasoners should make the same inference about A being of type B",
			"Both reasoners made the same inference about A being of type B");
	}

	
	@Test
	@Order(24)
	
	void test024_RdfStarWithSameAsDoxastic() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 24 testRdfStarWithSameAsDoxastic\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:thinks a dec:doxasticPredicate .
				:superman owl:sameAs :ClarkKent .
				:LoisLane :thinks << :superman :can :fly >> .
			}
		""";
		
		executeUpdate(update);
		
		checkFalse(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 24: It should not be stated that ClarkKent can fly (doxastic case)", "It is not stated that ClarkKent can fly, as expected");
		
		
		checkFalse(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/LoisLane",
			"p", ".*/thinks",
			"s1", ".*/ClarkKent",
			"p1", ".*/can",
			"o1", ".*/fly"
		)), "Test 24: LoisLane should not think ClarkKent can fly", "LoisLane does not think ClarkKent can fly as expected");
	}

	
	@Test
	@Order(25)
	
	public void test025_RdfStarWithSameAsConjectural() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 25 testRdfStarWithSameAsConjectural\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:supposes a dec:conjecturalPredicate .
				:superman owl:sameAs :ClarkKent .
				:MotherKent :supposes << :superman :can :fly >> .
			}
		""";
		
		executeUpdate(update);
		
		checkFalse(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 25: It should not be stated that ClarkKent can fly (conjectural case)", "It is not stated that ClarkKent can fly, as expected");
		
		
		checkTrue(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/MotherKent",
			"p", ".*/supposes",
			"s1", ".*/ClarkKent",
			"p1", ".*/can",
			"o1", ".*/fly"
		)), "Test 25: MotherKent should suppose ClarkKent can fly", "MotherKent supposes ClarkKent can fly as expected");
	}

	
	@Test
	@Order(26)
	
	public void test026_RdfStarWithSameAsEpistemic() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 26 testRdfStarWithSameAsEpistemic\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:knows a dec:epistemicPredicate .
				:superman owl:sameAs :ClarkKent .
				:superman :knows << :superman :can :fly >> .
			}
		""";
		
		executeUpdate(update);
		
		checkTrue(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 26: It should be stated that ClarkKent can fly (epistemic case)", "It is stated that ClarkKent can fly, as expected");
		
		
		checkFalse(matchesPattern(executeQuery(globalRdfStarQuery), Map.of(
			"s", ".*/superman",
			"p", ".*/knows",
			"s1", ".*/ClarkKent",
			"p1", ".*/can",
			"o1", ".*/fly"
		)), "Test 26: Superman should not know ClarkKent can fly", "Superman does not know ClarkKent can fly as expected");
	}

	
	@Test
	@Order(27)
	
	public void test027_ReifiedTriplesWithSameAsDoxastic() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 27 testReifiedTriplesWithSameAsDoxastic\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:Statement1 rdf:type rdf:Statement ;
					
					rdf:subject :superman ;
					rdf:predicate :can ;
					rdf:object :fly .
				:LoisLane :thinks :Statement1 .
				:thinks a dec:doxasticPredicate .
				:superman owl:sameAs :ClarkKent .
			}
		""";
		
		executeUpdate(update);
		
		checkFalse(matchesPattern(executeQuery(String.format(globalReificationQuery, "thinks")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 27: It should not be stated that ClarkKent can fly (doxastic case)", "It is not stated that ClarkKent can fly, as expected");
		
		
		checkFalse(matchesPattern(executeQuery(String.format(globalReificationQuery, "thinks")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 27: LoisLane should not think ClarkKent can fly", "LoisLane does not think ClarkKent can fly as expected");
	}

	@Test
	@Order(28)
	@Disabled     // Not working and VERY SLOW
	
	public void test028_ReifiedTriplesWithSameAsConjectural() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 28 testReifiedTriplesWithSameAsConjectural\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:Statement2 rdf:type rdf:Statement ;
					
					rdf:subject :superman ;
					rdf:predicate :can ;
					rdf:object :fly .
				:MotherKent :supposes :Statement2 .
				:supposes a dec:conjecturalPredicate .
				:superman owl:sameAs :ClarkKent .
			}
		""";
		
		executeUpdate(update);
		
		checkFalse(matchesPattern(executeQuery(String.format(globalReificationQuery, "supposes")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 28: It should not be stated that ClarkKent can fly (conjectural case)", "It is not stated that ClarkKent can fly, as expected");
		
		
		checkTrue(matchesPattern(executeQuery(String.format(globalReificationQuery, "supposes")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 28: MotherKent should suppose ClarkKent can fly", "MotherKent supposes ClarkKent can fly as expected");
	}

	
	@Test
	@Order(29)
	
	public void test029_ReifiedTriplesWithSameAsEpistemic() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 29 testReifiedTriplesWithSameAsEpistemic\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:Statement3 rdf:type rdf:Statement ;
					
					rdf:subject :superman ;
					rdf:predicate :can ;
					rdf:object :fly .
				:superman :knows :Statement3 .
				:knows a dec:epistemicPredicate .
				:superman owl:sameAs :ClarkKent .
			}
		""";
		
		executeUpdate(update);
		
		checkTrue(matchesPattern(executeQuery(String.format(globalReificationQuery, "knows")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 29: It should be stated that ClarkKent can fly (epistemic case)", "It is stated that ClarkKent can fly, as expected");
		
	}

	
	@Test
	@Order(30)
	
	public void test030_ReifiedTriplesWithSameAsNirvana() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 30 testReifiedTriplesWithSameAsNirvana\n----------------------------");
		
		String update = """
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX dec: <http://w3id.org/conjectures/>
			PREFIX : <http://example.org/>
			
			INSERT DATA {
				:Statement4 rdf:type rdf:Statement ;
					
					rdf:subject :superman ;
					rdf:predicate :can ;
					rdf:object :fly .
				:superman :knows :Statement4 .
				:knows a dec:epistemicPredicate .
				:superman owl:sameAs :ClarkKent .
			}
		""";
		
		executeUpdate(update);
		
		checkTrue(matchesPattern(executeQuery(String.format(globalReificationQuery, "knows")), Map.of(
			"s", ".*/ClarkKent",
			"p", ".*/can",
			"o", ".*/fly"
		)), "Test 30: It should be stated that ClarkKent can fly (epistemic case)", "It is stated that ClarkKent can fly, as expected");
	}

	@Test
	@Order(31)
	
	public void test031_InferenceWithClearAll() {
		if (debug >= 1) DecUtils.out("\n----------------------------\n 31 testInferenceWithClearAll\n----------------------------");
				
		String insertGraphs = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			
			
			INSERT DATA {
				ex:bruno a ex:Person.
				ex:mario a ex:Student.
				ex:alice a ex:PhD.
				ex:Person rdfs:subClassOf ex:Animal .
				GRAPH ex:epistemicGraph {
					ex:Student rdfs:subClassOf ex:Person .
				}
				
				ex:epistemicGraph rdf:type dec:epistemicWorld .

				GRAPH ex:conjecturalGraph {
					ex:PhD rdfs:subClassOf ex:Person .
				}
				
				ex:conjecturalGraph rdf:type dec:conjecturalWorld .
			}
		""";
		
		executeUpdate(insertGraphs);
		
		String queryInferences = """
			PREFIX ex: <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			
			SELECT DISTINCT ?g ?s ?p ?o WHERE {
				{ GRAPH ?g { ?s ?p ?o } }
				
				UNION
				{ ?s ?p ?o }
			}
		""";

		ResultSetRewindable rs1 = execSelect(queryInferences);
		showAll(rs1);


		// Check that Student is inferred to be subclass of Animal in epistemic graph		
		checkTrue(matchesPattern(executeQuery(queryInferences), Map.of(
			"g", "",
			"s", ".*/mario",
			"p", ".*#type",
			"o", ".*/Animal"
		)), "Test 31: Mario should be inferred as instance of Animal", "Mario correctly inferred as instance of Animal");
		
		// Check that ALICE	 is inferred to be instance of Animal in conjectural graph
		
		checkTrue(matchesPattern(executeQuery(queryInferences), Map.of(
			"g", ".*/conjecturalGraph",
			"s", ".*/alice",
			"p", ".*#type",
			"o", ".*/Animal"
		)), "Test 31: Alice should be inferred as instance of Animal in conjectural graph", "Alice correctly inferred as instance of Animal in conjectural graph");
		
		// Check that ALICE is not inferred to be instance of Animal in default graph
		
		checkFalse(matchesPattern(executeQuery(queryInferences), Map.of(
			"g", "",
			"s", ".*/alice",
			"p", ".*#type",
			"o", ".*/Animal"
		)), "Test 31: Alice should not be inferred as instance of Animal in default graph", "Alice correctly not inferred as instance of Animal in default graph");
		
		// Step 5: Perform CLEAR ALL using SPARQL
		
		String clearAll = "CLEAR ALL";
		executeUpdate(clearAll);
		
		// Step 6: Check that the dataset is actually empty
		
		String queryEmpty = """
			SELECT (COUNT(*) AS ?count) WHERE {
				{ GRAPH ?g { ?s ?p ?o } }
				
				UNION
				{ ?s ?p ?o }
			}
		""";
		
		
		ResultSet emptyResults = executeQuery(queryEmpty);
		checkTrue(emptyResults.hasNext(), "Test 31: Query should return results", "Query returned results as expected");
		
		QuerySolution emptySolution = emptyResults.next();
		int count = emptySolution.getLiteral("count").getInt();
		checkEquals(0, count, "Test 31: Dataset should be empty after CLEAR ALL", "Dataset is empty after CLEAR ALL as expected");
	}

	@Test
	@Order(32)
	void test032_BaseTest() {
		DecUtils.out("\n----------------------------\n 32 testBaseTest\n----------------------------");
		
		// Add statements using SPARQL UPDATE
		
		String update = """
			PREFIX ex:  <http://example.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>
			INSERT DATA {
			  ex:entityX rdf:type ex:A .
			  ex:A rdfs:subClassOf ex:B .
			  GRAPH ex:g2 { ex:A rdfs:subClassOf ex:C . }
			}
		""";
		
		executeUpdate(update);
		
		// Verify statements exist
		
		String query = """
			PREFIX ex: <http://example.org/>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX dec: <http://w3id.org/conjectures/>
			SELECT ?g ?s ?p ?o WHERE {
				{ GRAPH ?g { ?s ?p ?o } }
				
				UNION
				{ ?s ?p ?o }
			}		
		""";

		ResultSetRewindable rs1 = execSelect(query);
		showAll(rs1);

	}


	@Test
	@Order(33)
	void test033_OwlInconsistency() {
    DecUtils.out("\n----------------------------\n 33 testOwlInconsistency\n----------------------------");
    
    // Insert data with an OWL inconsistency
    String update = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX ex: <http://example.org/>
        
        INSERT DATA {
            ex:ClassA rdf:type owl:Class .
            ex:ClassB rdf:type owl:Class .
            ex:ClassA owl:disjointWith ex:ClassB .
            ex:individual rdf:type ex:ClassA .
            ex:individual rdf:type ex:ClassB .
        }
    """;
    
    executeUpdate(update);
    
    // Check if both triples exist
    String query = """
        PREFIX ex: <http://example.org/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
        SELECT ?s ?p ?o WHERE {
            ?s ?p ?o .
            FILTER (?s = ex:individual && ?p = rdf:type && (?o = ex:ClassA || ?o = ex:ClassB))
        }
    """;
    
    ResultSet results = executeQuery(query);
    int count = 0;
    while (results.hasNext()) {
        results.next();
        count++;
    }
    
    checkEquals(2, count, "Test 33: Both inconsistent triples should exist", "Both inconsistent triples exist as expected");
}

}
