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

import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.image.Size;
import is.galia.image.Format;
import is.galia.image.Metadata;
import is.galia.image.Region;
import is.galia.image.ReductionFactor;
import is.galia.plugin.turbojpeg.test.TestUtils;
import is.galia.stream.PathImageInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TurboJPEGDecoderTest {

    private static final boolean SAVE_IMAGES = true;

    private final Arena arena = Arena.ofConfined();
    private TurboJPEGDecoder instance;

    @BeforeAll
    public static void beforeClass() {
        try (TurboJPEGDecoder decoder = new TurboJPEGDecoder()) {
            decoder.onApplicationStart();
        }
    }

    @BeforeEach
    void setUp() {
        instance = new TurboJPEGDecoder();
        instance.setArena(arena);
        instance.initializePlugin();
        instance.setSource(TestUtils.getFixture("rgb.jpg"));
    }

    @AfterEach
    void tearDown() {
        instance.close();
        arena.close();
    }

    //region Plugin methods

    @Test
    void getPluginConfigKeys() {
        Set<String> keys = instance.getPluginConfigKeys();
        assertFalse(keys.isEmpty());
    }

    @Test
    void getPluginName() {
        assertEquals(TurboJPEGDecoder.class.getSimpleName(),
                instance.getPluginName());
    }

    //endregion
    //region Decoder methods

    /* detectFormat() */

    @Test
    void detectFormatWithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.detectFormat());
    }

    @Test
    void detectFormatWithEmptyFile() throws Exception {
        instance.setSource(TestUtils.getFixture("empty"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithIncompatibleImage() throws Exception {
        instance.setSource(TestUtils.getFixture("alpha.png"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithSupportedMagicBytes() throws Exception {
        assertEquals(TurboJPEGDecoder.FORMAT, instance.detectFormat());
    }

    /* getNumImages(int) */

    @Test
    void getNumImagesWithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertEquals(1, instance.getNumImages());
    }

    @Test
    void getNumImagesWithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertEquals(1, instance.getNumImages());
    }

    @Test
    void getNumImages() {
        assertEquals(1, instance.getNumImages());
    }

    /* getNumResolutions() */

    @Test
    void getNumResolutions() {
        assertEquals(1, instance.getNumResolutions());
    }

    /* getNumThumbnails() */

    @Test
    void getNumThumbnailsWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getNumThumbnails(99999));
    }

    @Test
    void getNumThumbnailsWithNoThumbnail() throws Exception {
        assertEquals(0, instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithUnsupportedCompression() {
        // TODO: write this
    }

    @Test
    void getNumThumbnails() throws Exception {
        instance.setSource(TestUtils.getFixture("thumbnail-jpg.jpg"));
        assertEquals(1, instance.getNumThumbnails(0));
    }

    /* getSize(int) */

    @Test
    void getSizeWithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("alpha.png"));
        assertThrows(SourceFormatException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getSize(1));
    }

    @Test
    void getSize() throws Exception {
        Size size = instance.getSize(0);
        assertEquals(64, size.intWidth());
        assertEquals(56, size.intHeight());
    }

    /* getThumbnailSize() */

    @Test
    void getThumbnailSizeWithNonexistentImage() {
        instance.setSource(Path.of("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getThumbnailSize(99999, 0));
    }

    @Test
    void getThumbnailSizeWithIllegalThumbnailIndex() {
        instance.setSource(TestUtils.getFixture("thumbnail-jpg.jpg"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getThumbnailSize(0, 1));
    }

    @Test
    void getThumbnailSizeWithUnsupportedCompression() {
        // TODO: write this
    }

    @Test
    void getThumbnailSize() throws Exception {
        instance.setSource(TestUtils.getFixture("thumbnail-jpg.jpg"));
        Size thumbSize = instance.getThumbnailSize(0, 0);
        assertEquals(64, thumbSize.intWidth());
        assertEquals(56, thumbSize.intHeight());
    }

    /* getTileSize(int) */

    @Test
    void getTileSizeWithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("alpha.png"));
        assertThrows(SourceFormatException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getTileSize(1));
    }

    @Test
    void getTileSize() throws Exception {
        Size size = instance.getTileSize(0);
        assertEquals(64, size.intWidth());
        assertEquals(56, size.intHeight());
    }

    /* read(int) */

    @Test
    void decode1WithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithInvalidImage() {
        instance.setSource(TestUtils.getFixture("alpha.png"));
        assertThrows(SourceFormatException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> instance.decode(1));
    }

    @Test
    void decode1FromFile() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1FromStream() throws Exception {
        Path fixture = TestUtils.getFixture("rgb.jpg");
        instance.setSource(new PathImageInputStream(fixture));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithGray() throws Exception {
        instance.setSource(TestUtils.getFixture("gray.jpg"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, image.getType());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithCMYK() throws Exception {
        instance.setSource(TestUtils.getFixture("cmyk.jpg"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(BufferedImage.TYPE_3BYTE_BGR, image.getType());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* read(int, ...) */

    @Test
    void decode2WithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.decode(0, null, null, null, null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class,
                () -> instance.decode(0, null, null, null, null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class, () ->
                instance.decode(1, null, null, null, null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2() throws Exception {
        Region region                   = new Region(0, 0, 100, 88);
        double[] scales                 = { 1, 1 };
        double[] diffScales             = { 1, 1 };
        ReductionFactor reductionFactor = new ReductionFactor();
        Set<DecoderHint> decoderHints   = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(0, region, scales, reductionFactor,
                diffScales, decoderHints);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode2PopulatesDecoderHints() throws Exception {
        Region region                   = new Region(0, 0, 32, 28);
        double[] scales                 = { 1, 1 };
        double[] diffScales             = new double[] { 1, 1 };
        ReductionFactor reductionFactor = new ReductionFactor();
        Set<DecoderHint> decoderHints   = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(0, region, scales, reductionFactor,
                diffScales, decoderHints);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertTrue(decoderHints.contains(DecoderHint.IGNORED_REGION));
        assertTrue(decoderHints.contains(DecoderHint.IGNORED_SCALE));
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* readMetadata() */

    @Test
    void decodeMetadataWithNonexistentFile() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithEmptyFile() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("alpha.png"));
        assertThrows(SourceFormatException.class,
                () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readMetadata(9999));
    }

    @Test
    void decodeMetadataWithEXIF() throws Exception {
        instance.setSource(TestUtils.getFixture("exif.jpg"));
        Metadata metadata = instance.readMetadata(0);
        assertFalse(metadata.getEXIF().isEmpty());
    }

    @Test
    void decodeMetadataWithIPTC() throws Exception {
        instance.setSource(TestUtils.getFixture("iptc.jpg"));
        Metadata metadata = instance.readMetadata(0);
        assertFalse(metadata.getIPTC().isEmpty());
    }

    @Test
    void decodeMetadataWithXMP() throws Exception {
        instance.setSource(TestUtils.getFixture("xmp.jpg"));
        Metadata metadata = instance.readMetadata(0);
        assertTrue(metadata.getXMP().isPresent());
    }

    /* readSequence() */

    @Test
    void decodeSequence() {
        assertThrows(UnsupportedOperationException.class,
                () -> instance.decodeSequence());
    }

    /* readThumbnail() */

    @Test
    void decodeThumbnailWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readThumbnail(99999, 0));
    }

    @Test
    void decodeThumbnailWithIllegalThumbnailIndex() {
        instance.setSource(TestUtils.getFixture("thumbnail-jpg.jpg"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readThumbnail(0, 1));
    }

    @Test
    void decodeThumbnailWithUnsupportedCompression() {
        // TODO: write this
    }

    @Test
    void decodeThumbnail() throws Exception {
        instance.setSource(TestUtils.getFixture("thumbnail-jpg.jpg"));
        BufferedImage image = instance.readThumbnail(0, 0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

}
