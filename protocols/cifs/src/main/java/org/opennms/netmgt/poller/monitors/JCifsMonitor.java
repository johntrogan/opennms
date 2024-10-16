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
package org.opennms.netmgt.poller.monitors;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Properties;

import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.netmgt.poller.monitors.support.ParameterSubstitutingMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Configuration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFilenameFilter;

/**
 * This class is designed to be used by the service poller framework to test the availability
 * of the existence of files or directories on remote interfaces via CIFS. The class implements
 * the ServiceMonitor interface that allows it to be used along with other plug-ins by the service
 * poller framework.
 *
 * @author <a mailto:christian.pape@informatik.hs-fulda.de>Christian Pape</a>
 * @version 1.10.9
 */
public class JCifsMonitor extends ParameterSubstitutingMonitor {

    /*
    * default retries
    */
    private static final int DEFAULT_RETRY = 0;

    /*
     * default timeout
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    private static String modeCandidates;

    static {
        modeCandidates = "";
        for (Mode m : Mode.values()) {
            if (!"".equals(modeCandidates)) {
                modeCandidates += ", ";
            }
            modeCandidates += m;
        }
    }

    /**
     * logging for JCifs monitor
     */
    private final Logger logger = LoggerFactory.getLogger(JCifsMonitor.class);

    /**
     * This method queries the CIFS share.
     *
     * @param svc        the monitored service
     * @param parameters the parameter map
     * @return the poll status for this system
     */
    @Override
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {

        final String domain = resolveKeyedString(parameters, "domain", "");
        final String username = resolveKeyedString(parameters, "username", "");
        final String password = resolveKeyedString(parameters, "password", "");
        String mode = parameters.containsKey("mode") ? ((String) parameters.get("mode")).toUpperCase() : "PATH_EXIST";
        String path = parameters.containsKey("path") ? (String) parameters.get("path") : "";
        String smbHost = parameters.containsKey("smbHost") ? (String) parameters.get("smbHost") : "";
        final String folderIgnoreFiles = parameters.containsKey("folderIgnoreFiles") ? (String) parameters.get("folderIgnoreFiles") : "";

        // changing to Ip address of MonitoredService if no smbHost is given
        if ("".equals(smbHost)) {
            smbHost = svc.getIpAddr();
        }

        // Filename filter to give user the possibility to ignore specific files in folder for the folder check.
        SmbFilenameFilter smbFilenameFilter = new SmbFilenameFilter() {
            @Override
            public boolean accept(SmbFile smbFile, String s) throws SmbException {
                return !s.matches(folderIgnoreFiles);
            }
        };

        // Initialize mode with default as PATH_EXIST
        Mode enumMode = Mode.PATH_EXIST;

        try {
            enumMode = Mode.valueOf(mode);
        } catch (IllegalArgumentException exception) {
            logger.error("Mode '{}‘ does not exists. Valid candidates are {}", mode, modeCandidates);
            return PollStatus.unknown("Mode " + mode + " does not exists. Valid candidates are " + modeCandidates);
        }

        // Checking path parameter
        if (!path.startsWith("/")) {
            path = "/" + path;
            logger.debug("Added leading / to path.");
        }

        // Build authentication string for NtlmPasswordAuthentication: syntax: domain;username:password
        String authString = "";

        // Setting up authenticationString...
        if (domain != null && !"".equals(domain)) {
            authString += domain + ";";
        }

        authString += username + ":" + password;

        // ... and path
        String fullUrl = "smb://" + smbHost + path;

        logger.debug("Domain: [{}], Username: [{}], Password: [{}], Mode: [{}], Path: [{}], Authentication: [{}], Full Url: [{}]", new Object[]{domain, username, password, mode, path, authString, fullUrl});

        // Initializing TimeoutTracker with default values
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        BaseContext baseCtx = null;
        Properties jcifsProps = new Properties();
        String connectionTimeoutAsStr = Integer.toString(tracker.getConnectionTimeout());
        jcifsProps.setProperty("jcifs.smb.client.soTimeout", connectionTimeoutAsStr);
        jcifsProps.setProperty("jcifs.smb.client.connTimeout", connectionTimeoutAsStr);
        jcifsProps.setProperty("jcifs.smb.client.responseTimeout", connectionTimeoutAsStr);
        jcifsProps.setProperty("jcifs.smb.client.sessionTimeout", connectionTimeoutAsStr);
        Configuration jcifsConfig = null;
        try {
            jcifsConfig = new PropertyConfiguration(jcifsProps);
            baseCtx = new BaseContext(jcifsConfig);
        } catch (CIFSException cifse) {
            logger.warn("Unable to configure CIFS timeout properties due to {}. Using defaults.", cifse.getMessage());
            baseCtx = SingletonContext.getInstance();
        }
        CIFSContext authedCtx = baseCtx.withCredentials(new NtlmPasswordAuthenticator(domain, username, password));

        // Setting default PollStatus
        PollStatus serviceStatus = PollStatus.unknown("unknown CIFS failure");

        try {
            for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
                SmbFile smbFile = null;

                try {
                    // Creating SmbFile object
                    smbFile = new SmbFile(fullUrl, authedCtx);
                    // Setting the defined timeout
                    smbFile.setConnectTimeout(tracker.getConnectionTimeout());
                    // Does the file exists?
                    boolean smbFileExists = smbFile.exists();

                    switch (enumMode) {
                        case PATH_EXIST:
                            if (smbFileExists) {
                                serviceStatus = PollStatus.up();
                            } else {
                                serviceStatus = PollStatus.down("File " + fullUrl + " should exists but doesn't!");
                            }
                            break;
                        case PATH_NOT_EXIST:
                            if (!smbFileExists) {
                                serviceStatus = PollStatus.up();
                            } else {
                                serviceStatus = PollStatus.down("File " + fullUrl + " should not exists but does!");
                            }
                            break;
                        case FOLDER_EMPTY:
                            if (smbFileExists) {
                                if (smbFile.list(smbFilenameFilter).length == 0) {
                                    serviceStatus = PollStatus.up();
                                } else {
                                    serviceStatus = PollStatus.down("Directory " + fullUrl + " should be empty but isn't!");
                                }
                            } else {
                                serviceStatus = PollStatus.down("Directory " + fullUrl + " should exists but doesn't!");
                            }
                            break;
                        case FOLDER_NOT_EMPTY:
                            if (smbFileExists) {
                                if (smbFile.list(smbFilenameFilter).length > 0) {
                                    serviceStatus = PollStatus.up();
                                } else {
                                    serviceStatus = PollStatus.down("Directory " + fullUrl + " should not be empty but is!");
                                }
                            } else {
                                serviceStatus = PollStatus.down("Directory " + fullUrl + " should exists but doesn't!");
                            }
                            break;
                        default:
                            logger.warn("There is no implementation for the specified mode '{}'", mode);
                            break;
                    }

                } catch (final MalformedURLException exception) {
                    logger.error("Malformed URL on '{}' with error: '{}'", smbHost, exception.getMessage());
                    serviceStatus = PollStatus.down(exception.getMessage());
                } catch (final SmbException exception) {
                    logger.error("SMB error on '{}' with error: '{}'", smbHost, exception.getMessage());
                    serviceStatus = PollStatus.down("Failed to query to " + smbHost + ": " + exception.getMessage());
                } finally {
                    if (smbFile != null) {
                        try {
                            smbFile.close();
                        } catch (final Exception e) {
                            LOG.warn("Unable to close {}", fullUrl, e);
                        }
                    }
                }
            }
        } finally {
            closeContext(authedCtx);
            closeContext(baseCtx);
        }

        return serviceStatus;
    }

    private void closeContext(final CIFSContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (final Exception e) {
                LOG.warn("Failed to close CIFS context", e);
            }
        }
    }

    /**
     * Supported modes for CIFS monitor
     */
    private enum Mode {
        PATH_EXIST,
        PATH_NOT_EXIST,
        FOLDER_EMPTY,
        FOLDER_NOT_EMPTY
    }
}
