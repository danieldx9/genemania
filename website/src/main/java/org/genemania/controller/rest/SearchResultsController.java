package org.genemania.controller.rest;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.genemania.domain.AttributeGroup;
import org.genemania.domain.Gene;
import org.genemania.domain.InteractionNetwork;
import org.genemania.domain.Organism;
import org.genemania.domain.SearchParameters;
import org.genemania.domain.SearchRequest;
import org.genemania.domain.SearchResults;
import org.genemania.exception.ApplicationException;
import org.genemania.exception.DataStoreException;
import org.genemania.exception.NoUserNetworkException;
import org.genemania.service.AttributeGroupService;
import org.genemania.service.AttributeService;
import org.genemania.service.GeneService;
import org.genemania.service.NetworkService;
import org.genemania.service.OrganismService;
import org.genemania.service.SearchService;
import org.genemania.type.SearchResultsErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SearchResultsController {

	@Autowired
	private SearchService searchService;

	@Autowired
	private OrganismService organismService;

	@Autowired
	private GeneService geneService;

	@Autowired
	private NetworkService networkService;

	@Autowired
	private AttributeService attributeService;

	@Autowired
	AttributeGroupService attributeGroupService;

	@Autowired
	private MappingJackson2HttpMessageConverter httpConverter;

	@RequestMapping(method = RequestMethod.POST, value = "/search_results")
	@ResponseBody
	public SearchResults list(HttpServletRequest req, HttpSession session) throws ApplicationException {

		SearchRequest sReq = null;

		String contentType = req.getHeader("Content-Type");
		contentType = contentType != null ? contentType : "";

		if (contentType.toLowerCase().contains("application/json")) {
			try {
				sReq = httpConverter.getObjectMapper().readValue(req.getInputStream(), SearchRequest.class);
			} catch (Exception e) {

			}
		} else {
			sReq = new SearchRequest();

			sReq.setOrganismFromString(req.getParameter("organism"));
			sReq.setGenes(req.getParameter("genes"));
			sReq.setWeighting(req.getParameter("weighting"));
			sReq.setGeneThresholdFromString(req.getParameter("geneThreshold"));
			sReq.setAttrThresholdFromString(req.getParameter("attrThreshold"));
			sReq.setNetworksFromString(req.getParameter("networks"));
			sReq.setAttrGroupsFromString(req.getParameter("attrGroups"));
			sReq.setSessionId(req.getParameter("sessionId"));
		}

		sReq.assertParamsSet();

		// set up search params
		//
		SearchParameters params = new SearchParameters();

		String sessionId = sReq.getSessionId();

		if (sessionId == null || sessionId.isEmpty()) {
			sessionId = session.getId();
		}

		params.setNamespace(sessionId);

		boolean includeUserNetworks = true;

		// set organism
		Organism organism;
		Long organismId;
		try {
			organismId = sReq.getOrganism();
			organism = organismService.findOrganismById(organismId);
			params.setOrganism(organism);
		} catch (DataStoreException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.DATASTORE);
		}

		// set genes
		String[] genesSplit = sReq.getGenes().split("\n");
		for (int i = 0; i < genesSplit.length; i++) {
			genesSplit[i] = genesSplit[i].trim();
		}
		List<String> geneLines = Arrays.asList(genesSplit);
		Collection<Gene> genes;
		try {
			genes = geneService.findGenesForOrganism(organismId.intValue(), geneLines);
			params.setGenes(genes);
		} catch (DataStoreException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.DATASTORE);
		}

		// set weighting
		params.setWeighting(sReq.getWeighting());

		// set gene threshold
		Integer geneThreshold = sReq.getGeneThreshold();
		if (geneThreshold < 0) {
			return new SearchResults("Non-negative gene threshold required", SearchResultsErrorCode.DATASTORE);
		} else {
			params.setResultsSize(geneThreshold);
		}

		// set attr threshold
		Integer attrThreshold = sReq.getAttrThreshold();
		if (attrThreshold < 0) {
			return new SearchResults("The attribute threshold must be non-negative", SearchResultsErrorCode.DATASTORE);
		} else {
			params.setAttributeResultsSize(attrThreshold);
		}

		// set networks
		Long[] networkIds = sReq.getNetworks();
		Collection<InteractionNetwork> networks;
		try {
			if (networkIds == null) {
				networks = networkService.findDefaultNetworksForOrganism((long) organismId, sessionId,
						includeUserNetworks);
			} else {
				networks = networkService.getNetworks(organismId.intValue(), networkIds, sessionId,
						includeUserNetworks);
			}

			params.setNetworks(networks);
		} catch (DataStoreException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.DATASTORE);
		}

		// set attrs
		Long[] attrGroupIds = sReq.getAttrGroups();
		Collection<AttributeGroup> attrs;
		try {
			if (attrGroupIds == null) {
				attrs = attributeGroupService.findDefaultAttributeGroups(organismId);
			} else {
				attrs = attributeGroupService.findAttributeGroups(organismId, attrGroupIds);
			}

			params.setAttributeGroups(attrs);
		} catch (DataStoreException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.DATASTORE);
		}

		//
		// //

		// submit and return the search
		try {
			SearchResults results = searchService.search(params);

			// back reference to parameters b/c params may be auto/default
			results.setParameters(params);

			return results;
		} catch (ApplicationException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.APP);
		} catch (DataStoreException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.DATASTORE);
		} catch (NoUserNetworkException e) {
			return new SearchResults(e.getMessage(), SearchResultsErrorCode.USER_NETWORK);
		}

	}

	public MappingJackson2HttpMessageConverter getHttpConverter() {
		return httpConverter;
	}

	public void setHttpConverter(MappingJackson2HttpMessageConverter httpConverter) {
		this.httpConverter = httpConverter;
	}

	public AttributeGroupService getAttributeGroupService() {
		return attributeGroupService;
	}

	public void setAttributeGroupService(AttributeGroupService attributeGroupService) {
		this.attributeGroupService = attributeGroupService;
	}

	public SearchService getSearchService() {
		return searchService;
	}

	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

	public OrganismService getOrganismService() {
		return organismService;
	}

	public void setOrganismService(OrganismService organismService) {
		this.organismService = organismService;
	}

	public GeneService getGeneService() {
		return geneService;
	}

	public void setGeneService(GeneService geneService) {
		this.geneService = geneService;
	}

	public NetworkService getNetworkService() {
		return networkService;
	}

	public void setNetworkService(NetworkService networkService) {
		this.networkService = networkService;
	}

	public AttributeService getAttributeService() {
		return attributeService;
	}

	public void setAttributeService(AttributeService attributeService) {
		this.attributeService = attributeService;
	}

}
