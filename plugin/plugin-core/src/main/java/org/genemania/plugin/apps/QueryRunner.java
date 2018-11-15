/**
 * This file is part of GeneMANIA.
 * Copyright (C) 2008-2011 University of Toronto.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.genemania.plugin.apps;

import java.awt.font.ImageGraphicAttribute;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.genemania.domain.Gene;
import org.genemania.domain.GeneNamingSource;
import org.genemania.domain.InteractionNetwork;
import org.genemania.domain.InteractionNetworkGroup;
import org.genemania.domain.NetworkMetadata;
import org.genemania.domain.Node;
import org.genemania.domain.Tag;
import org.genemania.domain.Organism;
import org.genemania.dto.AttributeDto;
import org.genemania.dto.EnrichmentEngineRequestDto;
import org.genemania.dto.EnrichmentEngineResponseDto;
import org.genemania.dto.InteractionDto;
import org.genemania.dto.InteractionVisitor;
import org.genemania.dto.NetworkCombinationRequestDto;
import org.genemania.dto.NetworkDto;
import org.genemania.dto.NodeDto;
import org.genemania.dto.RelatedGenesEngineRequestDto;
import org.genemania.dto.RelatedGenesEngineResponseDto;
import org.genemania.dto.OntologyCategoryDto;

import org.genemania.engine.Constants;
import org.genemania.engine.Constants.ScoringMethod;
import org.genemania.engine.Mania2;
import org.genemania.engine.actions.CombineNetworks;
import org.genemania.engine.actions.FindRelated;
import org.genemania.engine.cache.DataCache;
import org.genemania.engine.cache.MemObjectCache;
import org.genemania.engine.cache.SynchronizedObjectCache;
import org.genemania.engine.core.MatrixUtils;
import org.genemania.engine.core.data.NodeIds;
import org.genemania.engine.core.integration.Feature;
import org.genemania.engine.core.integration.FeatureWeightMap;
import org.genemania.engine.core.mania.CoreMania;
import org.genemania.engine.exception.CancellationException;
import org.genemania.engine.labels.LabelVectorGenerator;
import org.genemania.engine.matricks.SymMatrix;
import org.genemania.exception.ApplicationException;
import org.genemania.exception.DataStoreException;
import org.genemania.mediator.GeneMediator;
import org.genemania.mediator.NodeMediator;
import org.genemania.plugin.GeneMania;
import org.genemania.plugin.NetworkUtils;
import org.genemania.plugin.controllers.IGeneProvider;
import org.genemania.plugin.controllers.RankedGeneProviderWithUniprotHack;
import org.genemania.plugin.data.DataSet;
import org.genemania.plugin.data.DataSetManager;
import org.genemania.plugin.formatters.FlatReportOutputFormatter;
import org.genemania.plugin.formatters.GeneListOutputFormatter;
import org.genemania.plugin.formatters.GeneScoresOutputFormatter;
import org.genemania.plugin.formatters.IOutputFormatter;
import org.genemania.plugin.formatters.XmlReportOutputFormatter;
import org.genemania.plugin.model.Group;
import org.genemania.plugin.model.Network;
import org.genemania.plugin.model.SearchResult;
import org.genemania.plugin.model.ViewState;
import org.genemania.plugin.model.impl.InteractionNetworkImpl;
import org.genemania.plugin.model.impl.InteractionNetworkGroupImpl;
import org.genemania.plugin.model.impl.ViewStateImpl;
import org.genemania.plugin.model.impl.SearchResultImpl;
import org.genemania.plugin.model.impl.SearchResultImplNetDx;
import org.genemania.plugin.parsers.IQueryParser;
import org.genemania.plugin.parsers.Query;
import org.genemania.plugin.parsers.TabDelimitedQueryParser;
import org.genemania.plugin.report.ManiaReport.GeneEntry;
import org.genemania.type.CombiningMethod;
import org.genemania.util.NullProgressReporter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.xml.sax.SAXException;

import no.uib.cipr.matrix.Vector;

public class QueryRunner extends AbstractPluginDataApp {

	private static final int MIN_CATEGORIES = 10;

	private static final double Q_VALUE_THRESHOLD = 0.1;

	@Option(name = "--in", usage = "input format (one of: flat); defaults to \"flat\"")
	private String fInputFormat;

	@Option(name = "--out", usage = "output format (one of: genes, flat, xml, scores); defaults to \"genes\"")
	private String fOutputFormat;

	@Option(name = "--results", usage = "directory where results should be stored")
	private String fResultsPath = "."; //$NON-NLS-1$

	@Option(name = "--list-networks", usage = "list available networks for given organism")
	private String fListNetworksFor;

	@Option(name = "--list-genes", usage = "list recognized gene symbols for given organism")
	private String fListGenesFor;

	@Option(name = "--scoring-method", usage = "gene scoring method (one of: discriminant, z); defaults to \"discriminant\"")
	private String fScoringMethod = "discriminant"; //$NON-NLS-1$

	@Option(name = "--ids", usage = "comma-separated list gene identifier types to use in the output")
	private String fIds;

	@Option(name = "--netdx-flag", handler = BooleanOptionHandler.class, usage = "boolean value - if true we're using NetDx specific flat output formats, do a GeneEnrichmentRequest and LabelPropagation using average weights. Also conserve a significant amount of memory in report building; defaults to false")
	private boolean useNetdxModification = false;

	private Mania2 fMania;
	private IQueryParser fQueryParser;
	private File fOutputDirectory;
	private QueryHandler fQueryHandler;

	private DataCache fCache;

	private NetworkUtils fNetworkUtils;

	private static Object fJobMutex = new Object();

	private void initialize() throws ApplicationException, DataStoreException {
		try {
			DataSetManager manager = createDataSetManager();
			fNetworkUtils = new NetworkUtils();

			fData = manager.open(new File(fDataPath));
			fCache = new DataCache(new SynchronizedObjectCache(
					new MemObjectCache(fData.getObjectCache(NullProgressReporter.instance(), false))));
			fMania = new Mania2(fCache);

			if (fInputFormat == null) {
				fQueryParser = new TabDelimitedQueryParser(fData);
			} else if ("flat".equals(fInputFormat)) { //$NON-NLS-1$
				fQueryParser = new TabDelimitedQueryParser(fData);
			} else {
				throw new ApplicationException(String.format("Unrecognized input format: %s", fInputFormat)); //$NON-NLS-1$
			}

			IGeneProvider geneProvider = parseIdTypes(fIds);

			if (useNetdxModification) { // $NON-NLS-1$
				// our flag introduced problems - needed to remove additional elements from
				// attributes - otherwise we will run it twice - see oldArguments parsing?
				fQueryHandler = new NetdxQueryHandler(new FlatReportOutputFormatter(fData, geneProvider), fData);
			} else if (fOutputFormat == null) {
				fQueryHandler = new DefaultQueryHandler(new GeneListOutputFormatter(geneProvider));
			} else if ("genes".equals(fOutputFormat)) { //$NON-NLS-1$
				fQueryHandler = new DefaultQueryHandler(new GeneListOutputFormatter(geneProvider));
			} else if ("scores".equals(fOutputFormat)) { //$NON-NLS-1$
				fQueryHandler = new DefaultQueryHandler(new GeneScoresOutputFormatter(geneProvider));
			} else if ("flat".equals(fOutputFormat)) { //$NON-NLS-1$
				fQueryHandler = new DefaultQueryHandler(new FlatReportOutputFormatter(fData, geneProvider));
			} else if ("xml".equals(fOutputFormat)) { //$NON-NLS-1$
				fQueryHandler = new DefaultQueryHandler(new XmlReportOutputFormatter(fData, geneProvider));
			} else if ("combined-network".equals(fOutputFormat)) { //$NON-NLS-1$
				fQueryHandler = new CombineNetworksQueryHandler(geneProvider);
			} else {
				throw new ApplicationException(String.format("Unrecognized output format: %s", fOutputFormat)); //$NON-NLS-1$
			}

			if (fThreads < 1) {
				fThreads = 1;
			}

			fOutputDirectory = new File(fResultsPath);
			if (!fOutputDirectory.exists()) {
				throw new ApplicationException(String.format("Output directory doesn't exist: %s", fResultsPath)); //$NON-NLS-1$
			}
			if (!fOutputDirectory.isDirectory()) {
				throw new ApplicationException(String.format("Output directory isn't a directory: %s", fResultsPath)); //$NON-NLS-1$
			}
		} catch (SAXException e) {
			throw new ApplicationException(e);
		}
	}

	private IGeneProvider parseIdTypes(String idList) {
		if (idList == null) {
			List<GeneNamingSource> userPreferences = Collections.emptyList();
			return new RankedGeneProviderWithUniprotHack(fData.getAllNamingSources(), userPreferences);
		}
		String[] items = idList.split(","); //$NON-NLS-1$
		GeneMediator mediator = fData.getMediatorProvider().getGeneMediator();
		List<GeneNamingSource> userPreferences = new ArrayList<GeneNamingSource>();
		for (String item : items) {
			String name = item.trim();
			GeneNamingSource source = findSourceByName(mediator, name);
			if (source != null) {
				userPreferences.add(source);
			}
		}
		return new RankedGeneProviderWithUniprotHack(fData.getAllNamingSources(), userPreferences);
	}

	private GeneNamingSource findSourceByName(GeneMediator mediator, String name) {
		// Hack to support differentiating between Uniprot accessions and
		// entry names.
		if (RankedGeneProviderWithUniprotHack.UNIPROT_AC.equals(name)) {
			GeneNamingSource prototype = mediator.findNamingSourceByName(RankedGeneProviderWithUniprotHack.UNIPROT_ID);
			if (prototype == null) {
				return null;
			}
			GeneNamingSource source = new GeneNamingSource();
			source.setId(prototype.getId());
			source.setName(RankedGeneProviderWithUniprotHack.UNIPROT_AC);
			source.setRank(prototype.getRank());
			source.setShortName(prototype.getShortName());
			return source;
		} else {
			return mediator.findNamingSourceByName(name);
		}
	}

	RelatedGenesEngineRequestDto createRequest(Query query) throws ApplicationException {
		RelatedGenesEngineRequestDto request = new RelatedGenesEngineRequestDto();
		request.setNamespace(GeneMania.DEFAULT_NAMESPACE);
		request.setOrganismId(query.getOrganism().getId());
		request.setInteractionNetworks(collapseNetworks(query.getGroups()));
		request.setAttributeGroups(collapseAttributeGroups(query.getGroups()));
		request.setPositiveNodes(query.getNodes());
		request.setLimitResults(query.getGeneLimit());
		request.setAttributesLimit(query.getAttributeLimit());
		request.setCombiningMethod(query.getCombiningMethod());

		if (query.getScoringMethod() != null) {
			request.setScoringMethod(query.getScoringMethod());
		} else {
			request.setScoringMethod(parseScoringMethod());
		}

		return request;
	}

	private org.genemania.type.ScoringMethod parseScoringMethod() throws ApplicationException {
		if ("discriminant".equals(fScoringMethod)) { //$NON-NLS-1$
			return org.genemania.type.ScoringMethod.DISCRIMINANT;
		}
		if ("z".equals(fScoringMethod)) { //$NON-NLS-1$
			return org.genemania.type.ScoringMethod.ZSCORE;
		}
		throw new ApplicationException(String.format("Unrecognized scoring method: %s", fScoringMethod)); //$NON-NLS-1$
	}

	RelatedGenesEngineResponseDto runQuery(RelatedGenesEngineRequestDto request) throws DataStoreException {
		try {
			request.setProgressReporter(NullProgressReporter.instance());

			RelatedGenesEngineResponseDto result;
			if ("scores".equals(fOutputFormat)) { //$NON-NLS-1$
				result = new FindAllRelated(fCache, request).process();
			} else {
				result = fMania.findRelated(request);
			}
			request.setCombiningMethod(result.getCombiningMethodApplied());
			fNetworkUtils.normalizeNetworkWeights(result);
			return result;
		} catch (ApplicationException e) {
			Logger logger = Logger.getLogger(getClass());
			logger.error("Unexpected error", e); //$NON-NLS-1$
			return null;
		}
	}

	private EnrichmentEngineRequestDto createEnrichmentRequest(Query query, RelatedGenesEngineResponseDto response) {
		if (query.getOrganism().getOntology() == null) {
			return null;
		}

		EnrichmentEngineRequestDto request = new EnrichmentEngineRequestDto();
		request.setProgressReporter(NullProgressReporter.instance());
		request.setMinCategories(MIN_CATEGORIES);
		request.setqValueThreshold(Q_VALUE_THRESHOLD);

		Organism organism = query.getOrganism();

		request.setOrganismId(organism.getId());
		request.setOntologyId(organism.getOntology().getId());

		Set<Long> nodes = new HashSet<Long>();
		for (NodeDto node : response.getNodes()) {
			nodes.add(node.getId());
		}
		request.setNodes(nodes);
		return request;
	}

	
	/*
	 * Implementation using CoreMania with a minimal amount of Lucene 
	 */
	private SearchResultImplNetDx runAlgorithmNetDx(DataSet data, Query query)
			throws DataStoreException, ApplicationException {
		RelatedGenesEngineRequestDto request = createRequest(query);
		
		request.setProgressReporter(NullProgressReporter.instance());
		
//		need to create response object to hold networkWeights
		RelatedGenesEngineResponseDto response = new RelatedGenesEngineResponseDto();
		
		EnrichmentEngineResponseDto enrichmentResponse = new EnrichmentEngineResponseDto();
// 		outputformat is always flat for netDx

		List<String> queryGenes = query.getGenes();

//		modify and initialize query
		EnrichmentEngineRequestDto enrichmentRequest = createEnrichmentRequest(query, response);

		CoreMania mania = new CoreMania(fCache);
		Organism organism = query.getOrganism();
		
		Map<String, Double> networkNameToWeightMap = computeNetworkWeights(mania, query, request, organism);
		
		// construct the gene labels
		// use previous result for label propagation
		FindNetDxRelated findNetDxRelated = new FindNetDxRelated(fCache, request, mania);
		//		set required fields from first response object
		RelatedGenesEngineResponseDto response2 = findNetDxRelated.process2();
		response2.setNetworks(response.getNetworks());
		
		// SearchOptions / SearchResultImplNetDx is basically a storage object holding on to our references for usage by FlatNetDxHandler
		SearchResultImplNetDx options = fNetworkUtils.createSearchOptionsNetdx2(organism, request, response2,
				enrichmentResponse, data, queryGenes, networkNameToWeightMap);
		
		return options;
	}
	
	private Map<String, Double> computeNetworkWeights(CoreMania mania, Query query, RelatedGenesEngineRequestDto request, Organism organism) throws ApplicationException{

		final long organismId = organism.getId();
		String namespace;
		if (organismId < 0) {
			namespace = GeneMania.DEFAULT_NAMESPACE;
		} else {
			namespace = "CORE";
		}
		
		// implementated in parent class AbstractPluginDataApp or AbstractPluginApp
		Collection<Collection<Long>> networkIds = collapseNetworks(query.getGroups());
		Collection<Long> attributeGroupIds = collapseAttributeGroups(query.getGroups());
		int attributeLimit = query.getAttributeLimit();

		if (fVerbose) {
			System.err.println("Computing weights...");
		}
		
		// Compute weights
		Collection<Long> nodes = query.getNodes();
		ArrayList<Long> negativeNodes = new ArrayList<Long>();
		double posLabelValue = 1.0;
		double negLabelValue = -1.0;
		double unLabeledValueProduction = -1.0;
		
		Vector labels = LabelVectorGenerator.createLabelsFromIds(fCache.getNodeIds(organismId), nodes, negativeNodes,
				posLabelValue, negLabelValue, unLabeledValueProduction);
		Constants.CombiningMethod combiningMethod = Constants.convertCombiningMethod(query.getCombiningMethod(),
				nodes.size());
		mania.computeWeights(namespace, organismId, labels, combiningMethod, networkIds, attributeGroupIds,
				attributeLimit);
		
		//		 Extract weights
		FeatureWeightMap featureWeights = mania.getFeatureWeights();
		Map<Long, Double> weightMap = new HashMap<>();
		
		List<AttributeDto> attributes = new ArrayList<AttributeDto>();
		for (Entry<Feature, Double> entry : featureWeights.entrySet()) {
			Feature feature = entry.getKey();
			if (featureWeights.get(feature) <= 0d) {
				continue;
			}

			switch (feature.getType()) {
			case ATTRIBUTE_VECTOR:
				AttributeDto attribute = new AttributeDto();
				attribute.setId(feature.getId());
				attribute.setGroupId(feature.getGroupId());
				attribute.setWeight(entry.getValue());
				attributes.add(attribute);
				break;
			case SPARSE_MATRIX:
				// Case we care about				
				NetworkDto network = new NetworkDto();
				// sclaed weight - factor would otherwise be applied when writing output 				
				Double scaled_weight = entry.getValue() * 100.0;
				weightMap.put(feature.getId(), scaled_weight);
				break;
			case BIAS:
				// Ignore
			}
		}

		Map<String, Double> networkNameToWeightMap = new HashMap<>();
		Map<Long, String> networkIdToNameMap = new HashMap<>();
		for (Group<?,?> group : query.getGroups()) {
			Group<InteractionNetworkGroup, InteractionNetwork> adapted = group.adapt(InteractionNetworkGroup.class, InteractionNetwork.class);
			for (Network<InteractionNetwork> network : adapted.getNetworks()) {
				Long netId = network.getModel().getId();
				String netName = network.getName();
				networkIdToNameMap.put(netId, netName);
			}			
		}
		
		for (Collection<Long> ids : networkIds) {
			for (Long networkId : ids) {
				String name = networkIdToNameMap.get(networkId);
				Double weight = weightMap.get(networkId);
				if (weight != null) {
					networkNameToWeightMap.put(name, weight);
				}
			}
		}
		return networkNameToWeightMap;
	}
	

	private SearchResult runAlgorithm(DataSet data, Query query) throws DataStoreException, ApplicationException {
		RelatedGenesEngineRequestDto request = createRequest(query);
		RelatedGenesEngineResponseDto response = runQuery(request);
		EnrichmentEngineRequestDto enrichmentRequest;

		if ("scores".equals(fOutputFormat)) //$NON-NLS-1$
			enrichmentRequest = null;
		else
			enrichmentRequest = createEnrichmentRequest(query, response);

		EnrichmentEngineResponseDto enrichmentResponse = computeEnrichment(enrichmentRequest);

		List<String> queryGenes = query.getGenes();
		Organism organism = query.getOrganism();

		SearchResult options = fNetworkUtils.createSearchOptions(organism, request, response, enrichmentResponse, data,
				queryGenes);

		return options;
	}

	private EnrichmentEngineResponseDto computeEnrichment(EnrichmentEngineRequestDto request)
			throws ApplicationException {
		return request == null ? null : fMania.computeEnrichment(request);
	}

	File getOutputDirectory() {
		return fOutputDirectory;
	}

	private void runQuery(String filename, File outputDirectory)
			throws IOException, DataStoreException, ApplicationException {
		Query query;
		Reader reader = new InputStreamReader(new FileInputStream(filename), "UTF-8"); //$NON-NLS-1$

		try {
			query = fQueryParser.parse(reader, new IQueryErrorHandler() {
				public void handleUnrecognizedGene(String gene) {
					System.err.println(String.format("WARNING: Unrecognized gene \"%s\"", gene)); //$NON-NLS-1$
				}

				public void handleSynonym(String gene) {
					System.err.println(String.format("WARNING: Synonym \"%s\"", gene)); //$NON-NLS-1$
				}

				public void handleNetwork(InteractionNetwork network) {
					if (fVerbose) {
						System.err.println(String.format("INFO: Using network \"%s\"", network.getName())); //$NON-NLS-1$
					}
				}

				public void warn(String message) {
					System.err.println(String.format("WARNING: %s", message)); //$NON-NLS-1$
				}

				public void handleUnrecognizedNetwork(String network) {
					System.err.println(String.format("WARNING: Unrecognized network \"%s\"", network)); //$NON-NLS-1$
				}
			});
		} finally {
			reader.close();
		}

		File sourceFile = new File(filename);
		String baseName = sourceFile.getName();

		fQueryHandler.process(query, outputDirectory, baseName);
	}

	public void handleArguments() throws InterruptedException, ApplicationException, DataStoreException {
		initialize();

		if (fListNetworksFor != null) {
			printNetworks(fListNetworksFor);
			return;
		}

		if (fListGenesFor != null) {
			printGenes(fListGenesFor);
			return;
		}

		Logger.getLogger("org.genemania.plugin").setLevel(Level.WARN); //$NON-NLS-1$
		Logger logger = Logger.getLogger("org.genemania"); //$NON-NLS-1$
		logger.setLevel(Level.ERROR);

		List<String> oldArguments = getArguments();
		List<String> arguments = new ArrayList<>();// = getArguments();
//		need to remove all "weird" arguments from list
//		drop arguments that are just "true" and not actual query files - somehow the netDx flag got appended to the list of arguments...

		if ((oldArguments.size() > 1)) {
//			only attach query runs that are not just "true" --> query files
			for (String arg : oldArguments) {
				if (!arg.equals("true")) {
					arguments.add(arg);
				}
			}
		} else {
			arguments = oldArguments;
		}
		System.err.println(arguments);
		final Iterator<String> jobQueue = arguments.iterator();
		List<Thread> threads = new ArrayList<Thread>();
		long start = System.currentTimeMillis();

		for (int i = 0; i < getThreads(); i++) {
			final int threadId = i + 1;

			Thread thread = new Thread(new Runnable() {
				public void run() {
					while (true) {
						String filename;
						synchronized (fJobMutex) {
							if (jobQueue.hasNext()) {
								filename = jobQueue.next();
							} else {
								return;
							}
						}
						System.err.println(String.format("[Thread %d] Processing %s...", threadId, filename)); //$NON-NLS-1$
						try {
							runQuery(filename, getOutputDirectory());
						} catch (IOException e) {
							e.printStackTrace(System.err);
						} catch (DataStoreException e) {
							e.printStackTrace(System.err);
						} catch (ApplicationException e) {
							e.printStackTrace(System.err);
						}
						System.err.println(String.format("[Thread %d] Finished %s", threadId, filename)); //$NON-NLS-1$
					}
				}
			});
			threads.add(thread);
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		long duration = System.currentTimeMillis() - start;
		System.err.println(String.format("Performed %d predictions in %.2fs", arguments.size(), duration / 1000.0)); //$NON-NLS-1$
	}

	public static void main(String[] args) throws Exception {
		Logger.getLogger("org.genemania").setLevel(Level.FATAL); //$NON-NLS-1$

		final QueryRunner runner = new QueryRunner();
		CmdLineParser parser = new CmdLineParser(runner);

		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println(String.format("\nUsage: %s options query-file-1 [query-file-2...]\n", //$NON-NLS-1$
					QueryRunner.class.getSimpleName()));
			parser.printUsage(System.err);

			return;
		}

		runner.handleArguments();
	}
	

	/**
	 * 2nd Hack Class for netDx FindRelated implementation - so we can use a 
	 * precomputed CoreMania instance
	 */
	static class FindNetDxRelated extends FindRelated {
		// Hack: aliases so we can access FindRelated's internals
		private DataCache cache2;
		private RelatedGenesEngineRequestDto request2;
		private CoreMania mania;
		private static Logger logger = Logger.getLogger(FindNetDxRelated.class);
		
		public FindNetDxRelated(DataCache cache, RelatedGenesEngineRequestDto request, CoreMania precomputedMania) {
			super(cache, request);
			this.cache2 = cache;
			this.request2 = request;
			this.mania = precomputedMania;
		}
		
		public RelatedGenesEngineResponseDto process2() throws ApplicationException {
	        try {
	            super.setRequestStartTimeMillis(System.currentTimeMillis());

	            logStart();
	            checkQuery();
	            logQuery();
	            
	            ArrayList<Long> negativeNodes = new ArrayList<Long>();

	            double posLabelValue = 1.0d;
	            double negLabelValue = -1.0d;
	            double unLabeledValueProduction = -1.0d;

	            Vector labels = LabelVectorGenerator.createLabelsFromIds(cache2.getNodeIds(request2.getOrganismId()),
	                    request2.getPositiveNodes(), negativeNodes, posLabelValue, negLabelValue, unLabeledValueProduction);

	            String goCategory = null;
	            
	            // crunch the numbers
	            org.genemania.engine.Constants.CombiningMethod combiningMethod = Constants.convertCombiningMethod(request2.getCombiningMethod(), request2.getPositiveNodes().size());
	            org.genemania.engine.Constants.ScoringMethod scoringMethod = Constants.convertScoringMethod(request2.getScoringMethod());

	            Collection<Collection<Long>> idList = request2.getInteractionNetworks();

	            // Our "hack"
	            // Instead of re-computing - we just use getFeatureWeights and getDiscriminant()	            
	            CoreMania coreMania = mania;
	            //	            coreMania.compute(safeGetNamespace(), request.getOrganismId(), labels, combiningMethod, idList, request.getAttributeGroups(), request.getAttributesLimit(), goCategory, "average");
	            SymMatrix partiallyCombinedKernel = coreMania.getPartiallyCombinedKernel();
	            FeatureWeightMap featureWeights = coreMania.getFeatureWeights();
//	            before getting the discriminant we need to compute it
	            coreMania.computeDiscriminant(request2.getNamespace(), request2.getOrganismId(), labels, goCategory, "average");
//	            namespace, long organismId, Vector labels, String goCategory, String biasingMethod
	            Vector discriminant = coreMania.getDiscriminant();
//	            more hacking
//	            Vector score = super.convertScore(scoringMethod, discriminant, partiallyCombinedKernel, labels, posLabelValue, negLabelValue);
//	            TODO could potentially bring problems, as we dont rescale discriminant as in original - mark for potential bug source
//	            System.err.println("discriminant\n" + discriminant);
	            Vector score = MatrixUtils.rescale(discriminant);
	            
	            double scoreThreshold = Constants.DISCRIMINANT_THRESHOLD;
//	            double scoreThreshold = super.selectScoreThreshold(scoringMethod);
	            RelatedGenesEngineResponseDto response = prepareResponse(score, discriminant,
	                    featureWeights, partiallyCombinedKernel, scoreThreshold, scoringMethod, Constants.convertCombiningMethod(combiningMethod));
	            
	            super.setRequestEndTimeMillis(System.currentTimeMillis());

	            logEnd();

	            return response;
	        }
	        catch (CancellationException e) {
	            logger.info("request was cancelled");
	            return null;
	        }
		}
		

		//		need to ovewrite logging - another private field
		
		/*
		 * overwrite logging for query params 
		 */
		private void logQuery() {
		    logger.info(String.format("findRelated query using combining method %s for organism %d "
		    		+ "contains %d nodes, %d network groups",
		            request2.getCombiningMethod(), request2.getOrganismId(), 
		            request2.getPositiveNodes().size(), 
		            request2.getInteractionNetworks().size() ));//, // numRequestNetworks, numRequestAttributeGroups, request.getLimitResults(), request.getAttributesLimit()));
		}
		
		private void logStart() {
		    logger.info("processing findRelated() request");
		}
		
		private void logPreparingOutputs() {
		    logger.info("preparing outputs for findRelated() request");
		}
		
		private void logEnd() {
		    logger.info("completed processing request"); //, duration = " + Logging.duration(requestStartTimeMillis, requestEndTimeMillis));
		}
		
		
	}
	
	/**
	 * Hack class to support retrieving the scores for every single gene (related or
	 * not) with minimal code duplication.
	 */
	static class FindAllRelated extends FindRelated {
		// Hack: aliases so we can access FindRelated's internals
		private DataCache cache2;
		private RelatedGenesEngineRequestDto request2;

		public FindAllRelated(DataCache cache, RelatedGenesEngineRequestDto request) {
			super(cache, request);
			this.cache2 = cache;
			this.request2 = request;
		}

		@Override
		protected RelatedGenesEngineResponseDto prepareResponse(Vector scores, Vector discriminant,
				FeatureWeightMap featureWeights, SymMatrix combinedKernel, double scoreThreshold,
				ScoringMethod scoringMethod, CombiningMethod combiningMethod) throws ApplicationException {
			NodeIds nodeIds = cache2.getNodeIds(request2.getOrganismId());
			List<Integer> indicesForPositiveNodes = nodeIds.getIndicesForIds(request2.getPositiveNodes());

			// We want all the scores so we'll override the threshold.
			scoreThreshold = Double.NEGATIVE_INFINITY;

			// for context score we still want the top nodes by discriminant. for other
			// scoring methods just use the score itself.
			int[] indices;
			if (scoringMethod == ScoringMethod.CONTEXT) {
				indices = MatrixUtils.getIndicesForTopScores(discriminant, indicesForPositiveNodes,
						nodeIds.getNodeIds().length, scoreThreshold);
			} else {
				indices = MatrixUtils.getIndicesForTopScores(scores, indicesForPositiveNodes,
						nodeIds.getNodeIds().length, scoreThreshold);
			}

			// Create a phony network containing all the genes where each edge
			// is just the gene "interacting" with itself.
			RelatedGenesEngineResponseDto result = new RelatedGenesEngineResponseDto();
			List<NetworkDto> networks = new ArrayList<NetworkDto>();
			result.setNetworks(networks);

			NetworkDto network = new NetworkDto(0, 1);
			networks.add(network);

			List<NodeDto> nodes = new ArrayList<NodeDto>();
			for (int index : indices) {
				long nodeId = nodeIds.getIdForIndex(index);

				double score = scores.get(index);

				NodeDto nodeVO = new NodeDto();
				nodeVO.setId(nodeId);
				nodeVO.setScore(score);
				nodes.add(nodeVO);

				InteractionDto interaction = new InteractionDto(nodeVO, nodeVO, 1);
				network.addInteraction(interaction);
			}
			result.setNodes(nodes);
			return result;
		}
	}

	interface QueryHandler {
		void process(Query query, File outputDirectory, String baseName)
				throws ApplicationException, DataStoreException, IOException;
	}

	class DefaultQueryHandler implements QueryHandler {
		IOutputFormatter fFormatter;

		public DefaultQueryHandler(IOutputFormatter formatter) {
			fFormatter = formatter;
		}

		@Override
		public void process(Query query, File outputDirectory, String baseName)
				throws ApplicationException, DataStoreException, IOException {
			SearchResult options = runAlgorithm(fData, query);
			ViewState viewState = new ViewStateImpl(options);
			OutputStream out = new FileOutputStream(String.format("%s%s%s-results.%s", outputDirectory.getPath(), //$NON-NLS-1$
					File.separator, baseName, fFormatter.getExtension()));
			try {
				fFormatter.format(out, viewState);
			} finally {
				out.close();
			}
		}
	}

	class NetdxQueryHandler implements QueryHandler {
		IOutputFormatter fFormatter;
		DataSet data;

		public NetdxQueryHandler(IOutputFormatter formatter, DataSet data) {
			fFormatter = formatter;
			this.data = data;
		}

		@Override
		public void process(Query query, File outputDirectory, String baseName)
				throws ApplicationException, DataStoreException, IOException {
			// options are still based on Lucene - 
			SearchResultImplNetDx options = runAlgorithmNetDx(data, query);
			System.err.println("finished computation - writing results");
			String outPath = outputDirectory.getPath(); // "/home/philipp/netDx_mashup/netDxmashup/test/results";
//			String pathPrefixRankFiles = String.format("%s/%s_", outPath, baseName);
			String pathPrefixRankFiles = String.format("%s/%s", outPath, baseName);

//			OutputStream out = new FileOutputStream(String.format("%s%s%s-results.%s", outputDirectory.getPath(), File.separator, baseName, fFormatter.getExtension())); //$NON-NLS-1$

			RankedGeneProviderWithUniprotHack geneIdProvider = new RankedGeneProviderWithUniprotHack(
					data.getAllNamingSources(), Collections.emptyList());

			try {
				FlatNetDxHandler netDxhandler = new FlatNetDxHandler(pathPrefixRankFiles, options, fNetworkUtils, geneIdProvider);
			} catch (FileNotFoundException e) {
				System.err.println(e);
			}
		}

		class FlatNetDxHandler {

			private List<GeneEntry> genes;
			private String outfilePrefix;
			private NetworkUtils fNetworkUtils;
			private SearchResultImplNetDx options;
			private Map<Long, Gene> geneCache;
			private final IGeneProvider geneProvider;
			
			
			public FlatNetDxHandler(String outfilePrefix, SearchResultImplNetDx options, NetworkUtils fNetworkUtils,
					IGeneProvider geneProvider) throws FileNotFoundException {
				this.outfilePrefix = outfilePrefix;
				this.options = options;
				this.fNetworkUtils = fNetworkUtils;
				this.genes = this.populateGenes();
				this.geneCache = new HashMap<Long, Gene>();
				this.geneProvider = geneProvider;
				this.writePRANK();
				this.writeNRANK();
			}

			private List<GeneEntry> populateGenes() {
				List<GeneEntry> result = new ArrayList<GeneEntry>();
				
				final Map<Gene, Double> scores = options.getScores();
				List<Gene> genes = new ArrayList<Gene>(scores.keySet());
//				Sort genes by score
				Collections.sort(genes, new Comparator<Gene>() {
					public int compare(Gene gene1, Gene gene2) {
						return scores.get(gene2).compareTo(scores.get(gene1));
					}
				});
				
				Map<Long, Gene> queryGenes = options.getQueryGenes();
				for (Gene gene : genes) {
					double score;
					if (queryGenes.containsKey(gene.getNode().getId())) {
						score = Double.MAX_VALUE;
					} else {
						score = scores.get(gene) * 100;
					}
					result.add(new GeneEntry(gene, score));
				}
				return result;
			}
			
			private Gene findGene(Node node, SearchResultImplNetDx options) {
				long nodeId = node.getId();
				if (options.isQueryNode(nodeId)) {
					return options.getGene(node.getId());
				}
				
				Gene gene = geneCache.get(node.getId());
				if (gene == null) {
					gene = geneProvider.getGene(node);
					geneCache.put(node.getId(), gene);
				}
				return gene;
			}
			
//			modified copy of TextReportExporter exportGenes() 
			private void writePRANK() throws FileNotFoundException {				
				String prankOutfilename = this.outfilePrefix + "-results.report.txt.PRANK";				
//				System.err.println("Attempting to write PRANK to " + prankOutfilename);
				PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(prankOutfilename)));
				writer.print("#This Report has been generated with a netDx-specific version of GeneMania v3.5.\n"); //$NON-NLS-1$
				writer.print("Gene\tScore\tDescription\n"); //$NON-NLS-1$
				for (GeneEntry entry : this.genes) {
					Gene gene = findGene(entry.getGene().getNode(), options);
					writer.print(fNetworkUtils.getGeneLabel(gene));
					writer.print("\t"); //$NON-NLS-1$
					
					double score = entry.getScore();
					if (score != Double.MAX_VALUE) {
						writer.print(String.format("%.2f", score)); //$NON-NLS-1$
					}
					
					writer.print("\t"); //$NON-NLS-1$
					writer.print(gene.getNode().getGeneData().getDescription());
					writer.print("\n"); //$NON-NLS-1$
				}
				writer.print("\n"); //$NON-NLS-1$
				writer.flush();
				writer.close();
//				System.err.println("Finished writing PRANK to " + prankOutfilename);
			}
			

//			modified copy of TextReportExporter exportNetworks()
			private void writeNRANK() throws FileNotFoundException {
//				let's sort by weight from big to small - as previous versions did
				List<Entry<String, Double>> networkList = new ArrayList<Entry<String, Double>>(options.getParsedNetworkWeights()
						.entrySet());
		        Collections.sort(networkList, new Comparator<Map.Entry<String, Double>>() {
		            public int compare(Map.Entry<String, Double> o1,
		                    Map.Entry<String, Double> o2) {
		                return o2.getValue().compareTo(o1.getValue());
		            }
		        });
				
//		        String nrankOutfilename = this.outfilePrefix + "NRANK.txt";
				String nrankOutfilename = this.outfilePrefix + "-results.report.txt.NRANK";
//				System.err.println("Attempting to write NRANK to " + nrankOutfilename);
				PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(nrankOutfilename)));
				
				writer.print("#This Report has been generated with a netDx-specific version of GeneMania v3.5.\n"); //$NON-NLS-1$
				writer.print("Network"); //$NON-NLS-1$
				writer.print("\t"); //$NON-NLS-1$
				writer.print("Weight"); //$NON-NLS-1$
				writer.print("\n"); //$NON-NLS-1$
				
				for (Entry<String, Double> weightEntry : networkList) {
					writer.print(weightEntry.getKey()); //$NON-NLS-1$
					writer.print("\t"); //$NON-NLS-1$
					writer.print(String.format("%.2f", weightEntry.getValue())); //$NON-NLS-1$
					writer.print("\n"); //$NON-NLS-1$
//					
				}

				writer.print("\n"); //$NON-NLS-1$
				writer.flush();
				writer.close();
//				System.err.println("Finished writing NRANK to " + nrankOutfilename);
			}

		}
	}

	@SuppressWarnings("nls")
	class CombineNetworksQueryHandler implements QueryHandler {
		static final int INTERACTIONS_PER_DOT = 100000;

		private IGeneProvider fGeneProvider;
		Map<Long, String> fSymbolCache;
		private NodeMediator fNodeMediator;

		public CombineNetworksQueryHandler(IGeneProvider geneProvider) {
			fGeneProvider = geneProvider;
			fSymbolCache = new HashMap<Long, String>();
			fNodeMediator = fData.getMediatorProvider().getNodeMediator();
		}

		String findSymbol(long nodeId, long organismId) {
			String symbol = fSymbolCache.get(nodeId);
			if (symbol != null) {
				return symbol;
			}

			Node node = fNodeMediator.getNode(nodeId, organismId);
			if (node == null) {
				return null;
			}

			Gene gene = fGeneProvider.getGene(node);
			if (gene == null) {
				return null;
			}

			symbol = gene.getSymbol();
			fSymbolCache.put(nodeId, symbol);
			return symbol;
		}

		@Override
		public void process(Query query, File outputDirectory, String baseName)
				throws ApplicationException, DataStoreException, IOException {
			CoreMania mania = new CoreMania(fCache);
			Organism organism = query.getOrganism();
			final long organismId = organism.getId();
			String namespace;
			if (organismId < 0) {
				namespace = GeneMania.DEFAULT_NAMESPACE;
			} else {
				namespace = "CORE";
			}
			Collection<Collection<Long>> networkIds = collapseNetworks(query.getGroups());
			Collection<Long> attributeGroupIds = collapseAttributeGroups(query.getGroups());
			int attributeLimit = query.getAttributeLimit();

			if (fVerbose) {
				System.err.println("Computing weights...");
			}
			// Compute weights
			Collection<Long> nodes = query.getNodes();
			ArrayList<Long> negativeNodes = new ArrayList<Long>();
			double posLabelValue = 1.0;
			double negLabelValue = -1.0;
			double unLabeledValueProduction = -1.0;
			Vector labels = LabelVectorGenerator.createLabelsFromIds(fCache.getNodeIds(organismId), nodes,
					negativeNodes, posLabelValue, negLabelValue, unLabeledValueProduction);
			Constants.CombiningMethod combiningMethod = Constants.convertCombiningMethod(query.getCombiningMethod(),
					nodes.size());
			mania.computeWeights(namespace, organismId, labels, combiningMethod, networkIds, attributeGroupIds,
					attributeLimit);

			if (fVerbose) {
				System.err.println("Computing combined network...");
			}
			// Extract weights
			FeatureWeightMap featureWeights = mania.getFeatureWeights();
			List<AttributeDto> attributes = new ArrayList<AttributeDto>();
			List<NetworkDto> networks = new ArrayList<NetworkDto>();
			for (Entry<Feature, Double> entry : featureWeights.entrySet()) {
				Feature feature = entry.getKey();
				if (featureWeights.get(feature) <= 0d) {
					continue;
				}

				switch (feature.getType()) {
				case ATTRIBUTE_VECTOR:
					AttributeDto attribute = new AttributeDto();
					attribute.setId(feature.getId());
					attribute.setGroupId(feature.getGroupId());
					attribute.setWeight(entry.getValue());
					attributes.add(attribute);
					break;
				case SPARSE_MATRIX:
					NetworkDto network = new NetworkDto();
					network.setId(feature.getId());
					network.setWeight(entry.getValue());
					networks.add(network);
					break;
				case BIAS:
					// Ignore
				}
			}

			if (fVerbose) {
				System.err.print("Writing interactions");
			}
			// Generate combined network
			NetworkCombinationRequestDto request = new NetworkCombinationRequestDto();
			request.setOrganismId(organismId);
			request.setNamespace(namespace);
			request.setAttributes(attributes);
			request.setNetworks(networks);
			request.setProgressReporter(NullProgressReporter.instance());
			final PrintWriter writer = new PrintWriter(
					new BufferedOutputStream(new FileOutputStream(String.format("%s%s%s-results.combined-network.txt",
							outputDirectory.getPath(), File.separator, baseName))));
			try {
				request.setInteractionVistor(new InteractionVisitor() {
					int counter;

					@Override
					public void visit(long node1, long node2, double weight) {
						if (weight == 0) {
							return;
						}
						if (fVerbose) {
							if (counter % INTERACTIONS_PER_DOT == 0) {
								System.err.print(".");
							}
						}
						String symbol1 = findSymbol(node1, organismId);
						String symbol2 = findSymbol(node2, organismId);
						writer.printf("%s\t%s\t%s\n", symbol1, symbol2, Double.toString(weight));
						counter++;
					}
				});
				CombineNetworks combineNetworks = new CombineNetworks(fCache, request);
				combineNetworks.process();
			} finally {
				writer.close();
			}
			if (fVerbose) {
				System.err.println("\nDone.");
			}
		}
	}

}