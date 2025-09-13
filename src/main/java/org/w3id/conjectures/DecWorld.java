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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.FBRuleReasoner;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@SuppressWarnings("unused")
public class DecWorld {
    private static int debug = 0;
    private DecWorld world;
    private String name;
    private String graphType;
    private String decType;
    private Boolean pointOfView;
    private Graph baseGraph;
    private InfModel infModel;
    private DecDataset dataset;
    private boolean ready;
    private List<DecWorld> permeations;
    private boolean enableInference;

    private final ThreadLocal<Integer> infModelCallDepth = ThreadLocal.withInitial(() -> 0);

    // Default constructor (not recommended, but included for flexibility)
    public DecWorld() {
        this.world = this;
        this.name = "";
        this.graphType = "";
        this.decType = "";
        this.pointOfView = false;
        this.baseGraph = null;
        this.infModel = null;
        this.dataset = null;
        this.ready = false;
        this.enableInference = DecUtils.DEFAULT_GRAPH_ENABLED;
        this.decType = DecUtils.DEFAULT_GRAPH_DEC_TYPE;
        this.permeations = new ArrayList<>();
    }

    // Constructor for graph-based world
    public DecWorld(String name, String graphType, Graph baseGraph, DecDataset decDataset) {
        int debugLevel = DecUtils.getDebugLevel(3); // Position 3 for DecWorld
        // Set the debug level for this class
        this.debug = debugLevel;
        if (debug >= 2) DecUtils.out("Creating graph-based world: " + name + " of type " + graphType);
        this.world = this;
        this.name = name;
        this.graphType = graphType;
        this.baseGraph = baseGraph;
        setupWorld(graphType, decDataset);
    }

    private void setupWorld(String graphType, DecDataset dataset) {
        if ("default".equals(graphType)) {
            this.enableInference = DecUtils.DEFAULT_GRAPH_ENABLED;
            this.decType = DecUtils.DEFAULT_GRAPH_DEC_TYPE;
        } else if ("named".equals(graphType)) {
            this.enableInference = DecUtils.DEFAULT_NAMED_GRAPH_ENABLED;
            this.decType = DecUtils.DEFAULT_NAMED_GRAPH_DEC_TYPE;
        } else if ("reification".equals(graphType)) {
            this.enableInference = DecUtils.DEFAULT_REIFICATION_ENABLED;
            this.decType = DecUtils.DEFAULT_REIFICATION_DEC_TYPE;
        } else if ("rdf-star".equals(graphType)) {
            this.enableInference = DecUtils.DEFAULT_RDF_STAR_ENABLED;
            this.decType = DecUtils.DEFAULT_RDF_STAR_DEC_TYPE;
        } else if ("special".equals(graphType)) {
            this.enableInference = DecUtils.DEFAULT_SPECIAL_ENABLED;
            this.decType = DecUtils.DEFAULT_SPECIAL_DEC_TYPE;
        } else {
            this.enableInference = false;
            this.decType = "error";
        }
        this.dataset = dataset;
        this.infModel = null;
        this.ready = false;
        this.pointOfView = false;
        this.permeations = new ArrayList<>();
    }

    // Getters
    public String getName() { return name; }
    public String getGraphType() { return graphType; }
    public String getDecType() { return decType; }
    public DecDataset getDataset() { return dataset; }
    public List<DecWorld> getPermeations() { return permeations; }
    public boolean isInferenceEnabled() { return enableInference; }
    public boolean isPointOfView() { return pointOfView; }

    public Graph getBaseGraph() { return baseGraph; }
    public Graph getPermeatedGraph() { 
        if (debug >= 2) DecUtils.out("getPermeatedGraph for", name, 8); 
        if (debug >= 3) DecUtils.out("Permeations:", permeations.size() > 0 ? showPermeations() : "none", ready?"ready":"not ready", 8);  
        return getInfModel().getGraph(); 
    }

    public InfModel getInfModel() {
        int currentDepth = infModelCallDepth.get();

        if (debug >= 3) {
            DecUtils.out("getInfModel for", name, String.valueOf(currentDepth), 8); 
        }

        if (currentDepth >= 2) {
            DecUtils.out("!!! Nested getInfModel calls for", name, String.valueOf(currentDepth), 12);
            Thread.dumpStack();
            return null; // or handle appropriately
        }
        infModelCallDepth.set(currentDepth + 1);
        try {

            if (!ready || (infModel == null)) {
                Model baseModel;

                if (baseGraph == null || baseGraph.isEmpty()) {
                    baseModel = ModelFactory.createDefaultModel();
                } else {
                    baseModel = ModelFactory.createModelForGraph(baseGraph);
                }
               int baseCount = DecUtils.countTriples(baseModel.getGraph());

               List<Model> permeationModels = new ArrayList<>();
                for (DecWorld permeation : permeations) {
                    permeationModels.add(permeation.getInfModel());
                }
/*
                Model unionModel = ModelFactory.createDefaultModel();
                for (Model m : permeationModels) {
                    unionModel = ModelFactory.createUnion(unionModel, m);
                }
                int unionCount = DecUtils.countTriples(unionModel.getGraph());
                unionModel = ModelFactory.createUnion(unionModel, baseModel);
*/
                for (Model permeationModel : permeationModels) {
                    if (permeationModel == null) continue;
                    permeationModel.listStatements().forEachRemaining(stmt -> {
                        if (!DecUtils.isTrivial(stmt.asTriple())) {
                            baseModel.add(stmt);
                        }
                    });
                }
                
                int totalBaseCount = DecUtils.countTriples(baseModel.getGraph());

                this.infModel = enableInference ? 
                    ModelFactory.createInfModel(dataset.getBaseReasoner(), baseModel) : 
                    ModelFactory.createInfModel(new FBRuleReasoner(new ArrayList<>()), baseModel);
                ready = true;
//            int infCount = DecUtils.countTriples(this.infModel.getGraph());
//            if (debug >= 4) DecUtils.out("   infModel ready for " + name + " with " + infCount + " triples from " + totalBaseCount + " base triples (original: " + baseCount + ")");
            }
            return this.infModel;
        } finally {
            infModelCallDepth.set(currentDepth);
        }
    }

    public Boolean isReady() { return this.ready; }
    public Boolean isNotReady() { return !this.ready; }

    public boolean isModelValid() {
        if (debug >= 2) DecUtils.out("isModelValid ", name, 8);
        
        if (baseGraph == null) {
            if (debug >= 1) DecUtils.out("!!! Base graph is null for ", name, 8);
            return false;
        }
        
        try {
            baseGraph.size();
        } catch (Exception e) {
            if (debug >= 1) DecUtils.out("!!! Base graph is invalid for ", name, e.getMessage(), 8);
            return false;
        }
        
        for (DecWorld permeation : permeations) {
            if (permeation == null) {
                if (debug >= 2) DecUtils.out("Permeation is null for ", name, 8);
                return false;
            }
            
            try {
                if (debug >= 2) DecUtils.out("Permeation ", permeation.getName(), " is valid for ", name);
                permeation.getBaseGraph().size();
            } catch (Exception e) {
                if (debug >= 2) DecUtils.out("Permeation ", permeation.getName(), " is invalid for ", name, e.getMessage(), 8);
                return false;
            }
        }        
        return true;
    }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setGraphType(String graphType) { this.graphType = graphType; }
    public void setBaseGraph(Graph baseGraph) { this.baseGraph = baseGraph; }
    public void setInfModel(InfModel infModel) { 
        this.infModel = infModel; 
        this.ready = (infModel != null && !infModel.isEmpty());
    }
    public void setEnableInference(boolean enableInference) { this.enableInference = enableInference; }
    public void setPointOfView(boolean pointOfView) { this.pointOfView = pointOfView; }

    public void setPermeations(List<DecWorld> permeations) { this.permeations = permeations; }
    public void addPermeation(DecWorld permeation) { this.permeations.add(permeation); }
    public void setDecType(String decType) {
        String newType = this.decType;  // Default to current type
        
        if (this.decType == null) {
            newType = decType;
        } else if (this.decType.equals("shared")) {
            // Keep current type (shared never changes)
        } else if (this.decType.equals("reality")) {
            newType = decType.equals("conjectural") ? decType : "error";
        } else if ((this.decType.equals("epistemic") && decType.equals("conjectural")) ||
                   (this.decType.equals("conjectural") && decType.equals("epistemic"))) {
            newType = "union";
        } else {
            // Define progression order
            String[] progression = {"verbatim", "doxastic", "epistemic", "conjectural", "union", "shared"};
            
            // Find current position
            int currentPos = -1;
            for (int i = 0; i < progression.length; i++) {
                if (progression[i].equals(this.decType)) {
                    currentPos = i;
                    break;
                }
            }
            
            // Find new position
            int newPos = -1;
            for (int i = 0; i < progression.length; i++) {
                if (progression[i].equals(decType)) {
                    newPos = i;
                    break;
                }
            }
            
            // Only allow progression (higher position)
            if (newPos > currentPos) {
                newType = decType;
            }
        }

        this.decType = newType;
        if (debug >= 4) DecUtils.out("   DecWorld: setDecType: " + name + " from " + this.decType + " to " + newType);
    }

    public void markReady() { 
        if (debug >= 2) DecUtils.out("Marking world " + name + " as ready");
        this.ready = true; 
    }
    public void markNotReady() { 
        if (debug >= 2) DecUtils.out("Marking world " + name + " as not ready");
        this.ready = false; 
    }

    @Override
    public String toString() {
        String ret = "    "+ decType + " - " + DecUtils.countTriples(baseGraph) + "->" + 
            (infModel != null ? DecUtils.countTriples(infModel.getGraph()) : DecUtils.countTriples(baseGraph)) + 
            " triples from " + graphType + 
            (enableInference ? " (inference enabled)" : " (inference disabled)") + ": " + 
            (ready ? " ready " : " not ready ") +
            (pointOfView ? " (point of view enabled)" : " (point of view disabled)");
        ret += permeations.size() > 0 ? "\n      [permeations: " + showPermeations() + "]" : " - no permeations";
        return ret;
    }

    public void showTriples(Model model, String prefix) {
        model.listStatements().filterKeep(s -> 
            s.getSubject().toString().contains(prefix) &&
            s.getObject().toString().contains(prefix)
        ).forEachRemaining(System.out::println);
    }

    public String showPermeations() {
        return permeations.stream().map(DecWorld::getName).collect(Collectors.joining(", "));
    }
    
}
