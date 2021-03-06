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
package org.genemania.engine.core.data;

import org.genemania.engine.matricks.Matrix;

/*
 * the actual attributes belonging to a group,
 * represented as a sparse matrix.
 * 
 * rows are genes (nodes), columns are attributes. 
 * nodes are indexed as per the ordering in NodeIds,
 * and attributes are ordered as listed in AttributeGroups
 * for the particular group id. 
 */
public class AttributeData extends Data {
    private static final long serialVersionUID = 5370750395081040652L;
    long attributeGroupId;
    Matrix data;

    public AttributeData(String namespace, long organismId, long attributeGroupId) {
        super(namespace, organismId);
        this.attributeGroupId = attributeGroupId;
    }
    
    public long getAttributeGroupId() {
        return attributeGroupId;
    }

    public void setAttributeGroupId(long attributeGroupId) {
        this.attributeGroupId = attributeGroupId;
    }
    
    public Matrix getData() {
        return data;
    }

    public void setData(Matrix attributeData) {
        this.data = attributeData;
    }
    
    @Override
    public String [] getKey() {
        return new String [] {getNamespace(), "" + getOrganismId(), "attributes." + getAttributeGroupId()};
    }      
}
