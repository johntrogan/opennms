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
package org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.flows;

import java.util.Optional;

import org.bson.BsonBinary;
import org.bson.BsonWriter;
import org.opennms.netmgt.telemetry.listeners.utils.BufferUtils;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SampleDatagramEnrichment;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.InvalidPacketException;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SampleDatagramVisitor;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.Opaque;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.headers.EthernetHeader;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.headers.Inet4Header;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.proto.headers.Inet6Header;

import com.google.common.base.MoreObjects;

import io.netty.buffer.ByteBuf;

// struct sampled_header {
//    header_protocol protocol;       /* Format of sampled header */
//    unsigned int frame_length;      /* Original length of packet before
//                                       sampling.
//                                       Note: For a layer 2 header_protocol,
//                                             length is total number of octets
//                                             of data received on the network 
//                                             (excluding framing bits but
//                                             including FCS octets).
//                                             Hardware limitations may
//                                             prevent an exact reporting
//                                             of the underlying frame length,
//                                             but an agent should attempt to
//                                             be as accurate as possible. Any
//                                             octets added to the frame_length
//                                             to compensate for encapsulations
//                                             removed by the underlying hardware
//                                             must also be added to the stripped
//                                             count. */
//    unsigned int stripped;          /* The number of octets removed from
//                                       the packet before extracting the
//                                       header<> octets. Trailing encapsulation
//                                       data corresponding to any leading
//                                       encapsulations that were stripped must
//                                       also be stripped. Trailing encapsulation
//                                       data for the outermost protocol layer
//                                       included in the sampled header must be
//                                       stripped.
// 
//                                       In the case of a non-encapsulated 802.3
//                                       packet stripped >= 4 since VLAN tag
//                                       information might have been stripped off
//                                       in addition to the FCS.
// 
//                                       Outer encapsulations that are ambiguous,
//                                       or not one of the standard header_protocol
//                                       must be stripped. */
//    opaque header<>;                /* Header bytes */
// };

public class SampledHeader implements FlowData {
    public final HeaderProtocol protocol;
    public final long frame_length;
    public final long stripped;

    public final EthernetHeader ethernetHeader;
    public final Inet4Header inet4Header;
    public final Inet6Header inet6Header;

    public final byte[] rawHeader;

    public SampledHeader(final ByteBuf buffer) throws InvalidPacketException {
        this.protocol = HeaderProtocol.from(buffer);
        this.frame_length = BufferUtils.uint32(buffer);
        this.stripped = BufferUtils.uint32(buffer);

        switch (this.protocol) {
            case ETHERNET_ISO88023:
                this.ethernetHeader = new Opaque<>(buffer, Optional.empty(), EthernetHeader::new).value;
                this.inet4Header = this.ethernetHeader.inet4Header;
                this.inet6Header = this.ethernetHeader.inet6Header;
                this.rawHeader = this.ethernetHeader.rawHeader;
                break;

            case IPv4:
                this.ethernetHeader = null;
                this.inet4Header = new Opaque<>(buffer, Optional.empty(), Inet4Header::new).value;
                this.inet6Header = null;
                this.rawHeader = null;
                break;

            case IPv6:
                this.ethernetHeader = null;
                this.inet4Header = null;
                this.inet6Header = new Opaque<>(buffer, Optional.empty(), Inet6Header::new).value;
                this.rawHeader = null;
                break;

            default:
                this.ethernetHeader = null;
                this.inet4Header = null;
                this.inet6Header = null;
                this.rawHeader = new Opaque<>(buffer, Optional.empty(), Opaque::parseBytes).value;
        }
    }

    public SampledHeader(final HeaderProtocol protocol, final long frame_length, final long stripped, final EthernetHeader ethernetHeader, final Inet4Header inet4Header, final Inet6Header inet6Header, final byte[] rawHeader) {
        this.protocol = protocol;
        this.frame_length = frame_length;
        this.stripped = stripped;
        this.ethernetHeader = ethernetHeader;
        this.inet4Header = inet4Header;
        this.inet6Header = inet6Header;
        this.rawHeader = rawHeader;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("protocol", this.protocol)
                .add("frame_length", this.frame_length)
                .add("stripped", this.stripped)
                .add("header", this.rawHeader)
                .toString();
    }

    @Override
    public void writeBson(final BsonWriter bsonWriter, final SampleDatagramEnrichment enr) {
        bsonWriter.writeStartDocument();
        bsonWriter.writeName("protocol");
        this.protocol.writeBson(bsonWriter);
        bsonWriter.writeInt64("frame_length", this.frame_length);
        bsonWriter.writeInt64("stripped", this.stripped);

        if (this.ethernetHeader != null) {
            bsonWriter.writeName("ethernet");
            this.ethernetHeader.writeBson(bsonWriter, enr);
        }

        if (this.inet4Header != null) {
            bsonWriter.writeName("ipv4");
            this.inet4Header.writeBson(bsonWriter, enr);
        }

        if (this.inet6Header != null) {
            bsonWriter.writeName("ipv6");
            this.inet6Header.writeBson(bsonWriter, enr);
        }

        if (this.rawHeader != null) {
            bsonWriter.writeBinaryData("raw", new BsonBinary(this.rawHeader));
        }

        bsonWriter.writeEndDocument();
    }

    @Override
    public void visit(SampleDatagramVisitor visitor) {
        visitor.accept(this);
        if (this.inet4Header != null) {
            inet4Header.visit(visitor);
        }
        if (this.inet6Header != null) {
            inet6Header.visit(visitor);
        }
    }
}
