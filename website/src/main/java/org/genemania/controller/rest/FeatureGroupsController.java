package org.genemania.controller.rest;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.genemania.domain.AttributeGroup;
import org.genemania.domain.InteractionNetworkGroup;
import org.genemania.domain.Organism;
import org.genemania.exception.DataStoreException;
import org.genemania.service.AttributeGroupService;
import org.genemania.service.NetworkGroupService;
import org.genemania.service.OrganismService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FeatureGroupsController {

	public class Features {
		Collection<InteractionNetworkGroup> networkGroups;
		Collection<AttributeGroup> attributeGroups;

		public Collection<InteractionNetworkGroup> getNetworkGroups() {
			return networkGroups;
		}

		public void setNetworkGroups(
				Collection<InteractionNetworkGroup> networkGroups) {
			this.networkGroups = networkGroups;
		}

		public Collection<AttributeGroup> getAttributeGroups() {
			return attributeGroups;
		}

		public void setAttributeGroups(
				Collection<AttributeGroup> attributeGroups) {
			this.attributeGroups = attributeGroups;
		}

	}

	@Autowired
	private NetworkGroupService networkGroupService;

	@Autowired
	private AttributeGroupService attributeGroupService;

	@Autowired
	OrganismService organismService;

	@RequestMapping(method = RequestMethod.GET, value = "/feature_groups/{organismId}")
	@ResponseBody
	public Features list(@PathVariable Long organismId, HttpSession session)
			throws DataStoreException {

		Features ret = new Features();

		ret.setNetworkGroups(networkGroupService.findNetworkGroupsByOrganism(
				organismId, session.getId()));

		ret.setAttributeGroups(attributeGroupService
				.findAttributeGroups(organismId));

		return ret;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/feature_groups")
	@ResponseBody
	public Map<Long, Features> listAll(HttpSession session)
			throws DataStoreException {

		Map<Long, Features> idToFeatures = new HashMap<Long, Features>();
		Collection<Organism> organisms = this.organismService.getOrganisms();

		for (Organism organism : organisms) {
			Long organismId = organism.getId();

			Features features = new Features();

			features.setNetworkGroups(networkGroupService
					.findNetworkGroupsByOrganism(organismId, session.getId()));

			features.setAttributeGroups(attributeGroupService
					.findAttributeGroups(organismId));

			idToFeatures.put(organismId, features);
		}

		return idToFeatures;
	}

	public NetworkGroupService getNetworkGroupService() {
		return networkGroupService;
	}

	public void setNetworkGroupService(NetworkGroupService networkGroupService) {
		this.networkGroupService = networkGroupService;
	}

	public AttributeGroupService getAttributeGroupService() {
		return attributeGroupService;
	}

	public void setAttributeGroupService(
			AttributeGroupService attributeGroupService) {
		this.attributeGroupService = attributeGroupService;
	}

	public OrganismService getOrganismService() {
		return organismService;
	}

	public void setOrganismService(OrganismService organismService) {
		this.organismService = organismService;
	}

}
