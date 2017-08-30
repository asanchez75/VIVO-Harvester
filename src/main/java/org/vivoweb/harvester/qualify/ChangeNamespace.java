/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.qualify;

import java.io.IOException;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;

/**
 * Changes the namespace for all matching uris
 * @author Christopher Haines (hainesc@ctrip.ufl.edu)
 */
public class ChangeNamespace {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(ChangeNamespace.class);
	/**
	 * The model to change uris in
	 */
	private final JenaConnect model;
	/**
	 * The old namespace
	 */
	private final String oldNamespace;
	/**
	 * The new namespace
	 */
	private final String newNamespace;
	/**
	 * The model in which to search for previously used uris
	 */
	private final JenaConnect vivo;
	/**
	 * Log error messages for changed nodes
	 */
	private final boolean errorLogging;
	
	/*
	 * create sameAs statement 
	 */
	private final boolean sameAs;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private ChangeNamespace(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error reading config
	 */
	private ChangeNamespace(ArgList argList) throws IOException {
		this(
			JenaConnect.parseConfig(argList.get("i"), argList.getValueMap("I")), 
			JenaConnect.parseConfig(argList.get("v"), argList.getValueMap("V")), 
			argList.get("u"), 
			argList.get("n"), 
			argList.has("e"),
			argList.has("s")
		);
	}
	
	public ChangeNamespace(JenaConnect model, JenaConnect vivo, String oldName, String newName, boolean errorLog) {
		this(model, vivo, oldName, newName,  errorLog, false);
	}
	
	/**
	 * Constructor
	 * @param model model to change uris in
	 * @param vivo model in which to search for previously used uris
	 * @param oldName old namespace
	 * @param newName new namespace
	 * @param errorLog log error messages for changed nodes
	 */
	public ChangeNamespace(JenaConnect model, JenaConnect vivo, String oldName, String newName, boolean errorLog, boolean sameAs) {
		if(model == null) {
			throw new IllegalArgumentException("No input model provided! Must provide an input model");
		}
		this.model = model;
		if(vivo == null) {
			throw new IllegalArgumentException("No vivo model provided! Must provide a vivo model");
		}
		this.vivo = vivo;
		this.oldNamespace = oldName;
		this.newNamespace = newName;
		this.errorLogging = errorLog;
		this.sameAs = sameAs;
		
		this.model.printParameters();
		this.vivo.printParameters();
	}
	
	/**
	 * Gets an unused URI in the the given namespace for the given models
	 * @param namespace the namespace
	 * @param models models to check in
	 * @return the uri
	 * @throws IOException error connecting
	 */
	public static String getUnusedURI(String namespace, JenaConnect... models) throws IOException {
		if((namespace == null) || namespace.equals("")) {
			throw new IllegalArgumentException("namespace cannot be empty");
		}
		String uri = null;
		Random random = new Random();
		while(uri == null) {
			uri = namespace + "n" + random.nextInt(Integer.MAX_VALUE);
			log.trace("evaluating uri <" + uri + ">");
			for(JenaConnect model : models) {
				boolean modelContains = model.containsURI(uri);
				log.trace("model <" + model.getModelName() + "> contains this uri?: " + modelContains);
				if(modelContains) {
					uri = null;
					break;
				}
			}
		}
		log.debug("Using new URI: <" + uri + ">");
		return uri;
	}
	
	/**
	 * Gets an unused URI in the the given namespace for the given models
	 * @param namespace the namespace
	 * @param models models to check in
	 * @return the uri
	 * @throws IOException error connecting
	 */
	public static String getUnusedURI(String namespace, OntModel... models) throws IOException {
		if((namespace == null) || namespace.equals("")) {
			throw new IllegalArgumentException("namespace cannot be empty");
		}
		String uri = null;
		Random random = new Random();
		while(uri == null) {
			uri = namespace + "n" + random.nextInt(Integer.MAX_VALUE);
			log.trace("evaluating uri <" + uri + ">");
			for(OntModel model : models) {
				boolean modelContains = model.contains(ResourceFactory.createResource(uri), null);
				log.trace("model contains this uri?: " + modelContains);
				if(modelContains) {
					uri = null;
					break;
				}
			}
		}
		log.debug("Using new URI: <" + uri + ">");
		return uri;
	}
	
	/**
	 * Changes the namespace for all matching uris
	 * @param model the model to change namespaces for
	 * @param vivo the model to search for uris in
	 * @param oldNamespace the old namespace
	 * @param newNamespace the new namespace
	 * @param errorLog log error messages for changed nodes
	 * @throws IOException error connecting
	 */
	public static void changeNS(JenaConnect model, JenaConnect vivo, String oldNamespace, String newNamespace, boolean errorLog, boolean sameAs) throws IOException {
		if((oldNamespace == null) || oldNamespace.trim().equals("")) {
			throw new IllegalArgumentException("old namespace cannot be empty");
		}
		if((newNamespace == null) || newNamespace.trim().equals("")) {
			throw new IllegalArgumentException("new namespace cannot be empty");
		}
		if(oldNamespace.trim().equals(newNamespace.trim())) {
			log.trace("namespaces are equal, nothing to change");
			return;
		}
		log.debug("sameAs: "+ sameAs);
		batchRename(model, vivo, oldNamespace.trim(), newNamespace.trim(), errorLog, sameAs);
	}
	
	/**
	 * Rename unmatched resources from a given namespace in the given model to another (vivo) model
	 * @param model the model to change namespaces for
	 * @param vivo the model to search for uris in
	 * @param oldNamespace the old namespace
	 * @param newNamespace the new namespace
	 * @param errorLog log error messages for changed nodes
	 * @throws IOException error connecting
	 */
	private static void batchRename(JenaConnect model, JenaConnect vivo, String oldNamespace, String newNamespace, boolean errorLog, boolean sameAs) throws IOException {
		//Grab all resources matching namespaces needing changed
		String subjectQuery = "" + 
		"PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" + 
		"PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#> \n" + 
		"PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#> \n" + 
		"PREFIX owl:   <http://www.w3.org/2002/07/owl#> \n" + 
		"PREFIX swrl:  <http://www.w3.org/2003/11/swrl#> \n" + 
		"PREFIX swrlb: <http://www.w3.org/2003/11/swrlb#> \n" + 
		"PREFIX vitro: <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#> \n" + 
		"PREFIX bibo: <http://purl.org/ontology/bibo/> \n" + 
		"PREFIX dcelem: <http://purl.org/dc/elements/1.1/> \n" + 
		"PREFIX dcterms: <http://purl.org/dc/terms/> \n" + 
		"PREFIX event: <http://purl.org/NET/c4dm/event.owl#> \n" + 
		"PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n" + 
		"PREFIX geo: <http://aims.fao.org/aos/geopolitical.owl#> \n" + 
		"PREFIX skos: <http://www.w3.org/2004/02/skos/core#> \n" + 
		"PREFIX ufVivo: <http://vivo.ufl.edu/ontology/vivo-ufl/> \n" + 
		"PREFIX core: <http://vivoweb.org/ontology/core#> \n" + 
		"\n" + 
		"SELECT ?sub \n" + 
		"WHERE {\n" + 
		"\t" + "?sub ?p ?o . \n" + 
		"\t" + "FILTER regex(str(?sub), \"^" + oldNamespace + "\" ) \n" + "} ORDER BY ?sub";
		log.debug("Change Query:\n" + subjectQuery);
		
		Set<String> changeArray = new TreeSet<String>();
		ResultSet results = model.executeSelectQuery(subjectQuery,true,false);
		for(QuerySolution solution : IterableAdaptor.adapt(results)) {
			String renameURI = solution.getResource("sub").getURI();
			changeArray.add(renameURI);
		}
		int total = changeArray.size();
		int count = 0;
		for(String sub : changeArray) {
			count++;
			Resource res = model.getJenaModel().getResource(sub);
			float percent = Math.round(10000f * count / total) / 100f;
			log.trace("(" + count + "/" + total + ": " + percent + "%): Finding unused URI for resource <" + res + ">");
			String uri = getUnusedURI(newNamespace, vivo, model);			 
	        log.debug("Resource <" + res.getURI() + "> was found and renamed to new uri <" + uri + ">!");
			 
			RenameResources.renameResource(res, uri);
			if (sameAs) {
			   Resource newRes = model.getJenaModel().getResource(uri); 
			   model.getJenaModel().add(newRes, OWL.sameAs, res);
		    }
		}
		log.info("Changed namespace for " + changeArray.size() + " rdf nodes");
	}
	
	/**
	 * Change namespace
	 * @throws IOException error connecting
	 */
	public void execute() throws IOException {
		changeNS(this.model, this.vivo, this.oldNamespace, this.newNamespace, this.errorLogging, this.sameAs);
		this.model.sync();
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("ChangeNamespace");
		// Inputs
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("inputModel").withParameter(true, "CONFIG_FILE").setDescription("config file for input jena model").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("inputModelOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of input jena model config using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('v').setLongOpt("vivoModel").withParameter(true, "CONFIG_FILE").setDescription("config file for vivo jena model").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('V').setLongOpt("vivoModelOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of vivo jena model config using VALUE").setRequired(false));
		
		// Params
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("oldNamespace").withParameter(true, "OLD_NAMESPACE").setDescription("The old namespace").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("newNamespace").withParameter(true, "NEW_NAMESPACE").setDescription("The new namespace").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('e').setLongOpt("errorLogging").setDescription("Log error messages for each record changed").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('s').setLongOpt("sameAs").setDescription("Create sameAs Statement").setRequired(false));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			InitLog.initLogger(args, getParser());
			log.info(getParser().getAppName() + ": Start");
			new ChangeNamespace(args).execute();
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			System.out.println(getParser().getUsage());
			error = e;
		} catch(UsageException e) {
			log.info("Printing Usage:");
			System.out.println(getParser().getUsage());
			error = e;
		} catch(Exception e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			error = e;
		} finally {
			log.info(getParser().getAppName() + ": End");
			if(error != null) {
				System.exit(1);
			}
		}
	}
}
