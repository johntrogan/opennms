/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 * OpenNMS(R) Licensing <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.topology.plugins.topo.application;

import java.util.ArrayList;
import java.util.List;

import org.opennms.features.topology.api.topo.AbstractVertex;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.netmgt.model.OnmsServiceType;

class ApplicationVertex extends AbstractVertex {

    private List<VertexRef> children = new ArrayList<>();

    private OnmsServiceType serviceType;

    /**
     * Creates a new {@link ApplicationVertex}.
     * @param id the unique id of this vertex. Must be unique overall the namespace.
     */
    public ApplicationVertex(String id) {
        super(ApplicationTopologyProvider.TOPOLOGY_NAMESPACE, id);
        setIconKey(null);
        setLocked(false);
        setSelected(false);
    }

    public void addChildren(AbstractVertex vertex) {
        if (!children.contains(vertex)) {
            children.add(vertex);
            vertex.setParent(this);
        }
    }
    
    public void setServiceType(OnmsServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public boolean isRoot() {
        return getParent() == null;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public List<VertexRef> getChildren() {
        return children;
    }

    public OnmsServiceType getServiceType() {
        return serviceType;
    }

    public boolean isPartOf(String applicationId) {
        return applicationId != null && applicationId.equals(getRoot().getId());
    }

    public ApplicationVertex getRoot() {
        if (isRoot()) {
            return this;
        }
        return ((ApplicationVertex)getParent()).getRoot();
    }
}
