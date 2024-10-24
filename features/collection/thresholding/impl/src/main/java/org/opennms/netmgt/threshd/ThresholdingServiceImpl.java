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
package org.opennms.netmgt.threshd;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.opennms.core.soa.lookup.ServiceLookup;
import org.opennms.core.soa.lookup.ServiceLookupBuilder;
import org.opennms.core.soa.lookup.ServiceRegistryLookup;
import org.opennms.core.soa.support.DefaultServiceRegistry;
import org.opennms.features.distributed.kvstore.api.BlobStore;
import org.opennms.netmgt.collection.api.ServiceParameters;
import org.opennms.netmgt.config.dao.thresholding.api.ReadableThreshdDao;
import org.opennms.netmgt.config.dao.thresholding.api.ReadableThresholdingDao;
import org.opennms.netmgt.dao.api.ResourceStorageDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.EventListener;
import org.opennms.netmgt.events.api.model.IEvent;
import org.opennms.netmgt.events.api.model.IParm;
import org.opennms.netmgt.threshd.api.ThresholdInitializationException;
import org.opennms.netmgt.threshd.api.ThresholdStateMonitor;
import org.opennms.netmgt.threshd.api.ThresholdingEventProxy;
import org.opennms.netmgt.threshd.api.ThresholdingService;
import org.opennms.netmgt.threshd.api.ThresholdingSession;
import org.opennms.netmgt.threshd.api.ThresholdingSessionKey;
import org.opennms.netmgt.threshd.api.ThresholdingSetPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

/**
 * Thresholding Service.
 */
public class ThresholdingServiceImpl implements ThresholdingService, EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(ThresholdingServiceImpl.class);

    public static final List<String> UEI_LIST =
            Lists.newArrayList(EventConstants.NODE_GAINED_SERVICE_EVENT_UEI,
                               EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI,
                               EventConstants.RELOAD_DAEMON_CONFIG_UEI,
                               EventConstants.THRESHOLDCONFIG_CHANGED_EVENT_UEI);

    private ThresholdingSetPersister thresholdingSetPersister;

    private ThresholdingEventProxy eventProxy;

    @Autowired
    private ResourceStorageDao resourceStorageDao;

    @Autowired
    private EventIpcManager eventIpcManager;
    
    private final AtomicReference<BlobStore> kvStore = new AtomicReference<>();
    
    @Autowired
    private ReadableThreshdDao threshdDao;

    @Autowired
    private ReadableThresholdingDao thresholdingDao;
    
    @Autowired
    private ThresholdStateMonitor thresholdStateMonitor;

    private static final ServiceLookup<Class<?>, String> SERVICE_LOOKUP = new ServiceLookupBuilder(new ServiceRegistryLookup(DefaultServiceRegistry.INSTANCE))
            .blocking()
            .build();

    private final Timer reInitializeTimer = new Timer();

    private boolean isDistributed = false;
    
    // Spring init entry point
    @PostConstruct
    private void init() {
        // When we are on OpenNMS we will have been wired an event manager and can listen for events
        eventIpcManager.addEventListener(this, UEI_LIST);
    }

    // OSGi init entry point
    public void initOsgi() {
        // If we were started viag OSGi then we are on Sentinel therefore we will mark ourselves as being distributed
        // for thresholding
        isDistributed = true;
        
        reInitializeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // On Sentinel we won't have access to an event manager so we will have to manage config updates via
                // timer
                reinitializeOnTimer();
            }
        }, 0, TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    }
    
    private void reinitializeOnTimer() {
        thresholdingSetPersister.reinitializeThresholdingSets();
    }

    @Override
    public String getName() {
        return "ThresholdingService";
    }

    @Override
    public void onEvent(IEvent e) {
        switch (e.getUei()) {
        case EventConstants.NODE_GAINED_SERVICE_EVENT_UEI:
            nodeGainedService(e);
            break;
        case EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI:
            handleNodeCategoryChanged(e);
            break;
        case EventConstants.RELOAD_DAEMON_CONFIG_UEI:
            daemonReload(e);
            break;
        case EventConstants.THRESHOLDCONFIG_CHANGED_EVENT_UEI:
            reinitializeThresholdingSets(e);
            break;
        default:
            LOG.debug("Unexpected Event for Thresholding: {}", e);
            break;
        }
    }

    public void nodeGainedService(IEvent event) {
        LOG.debug(event.toString());
        // Trigger re-evaluation of Threshold Packages, re-evaluating Filters.
        threshdDao.rebuildPackageIpListMap();
        reinitializeThresholdingSets(event);
    }

    public void handleNodeCategoryChanged(IEvent event) {
        LOG.debug(event.toString());
        // Trigger re-evaluation of Threshold Packages, re-evaluating Filters.
        threshdDao.rebuildPackageIpListMap();
        reinitializeThresholdingSets(event);
    }

    @Override
    public ThresholdingSession createSession(int nodeId, String hostAddress, String serviceName, ServiceParameters serviceParams)
            throws ThresholdInitializationException {
        Objects.requireNonNull(serviceParams, "ServiceParameters must not be null");

        synchronized (kvStore) {
            if (kvStore.get() == null) {
                waitForKvStore();
            }
        }
        
        ThresholdingSessionKey sessionKey = new ThresholdingSessionKeyImpl(nodeId, hostAddress, serviceName);
        return new ThresholdingSessionImpl(this, sessionKey, serviceParams,
                                           kvStore.get(), isDistributed, thresholdStateMonitor);
    }

    public ThresholdingVisitorImpl getThresholdingVistor(ThresholdingSession session, Long sequenceNumber) throws ThresholdInitializationException {
        ThresholdingSetImpl thresholdingSet = (ThresholdingSetImpl) thresholdingSetPersister.getThresholdingSet(session, eventProxy);
        return new ThresholdingVisitorImpl(thresholdingSet, eventProxy, sequenceNumber);
    }

    public EventIpcManager getEventIpcManager() {
        return eventIpcManager;
    }

    public void setEventIpcManager(EventIpcManager eventIpcManager) {
        this.eventIpcManager = eventIpcManager;
    }

    @Autowired
    public void setEventProxy(EventForwarder eventForwarder) {
        Objects.requireNonNull(eventForwarder);
        eventProxy = new ThresholdingEventProxyImpl(eventForwarder);
    }

    @Override
    public ThresholdingSetPersister getThresholdingSetPersister() {
        return thresholdingSetPersister;
    }

    public void setThresholdingSetPersister(ThresholdingSetPersister thresholdingSetPersister) {
        this.thresholdingSetPersister = thresholdingSetPersister;
    }

    public void close(ThresholdingSessionImpl session) {
        thresholdingSetPersister.clear(session);
    }

    private void daemonReload(IEvent event) {
        final String thresholdsDaemonName = "Threshd";
        boolean isThresholds = false;
        for (IParm parm : event.getParmCollection()) {
            if (EventConstants.PARM_DAEMON_NAME.equals(parm.getParmName()) && thresholdsDaemonName.equalsIgnoreCase(parm.getValue().getContent())) {
                isThresholds = true;
                break;
            }
        }
        if (isThresholds) {
            try {
                threshdDao.reload();
                thresholdingDao.reload();
                thresholdingSetPersister.reinitializeThresholdingSets();
            } catch (final Exception e) {
                throw new RuntimeException("Unable to reload thresholding.", e);
            }
        }
    }

    private void reinitializeThresholdingSets(IEvent e) {
        thresholdingSetPersister.reinitializeThresholdingSets();
    }

    private void waitForKvStore() {
        BlobStore osgiKvStore = SERVICE_LOOKUP.lookup(BlobStore.class, null);

        if (osgiKvStore == null) {
            throw new RuntimeException("Timed out waiting for a key value store");
        } else {
            kvStore.set(osgiKvStore);
        }
    }

    public void setKvStore(BlobStore keyValueStore) {
        Objects.requireNonNull(keyValueStore);

        synchronized (kvStore) {
            if (kvStore.get() == null) {
                kvStore.set(keyValueStore);
            }
        }
    }

    @VisibleForTesting
    public void setDistributed(boolean distributed) {
        isDistributed = distributed;
    }

    public void setThresholdStateMonitor(ThresholdStateMonitor thresholdStateMonitor) {
        this.thresholdStateMonitor = Objects.requireNonNull(thresholdStateMonitor);
    }
}
