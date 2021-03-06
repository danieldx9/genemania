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
package org.genemania.plugin.delegates;

import org.cytoscape.model.CyNetwork;
import org.genemania.exception.ApplicationException;
import org.genemania.plugin.GeneMania;
import org.genemania.plugin.cytoscape.CytoscapeUtils;
import org.genemania.plugin.model.ViewState;
import org.genemania.plugin.selection.SessionManager;

public class SelectionDelegate implements Delegate {

	protected final boolean selected;
	protected CyNetwork network;
	protected final SessionManager manager;
	private final GeneMania plugin;
	protected final CytoscapeUtils cytoscapeUtils;

	private static Object selectionMutex = new Object();
	
	public SelectionDelegate(boolean selected, CyNetwork network, SessionManager manager, GeneMania plugin,
			CytoscapeUtils cytoscapeUtils) {
		this.selected = selected;
		this.network = network;
		this.manager = manager;
		this.plugin = plugin;
		this.cytoscapeUtils = cytoscapeUtils;
	}
	
	@Override
	public void invoke() throws ApplicationException {
		synchronized (selectionMutex) {
			if (!manager.isSelectionListenerEnabled())
				return;
			
			ViewState options = manager.getNetworkConfiguration(network);
			
			if (options == null)
				return;
			
			handleSelection(options);
			
			boolean listenerState = manager.isSelectionListenerEnabled();
			manager.setSelectionListenerEnabled(false);
			try {
				plugin.updateSelection(options);
			} finally {
				manager.setSelectionListenerEnabled(listenerState);
			}
		}
	}

	protected void handleSelection(ViewState options) throws ApplicationException {
	}
}
