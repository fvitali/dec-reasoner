# DEC Reasoner

DEC Reasoner is a prototype reasoner for RDF datasets containing attributed,
subjective, or hypothetical statements. It implements the DEC model
(**Doxastic, Epistemic, and Conjectural**) over Apache Jena Fuseki.

The reasoner treats attributed statements as belonging to **cognitive worlds**:
separate contexts in which statements may be reported verbatim, believed,
known, conjectured, shared, or accepted as factual. DEC controls how these
worlds interact through **permeation**, namely the controlled availability of
statements from one world to another.

The project is part of the work described in:

> Fabio Vitali and Valentina Pasqual, *Provenance-Enhanced Statements in
> Knowledge Graphs*, arXiv:2606.15246, 2026.
> <https://arxiv.org/abs/2606.15246>

A live demonstrator is available at:

<https://w3id.org/conjectures/dec>

The web interface is maintained separately in the
[`dec-viewer`](https://github.com/fvitali/dec-viewer) repository.

## Status

This is a research prototype. It is intended to demonstrate DEC reasoning over
RDF datasets, not to provide a production-grade triple store.

The current codebase is built with:

- Java 17;
- Apache Jena / Fuseki 4.8.0;
- Maven;
- a configurable Jena base reasoner, by default OWL FB rule reasoning in the
  provided configuration.

## What DEC adds to RDF

RDF provides several ways to represent attributed or contextual statements:

- named graphs;
- RDF-star quoted triples;
- classical RDF reification.

DEC gives these structures an epistemic interpretation. A quoted, reified, or
named set of statements can be treated as a cognitive world, and a cognitive
world can be classified according to the kind of stance it represents.

The main world types are:

| World type | Meaning |
|---|---|
| `dec:verbatimWorld` | A reported wording or assertion kept exactly as stated. No inference is performed inside it. |
| `dec:sharedWorld` | Background knowledge available to reality and to non-verbatim worlds. |
| `dec:doxasticWorld` | A belief, opinion, conviction, or subjective stance independent of reality. |
| `dec:epistemicWorld` | Knowledge, discovery, judgment, or accepted evidence. Its consequences become factual. |
| `dec:conjecturalWorld` | A hypothesis or supposition evaluated against reality without becoming factual. |
| `dec:realityWorld` | The factual core: ordinary assertions, shared knowledge, and epistemic consequences. |

The implementation also contains the following DEC categories:

| Category | Current role |
|---|---|
| `dec:nirvanaWorld` | Implemented experimental category. It receives shared and reality, and contributes to reality. |
| `dec:unionWorld` | Implemented category with inference disabled. It can contribute to reality. |
| `dec:coloredWorld` | Implemented category receiving shared knowledge with inference enabled. |
| `dec:specialWorld` | Internal category used for implementation graphs. |
| `dec:errorWorld` | Internal category for invalid or inconsistent world classification. |

## Declaring cognitive worlds

A cognitive world can be declared directly:

```turtle
:g1 a dec:doxasticWorld .
GRAPH :g1 {
    :Whale rdfs:subClassOf :Fish .
}
```

More commonly, the world type is inferred from the predicate connecting an
agent or context to the world:

```turtle
:believes a dec:doxasticPredicate .
:bruce :believes :fallacy .

GRAPH :fallacy {
    :Whale rdfs:subClassOf :Fish .
}
```

Reverse predicates are also supported:

```turtle
:wasDiscoveredBy a dec:epistemicReversePredicate .
:discovery :wasDiscoveredBy :aristotle .

GRAPH :discovery {
    :Whale rdfs:subClassOf :Mammal .
}
```

A predicate may also be associated with a world type through its range:

```turtle
:knows rdfs:range dec:epistemicWorld .
:alice :knows :g1 .
```

For each DEC category, the implementation recognises three related forms:

| World category | Direct world class | Predicate class | Reverse predicate class |
|---|---|---|---|
| verbatim | `dec:verbatimWorld` | `dec:verbatimPredicate` | `dec:verbatimReversePredicate` |
| shared | `dec:sharedWorld` | `dec:sharedPredicate` | `dec:sharedReversePredicate` |
| doxastic | `dec:doxasticWorld` | `dec:doxasticPredicate` | `dec:doxasticReversePredicate` |
| epistemic | `dec:epistemicWorld` | `dec:epistemicPredicate` | `dec:epistemicReversePredicate` |
| conjectural | `dec:conjecturalWorld` | `dec:conjecturalPredicate` | `dec:conjecturalReversePredicate` |
| nirvana | `dec:nirvanaWorld` | `dec:nirvanaPredicate` | `dec:nirvanaReversePredicate` |
| shared | `dec:sharedWorld` | `dec:sharedPredicate` | `dec:sharedReversePredicate` |
| reality | `dec:realityWorld` | `dec:realityPredicate` | `dec:realityReversePredicate` |
| union | `dec:unionWorld` | `dec:unionPredicate` | `dec:unionReversePredicate` |
| colored | `dec:coloredWorld` | `dec:coloredPredicate` | `dec:coloredReversePredicate` |
| special | `dec:specialWorld` | `dec:specialPredicate` | `dec:specialReversePredicate` |
| error | `dec:errorWorld` | `dec:errorPredicate` | `dec:errorReversePredicate` |

## Permeation model

DEC worlds differ in the way statements become available across cognitive
worlds and reality. This availability is called **permeation**.

| World type | Receives from | Permeates to | Inference |
|---|---|---|---|
| **verbatim** | nothing | nowhere | no |
| **shared** | shared | reality and non-verbatim worlds | yes |
| **doxastic** | shared | nowhere | yes, locally |
| **epistemic** | shared | reality | yes, locally; consequences become factual |
| **conjectural** | shared and reality | nowhere | yes, locally |
| **reality** | shared and epistemic worlds | conjectural worlds | yes |
| **nirvana** | shared and reality | reality | yes |
| **union** | nothing | reality | no |
| **colored** | shared | nowhere | yes, locally |
| **special** | nothing | nowhere | no |
| **error** | nothing | nowhere | no |

A **verbatim world** is isolated. Its statements are preserved as reported and
no inference is performed inside it.

A **shared world** contains background knowledge available to reality and to all
non-verbatim cognitive worlds. It is useful for common taxonomies, shared
individuals, and assumptions that every reasoning context should see.

A **doxastic world** represents a belief, opinion, or subjective stance. It
receives shared knowledge and performs local inference, but its conclusions do
not become facts and reality does not enter it.

An **epistemic world** represents knowledge, discovery, judgment, or accepted
evidence. It receives shared knowledge and performs local inference. Its
contents and consequences permeate into reality.

A **conjectural world** represents a hypothesis or supposition. It receives both
shared knowledge and reality, so it can reason from known facts plus additional
assumptions. Its conclusions remain conjectural and do not permeate back into
reality.

**Reality** contains ordinary factual assertions, shared knowledge, and the
materialised contribution of epistemic worlds. Reality is also the background
against which conjectural worlds are evaluated.

## Disagreements and delusional worlds

DEC can materialise two kinds of diagnostic relation:

```turtle
:g1 dec:disagreesWith :g2 .
:g1 a dec:delusionalWorld .
```

Two worlds **disagree** when they cannot be true together. A world is
**delusional** when it disagrees with reality.

The implementation supports two mechanisms.

### `dec:checkInconsistencies`

```turtle
[] dec:checkInconsistencies true .
```

This asks DEC to combine pairs of worlds and delegate inconsistency checking to
the configured base reasoner. The exact results therefore depend on the
underlying Jena reasoner and on the ontology available in the dataset.

### `dec:closedFor`

```turtle
rdf:subject dec:closedFor :author .
rdf:object  dec:closedFor :creator .
```

`dec:closedFor` introduces a local closure condition for a given predicate. It
is useful when alternatives should be treated as mutually exclusive even though
plain RDF/OWL open-world reasoning would not make them inconsistent.

- `rdf:subject dec:closedFor P` compares the objects used with predicate `P`
  for the same subject across worlds.
- `rdf:object dec:closedFor P` compares the subjects used with predicate `P`
  for the same object across worlds.

## Example: a delusional belief about whales

Bruce believes that whales are fishes. Reality says that whales are mammals,
and mammals are disjoint from fishes. Shared knowledge says that Moby Dick is a
whale. In Bruce's doxastic world, Moby Dick is inferred to be a fish; since this
conflicts with reality, Bruce's world is delusional.

```turtle
@prefix :    <http://example.org/> .
@prefix zoo: <http://example.org/zoo/> .
@prefix dec: <http://w3id.org/conjectures/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

zoo:Whale rdfs:subClassOf zoo:Mammal .
zoo:Fish owl:disjointWith zoo:Mammal .

GRAPH :shared {
    :mobydick a zoo:Whale .
}
:shared a dec:sharedWorld .

GRAPH :fallacy {
    zoo:Whale rdfs:subClassOf zoo:Fish .
}
:bruce :believes :fallacy .
:believes a dec:doxasticPredicate .

[] dec:checkInconsistencies true .
```

Expected behaviour:

- reality infers that `:mobydick` is a `zoo:Mammal`;
- Bruce's doxastic world infers that `:mobydick` is a `zoo:Fish`;
- `:fallacy dec:disagreesWith dec:reality` is materialised;
- `:fallacy a dec:delusionalWorld` is materialised.

## Example: a conjecture about dragons

David supposes that dragons are reptiles. Reality contains Toothless as a
dragon, but does not contain the claim that dragons are reptiles. Since David's
world is conjectural, it receives reality and can infer that Toothless is a
reptile. The conclusion remains conjectural and does not become a fact.

```turtle
@prefix :    <http://example.org/> .
@prefix zoo: <http://example.org/zoo/> .
@prefix dec: <http://w3id.org/conjectures/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

:Toothless a zoo:Dragon .
zoo:Dragon rdfs:subClassOf zoo:ImaginaryBeing .
zoo:Reptile rdfs:subClassOf zoo:Animal .

GRAPH :hypothesis {
    zoo:Dragon rdfs:subClassOf zoo:Reptile .
}

:david :supposes :hypothesis .
:supposes a dec:conjecturalPredicate .
```

Expected behaviour:

- reality contains `:Toothless a zoo:Dragon` and may infer that Toothless is a
  `zoo:ImaginaryBeing`;
- David's conjectural world receives reality;
- inside `:hypothesis`, Toothless is inferred to be a `zoo:Reptile` and a
  `zoo:Animal`;
- these conjectural consequences do not permeate back into reality.

## Building

Prerequisites:

- Java 17;
- Maven 3.6 or later;
- Apache Jena Fuseki 4.8.0 standalone server, if running through Fuseki.

Build the project:

```bash
git clone https://github.com/fvitali/dec-reasoner.git
cd dec-reasoner
mvn clean package
```

The build creates:

```bash
target/dec-1.0.0.jar
```

Run tests:

```bash
mvn test
```

## Running with Fuseki

The repository contains a Fuseki assembler configuration in:

```text
src/main/resources/config.ttl
```

The provided configuration exposes the dataset as `/dec` with the following
endpoints:

| Endpoint | Purpose |
|---|---|
| `/dec/sparql` | SPARQL query |
| `/dec/update` | SPARQL update |
| `/dec/get` | Graph Store Protocol read |
| `/dec/data` | Graph Store Protocol read/write |

To run with a standalone Fuseki server, place the DEC JAR on Fuseki's classpath
and load the provided configuration. For example:

```bash
java -cp "fuseki-server.jar:target/dec-1.0.0.jar" \
    org.apache.jena.fuseki.cmd.FusekiCmd \
    --config=src/main/resources/config.ttl
```

On Windows, replace `:` in the classpath with `;`.

## Configuration

The dataset is installed through `DecDatasetAssembler` and configured in Turtle.
The relevant options are:

```turtle
@prefix dec: <http://w3id.org/conjectures/> .

:decDataset
    dec:location "" ;
    dec:unionDefaultGraph true ;
    dec:allowUpdate true ;
    dec:queryTimeout 60000 ;
    dec:baseReasoner "http://jena.hpl.hp.com/2003/OWLFBRuleReasoner" ;
    dec:loggingLevels "1 2 1 1 1 2 1 1 1 1 1" .
```

| Option | Meaning |
|---|---|
| `dec:location` | Dataset location. Empty string uses an in-memory dataset. |
| `dec:useTDB2` | Use a persistent TDB2 dataset when enabled. |
| `dec:unionDefaultGraph` | Fuseki/Jena default graph setting exposed by the dataset configuration. |
| `dec:allowUpdate` | Enables or disables updates. |
| `dec:queryTimeout` | Query timeout in milliseconds. |
| `dec:baseReasoner` | URI of the Jena reasoner used for RDF/RDFS/OWL inference. |
| `dec:loggingLevels` | Space-separated debug levels for internal components. |

## Repository layout

```text
dec-reasoner/
├── pom.xml
├── README.md
├── LICENSE
└── src/
    ├── main/
    │   ├── java/org/w3id/conjectures/
    │   │   ├── DecDataset.java
    │   │   ├── DecDatasetAssembler.java
    │   │   ├── DecReasoner.java
    │   │   ├── DecUtils.java
    │   │   ├── DecWorld.java
    │   │   └── handler/
    │   │       ├── DecStatementHandler.java
    │   │       ├── DefaultGraphHandler.java
    │   │       ├── NamedGraphHandler.java
    │   │       ├── RDFStarHandler.java
    │   │       └── ReificationHandler.java
    │   └── resources/
    │       └── config.ttl
    └── test/
```

## Current limitations

- DEC Reasoner is a prototype research implementation.
- Reasoning is materialisation-oriented: updates may trigger reconstruction of
  world-relative graphs and inferred statements.
- Inconsistency checking depends on the configured Jena base reasoner.
- The implemented categories include internal and experimental world types whose
  behaviour may change.
- The project is designed to demonstrate the DEC model, not to replace a
  general-purpose RDF store.

## Related projects

- DEC live demonstrator: <https://w3id.org/conjectures/dec>
- DEC Viewer source code: <https://github.com/fvitali/dec-viewer>
- DEC Reasoner source code: <https://github.com/fvitali/dec-reasoner>

## Citation

```bibtex
@misc{vitali_pasqual_2026_dec,
  title        = {Provenance-Enhanced Statements in Knowledge Graphs},
  author       = {Fabio Vitali and Valentina Pasqual},
  year         = {2026},
  eprint       = {2606.15246},
  archivePrefix = {arXiv},
  url          = {https://arxiv.org/abs/2606.15246}
}
```

## License

This project is released under the MIT License. See [`LICENSE`](LICENSE).
