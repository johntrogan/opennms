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
package org.opennms.web.rest.v2;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.core.test.rest.AbstractSpringJerseyRestTestCase;
import org.opennms.netmgt.dao.DatabasePopulator;
import org.opennms.netmgt.model.NetworkBuilder;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * TODO
 * 1. Need to figure it out how to create a Mock for EventProxy to validate events sent by RESTful service
 */
@RunWith(OpenNMSJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-mockConfigManager.xml",
        "classpath*:/META-INF/opennms/component-service.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-databasePopulator.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-svclayer.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-cxf-common.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
public class NodeRestServiceIT extends AbstractSpringJerseyRestTestCase {
    private static final Logger LOG = LoggerFactory.getLogger(NodeRestServiceIT.class);

    public NodeRestServiceIT() {
        super(CXF_REST_V2_CONTEXT_PATH);
    }
    @Autowired
    private DatabasePopulator m_databasePopulator;
    @Override
    protected void afterServletStart() throws Exception {
        MockLogAppender.setupLogging(true, "DEBUG");
    }

    @Test
    @JUnitTemporaryDatabase
    public void testFiqlSearch() throws Exception {
        // Add 5 nodes
        for (int i = 0; i < 5; i++) {
            createNode(201);
        }

        String url = "/nodes";

        LOG.warn(sendRequest(GET, url, parseParamData("limit=2&offset=2&_s=node.label==*Test*"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=node.label==*1"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=node.label==*2"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=assetRecord.id==2"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=node.label==*2;assetRecord.id==2"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=(node.label==*2;assetRecord.id==2),(node.label==*1)"), 200));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=ipInterface.ipAddress==10.10.10.10"), 204));
        LOG.warn(sendRequest(GET, url, parseParamData("_s=ipInterface.snmpPrimary==P"), 204));
        LOG.warn(sendRequest(GET, url + "/1/ipinterfaces", Collections.emptyMap(), 204));
        LOG.warn(sendRequest(GET, url + "/1/ipinterfaces", parseParamData("_s=snmpPrimary==P"), 204));

        // Use "Hello, Handsome" as a value to test CXF 'search.decode.values' property which will
        // URL-decode FIQL search values
        LOG.warn(sendRequest(GET, url, parseParamData("_s=node.label==Hello%252C+Handsome"), 204));

        // Put all of the FIQL reserved characters into a string which should equal:
        // !$'()+,;=
        LOG.warn(sendRequest(GET, url, parseParamData("_s=node.label==%2521%2524%2527%2528%2529%252B%252C%253B%253D"), 204));
    }

    @Test
    @JUnitTemporaryDatabase
    public void testAllEndPoints() throws Exception {
        String node = "<node type=\"A\" label=\"TestMachine1\" foreignSource=\"JUnit\" foreignId=\"TestMachine1\">" +
                "<location>Default</location>" +
                "<labelSource>H</labelSource>" +
                "<sysContact>The Owner</sysContact>" +
                "<sysDescription>" +
                "Darwin TestMachine 9.4.0 Darwin Kernel Version 9.4.0: Mon Jun  9 19:30:53 PDT 2008; root:xnu-1228.5.20~1/RELEASE_I386 i386" +
                "</sysDescription>" +
                "<sysLocation>DevJam</sysLocation>" +
                "<sysName>TestMachine1</sysName>" +
                "<sysObjectId>.1.3.6.1.4.1.8072.3.2.255</sysObjectId>" +
                "</node>";
        sendPost("/nodes", node, 201);
        LOG.warn(sendRequest(GET, "/nodes", 200));
        LOG.warn(sendRequest(GET, "/nodes/1", 200)); // By ID
        LOG.warn(sendRequest(GET, "/nodes/JUnit:TestMachine1", 200)); // By foreignSource/foreignId combination

        String ipInterface = "<ipInterface snmpPrimary=\"P\">" +
                "<ipAddress>10.10.10.10</ipAddress>" +
                "<hostName>TestMachine</hostName>" +
                "</ipInterface>";
        sendPost("/nodes/1/ipinterfaces", ipInterface, 201);
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10", 200)); // By IP Address

        String service = "<service status=\"A\">" +
                "<serviceType>" +
                "<name>ICMP</name>" +
                "</serviceType>" +
                "</service>";
        sendPost("/nodes/1/ipinterfaces/10.10.10.10/services", service, 201);
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10/services", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10/services/ICMP", 200)); // By Name

        String snmpInterface = "<snmpInterface ifIndex=\"6\">" +
                "<ifAdminStatus>1</ifAdminStatus>" +
                "<ifDescr>en1</ifDescr>" +
                "<ifName>en1</ifName>" +
                "<ifOperStatus>1</ifOperStatus>" +
                "<ifSpeed>10000000</ifSpeed>" +
                "<ifType>6</ifType>" +
                "<physAddr>001e5271136d</physAddr>" +
                "</snmpInterface>";
        sendPost("/nodes/1/snmpinterfaces", snmpInterface, 201);
        LOG.warn(sendRequest(GET, "/nodes/1/snmpinterfaces", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/snmpinterfaces/6", 200)); // By ifIndex

        LOG.warn(sendRequest(GET, "/nodes/1/hardwareInventory", 404));
        byte[] encoded = Files.readAllBytes(Paths.get("src/test/resources/hardware-inventory.xml"));
        String entity = new String(encoded, StandardCharsets.UTF_8);
        sendPost("/nodes/1/hardwareInventory", entity, 204, null);
        String xml = sendRequest(GET, "/nodes/1/hardwareInventory", 200);
        assertTrue(xml, xml.contains("Cisco 7206VXR, 6-slot chassis"));

        String category = "<category name=\"Production\"/>";
        sendPost("/nodes/1/categories", category, 201);
        LOG.warn(sendRequest(GET, "/nodes/1/categories", 200));

        // UPDATE

        LOG.warn(sendRequest(PUT, "/nodes/1", parseParamData("sysLocation=USA"), 204));
        LOG.warn(sendRequest(PUT, "/nodes/1/ipinterfaces/10.10.10.10/services/ICMP", parseParamData("status=F"), 204));

        // DELETE

        LOG.warn(sendRequest(DELETE, "/nodes/1/snmpinterfaces/6", 204));
        LOG.warn(sendRequest(GET, "/nodes/1/snmpinterfaces/6", 404));
        LOG.warn(sendRequest(DELETE, "/nodes/1/ipinterfaces/10.10.10.10/services/ICMP", 204));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10/services/ICMP", 404));
        LOG.warn(sendRequest(DELETE, "/nodes/1/ipinterfaces/10.10.10.10", 204));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10", 404));
        LOG.warn(sendRequest(DELETE, "/nodes/1", 204));
        LOG.warn(sendRequest(GET, "/nodes/1", 404));
    }

    @Test
    @JUnitTemporaryDatabase
    public void testAllEndPointsWithJSON() throws Exception {
        JSONObject node = new JSONObject();
        node.put("type", "A");
        node.put("label", "TestMachine1");
        node.put("foreignSource", "JUnit");
        node.put("foreignId", "TestMachine1");
        node.put("location", "Default");
        node.put("labelSource", "H");
        node.put("sysContact", "The Owner");
        node.put("sysDescription", "Darwin TestMachine 9.4.0 Darwin Kernel Version 9.4.0: Mon Jun  9 19:30:53 PDT 2008; root:xnu-1228.5.20~1/RELEASE_I386 i386");
        node.put("sysLocation", "Earth");
        node.put("sysName", "TestMachine1");
        node.put("sysObjectId", ".1.3.6.1.4.1.8072.3.2.255");

        sendData(POST, MediaType.APPLICATION_JSON, "/nodes", node.toString(), 201);
        LOG.warn(sendRequest(GET, "/nodes", 200));
        LOG.warn(sendRequest(GET, "/nodes/1", 200)); // By ID
        LOG.warn(sendRequest(GET, "/nodes/JUnit:TestMachine1", 200)); // By foreignSource/foreignId combination

        JSONObject ipInterface = new JSONObject();
        ipInterface.put("snmpPrimary", "P");
        ipInterface.put("ipAddress", "10.10.10.10");
        ipInterface.put("hostName", "TestMachine");

        sendData(POST, MediaType.APPLICATION_JSON, "/nodes/1/ipinterfaces", ipInterface.toString(), 201);
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10", 200)); // By IP Address

        JSONObject serviceType = new JSONObject();
        serviceType.put("name", "ICMP");
        JSONObject service = new JSONObject();
        service.put("status", "A");
        service.put("serviceType", serviceType);
        sendData(POST, MediaType.APPLICATION_JSON, "/nodes/1/ipinterfaces/10.10.10.10/services", service.toString(), 201);
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10/services", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/ipinterfaces/10.10.10.10/services/ICMP", 200)); // By Name

        JSONObject snmpInterface = new JSONObject();
        snmpInterface.put("ifIndex", 6);
        snmpInterface.put("ifAdminStatus", 1);
        snmpInterface.put("ifOperStatus", 1);
        snmpInterface.put("ifDescr", "en1");
        snmpInterface.put("ifName", "en1");
        snmpInterface.put("ifSpeed", 10000000);
        snmpInterface.put("ifType", 6);
        snmpInterface.put("physAddr", "001e5271136d");

        sendData(POST, MediaType.APPLICATION_JSON, "/nodes/1/snmpinterfaces", snmpInterface.toString(), 201);
        LOG.warn(sendRequest(GET, "/nodes/1/snmpinterfaces", 200));
        LOG.warn(sendRequest(GET, "/nodes/1/snmpinterfaces/6", 200)); // By ifIndex

        JSONObject category = new JSONObject();
        category.put("name", "Production");
        sendData(POST, MediaType.APPLICATION_JSON, "/nodes/1/categories", category.toString(), 201);
        LOG.warn(sendRequest(GET, "/nodes/1/categories", 200));
    }
    @Test
    public void createNodeWithParent() throws Exception{

        final NetworkBuilder builder = new NetworkBuilder();
        builder.addNode("Parent").setForeignSource("JUnit").setForeignId("Parent").setType(OnmsNode.NodeType.ACTIVE);
        final OnmsNode parent = builder.getCurrentNode();
        m_databasePopulator.getNodeDao().save(parent);

        builder.addNode("Child").setForeignSource("Junit").setForeignId("Child").setType(OnmsNode.NodeType.ACTIVE)
                .setParent(parent).setNodeParentId(parent.getId());
        final OnmsNode child = builder.getCurrentNode();
        m_databasePopulator.getNodeDao().save(child);
        m_databasePopulator.getNodeDao().flush();

        Assert.assertNotNull(child.getId());
        Assert.assertNotNull(parent.getId());
        sendRequest(GET, "/nodes/"+parent.getId(), 200);
        final String response = sendRequest(GET, "/nodes/"+child.getId(), 200);

        final JSONObject object = new JSONObject(response);
        Assert.assertEquals(Optional.ofNullable(parent.getId()), Optional.of(object.getInt("nodeParentID")));
    }
}
