FROM stain/jena-fuseki:latest

# Copy your custom JAR to Fuseki lib directory
COPY target/dec-1.0.0.jar /jena-fuseki/

# Copy your custom config
COPY src/main/resources/config.ttl /jena-fuseki/config.ttl

# Expose Fuseki port
EXPOSE 3030

# Start Fuseki with your config
CMD ["java", "-cp", "/jena-fuseki/fuseki-server.jar:/jena-fuseki/dec-1.0.0.jar", "org.apache.jena.fuseki.cmd.FusekiCmd", "--config=/jena-fuseki/config.ttl"]
