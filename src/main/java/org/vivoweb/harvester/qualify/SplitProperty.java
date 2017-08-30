/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.qualify;

import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.vivoweb.harvester.util.repo.MemJenaConnect;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shared.Lock;

/**
 * Changes the namespace for all matching uris
 * @author Cornell University VIVO Team (Algorithm)
 * @author Christopher Haines (hainesc@ctrip.ufl.edu) (Harvester Tool)
 */
public class SplitProperty {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(SplitProperty.class);
	/**
	 * model to split property values in
	 */
	private JenaConnect model;
	/**
	 * regex to split oldPropertyURI value on
	 */
	private String splitRegex;
	/**
	 * old property uri (to be split)
	 */
	private String oldPropertyURI;
	/**
	 * new property uri (to store split values in)
	 */
	private String newPropertyURI;
	/**
	 * trim() the new values
	 */
	private boolean trim;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private SplitProperty(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param model model to split property values in
	 * @param splitRegex regex to split oldPropertyURI value on
	 * @param oldPropertyURI old property uri (to be split)
	 * @param newPropertyURI new property uri (to store split values in)
	 * @param trim trim() the new values
	 */
	public SplitProperty(JenaConnect model, String splitRegex, String oldPropertyURI, String newPropertyURI, boolean trim) {
		if(model == null) {
			throw new IllegalArgumentException("No model provided! Must provide a model");
		}
		this.model = model;
		this.splitRegex = splitRegex;
		this.oldPropertyURI = oldPropertyURI;
		this.newPropertyURI = newPropertyURI;
		this.trim = trim;
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error reading config
	 */
	private SplitProperty(ArgList argList) throws IOException {
		this(
			JenaConnect.parseConfig(argList.get("i"), argList.getValueMap("I")), 
			argList.get("r"), 
			argList.get("u"), 
			argList.get("n"), 
			argList.has("t")
		);
	}
	
	/**
	 * Splits values for a given data property URI on a supplied regex and 
	 * asserts each value using newPropertyURI.  New statements returned in
	 * a Jena Model.  Split values may be optionally trim()ed.
	 * @param model model to split property values in
	 * @param splitRegex regex to split oldPropertyURI value on
	 * @param oldPropertyURI old property uri (to be split)
	 * @param newPropertyURI new property uri (to store split values in)
	 * @param trim trim() the new values
	 * @return outModel
	 */
	public static JenaConnect splitPropertyValues(JenaConnect model, String oldPropertyURI, String splitRegex, String newPropertyURI, boolean trim) {
		JenaConnect outModel = new MemJenaConnect();
		Pattern delimiterPattern = Pattern.compile(splitRegex);
		Property theProp = ResourceFactory.createProperty(oldPropertyURI);
		Property newProp = ResourceFactory.createProperty(newPropertyURI);
		model.getJenaModel().enterCriticalSection(Lock.READ);
		try {
			StmtIterator stmtIt = model.getJenaModel().listStatements( (Resource)null, theProp, (RDFNode)null );
			try {
				while(stmtIt.hasNext()) {
					Statement stmt = stmtIt.nextStatement();
					Resource subj = stmt.getSubject();
					RDFNode obj = stmt.getObject();
					if (obj.isLiteral()) {
						Literal lit = (Literal) obj;
						String unsplitStr = lit.getLexicalForm();
						String[] splitPieces = delimiterPattern.split(unsplitStr);
						for (int i=0; i<splitPieces.length; i++) {
							String newLexicalForm = splitPieces[i];
							if (trim) {
								newLexicalForm = newLexicalForm.trim();
							}
							if (newLexicalForm.length() > 0) {
								Literal newLiteral = null;
								if (lit.getDatatype() != null) {
									newLiteral = outModel.getJenaModel().createTypedLiteral(newLexicalForm, lit.getDatatype());
								} else {
									if (lit.getLanguage() != null) {
										newLiteral = outModel.getJenaModel().createLiteral(newLexicalForm, lit.getLanguage());
									} else {
										newLiteral = outModel.getJenaModel().createLiteral(newLexicalForm);
									}
								}
								outModel.getJenaModel().add(subj,newProp,newLiteral);
							}
						}
					}
				}
			} finally {
				stmtIt.close();
			}
		} finally {
			model.getJenaModel().leaveCriticalSection();
		}	
		return outModel;
	}
	
	/**
	 * Split Property Values
	 */
	public void execute() {
		this.model.loadRdfFromJC(splitPropertyValues(this.model, this.splitRegex, this.oldPropertyURI, this.newPropertyURI, this.trim));
		this.model.sync();
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("SplitProperty");
		// Inputs
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("inputModel").withParameter(true, "CONFIG_FILE").setDescription("config file for jena model").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("inputModelOverride").withParameterValueMap("JENA_PARAM", "VALUE").setDescription("override the JENA_PARAM of jena model config using VALUE").setRequired(false));
		
		// Params
		parser.addArgument(new ArgDef().setShortOption('r').setLongOpt("regex").withParameter(true, "SPLIT_REGEX").setDescription("regex to split oldPropertyURI value on").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("oldPropertyURI").withParameter(true, "OLD_PREDICATE").setDescription("old property uri (to be split)").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("newPropertyURI").withParameter(true, "NEW_PREDICATE").setDescription("new property uri (to store split values in)").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("trim").setDescription("trim() the new values").setRequired(false));
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
			new SplitProperty(args).execute();
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
