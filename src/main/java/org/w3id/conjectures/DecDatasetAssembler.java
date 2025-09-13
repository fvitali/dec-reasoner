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

import java.util.Arrays;

@SuppressWarnings("unused")
public class DecDatasetAssembler extends AssemblerBase {
    private static int debug = 0;

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
        try {
            String location = getStringValue(root, "location", DecUtils.DEFAULT_LOCATION);
            boolean useTDB2 = getBooleanValue(root, "useTDB2", false);
            boolean unionDefaultGraph = getBooleanValue(root, "unionDefaultGraph", false);
            boolean allowUpdate = getBooleanValue(root, "allowUpdate", true);
            int queryTimeout = getIntValue(root, "queryTimeout", 60000);
            String reasonerURI = getStringValue(root, "baseReasoner", null);
            String levels = getStringValue(root, "loggingLevels", "1");

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
    
    private String getStringValue(Resource root, String property, String defaultValue) {
        if (root.hasProperty(root.getModel().createProperty(property))) {
            return root.getProperty(root.getModel().createProperty(property)).getString();
        }
        return defaultValue;
    }

    private boolean getBooleanValue(Resource root, String property, boolean defaultValue) {
        if (root.hasProperty(root.getModel().createProperty(property))) {
            return root.getProperty(root.getModel().createProperty(property)).getBoolean();
        }
        return defaultValue;
    }

    private int getIntValue(Resource root, String property, int defaultValue) {
        if (root.hasProperty(root.getModel().createProperty(property))) {
            return root.getProperty(root.getModel().createProperty(property)).getInt();
        }
        return defaultValue;
    }
}