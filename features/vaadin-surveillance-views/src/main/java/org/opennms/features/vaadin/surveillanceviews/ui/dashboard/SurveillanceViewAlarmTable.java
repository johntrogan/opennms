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
package org.opennms.features.vaadin.surveillanceviews.ui.dashboard;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opennms.features.topology.api.support.InfoWindow;
import org.opennms.features.vaadin.surveillanceviews.service.SurveillanceViewService;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Button;
import com.vaadin.v7.data.util.BeanItemContainer;
import com.vaadin.v7.ui.HorizontalLayout;
import com.vaadin.v7.ui.Table;
import com.vaadin.v7.ui.themes.BaseTheme;

/**
 * This class represents a table displaying the OpenNMS alarms for given row/column categories.
 *
 * @author Christian Pape
 */
public class SurveillanceViewAlarmTable extends SurveillanceViewDetailTable {
    /**
     * Helper class for alarm entries.
     */
    public class Alarm {
        /**
         * the alarm's id
         */
        private int id;
        /**
         * this node's id
         */
        private int nodeId;
        /**
         * alarm severity id
         */
        private int severityId;
        /**
         * alarm severity
         */
        private String severity;
        /**
         * label of the node
         */
        private String nodeLabel;
        /**
         * log message
         */
        private String logMsg;
        /**
         * the counter value
         */
        private int counter;
        /**
         * first event date
         */
        private Date firstEventTime;
        /**
         * last event date
         */
        private Date lastEventTime;
        /**
         * the UEI
         */
        private String uei;

        /**
         * Constructor for instantiating new alarm instances.
         *
         * @param id             the alarm id
         * @param severity       the severity of the alarm
         * @param nodeLabel      the label of the node
         * @param nodeId         the node id
         * @param logMsg         the log message
         * @param counter        the counter value
         * @param firstEventTime the first event date
         * @param lastEventTime  the last event date
         */
        public Alarm(int id, String uei, int severityId, String severity, String nodeLabel, int nodeId, String logMsg, int counter, Date firstEventTime, Date lastEventTime) {
            this.id = id;
            this.uei = uei;
            this.severityId = severityId;
            this.severity = severity;
            this.nodeLabel = nodeLabel;
            this.nodeId = nodeId;
            this.logMsg = logMsg;
            this.counter = counter;
            this.firstEventTime = firstEventTime;
            this.lastEventTime = lastEventTime;
        }

        public String getSeverity() {
            return severity;
        }

        public int getSeverityId() {
            return severityId;
        }

        public int getNodeId() {
            return nodeId;
        }

        public int getId() {
            return id;
        }

        public String getUei() {
            return uei;
        }

        public String getNodeLabel() {
            return nodeLabel;
        }

        public String getLogMsg() {
            return logMsg;
        }

        public int getCounter() {
            return counter;
        }

        public Date getFirstEventTime() {
            return firstEventTime;
        }

        public Date getLastEventTime() {
            return lastEventTime;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Alarm alarm = (Alarm) o;

            if (counter != alarm.counter) {
                return false;
            }
            if (id != alarm.id) {
                return false;
            }
            if (nodeId != alarm.nodeId) {
                return false;
            }
            if (firstEventTime != null ? !firstEventTime.equals(alarm.firstEventTime) : alarm.firstEventTime != null) {
                return false;
            }
            if (lastEventTime != null ? !lastEventTime.equals(alarm.lastEventTime) : alarm.lastEventTime != null) {
                return false;
            }
            if (logMsg != null ? !logMsg.equals(alarm.logMsg) : alarm.logMsg != null) {
                return false;
            }
            if (nodeLabel != null ? !nodeLabel.equals(alarm.nodeLabel) : alarm.nodeLabel != null) {
                return false;
            }
            if (severity != null ? !severity.equals(alarm.severity) : alarm.severity != null) {
                return false;
            }

            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + nodeId;
            result = 31 * result + (severity != null ? severity.hashCode() : 0);
            result = 31 * result + (nodeLabel != null ? nodeLabel.hashCode() : 0);
            result = 31 * result + (logMsg != null ? logMsg.hashCode() : 0);
            result = 31 * result + counter;
            result = 31 * result + (firstEventTime != null ? firstEventTime.hashCode() : 0);
            result = 31 * result + (lastEventTime != null ? lastEventTime.hashCode() : 0);
            return result;
        }
    }

    /**
     * the logger instance
     */
    private static final Logger LOG = LoggerFactory.getLogger(SurveillanceViewAlarmTable.class);
    /**
     * the bean container storing the alarm instances
     */
    private BeanItemContainer<Alarm> m_beanItemContainer = new BeanItemContainer<>(Alarm.class);
    /**
     * the refresh future
     */
    protected ListenableFuture<List<Alarm>> m_future;

    /**
     * Constructor for instantiating this component.
     *
     * @param surveillanceViewService the surveillance view service to be used
     * @param enabled                 the flag should links be enabled?
     */
    public SurveillanceViewAlarmTable(SurveillanceViewService surveillanceViewService, boolean enabled) {
        /**
         * calling the super constructor
         */
        super("Alarms", surveillanceViewService, enabled);

        /**
         * set the datasource
         */
        setContainerDataSource(m_beanItemContainer);

        /**
         * the base stylename
         */
        addStyleName("surveillance-view");

        /**
         * add node column
         */
        addGeneratedColumn("nodeLabel", new ColumnGenerator() {
            @Override
            public Object generateCell(final Table table, final Object itemId, final Object propertyId) {
                final Alarm alarm = (Alarm) itemId;

                Button icon = getClickableIcon(FontAwesome.EXCLAMATION_TRIANGLE, new Button.ClickListener() {
                    @Override
                    public void buttonClick(Button.ClickEvent clickEvent) {
                        final URI currentLocation = getUI().getPage().getLocation();
                        final String contextRoot = VaadinServlet.getCurrent().getServletContext().getContextPath();
                        final String redirectFragment = contextRoot + "/alarm/detail.htm?quiet=true&id=" + alarm.getId();

                        LOG.debug("alarm {} clicked, current location = {}, uri = {}", alarm.getId(), currentLocation, redirectFragment);

                        try {
                            SurveillanceViewAlarmTable.this.getUI().addWindow(new InfoWindow(new URL(currentLocation.toURL(), redirectFragment), new InfoWindow.LabelCreator() {
                                @Override
                                public String getLabel() {
                                    return "Alarm Info " + alarm.getId();
                                }
                            }));
                        } catch (MalformedURLException e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                });

                Button button = new Button(alarm.getNodeLabel());
                button.setPrimaryStyleName(BaseTheme.BUTTON_LINK);
                button.setEnabled(m_enabled);

                button.addClickListener(new Button.ClickListener() {
                    @Override
                    public void buttonClick(Button.ClickEvent clickEvent) {
                        final URI currentLocation = getUI().getPage().getLocation();
                        final String contextRoot = VaadinServlet.getCurrent().getServletContext().getContextPath();
                        final String redirectFragment = contextRoot + "/element/node.jsp?quiet=true&node=" + alarm.getNodeId();

                        LOG.debug("node {} clicked, current location = {}, uri = {}", alarm.getNodeId(), currentLocation, redirectFragment);

                        try {
                            SurveillanceViewAlarmTable.this.getUI().addWindow(new InfoWindow(new URL(currentLocation.toURL(), redirectFragment), new InfoWindow.LabelCreator() {
                                @Override
                                public String getLabel() {
                                    return "Node Info " + alarm.getNodeId();
                                }
                            }));
                        } catch (MalformedURLException e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                });

                HorizontalLayout horizontalLayout = new HorizontalLayout();

                horizontalLayout.addComponent(icon);
                horizontalLayout.addComponent(button);

                horizontalLayout.setSpacing(true);

                return horizontalLayout;
            }
        });

        /**
         * add severity column
         */
        addGeneratedColumn("severity", new ColumnGenerator() {
            @Override
            public Object generateCell(Table table, Object itemId, Object propertyId) {
                Alarm alarm = (Alarm) itemId;
                return getImageSeverityLayout(alarm.getSeverity());
            }
        });

        /**
         * set a cell style generator that handles the severity column
         */
        setCellStyleGenerator(new CellStyleGenerator() {
            @Override
            public String getStyle(final Table source, final Object itemId, final Object propertyId) {
                Alarm alarm = ((Alarm) itemId);

                String style = alarm.getSeverity().toLowerCase();

                if ("severity".equals(propertyId)) {
                    style += "-image";
                }

                return style;
            }
        });

        /**
         * set column headers
         */
        setColumnHeader("nodeLabel", "Node");
        setColumnHeader("severity", "Severity");
        setColumnHeader("uei", "UEI");
        setColumnHeader("counter", "Count");
        setColumnHeader("lastEventTime", "Last Time");
        setColumnHeader("logMsg", "Log Msg");

        setColumnExpandRatio("nodeLabel", 2.25f);
        setColumnExpandRatio("severity", 1.0f);
        setColumnExpandRatio("uei", 3.0f);
        setColumnExpandRatio("counter", 0.5f);
        setColumnExpandRatio("lastEventTime", 1.5f);
        setColumnExpandRatio("logMsg", 4.0f);

        /**
         * set visible columns
         */
        setVisibleColumns("nodeLabel", "severity", "uei", "counter", "lastEventTime", "logMsg");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshDetails(final Set<OnmsCategory> rowCategories, final Set<OnmsCategory> colCategories) {
        if (m_future != null && !m_future.isDone()) {
            return;
        }

        m_future = getSurveillanceViewService().getExecutorService().submit(new Callable<List<Alarm>>() {
            @Override
            public List<Alarm> call() throws Exception {
                /**
                 * retrieve the matching alarms
                 */
                List<OnmsAlarm> onmsAlarms = getSurveillanceViewService().getAlarmsForCategories(rowCategories, colCategories);

                List<Alarm> alarms = new ArrayList<>();

                Map<Integer, OnmsNode> nodeMap = new HashMap<>();

                for (OnmsAlarm onmsAlarm : onmsAlarms) {
                    if (!nodeMap.containsKey(onmsAlarm.getNodeId())) {
                        nodeMap.put(onmsAlarm.getNodeId(), getSurveillanceViewService().getNodeForId(onmsAlarm.getNodeId()));
                    }

                    alarms.add(new Alarm(onmsAlarm.getId(), onmsAlarm.getUei(), onmsAlarm.getSeverityId(), onmsAlarm.getSeverity().getLabel(), nodeMap.get(onmsAlarm.getNodeId()).getLabel(), onmsAlarm.getNodeId(), onmsAlarm.getLogMsg(), onmsAlarm.getCounter(), onmsAlarm.getFirstEventTime(), onmsAlarm.getLastEventTime()));
                }
                return alarms;
            }
        });

        m_future.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Alarm> alarms = m_future.get();
                    getUI().access(new Runnable() {
                        @Override
                        public void run() {
                            /**
                             * empty the container
                             */
                            m_beanItemContainer.removeAllItems();

                            /**
                             * add items to container
                             */
                            if (alarms != null && !alarms.isEmpty()) {
                                for (Alarm alarm : alarms) {
                                    m_beanItemContainer.addItem(alarm);
                                }
                            }
                            /**
                             * sort the alarms
                             */
                            sort(new Object[]{"severityId", "lastEventTime"}, new boolean[]{false, false});

                            /**
                             * refresh the table
                             */
                            refreshRowCache();
                        }
                    });
                } catch (InterruptedException e) {
                    LOG.error("Interrupted", e);
                } catch (ExecutionException e) {
                    LOG.error("Exception in task", e.getCause());
                }
            }
        }, MoreExecutors.directExecutor());
    }
}
