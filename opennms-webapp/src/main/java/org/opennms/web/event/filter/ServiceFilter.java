/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.web.event.filter;

import javax.servlet.ServletContext;

import org.opennms.web.element.NetworkElementFactory;
import org.opennms.web.element.NetworkElementFactoryInterface;
import org.opennms.web.filter.EqualsFilter;
import org.opennms.web.filter.SQLType;
import org.springframework.context.ApplicationContext;

/**
 * Encapsulates all service filtering functionality.
 *
 * @author ranger
 * @version $Id: $
 * @since 1.8.1
 */
public class ServiceFilter extends EqualsFilter<Integer> {
    /** Constant <code>TYPE="service"</code> */
    public static final String TYPE = "service";
    private ServletContext m_servletContext;
    private ApplicationContext m_appContext;

    /**
     * <p>Constructor for ServiceFilter.</p>
     *
     * @param serviceId a int.
     * @param servletContext
     */
    public ServiceFilter(int serviceId, ServletContext servletContext) {
        super(TYPE, SQLType.INT, "EVENTS.SERVICEID", "serviceType.id", serviceId);
        m_servletContext = servletContext;
    }

    /**
     * <p>Constructor for ServiceFilter.</p>
     *
     * @param serviceId a int.
     * @param appContext
     */
    public ServiceFilter(int serviceId, ApplicationContext appContext) {
        super(TYPE, SQLType.INT, "EVENTS.SERVICEID", "serviceType.id", serviceId);
        m_appContext = appContext;
    }

    /**
     * <p>getTextDescription</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getTextDescription() {
        NetworkElementFactoryInterface factory = (m_servletContext == null ? NetworkElementFactory.getInstance(m_appContext) : NetworkElementFactory.getInstance(m_servletContext));
        String serviceName = factory.getServiceNameFromId(getServiceId());

        return (TYPE + " is " + serviceName);
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return ("<WebEventRepository.ServiceFilter: " + this.getDescription() + ">");
    }

    /**
     * <p>getServiceId</p>
     *
     * @return a int.
     */
    public int getServiceId() {
        return getValue();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof ServiceFilter)) return false;
        return (this.toString().equals(obj.toString()));
    }
}
