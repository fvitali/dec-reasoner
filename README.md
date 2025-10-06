# DEC Reasoner

DEC Reasoner is an RDF reasoner for cognitive worlds over a quad store using Fuseki 4.8.0.

### DEC cognitive worlds
DEC stands for Doxastic, Epistemic, and Conjectural, three types of cognitive worlds meant to characterize the subjective state of mind of one or more actors holding beliefs, concepts and hypotheses over reality. Examples are "Alice believes that John is a painter" or "Bruce knows that painters are artists." or "Catherine supposes John painted 'Still life #13'." The DEC Reasoner is able to carry out inferences on objective and subjective assertions like these when expressed in RDF.

Cognitive worlds are sets of statements, isolated from each other (and, in general, from reality), which are held for true, or abstractly considered for evaluation, by a cognitive agent or within a given cognitive contest. The exact nature of the cognitive world is given by a predicate connecting the agent (or the context) to the cognitive world, and is determined by the nature of the governing verb. 

Please visit [the DEC Viewer](http://204.216.209.229:3000/) web application for a more detailed introduction and several examples.  

## Features

- **DEC Reasoning**: Implements Doxastic, Epistemic and Conjectural Inference for RDF graphs and rdf-star datasets
- **SPARQL Endpoint**: Provides a Fuseki-based SPARQL endpoint with custom reasoning capabilities
- **TDB2 Integration**: Uses Jena TDB2 for efficient triple storage and querying
- **Custom Dataset Assembler**: Extends Jena's dataset functionality for DEC-specific operations

## Prerequisites

- **Java 17** or higher
- **Apache Maven 3.6+** for building the project

## Installation

### Option 1: Download Pre-built JAR

1. Download the latest `dec-1.0.0.jar` from the releases page
2. Download the Apache Jena Fuseki server JAR (`jena-fuseki-server-4.8.0.jar`) from the [Jena releases](https://jena.apache.org/download/)
3. Copy both JARs to the `fuseki/server/` directory
4. Copy `src/main/resources/config.ttl` to `fuseki/server/config.ttl`

### Option 2: Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/dec-graphs.git
   cd dec-graphs
   ```

2. Build the project using Maven:
   ```bash
   mvn clean compile package
   ```

3. Copy the built JAR files to the `fuseki/server/` directory:
   ```bash
   cp target/dec-1.0.0.jar fuseki/server/
   # Also copy fuseki-server.jar if not already present
   ```

## Quick Start

1. **Start the server** (from the project root):
   ```bash
   cd fuseki/server
   java -cp "fuseki-server.jar:dec-1.0.0.jar" org.apache.jena.fuseki.cmd.FusekiCmd --config=config.ttl
   ```

   *Note: Ensure `fuseki-server.jar` is available in the `fuseki/server/` directory alongside your `dec-1.0.0.jar`*

2. **Access the endpoint**:
   - SPARQL Query: http://localhost:3030/dec/sparql
   - SPARQL Update: http://localhost:3030/dec/update
   - Data Access: http://localhost:3030/dec/data

## Configuration

The `config.ttl` file contains the server configuration:

```turtle
@prefix :        <#> .
@prefix fuseki:  <http://jena.apache.org/fuseki#> .
@prefix dec:     <http://w3id.org/conjectures/> .

:service1 rdf:type fuseki:Service ;
    fuseki:name "dec" ;
    fuseki:endpoint [ fuseki:operation fuseki:query ; fuseki:name "sparql" ] ;
    fuseki:dataset :decDataset .

:decDataset rdf:type ja:Dataset ;
    ja:assembler "org.w3id.conjectures.DecDatasetAssembler" ;
    dec:location "" ;
    dec:unionDefaultGraph true .
```

## Usage Examples

### SPARQL Query

```bash
curl -X POST -H "Content-Type: application/sparql-query" \
     -d "SELECT * WHERE { ?s ?p ?o } LIMIT 10" \
     http://localhost:3030/dec/sparql
```

### Loading Data

```bash
curl -X PUT -H "Content-Type: application/rdf+xml" \
     -d @your-data.rdf \
     http://localhost:3030/dec/data
```

## Development

### Project Structure

```
src/main/java/org/w3id/conjectures/
├── DecDataset.java           # Main dataset implementation
├── DecDatasetAssembler.java  # Jena assembler for DEC datasets
├── DecReasoner.java          # Wrapper of the reasoning engine
├── DecWorld.java             # Cognitive world model
└── handler/                  # Statement handlers for different RDF syntaxes
```

### Building and Testing

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Generate documentation
mvn javadoc:javadoc
```

## Configuration Options

The DEC dataset assembler supports several configuration parameters:

- `dec:location`: Base location for TDB datasets
- `dec:unionDefaultGraph`: Whether to use union default graph (boolean)
- `dec:allowUpdate`: Allow SPARQL updates (boolean)
- `dec:queryTimeout`: Query timeout in milliseconds
- `dec:baseReasoner`: Base reasoner URI (default: OWL FB rule reasoner)
- `dec:loggingLevels`: Logging levels for different components

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

For issues and questions, please use the GitHub Issues page.

