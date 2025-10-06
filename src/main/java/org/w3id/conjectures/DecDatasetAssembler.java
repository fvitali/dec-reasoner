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

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.assemblers.AssemblerBase;
import org.apache.jena.assembler.Mode;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.rdf.model.Property;

import java.util.Arrays;

@SuppressWarnings("unused")
public class DecDatasetAssembler extends AssemblerBase {
    private static int debug = 0;

    private static final String DEC = "http://w3id.org/conjectures/";
private static final Property LOCATION_PROP = ResourceFactory.createProperty(DEC, "location");
private static final Property USE_TDB2_PROP = ResourceFactory.createProperty(DEC, "useTDB2");
private static final Property UNION_DG_PROP = ResourceFactory.createProperty(DEC, "unionDefaultGraph");
private static final Property ALLOW_UPDATE_PROP = ResourceFactory.createProperty(DEC, "allowUpdate");
private static final Property QUERY_TIMEOUT_PROP = ResourceFactory.createProperty(DEC, "queryTimeout");
private static final Property REASONER_URI_PROP = ResourceFactory.createProperty(DEC, "baseReasoner");
private static final Property LOG_LEVELS_PROP = ResourceFactory.createProperty(DEC, "loggingLevels");


    static {
        init();  // Ensures `init()` is always called when the class is loaded
    }

    // Initialize the assembler for DecDataset
    public static void init() {
        DecUtils.out("Assembler init");
        Assembler.general.implementWith(
            ResourceFactory.createResource(DecUtils.decPrefix + "#DecDataset"),
            new DecDatasetAssembler()
        );
    }

    @Override
    public Dataset open(Assembler a, Resource root, Mode mode) {
        DecUtils.out("DecDatasetAssembler: open");
        try {
            String location = getStringValue(root, LOCATION_PROP, DecUtils.DEFAULT_LOCATION);
            boolean useTDB2 = getBooleanValue(root, USE_TDB2_PROP, false);
            boolean unionDefaultGraph = getBooleanValue(root, UNION_DG_PROP, false);
            boolean allowUpdate = getBooleanValue(root, ALLOW_UPDATE_PROP, true);
            int queryTimeout = getIntValue(root, QUERY_TIMEOUT_PROP, 60000);
            String reasonerURI = getStringValue(root, REASONER_URI_PROP, null);
            String levels = getStringValue(root, LOG_LEVELS_PROP, "1");

            root.listProperties().forEachRemaining(stmt ->
                DecUtils.out("Property: " + stmt.getPredicate() + " -> " + stmt.getObject())
            );

            // Load the reasoner
            Reasoner reasoner = null;
            if (reasonerURI != null) {
                try {
                    reasoner = ReasonerRegistry.theRegistry().create(reasonerURI, null);
                } catch (Exception e) {
                    DecUtils.out("Failed to load reasoner from URI: " + e.getMessage());
                }
            }
            if (reasoner == null) {
                try {
                    reasoner = ReasonerRegistry.getOWLReasoner();
                } catch (Exception e) {
                    DecUtils.out("Failed to load OWL reasoner: " + e.getMessage());
                }
            }
            Reasoner baseReasoner = reasoner;
            if (debug >= 1) DecUtils.out("DecDatasetAssembler: baseReasoner: " + baseReasoner);

            DatasetGraph datasetGraph = new DecDataset(location, useTDB2, unionDefaultGraph, allowUpdate, queryTimeout, baseReasoner, levels);            
            debug = DecUtils.getDebugLevel(0);
            DecUtils.out("DecDatasetAssembler: debug level (0): " + debug);

            return DatasetFactory.wrap(datasetGraph);
        } catch (Exception e) {
            logError("DecDatasetAssembler", "open", e);
            throw new RuntimeException("Error opening dataset", e);
        }
    }

    private static void logError(String className, String method, Exception e) {
        StackTraceElement origin = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
        String location = origin != null ? String.format("(%s:%d)", origin.getFileName(), origin.getLineNumber()) : "(unknown location)";
        DecUtils.out(String.format("[%s] [Thread: %s] Exception in %s.%s %s - %s", 
            java.time.LocalDateTime.now(), Thread.currentThread().getName(), 
            className, method, location, e.getMessage()));
    }
    
    private String getStringValue(Resource root, Property property, String defaultValue) {
        if (root.hasProperty(property)) {
            return root.getProperty(property).getString();
        }
        return defaultValue;
    }
    
    private boolean getBooleanValue(Resource root, Property property, boolean defaultValue) {
        if (root.hasProperty(property)) {
            return root.getProperty(property).getBoolean();
        }
        return defaultValue;
    }

    private int getIntValue(Resource root, Property property, int defaultValue) {
        if (root.hasProperty(property)) {
            return root.getProperty(property).getInt();
        }
        return defaultValue;
    }
}