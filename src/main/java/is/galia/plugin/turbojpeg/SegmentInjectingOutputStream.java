/*
 * Copyright © 2024 Baird Creek Software LLC
 *
 * Licensed under the PolyForm Noncommercial License, version 1.0.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     https://polyformproject.org/licenses/noncommercial/1.0.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package is.galia.plugin.turbojpeg;

import is.galia.codec.xmp.XMPUtils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Injects an {@literal APP1} segment into the stream at the appropriate
 * position if there is any metadata to write.
 */
class SegmentInjectingOutputStream extends FilterOutputStream {

    private static final byte[] APP1_MARKER =
            new byte[] { (byte) 0xff, (byte) 0xe1 };

    /**
     * Header immediately following an {@literal APP1} segment marker
     * indicating that the segment contains "StandardXMP" XMP data.
     */
    private static final byte[] STANDARD_XMP_SEGMENT_HEADER =
            "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);

    private byte[] app1;

    SegmentInjectingOutputStream(String xmp, OutputStream os) {
        super(os);
        assembleAPP1Segment(xmp);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (app1 != null) {
            out.write(Arrays.copyOfRange(b, off, 20));
            out.write(app1);
            out.write(Arrays.copyOfRange(b, 20, len));
            app1 = null;
        } else {
            out.write(b, off, len);
        }
    }

    /**
     * Assembles a full {@code APP1} segment including marker and length.
     *
     * @param xmp XMP data with a root {@code <rdf:RDF>} element.
     */
    private void assembleAPP1Segment(String xmp) {
        final byte[] headerBytes = STANDARD_XMP_SEGMENT_HEADER;
        final byte[] xmpBytes = XMPUtils.wrapInXPacket(xmp).
                getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buffer = ByteBuffer.allocate(
                APP1_MARKER.length +          // segment marker length
                        2 +                   // segment length
                        headerBytes.length +  // segment header length
                        xmpBytes.length +     // XMP length
                        1);                   // null terminator
        // write segment marker
        buffer.put(APP1_MARKER);
        // write segment length
        buffer.putShort((short) (headerBytes.length + xmpBytes.length + 3));
        // write segment header
        buffer.put(headerBytes);
        // write XMP data
        buffer.put(xmpBytes);
        // write null terminator
        buffer.put((byte) 0x00);
        app1 = buffer.array();
    }

}
