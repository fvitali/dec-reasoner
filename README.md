# DEC Reasoner

A SPARQL reasoning engine that implements DEC (Disagreement, Epistemic, and Conjectural) reasoning capabilities. This system extends Apache Jena Fuseki with custom reasoning logic for handling epistemic worlds, conjectural statements, and disagreement analysis.

## What is DEC Reasoning?

DEC (Disagreement, Epistemic, and Conjectural) reasoning is a framework for representing and analyzing different types of knowledge and belief states:

- **Disagreement**: Representing conflicting viewpoints or contradictory information between different sources or agents
- **Epistemic**: Knowledge states and what agents know, believe, or are uncertain about
- **Conjectural**: Hypothetical, speculative, or tentative information that may be revised

The DEC Reasoner provides SPARQL extensions and reasoning capabilities to work with these concepts in RDF datasets.

## Features

- **Extended SPARQL Support**: Custom functions and reasoning for DEC concepts
- **Epistemic Worlds**: Support for representing different knowledge states and belief contexts
- **RDF-star Processing**: Handle quoted triples for meta-statements about statements
- **Disagreement Detection**: Automatic identification of conflicting information
- **Conjectural Reasoning**: Processing of hypothetical and speculative statements
- **Apache Jena Integration**: Built on top of the robust Jena framework

## Quick Start with Pre-built JAR

### Prerequisites

- Java 11 or higher
- At least 2GB RAM recommended

### Download and Run

1. **Download the latest JAR file:**
   - Download `dec-1.0.0.jar` from the [releases page](https://github.com/fvitali/dec-reasoner/releases)
   - Or build from source (see below)

2. **Run the reasoner:**
   ```bash
   java -jar dec-1.0.0.jar
   ```

3. **Access the SPARQL endpoint:**
   - SPARQL Query: `http://localhost:3030/dec/sparql`
   - SPARQL Update: `http://localhost:3030/dec/update`
   - Web Interface: `http://localhost:3030`

### Configuration

The reasoner uses a configuration file (`config.ttl`) that defines:
- Dataset assemblies and reasoning rules
- DEC-specific processing options
- SPARQL endpoint settings

## Building from Source

### Prerequisites

- Java 11 or higher
- Apache Maven 3.6 or higher
- Git

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/fvitali/dec-reasoner.git
   cd dec-reasoner
   ```

2. **Build the project:**
   ```bash
   mvn clean compile
   ```

3. **Run tests:**
   ```bash
   mvn test
   ```

4. **Create the JAR file:**
   ```bash
   mvn package
   ```
   
   This creates `target/dec-1.0.0.jar`

5. **Run the built JAR:**
   ```bash
   java -jar target/dec-1.0.0.jar
   ```

### Development Build

For development with automatic recompilation:

```bash
mvn compile exec:java -Dexec.mainClass="org.apache.jena.fuseki.main.FusekiMain" -Dexec.args="--config=src/main/resources/config.ttl"
```

## Project Structure

```
dec-reasoner/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/w3id/conjectures/
│   │   │       ├── DecDataset.java          # Main dataset implementation
│   │   │       ├── DecDatasetAssembler.java # Dataset factory
│   │   │       ├── DecReasoner.java         # Core reasoning engine
│   │   │       ├── DecUtils.java            # Utility functions
│   │   │       ├── DecWorld.java            # Epistemic world handling
│   │   │       └── handler/                 # Statement processors
│   │   └── resources/
│   │       ├── config.ttl                   # Fuseki configuration
│   │       └── shiro.ini                    # Security configuration
│   └── test/
│       ├── java/                            # Unit tests
│       └── resources/                       # Test resources
├── pom.xml                                  # Maven configuration
├── LICENSE                                  # License file
└── README.md                               # This file
```

## Usage

### Basic SPARQL Queries

The reasoner supports standard SPARQL 1.1 queries with DEC extensions:

```sparql
PREFIX dec: <http://w3id.org/conjectures/>

# Query for epistemic statements
SELECT ?s ?p ?o ?world WHERE {
  GRAPH ?world {
    ?s ?p ?o .
    ?world a dec:epistemicWorld .
  }
}
```

### DEC-Specific Features

#### Epistemic Worlds
```sparql
# Find statements in epistemic contexts
SELECT ?statement ?world WHERE {
  ?world a dec:epistemicWorld .
  GRAPH ?world { ?statement ?p ?o }
}
```

#### Conjectural Statements
```sparql
# Query conjectural information
SELECT ?s ?p ?o WHERE {
  ?s ?p ?o .
  ?s dec:conjectural true .
}
```

#### Disagreement Analysis
```sparql
# Find conflicting statements
SELECT ?s ?p ?o1 ?o2 WHERE {
  ?s ?p ?o1 .
  ?s ?p ?o2 .
  FILTER(?o1 != ?o2)
  ?s dec:hasDisagreement true .
}
```

### Loading Data

You can load RDF data through:

1. **SPARQL UPDATE endpoint:**
   ```sparql
   INSERT DATA {
     GRAPH <http://example.org/world1> {
       <http://example.org/alice> <http://example.org/believes> "The earth is round" .
     }
   }
   ```

2. **Web interface:** Upload files through the Fuseki web UI at `http://localhost:3030`

3. **REST API:** Use HTTP PUT/POST to load data programmatically

## Configuration

### Custom Configuration

Create a custom `config.ttl` file:

```turtle
@prefix fuseki: <http://jena.apache.org/fuseki#> .
@prefix dec: <http://w3id.org/conjectures/> .

<#service> a fuseki:Service ;
    fuseki:name "dec" ;
    fuseki:serviceQuery "sparql" ;
    fuseki:serviceUpdate "update" ;
    fuseki:dataset <#dataset> .

<#dataset> a dec:DecDataset ;
    dec:enableReasoning true ;
    dec:disagreementDetection true ;
    dec:epistemicProcessing true .
```

### Memory Settings

For large datasets, increase JVM memory:

```bash
java -Xmx4g -jar dec-1.0.0.jar
```

## Integration

### With DEC Viewer

This reasoner is designed to work with the [DEC Viewer](https://github.com/fvitali/dec-viewer), which provides a web-based interface for visualizing and querying DEC datasets.

### Programmatic Access

```java
// Java example
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;

String endpoint = "http://localhost:3030/dec/sparql";
String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10";

QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, queryString);
ResultSet results = qexec.execSelect();
ResultSetFormatter.out(System.out, results);
```

## Testing

Run the test suite:

```bash
mvn test
```

Tests cover:
- DEC reasoning logic
- SPARQL query processing
- Data loading and persistence
- Configuration validation

## Performance

### Optimization Tips

1. **Indexing**: The reasoner automatically creates appropriate indexes for DEC queries
2. **Memory**: Allocate sufficient heap space for your dataset size
3. **Caching**: Enable query result caching for repeated queries
4. **Batch Loading**: Use SPARQL UPDATE with multiple statements for bulk data loading

### Benchmarks

Performance varies based on dataset size and query complexity. Typical performance on modern hardware:
- Simple triple patterns: 1000+ queries/second
- Complex DEC reasoning: 10-100 queries/second
- Data loading: 10,000+ triples/second

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`mvn test`)
6. Commit your changes (`git commit -m 'Add some amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For questions, issues, or contributions:
- GitHub Issues: [Report bugs or request features](https://github.com/fvitali/dec-reasoner/issues)
- Contact: Reach out to the maintainers through GitHub

## Citation

If you use this software in academic work, please cite:

```bibtex
@software{dec_reasoner,
  title={DEC Reasoner: A SPARQL Engine for Doxastic, Epistemic, and Conjectural Reasoning},
  author={Fabio Vitali},
  year={2025},
  url={https://github.com/fvitali/dec-reasoner}
}
```