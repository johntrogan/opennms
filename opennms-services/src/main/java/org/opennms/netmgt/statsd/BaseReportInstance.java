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
package org.opennms.netmgt.statsd;

import java.util.Date;
import java.util.SortedSet;

import org.opennms.netmgt.dao.api.ResourceDao;
import org.opennms.netmgt.dao.support.AttributeMatchingResourceVisitor;
import org.opennms.netmgt.dao.support.ResourceAttributeFilteringResourceVisitor;
import org.opennms.netmgt.dao.support.ResourceTypeFilteringResourceVisitor;
import org.opennms.netmgt.dao.support.ResourceWalker;
import org.opennms.netmgt.dao.support.RrdStatisticAttributeVisitor;
import org.opennms.netmgt.measurements.api.MeasurementFetchStrategy;
import org.opennms.netmgt.model.AttributeStatistic;
import org.opennms.netmgt.model.AttributeStatisticVisitorWithResults;
import org.springframework.beans.factory.InitializingBean;

/**
 * <p>BaseReportInstance class.</p>
 * 
 * TODO: Merge this class with {@link AbstractReportInstance}.
 *
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 */
public abstract class BaseReportInstance extends AbstractReportInstance implements InitializingBean {
    private final AttributeStatisticVisitorWithResults m_attributeStatisticVisitor;
    private final RrdStatisticAttributeVisitor m_rrdVisitor = new RrdStatisticAttributeVisitor();
    private final AttributeMatchingResourceVisitor m_attributeVisitor = new AttributeMatchingResourceVisitor();
    private final ResourceTypeFilteringResourceVisitor m_resourceTypeVisitor = new ResourceTypeFilteringResourceVisitor();
    private String m_resourceAttributeKey;
    private String m_resourceAttributeValueMatch;
    private ResourceAttributeFilteringResourceVisitor m_resourceAttributeVisitor;
    
    /**
     * <p>Constructor for UnfilteredReportInstance.</p>
     *
     * @param visitor a {@link org.opennms.netmgt.model.AttributeStatisticVisitorWithResults} object.
     */
    protected BaseReportInstance(AttributeStatisticVisitorWithResults visitor) {
        m_attributeStatisticVisitor = visitor;
    }

    public abstract ResourceWalker getWalker();

    /**
     * <p>setResourceDao</p>
     *
     * @param resourceDao a {@link org.opennms.netmgt.dao.api.ResourceDao} object.
     */
    public void setResourceDao(ResourceDao resourceDao) {
        getWalker().setResourceDao(resourceDao);
    }

    public void setFetchStrategy(MeasurementFetchStrategy fetchStrategy) {
        m_rrdVisitor.setFetchStrategy(fetchStrategy);
    }

    /**
     * <p>walk</p>
     */
    @Override
    public void walk() {
        setJobStartedDate(new Date());
        getWalker().walk();
        setJobCompletedDate(new Date());
    }

    /**
     * <p>getResults</p>
     *
     * @return a {@link java.util.SortedSet} object.
     */
    @Override
    public SortedSet<AttributeStatistic> getResults() {
        return m_attributeStatisticVisitor.getResults();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getResourceTypeMatch()
     */
    /**
     * <p>getResourceTypeMatch</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getResourceTypeMatch() {
        return m_resourceTypeVisitor.getResourceTypeMatch();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setResourceTypeMatch(java.lang.String)
     */
    /** {@inheritDoc} */
    @Override
    public void setResourceTypeMatch(String resourceType) {
        m_resourceTypeVisitor.setResourceTypeMatch(resourceType);
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getAttributeMatch()
     */
    /**
     * <p>getAttributeMatch</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getAttributeMatch() {
        return m_attributeVisitor.getAttributeMatch();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setAttributeMatch(java.lang.String)
     */
    /** {@inheritDoc} */
    @Override
    public void setAttributeMatch(String attr) {
        m_attributeVisitor.setAttributeMatch(attr);
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getStartTime()
     */
    /**
     * <p>getStartTime</p>
     *
     * @return a long.
     */
    @Override
    public long getStartTime() {
        return m_rrdVisitor.getStartTime();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setStartTime(long)
     */
    /** {@inheritDoc} */
    @Override
    public void setStartTime(long start) {
        m_rrdVisitor.setStartTime(start);
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getEndTime()
     */
    /**
     * <p>getEndTime</p>
     *
     * @return a long.
     */
    @Override
    public long getEndTime() {
        return m_rrdVisitor.getEndTime();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setEndTime(long)
     */
    /** {@inheritDoc} */
    @Override
    public void setEndTime(long end) {
        m_rrdVisitor.setEndTime(end);
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getConsolidationFunction()
     */
    /**
     * <p>getConsolidationFunction</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getConsolidationFunction() {
        return m_rrdVisitor.getConsolidationFunction();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setConsolidationFunction(java.lang.String)
     */
    /** {@inheritDoc} */
    @Override
    public void setConsolidationFunction(String cf) {
        m_rrdVisitor.setConsolidationFunction(cf);
    }
    
    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#getCount()
     */
    /**
     * <p>getCount</p>
     *
     * @return a int.
     */
    @Override
    public int getCount() {
        return m_attributeStatisticVisitor.getCount();
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#setCount(int)
     */
    /** {@inheritDoc} */
    @Override
    public void setCount(int count) {
        m_attributeStatisticVisitor.setCount(count);
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.topn.Report#afterPropertiesSet()
     */
    /**
     * <p>afterPropertiesSet</p>
     */
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        
        m_rrdVisitor.setStatisticVisitor(m_attributeStatisticVisitor);
        m_attributeVisitor.setAttributeVisitor(m_rrdVisitor);
        
        if (m_resourceAttributeKey != null && m_resourceAttributeValueMatch != null) {
            m_resourceAttributeVisitor = new ResourceAttributeFilteringResourceVisitor();
            m_resourceAttributeVisitor.setDelegatedVisitor(m_attributeVisitor);
            m_resourceAttributeVisitor.setResourceAttributeKey(m_resourceAttributeKey);
            m_resourceAttributeVisitor.setResourceAttributeValueMatch(m_resourceAttributeValueMatch);
            m_resourceAttributeVisitor.afterPropertiesSet();
            
            m_resourceTypeVisitor.setDelegatedVisitor(m_resourceAttributeVisitor);
        } else {
            m_resourceTypeVisitor.setDelegatedVisitor(m_attributeVisitor);
        }
        
        getWalker().setVisitor(m_resourceTypeVisitor);

        m_attributeStatisticVisitor.afterPropertiesSet();
        m_rrdVisitor.afterPropertiesSet();
        m_attributeVisitor.afterPropertiesSet();
        m_resourceTypeVisitor.afterPropertiesSet();
        getWalker().afterPropertiesSet();
    }

    /** {@inheritDoc} */
    @Override
    public void setResourceAttributeKey(String resourceAttributeKey) {
        m_resourceAttributeKey = resourceAttributeKey;
    }

    /** {@inheritDoc} */
    @Override
    public void setResourceAttributeValueMatch(String resourceAttributeValueMatch) {
        m_resourceAttributeValueMatch = resourceAttributeValueMatch;
    }

    /**
     * <p>getResourceAttributeKey</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getResourceAttributeKey() {
        return m_resourceAttributeKey;
    }

    /**
     * <p>getResourceAttributeValueMatch</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getResourceAttributeValueMatch() {
        return m_resourceAttributeValueMatch;
    }
}
