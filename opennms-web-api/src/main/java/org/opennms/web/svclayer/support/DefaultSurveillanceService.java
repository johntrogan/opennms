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
package org.opennms.web.svclayer.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.opennms.netmgt.config.surveillanceViews.Category;
import org.opennms.netmgt.config.surveillanceViews.ColumnDef;
import org.opennms.netmgt.config.surveillanceViews.RowDef;
import org.opennms.netmgt.config.surveillanceViews.View;
import org.opennms.netmgt.dao.api.CategoryDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SurveillanceViewConfigDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.SurveillanceStatus;
import org.opennms.web.api.Util;
import org.opennms.web.svclayer.SurveillanceService;
import org.opennms.web.svclayer.model.AggregateStatus;
import org.opennms.web.svclayer.model.ProgressMonitor;
import org.opennms.web.svclayer.model.SimpleWebTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.util.StringUtils;

/**
 * <p>DefaultSurveillanceService class.</p>
 *
 * @author <a href="mailto:david@opennms.org">David Hustace</a>
 * @author <a href="mailto:brozow@opennms.org">Mathew Brozowski</a>
 * @author <a href="mailto:jeffg@opennms.org">Jeff Gehlbach</a>
 * @since 1.8.1
 */
public class DefaultSurveillanceService implements SurveillanceService {
	
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSurveillanceService.class);


    private NodeDao m_nodeDao;
    private CategoryDao m_categoryDao;
    private SurveillanceViewConfigDao m_surveillanceConfigDao;
    
    interface CellStatusStrategy {
        public SurveillanceStatus[][] calculateCellStatus(SurveillanceView sView, ProgressMonitor progressMonitor);

        public int getPhaseCount(SurveillanceView sView);
    }

    class DefaultCellStatusStrategy implements CellStatusStrategy {

        private Collection<OnmsNode> getNodesInCategories(final Set<OnmsCategory> categories) {
            return m_nodeDao.findAllByCategoryList(categories);
        }

        @Override
        public SurveillanceStatus[][] calculateCellStatus(final SurveillanceView sView, final ProgressMonitor progressMonitor) {

        	final List<Collection<OnmsNode>> nodesByRowIndex = new ArrayList<Collection<OnmsNode>>();
        	final List<Collection<OnmsNode>> nodesByColIndex = new ArrayList<Collection<OnmsNode>>();

            /*
             * Iterate of the requested view's configuration (row's and columns) and set an aggreated status into each table
             * cell.
             */
            for(int rowIndex = 0; rowIndex < sView.getRowCount(); rowIndex++) {
                progressMonitor.beginNextPhase("Loading nodes for row '"+sView.getRowLabel(rowIndex) + "'");
                final Collection<OnmsNode> nodesForRow = getNodesInCategories(sView.getCategoriesForRow(rowIndex));
                nodesByRowIndex.add(rowIndex, nodesForRow);
            }

            for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
                progressMonitor.beginNextPhase("Loading nodes for column '"+sView.getColumnLabel(colIndex) + "'");
                final Collection<OnmsNode> nodesForCol = getNodesInCategories(sView.getCategoriesForColumn(colIndex));
                nodesByColIndex.add(colIndex, nodesForCol);
            }

            final SurveillanceStatus[][] cellStatus = new SurveillanceStatus[sView.getRowCount()][sView.getColumnCount()];

            progressMonitor.beginNextPhase("Intersecting rows and columns");

            for(int rowIndex = 0; rowIndex < sView.getRowCount(); rowIndex++) {
            	final Collection<OnmsNode> nodesForRow = nodesByRowIndex.get(rowIndex);

                for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
                	final Collection<OnmsNode> nodesForCol = nodesByColIndex.get(colIndex);
                	final Set<OnmsNode> cellNodes = new HashSet<OnmsNode>(nodesForRow);

                	cellNodes.retainAll(nodesForCol);
                    cellStatus[rowIndex][colIndex] = new AggregateStatus(cellNodes);
                }

            }
            return cellStatus;
        }

        @Override
        public int getPhaseCount(final SurveillanceView sView) {
            return sView.getRowCount()+sView.getColumnCount()+1;
        }

    }

    class LowMemCellStatusStrategy implements CellStatusStrategy {
        
        private String toString(final Collection<OnmsCategory> categories) {
        	final StringBuilder buf = new StringBuilder();
            
            buf.append("{");
            
            for(final OnmsCategory cat : categories) {
            	if (buf.length() != 0) {
                    buf.append(", ");
                }
                buf.append(cat.getName());
            }
            
            buf.append("}");
            
            return buf.toString();
        }
        

        @Override
        public SurveillanceStatus[][] calculateCellStatus(final SurveillanceView sView, final ProgressMonitor progressMonitor) {
        	final SurveillanceStatus[][] cellStatus = new SurveillanceStatus[sView.getRowCount()][sView.getColumnCount()];

            for(int rowIndex = 0; rowIndex < sView.getRowCount(); rowIndex++) {

                for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
                    
                	final Collection<OnmsCategory> rowCategories = sView.getCategoriesForRow(rowIndex);
                	final Collection<OnmsCategory> columnCategories = sView.getCategoriesForColumn(colIndex);

                	progressMonitor.beginNextPhase(String.format("Finding nodes in %s intersect %s", toString(rowCategories), toString(columnCategories)));

                    final Collection<OnmsNode> cellNodes = m_nodeDao.findAllByCategoryLists(rowCategories, columnCategories);

                    cellStatus[rowIndex][colIndex] = new AggregateStatus(cellNodes);
                }

            }
            return cellStatus;
        }


        @Override
        public int getPhaseCount(final SurveillanceView sView) {
            return sView.getRowCount()*sView.getColumnCount();
        }

    }

    class VeryLowMemCellStatusStrategy implements CellStatusStrategy {
        
        private String toString(final Collection<OnmsCategory> categories) {
        	final StringBuilder buf = new StringBuilder();
            
            buf.append("{");
            
            for(final OnmsCategory cat : categories) {
            	if (buf.length() != 0) {
                    buf.append(", ");
                }
                buf.append(cat.getName());
            }
            
            buf.append("}");
            
            return buf.toString();
        }
        

        @Override
        public SurveillanceStatus[][] calculateCellStatus(final SurveillanceView sView, final ProgressMonitor progressMonitor) {

        	final SurveillanceStatus[][] cellStatus = new SurveillanceStatus[sView.getRowCount()][sView.getColumnCount()];

            for(int rowIndex = 0; rowIndex < sView.getRowCount(); rowIndex++) {

                for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
                    
                	final Collection<OnmsCategory> rowCategories = sView.getCategoriesForRow(rowIndex);
                	final Collection<OnmsCategory> columnCategories = sView.getCategoriesForColumn(colIndex);

                    progressMonitor.beginNextPhase(String.format("Finding status for nodes in %s intersect %s", toString(rowCategories), toString(columnCategories)));

                    final SurveillanceStatus status = m_nodeDao.findSurveillanceStatusByCategoryLists(rowCategories, columnCategories);
                    
                    cellStatus[rowIndex][colIndex] = status;

                }

            }
            return cellStatus;
        }


        @Override
        public int getPhaseCount(final SurveillanceView sView) {
            return sView.getRowCount()*sView.getColumnCount();
        }

    }

    /**
     * <p>createSurveillanceTable</p>
     *
     * @return a {@link org.opennms.web.svclayer.model.SimpleWebTable} object.
     */
    public SimpleWebTable createSurveillanceTable() {
        return createSurveillanceTable("default", new ProgressMonitor());
    }

    public static class SurveillanceView {
    	private final SurveillanceViewConfigDao m_surveillanceConfigDao;
        private final CategoryDao m_categoryDao;
        private final View m_view;

        public SurveillanceView(final String viewName, final SurveillanceViewConfigDao surveillanceConfigDao, final CategoryDao categoryDao) {
            m_surveillanceConfigDao = surveillanceConfigDao;
            m_categoryDao = categoryDao;
            m_view = m_surveillanceConfigDao.getView(viewName);
        }

        public int getRowCount() {
            return m_view.getRows().size();
        }

        public int getColumnCount() {
            return m_view.getColumns().size();
        }

        public Set<OnmsCategory> getCategoriesForRow(final int rowIndex) {
            return getOnmsCategoriesFromViewCategories(getRowDef(rowIndex).getCategories());
        }

        private RowDef getRowDef(final int rowIndex) {
            return m_view.getRows().get(rowIndex);
        }

        public Set<OnmsCategory> getCategoriesForColumn(final int colIndex) {
            return getOnmsCategoriesFromViewCategories(getColumnDef(colIndex).getCategories());
        }

        private ColumnDef getColumnDef(final int colIndex) {
            return m_view.getColumns().get(colIndex);
        }

        private Set<OnmsCategory> getOnmsCategoriesFromViewCategories(final Collection<Category> viewCats) {
        	final Set<OnmsCategory> categories = new HashSet<OnmsCategory>();

        	for (final Category viewCat : viewCats) {
        		final OnmsCategory category = m_categoryDao.findByName(viewCat.getName());
                if (category == null)
                    throw new ObjectRetrievalFailureException(OnmsCategory.class, viewCat.getName(), "Unable to locate OnmsCategory named: "+viewCat.getName()+" as specified in the surveillance view configuration file", null);
                categories.add(category);
            }

            return categories;
        }

        public String getRowLabel(final int rowIndex) {
            return getRowDef(rowIndex).getLabel();
        }

        public String getColumnLabel(final int colIndex) {
            return getColumnDef(colIndex).getLabel();
        }

        public Optional<String> getColumnReportCategory(final int colIndex) {
            return getColumnDef(colIndex).getReportCategory();
        }

        public Optional<String> getRowReportCategory(final int rowIndex) {
            return getRowDef(rowIndex).getReportCategory();
        }

    }

    /**
     * {@inheritDoc}
     *
     * Creates a custom table object containing intersected rows and
     * columns and categories.
     */
    @Override
    public SimpleWebTable createSurveillanceTable(final String surveillanceViewName, final ProgressMonitor progressMonitor) {
        
        CellStatusStrategy strategy = getCellStatusStrategy();

        final String name = (surveillanceViewName == null ? m_surveillanceConfigDao.getDefaultView().getName() : surveillanceViewName);
        final View view = m_surveillanceConfigDao.getView(name);
        final SurveillanceView sView = new SurveillanceView(name, m_surveillanceConfigDao, m_categoryDao);

        progressMonitor.setPhaseCount(strategy.getPhaseCount(sView) + 1);

        /*
         * Initialize a status table 
         */
        final SimpleWebTable webTable = new SimpleWebTable();
        webTable.setTitle(view.getName());
        webTable.addColumn("Nodes Down", "simpleWebTableHeader");

        // set up the column headings
        for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
            webTable.addColumn(sView.getColumnLabel(colIndex), "simpleWebTableHeader")
            	.setLink(computeReportCategoryLink(sView.getColumnReportCategory(colIndex).orElse(null)));
        }


        // build the set of nodes for each cell
        final SurveillanceStatus[][] cellStatus = strategy.calculateCellStatus(sView, progressMonitor);

        progressMonitor.beginNextPhase("Calculating Status Values");

        for(int rowIndex = 0; rowIndex < sView.getRowCount(); rowIndex++) {

            webTable.newRow();
            webTable.addCell(sView.getRowLabel(rowIndex), "simpleWebTableRowLabel")
            	.setLink(computeReportCategoryLink(sView.getRowReportCategory(rowIndex).orElse(null)));


            for(int colIndex = 0; colIndex < sView.getColumnCount(); colIndex++) {
            	final SurveillanceStatus survStatus = cellStatus[rowIndex][colIndex];

                final String text = survStatus.getDownEntityCount()+" of "+survStatus.getTotalEntityCount();
				LOG.debug("Text: {}, Style {}", text, survStatus.getStatus());
				final SimpleWebTable.Cell cell = webTable.addCell(text, survStatus.getStatus());

                if (survStatus.getDownEntityCount() > 0) {
                    cell.setLink(createNodePageUrl(sView, colIndex, rowIndex));
                }
            }

        }
        progressMonitor.finished(webTable);

        return webTable;
    }

    private CellStatusStrategy getCellStatusStrategy() {
        return new VeryLowMemCellStatusStrategy();
    }

    private String computeReportCategoryLink(final String reportCategory) {
        String link = null;

        if (reportCategory != null) {
            link = "rtc/category.jsp?category=" + Util.encode(reportCategory);
        }
        return link;
    }

    /*
     * This creates a relative url to the node list page and sets the category
     * parameters to show the categories for this cell.
     * FIXME: this code should move to the jsp after the status table is
     * enhanced to support this requirement.
     */
    private String createNodePageUrl(final SurveillanceView view, final int colIndex, final int rowIndex) {
    	Set<OnmsCategory> columns = Collections.emptySet();
    	Set<OnmsCategory> rows    = Collections.emptySet();

    	try {
    		columns = view.getCategoriesForColumn(colIndex); 
    	} catch (final ObjectRetrievalFailureException e) {
    		LOG.warn("An error occurred while getting categories for view {}, column {}", view, colIndex);
    	}

    	try {
    		rows = view.getCategoriesForRow(rowIndex);
    	} catch (final ObjectRetrievalFailureException e) {
    		LOG.warn("An error occurred while getting categories for view {}, row {}", view, rowIndex);
    	}

    	final List<String> params = new ArrayList<String>(columns.size() + rows.size());
        for (final OnmsCategory category : columns) {
            params.add("category1=" + Util.encode(category.getName()));
        }
        for (final OnmsCategory category : rows) {
            params.add("category2=" + Util.encode(category.getName()));
        }
        params.add("nodesWithDownAggregateStatus=true");
        return "element/nodeList.htm?" + StringUtils.collectionToDelimitedString(params, "&");
    }

    /**
     * <p>getNodeDao</p>
     *
     * @return a {@link org.opennms.netmgt.dao.api.NodeDao} object.
     */
    public NodeDao getNodeDao() {
        return m_nodeDao;
    }

    /**
     * <p>setNodeDao</p>
     *
     * @param nodeDao a {@link org.opennms.netmgt.dao.api.NodeDao} object.
     */
    public void setNodeDao(final NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }

    /**
     * <p>getCategoryDao</p>
     *
     * @return a {@link org.opennms.netmgt.dao.api.CategoryDao} object.
     */
    public CategoryDao getCategoryDao() {
        return m_categoryDao;
    }

    /**
     * <p>setCategoryDao</p>
     *
     * @param categoryDao a {@link org.opennms.netmgt.dao.api.CategoryDao} object.
     */
    public void setCategoryDao(final CategoryDao categoryDao) {
        m_categoryDao = categoryDao;
    }

    /**
     * <p>getSurveillanceConfigDao</p>
     *
     * @return a {@link org.opennms.netmgt.dao.api.SurveillanceViewConfigDao} object.
     */
    public SurveillanceViewConfigDao getSurveillanceConfigDao() {
        return m_surveillanceConfigDao;
    }

    /**
     * <p>setSurveillanceConfigDao</p>
     *
     * @param surveillanceConfigDao a {@link org.opennms.netmgt.dao.api.SurveillanceViewConfigDao} object.
     */
    public void setSurveillanceConfigDao(final SurveillanceViewConfigDao surveillanceConfigDao) {
        m_surveillanceConfigDao = surveillanceConfigDao;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getHeaderRefreshSeconds(final String viewName) {
        return m_surveillanceConfigDao.getView(viewName == null ? m_surveillanceConfigDao.getDefaultView().getName() : viewName).getRefreshSeconds();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isViewName(final String viewName) {
        View view = ( viewName == null ? m_surveillanceConfigDao.getDefaultView() : m_surveillanceConfigDao.getView(viewName) );
        return (view == null) ? false : true;
    }

    /**
     * <p>getViewNames</p>
     *
     * @return a {@link java.util.List} object.
     */
    @Override
    public List<String> getViewNames() {
    	final List<String> viewNames = new ArrayList<String>(m_surveillanceConfigDao.getViews().size());
        for (final View view : m_surveillanceConfigDao.getViews()) {
            viewNames.add(view.getName());
        }
        Collections.sort(viewNames);
        return viewNames;
    }

}
