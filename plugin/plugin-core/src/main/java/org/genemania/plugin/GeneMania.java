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
package org.genemania.plugin;

import java.awt.Window;
import java.io.File;

import org.genemania.exception.ApplicationException;
import org.genemania.plugin.data.DataSetManager;
import org.genemania.plugin.model.ViewState;
import org.genemania.plugin.selection.SessionManager;
import org.genemania.util.ProgressReporter;

public interface GeneMania {
	
	public static final String SCHEMA_VERSION = "1.1"; //$NON-NLS-1$
	public static final String DEFAULT_NAMESPACE = "user"; //$NON-NLS-1$

	public static final String DATA_SOURCE_PATH_PROPERTY = "genemania.datasource"; //$NON-NLS-1$
	public static final String SETTINGS_PROPERTY = "genemania.plugin.settings"; //$NON-NLS-1$
	
	public static final String APP_CYPROPERTY_NAME = "GeneMANIA";
	
	public static final String ORGANISMS_API_URL = "api.organisms.url"; //$NON-NLS-1$
	public static final String NETWORKS_API_URL = "api.networks.url"; //$NON-NLS-1$
	public static final String VERSION_API_URL = "api.version.url"; //$NON-NLS-1$
	public static final String SEARCH_API_URL = "api.search.url"; //$NON-NLS-1$
	
	void showResults();

	void hideResults();

	SessionManager getSessionManager();

	DataSetManager getDataSetManager();

	boolean initializeData(ProgressReporter progress, boolean reportErrors) throws ApplicationException;

	void initializeData(Window parent, boolean reportErrors);

	void loadDataSet(File path, ProgressReporter progress, boolean promptToUpdate, boolean reportErrors)
			throws ApplicationException;

	void applyOptions(ViewState options);

	void updateSelection(ViewState options);

	void handleCheck();

	void handleDownload();

	void handleSwitch();
}
