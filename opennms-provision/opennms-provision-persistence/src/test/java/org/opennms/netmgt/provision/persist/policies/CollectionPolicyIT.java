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
package org.opennms.netmgt.provision.persist.policies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opennms.core.utils.InetAddressUtils.addr;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.spring.BeanUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.netmgt.dao.DatabasePopulator;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.provision.BasePolicy;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml"
})
@JUnitConfigurationEnvironment
public class CollectionPolicyIT implements InitializingBean {
    
    @Autowired
    private NodeDao m_nodeDao;
    
    @Autowired
    private SnmpInterfaceDao m_snmpInterfaceDao;

    @Autowired
    private DatabasePopulator m_populator;

    private List<OnmsSnmpInterface> m_interfaces;

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    @Before
    public void setUp() {
        MockLogAppender.setupLogging();
        m_populator.populateDatabase();
        m_interfaces = m_snmpInterfaceDao.findAll();
    }
    
    @After
    public void tearDown() {
        m_populator.resetDatabase();
    }

    @Test
    @Transactional
    public void testMatchingIfDescr() {
        MatchingSnmpInterfacePolicy p = createPolicy();
        p.setIfDescr("~^ATM.*");

        matchPolicy(m_interfaces, p, addr("192.168.1.1"));
    }

    private MatchingSnmpInterfacePolicy createPolicy() {
        MatchingSnmpInterfacePolicy policy = new MatchingSnmpInterfacePolicy();
        policy.setMatchBehavior(BasePolicy.Match.NO_PARAMETERS.toString());
        return policy;
    }

    @Test
    @Transactional
    public void testMatchingIfName() {
        MatchingSnmpInterfacePolicy p = createPolicy();
        p.setIfName("eth0");

        matchPolicy(m_interfaces, p, addr("192.168.1.2"));
    }

    @Test
    @Transactional
    public void testMatchingIfType() {
        MatchingSnmpInterfacePolicy p = createPolicy();
        p.setIfType("6");

        matchPolicy(m_interfaces, p, addr("192.168.1.2"));
    }
    
    @Test
    @Transactional
    public void testCategoryAssignment() {
        final String TEST_CATEGORY = "TestCategory"; 
        NodeCategorySettingPolicy policy = new NodeCategorySettingPolicy();
        policy.setCategory(TEST_CATEGORY);
        policy.setLabel("~n.*2");
        
        OnmsNode node1 = m_nodeDao.get(m_populator.getNode1().getId());
        assertNotNull(node1);
        assertEquals("node1", node1.getLabel());
        
        OnmsNode node2 = m_nodeDao.get(m_populator.getNode2().getId());
        assertNotNull(node2);
        assertEquals("node2", node2.getLabel());
        
        node1 = policy.apply(node1, Collections.emptyMap());
        assertNotNull(node1);
        assertFalse(node1.hasCategory(TEST_CATEGORY));
        
        node2 = policy.apply(node2, Collections.emptyMap());
        assertNotNull(node1);
        assertTrue(node2.getRequisitionedCategories().contains(TEST_CATEGORY));
    }

    private static void matchPolicy(List<OnmsSnmpInterface> interfaces, MatchingSnmpInterfacePolicy p, InetAddress matchingIp) {
        OnmsSnmpInterface o;
        List<OnmsSnmpInterface> populatedInterfaces = new ArrayList<>();
        List<OnmsSnmpInterface> matchedInterfaces = new ArrayList<>();
        
        for (OnmsSnmpInterface iface : interfaces) {
            System.err.println(iface);
            o = p.apply(iface, Collections.emptyMap());
            if (o != null) {
                matchedInterfaces.add(o);
                if (p.getAction().contains("COLLECT")) {
                    assertTrue(o.isCollectionPolicySpecified());
                    assertFalse(o.isCollectionUserSpecified());
                }
            }
            for (OnmsIpInterface ipif : iface.getIpInterfaces()) {
                if (ipif.getIpAddress().equals(matchingIp)) {
                    populatedInterfaces.add(iface);
                }
            }
        }
        
        assertEquals(populatedInterfaces, matchedInterfaces);
    }

}
