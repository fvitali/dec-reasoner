# DEC Reasoner

A Java-based RDF reasoner implementing DEC (Description Logic with Exceptions and Constraints) reasoning capabilities, built on top of Apache Jena and Fuseki.

## Features

- **DEC Reasoning**: Implements Description Logic with Exceptions and Constraints for advanced RDF reasoning
- **SPARQL Endpoint**: Provides a Fuseki-based SPARQL endpoint with custom reasoning capabilities
- **TDB2 Integration**: Uses Jena TDB2 for efficient triple storage and querying
- **Custom Dataset Assembler**: Extends Jena's dataset functionality for DEC-specific operations

## Prerequisites

- **Java 17** or higher
- **Apache Maven 3.6+** for building the project

## Installation

### Option 1: Download Pre-built JAR

1. Download the latest `dec-1.0.0.jar` from the releases page
2. Download the configuration file `config.ttl`
3. Place both files in your working directory

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

3. The JAR file will be created in the `target/` directory as `dec-1.0.0.jar`

## Quick Start

1. **Start the server**:
   ```bash
   java -cp "dec-1.0.0.jar:fuseki-server.jar" org.apache.jena.fuseki.cmd.FusekiCmd --config=config.ttl
   ```

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
├── DecReasoner.java          # Core reasoning engine
├── DecWorld.java             # World model for DEC reasoning
└── handler/                  # Statement handlers for different RDF features
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

This project is licensed under the BSD 3-Clause License - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

For issues and questions, please use the GitHub Issues page.

