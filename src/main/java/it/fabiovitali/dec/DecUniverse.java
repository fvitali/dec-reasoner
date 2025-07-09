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
public class DecUniverse {
    private static final int debug = 0;
    private DecUniverse universe;
    private String name;
    private String graphType;
    private String decType;
    private Boolean pointOfView;
    private Graph baseGraph;
    private InfModel infModel;
    private Reasoner baseReasoner;
    private boolean ready;
    private List<DecUniverse> permeations;
    private boolean enableInference;

    // Default constructor (not recommended, but included for flexibility)
    public DecUniverse() {
        this.universe = this;
        this.name = "";
        this.graphType = "";
        this.decType = "";
        this.pointOfView = false;
        this.baseGraph = null;
        this.infModel = null;
        this.baseReasoner = null;
        this.ready = false;
        this.enableInference = DecUtils.DEFAULT_GRAPH_ENABLED;
        this.decType = DecUtils.DEFAULT_GRAPH_DEC_TYPE;
        this.permeations = new ArrayList<>();
    }

    // Constructor for graph-based universe
    public DecUniverse(String name, String graphType, Graph baseGraph, Reasoner baseReasoner) {
        if (debug >= 2) DecUtils.out("Creating graph-based universe: " + name + " of type " + graphType);
        this.universe = this;
        this.name = name;
        this.graphType = graphType;
        this.baseGraph = baseGraph;
        setupUniverse(graphType, baseReasoner);
    }

    private void setupUniverse(String graphType, Reasoner baseReasoner) {
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
        this.baseReasoner = baseReasoner;
        this.infModel = null;
        this.ready = false;
        this.pointOfView = false;
        this.permeations = new ArrayList<>();
    }

    // Getters
    public String getName() { return name; }
    public String getGraphType() { return graphType; }
    public String getDecType() { return decType; }
    public Graph getBaseGraph() { return baseGraph; }
    public Reasoner getBaseReasoner() { return baseReasoner; }
    public List<DecUniverse> getPermeations() { return permeations; }
    public boolean isInferenceEnabled() { return enableInference; }
    public boolean isPointOfView() { return pointOfView; }

    public InfModel getInfModel() {
        if (debug >= 2) DecUtils.out("   DecUniverse: getInfModel: " + name + " ready: " + ready + 
        " infModel: " + (infModel==null ? "null" : "not null " + (infModel.isEmpty() ? "empty" : "not empty")));

        if (!ready || (infModel == null)) {
            if (debug >= 2) DecUtils.out("   infModel not ready for " + name + " with permeations: " + permeations.stream().map(DecUniverse::getName).collect(Collectors.joining(", ")));
            
            Model baseModel;

            if (baseGraph == null || baseGraph.isEmpty()) {
                baseModel = ModelFactory.createDefaultModel();
            } else {
                baseModel = ModelFactory.createModelForGraph(baseGraph);
            }
           int baseCount = DecUtils.countTriples(baseModel.getGraph());

            List<Model> permeationModels = new ArrayList<>();
            for (DecUniverse permeation : permeations) {
                if (debug >= 4) DecUtils.out("     DecUniverse: permeation for " + name + ": " + permeation.getName());
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
                permeationModel.listStatements().forEachRemaining(stmt -> {
                    if (!DecUtils.isTrivial(stmt.asTriple())) {
                        baseModel.add(stmt);
                    }
                });
            }
            
            int totalBaseCount = DecUtils.countTriples(baseModel.getGraph());

            this.infModel = enableInference ? 
                ModelFactory.createInfModel(baseReasoner, baseModel) : 
                ModelFactory.createInfModel(new FBRuleReasoner(new ArrayList<>()), baseModel);
            ready = true;
//            int infCount = DecUtils.countTriples(this.infModel.getGraph());
//            if (debug >= 4) DecUtils.out("   infModel ready for " + name + " with " + infCount + " triples from " + totalBaseCount + " base triples (original: " + baseCount + ")");
        }
        return this.infModel;
    }

    public Boolean isReady() { return this.ready; }
    public Boolean isNotReady() { return !this.ready; }

    public boolean isModelValid() {
        if (debug >= 1) DecUtils.out("   DecUniverse: infModelValidate: " + name);
        
        // Check if base graph exists and is valid
        if (baseGraph == null) {
            if (debug >= 1) DecUtils.out("     Base graph is null for " + name);
            return false;
        }
        
        try {
            // Test if base graph is accessible
            baseGraph.size();
        } catch (Exception e) {
            if (debug >= 1) DecUtils.out("     Base graph is invalid for " + name + ": " + e.getMessage());
            return false;
        }
        
        // Check all permeations
        for (DecUniverse permeation : permeations) {
            if (permeation == null) {
                if (debug >= 1) DecUtils.out("     Permeation is null for " + name);
                return false;
            }
            
            try {
                // Test if permeation's graph is accessible
                if (debug >= 1) DecUtils.out("     Permeation " + permeation.getName() + " is valid for " + name);
                permeation.getBaseGraph().size();
            } catch (Exception e) {
                if (debug >= 1) DecUtils.out("     Permeation " + permeation.getName() + " is invalid for " + name + ": " + e.getMessage());
                return false;
            }
        }
        
        if (debug >= 1) DecUtils.out("     InfModel is valid for " + name);
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

    public void setPermeations(List<DecUniverse> permeations) { this.permeations = permeations; }
    public void addPermeation(DecUniverse permeation) { this.permeations.add(permeation); }
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
        if (debug >= 4) DecUtils.out("   DecUniverse: setDecType: " + name + " from " + this.decType + " to " + newType);
    }

    public void markReady() { 
        if (debug >= 2) DecUtils.out("Marking universe " + name + " as ready");
        this.ready = true; 
    }
    public void markNotReady() { 
        if (debug >= 2) DecUtils.out("Marking universe " + name + " as not ready");
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
        ret += permeations.size() > 0 ? "\n      [permeations: " + permeations.stream().map(DecUniverse::getName).collect(Collectors.joining(", ")) + "]" : " - no permeations";
        return ret;
    }

    public void showTriples(Model model, String prefix) {
        model.listStatements().filterKeep(s -> 
            s.getSubject().toString().contains(prefix) &&
            s.getObject().toString().contains(prefix)
        ).forEachRemaining(System.out::println);
    }
    
}
