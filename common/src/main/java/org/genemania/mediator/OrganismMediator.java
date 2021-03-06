/**
 * This file is part of GeneMANIA.
 * Copyright (C) 2010 University of Toronto.
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

/**
 * OrganismMediator: TODO add description
 * Created Jun 27, 2008
 * @author Ovi Comes
 */
package org.genemania.mediator;

import java.util.List;

import org.genemania.domain.Gene;
import org.genemania.domain.InteractionNetwork;
import org.genemania.domain.Organism;
import org.genemania.exception.DataStoreException;

public interface OrganismMediator extends BaseMediator {

	public Organism getOrganism(long organismId) throws DataStoreException;
	public List<Organism> getAllOrganisms() throws DataStoreException;
	public List<Gene> getDefaultGenes(long organismId) throws DataStoreException;
	public List<InteractionNetwork> getDefaultNetworks(long organismId) throws DataStoreException;
	public NodeCursor createNodeCursor(long organismId) throws DataStoreException;
	public Organism getOrganismForGroup(long groupId) throws DataStoreException;
	
}
