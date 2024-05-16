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

import is.galia.codec.AbstractDecoder;
import is.galia.codec.Decoder;
import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.codec.tiff.Directory;
import is.galia.codec.tiff.DirectoryReader;
import is.galia.codec.iptc.DataSet;
import is.galia.codec.iptc.IIMReader;
import is.galia.codec.jpeg.JPEGMetadataReader;
import is.galia.codec.tiff.EXIFBaselineTIFFTagSet;
import is.galia.codec.tiff.EXIFGPSTagSet;
import is.galia.codec.tiff.EXIFInteroperabilityTagSet;
import is.galia.codec.tiff.EXIFTagSet;
import is.galia.config.Configuration;
import is.galia.image.MutableMetadata;
import is.galia.image.Size;
import is.galia.image.Format;
import is.galia.image.MediaType;
import is.galia.image.Metadata;
import is.galia.image.Region;
import is.galia.image.ReductionFactor;
import is.galia.plugin.Plugin;
import is.galia.plugin.turbojpeg.config.Key;
import is.galia.processor.Java2DUtils;
import is.galia.stream.ByteArrayImageInputStream;
import is.galia.stream.PathImageInputStream;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJINIT_DECOMPRESS;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_COLORSPACE;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_FASTDCT;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_FASTUPSAMPLE;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_JPEGHEIGHT;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_JPEGWIDTH;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_CMYK;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_GRAY;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_RGB;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Alloc;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Decompress8;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3DecompressHeader;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Destroy;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Free;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Get;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3GetErrorStr;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Init;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Set;

/**
 * <p>Implementation using the Java Foreign Function & Memory API to call into
 * the TurboJPEG library.</p>
 */
public final class TurboJPEGDecoder extends AbstractDecoder
        implements Decoder, Plugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TurboJPEGDecoder.class);

    static final Format FORMAT = new Format(
            "jpg",                                               // key
            "JPEG",                                              // name
            List.of(new MediaType("image", "jpeg")),             // media types
            List.of("jpg", "jpeg", "jpe", "jif", "jfif", "jfi"), // extensions
            true,                                                // isRaster
            false,                                               // isVideo
            false);                                              // supportsTransparency

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    /**
     * There are a huge number of possible compressions. For now, we only
     * support the ones that can be decoded with the standard Image I/O
     * plugins.
     */
    private static final Set<Integer> SUPPORTED_THUMBNAIL_COMPRESSIONS = Set.of(
            6,      // JPEG (old-style)
            7,      // JPEG
            99,     // JPEG
            34892); // PNG

    private final JPEGMetadataReader metadataReader = new JPEGMetadataReader();
    private MemorySegment tjInstance, imageSegment;
    private long imageLength;
    private int width, height;
    private MutableMetadata metadata;
    private transient BufferedImage cachedThumb;

    //endregion
    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Arrays.stream(Key.values())
                .map(Key::toString)
                .filter(k -> k.contains(TurboJPEGDecoder.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPluginName() {
        return TurboJPEGDecoder.class.getSimpleName();
    }

    @Override
    public void onApplicationStart() {
        if (!IS_CLASS_INITIALIZED.getAndSet(true)) {
            System.loadLibrary("turbojpeg");
        }
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void initializePlugin() {
    }

    //endregion
    //region Decoder methods

    @Override
    public void close() {
        super.close();
        if (tjInstance != null) {
            tj3Destroy(tjInstance);
        }
        if (imageSegment != null) {
            tj3Free(imageSegment);
        }
    }

    @Override
    public Format detectFormat() throws IOException {
        ImageInputStream is = null;
        boolean close       = false;
        try {
            if (imageFile != null) {
                is    = new PathImageInputStream(imageFile);
                close = true;
            } else {
                is = inputStream;
            }
            final long length = Math.min(is.length(), 12);
            if (length >= 4) {
                byte[] magicBytes = new byte[(int) length];
                is.seek(0);
                is.readFully(magicBytes);
                if ((magicBytes[0] == (byte) 0xff &&
                        magicBytes[1] == (byte) 0xd8 &&
                        magicBytes[2] == (byte) 0xff &&
                        magicBytes[3] == (byte) 0xdb) ||
                        (length >= 12 &&
                                magicBytes[0] == (byte) 0xff &&
                                magicBytes[1] == (byte) 0xd8 &&
                                magicBytes[2] == (byte) 0xff &&
                                magicBytes[3] == (byte) 0xe0 &&
                                magicBytes[4] == 0x00 &&
                                magicBytes[5] == 0x10 &&
                                magicBytes[6] == 0x4a &&
                                magicBytes[7] == 0x46 &&
                                magicBytes[8] == 0x49 &&
                                magicBytes[9] == 0x46 &&
                                magicBytes[10] == 0x00 &&
                                magicBytes[11] == 0x01) ||
                        (magicBytes[0] == (byte) 0xff &&
                                magicBytes[1] == (byte) 0xd8 &&
                                magicBytes[2] == (byte) 0xff &&
                                magicBytes[3] == (byte) 0xee) ||
                        (length >= 12 &&
                                magicBytes[0] == (byte) 0xff &&
                                magicBytes[1] == (byte) 0xd8 &&
                                magicBytes[2] == (byte) 0xff &&
                                magicBytes[3] == (byte) 0xe1 &&
                                magicBytes[6] == 0x45 &&
                                magicBytes[7] == 0x78 &&
                                magicBytes[8] == 0x69 &&
                                magicBytes[9] == 0x66 &&
                                magicBytes[10] == 0x00 &&
                                magicBytes[11] == 0x00) ||
                        (magicBytes[0] == (byte) 0xff &&
                                magicBytes[1] == (byte) 0xd8 &&
                                magicBytes[2] == (byte) 0xff &&
                                magicBytes[3] == (byte) 0xe0)) {
                    return FORMAT;
                }
            }
        } finally {
            if (is != null) {
                if (close) {
                    is.close();
                } else {
                    is.seek(0);
                }
            }
        }
        return Format.UNKNOWN;
    }

    @Override
    public int getNumImages() {
        return 1;
    }

    @Override
    public int getNumResolutions() {
        return 1;
    }

    @Override
    public int getNumThumbnails(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        initMetadataReader();
        int compression = metadataReader.getThumbnailCompression();
        return SUPPORTED_THUMBNAIL_COMPRESSIONS.contains(compression) ? 1 : 0;
    }

    /**
     * @return Full source image dimensions.
     */
    @Override
    public Size getSize(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        readHeader();
        return new Size(width, height);
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Set.of(FORMAT);
    }

    @Override
    public Size getThumbnailSize(int imageIndex,
                                 int thumbIndex) throws IOException {
        BufferedImage image = readThumbnail(imageIndex, thumbIndex);
        if (image != null) {
            return new Size(image.getWidth(), image.getHeight());
        }
        return null;
    }

    @Override
    public Size getTileSize(int imageIndex) throws IOException {
        return getSize(imageIndex);
    }

    @Override
    public BufferedImage decode(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        readHeader();

        final int inColorSpace = tj3Get(tjInstance, TJPARAM_COLORSPACE());
        int inNumBands, outNumBands, outPixelFormat;
        ColorSpace outColorSpace = null;
        switch (inColorSpace) {
            case 2: // gray
                inNumBands     = 1;
                outNumBands    = 1;
                outPixelFormat = TJPF_GRAY();
                outColorSpace  = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                break;
            case 3: // CMYK
                inNumBands     = 4;
                outNumBands    = 3;
                outPixelFormat = TJPF_CMYK();
                break;
            case 4: // YCCK
                inNumBands     = 4;
                outNumBands    = 3;
                outPixelFormat = TJPF_CMYK();
                break;
            default: // RGB or YCbCr
                inNumBands     = 3;
                outNumBands    = 3;
                outPixelFormat = TJPF_RGB();
                outColorSpace  = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                break;
        }
        final long decodedLength     = (long) width * height * inNumBands;
        MemorySegment decodedSegment = arena.allocate(decodedLength);
        tj3Decompress8(tjInstance, imageSegment, imageLength,
                decodedSegment, 0, outPixelFormat);
        byte[] decodedBytes = decodedSegment.asSlice(0, decodedLength)
                .toArray(ValueLayout.JAVA_BYTE);

        if (inColorSpace == 3 || inColorSpace == 4) {
            WritableRaster raster = newWritableRaster(
                    width, height, inNumBands, decodedBytes);
            initMetadataReader();
            if (metadataReader.hasAdobeSegment()) {
                Java2DUtils.invertColor(raster);
            }
            ICC_Profile profile = metadataReader.getICCProfile();
            return Java2DUtils.convertCMYKToRGB(raster, profile);
        } else {
            return newBufferedImage(
                    width, height, outNumBands, outColorSpace, decodedBytes);
        }
    }

    @Override
    public BufferedImage decode(int imageIndex,
                                Region region,
                                double[] scales,
                                ReductionFactor reductionFactor,
                                double[] diffScales,
                                Set<DecoderHint> decoderHints) throws IOException {
        validateImageIndex(imageIndex);
        decoderHints.add(DecoderHint.IGNORED_REGION);
        decoderHints.add(DecoderHint.IGNORED_SCALE);
        return decode(imageIndex);
    }

    @Override
    public Metadata readMetadata(int imageIndex) throws IOException {
        if (metadata != null) {
            return metadata;
        }
        validateImageIndex(imageIndex);
        initMetadataReader();

        metadata = new MutableMetadata();
        { // EXIF
            byte[] exifBytes = metadataReader.getEXIF();
            if (exifBytes != null) {
                DirectoryReader exifReader = new DirectoryReader();
                exifReader.addTagSet(new EXIFBaselineTIFFTagSet());
                exifReader.addTagSet(new EXIFTagSet());
                exifReader.addTagSet(new EXIFGPSTagSet());
                exifReader.addTagSet(new EXIFInteroperabilityTagSet());
                try (ImageInputStream is = new ByteArrayImageInputStream(exifBytes)) {
                    exifReader.setSource(is);
                    Directory dir = exifReader.readFirst();
                    metadata.setEXIF(dir);
                }
            }
        }
        { // IPTC
            byte[] iptcBytes = metadataReader.getIPTC();
            if (iptcBytes != null) {
                IIMReader iptcReader = new IIMReader();
                iptcReader.setSource(iptcBytes);
                List<DataSet> dataSets = iptcReader.read();
                metadata.setIPTC(dataSets);
            }
        }
        { // XMP
            String xmpStr = metadataReader.getXMP();
            metadata.setXMP(xmpStr);
        }
        return metadata;
    }

    @Override
    public BufferedImage readThumbnail(int imageIndex,
                                       int thumbIndex) throws IOException {
        if (thumbIndex != 0) {
            throw new IndexOutOfBoundsException("Invalid thumbnail index");
        }
        validateImageIndex(imageIndex);
        initMetadataReader();
        if (cachedThumb == null) {
            byte[] data = metadataReader.getThumbnailData();
            if (data != null) {
                try (ImageInputStream thumbIS = new ByteArrayImageInputStream(data)) {
                    cachedThumb = ImageIO.read(thumbIS);
                }
            }
        }
        return cachedThumb;
    }

    //endregion
    //region Private methods

    private void validateImageIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Ensures that {@link #inputStream} is set regardless of any other
     * source ivar that is set.
     */
    private void setupInputStream() throws IOException {
        if (inputStream == null) {
            if (imageFile != null) {
                inputStream = new PathImageInputStream(imageFile);
            } else {
                throw new IOException("Source not set");
            }
        }
    }

    /**
     * Reads the source image data into a native memory segment,
     */
    private void bufferImageData() throws IOException {
        if (imageSegment != null && imageLength > 0) {
            return;
        }
        final Stopwatch watch = new Stopwatch();
        byte[] imageBytes;
        if (imageFile != null) {
            imageBytes = Files.readAllBytes(imageFile);
        } else {
            setupInputStream();
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[16384];
                int n;
                while ((n = inputStream.read(chunk)) != -1) {
                    os.write(chunk, 0, n);
                }
                imageBytes = os.toByteArray();
            }
        }

        LOGGER.trace("bufferImageData(): read image into heap in {}", watch);

        watch.reset();
        imageLength  = imageBytes.length;
        imageSegment = tj3Alloc(imageLength);
        imageSegment.asSlice(0, imageLength).asByteBuffer().put(imageBytes);
        LOGGER.trace("bufferImageData(): copied image into native memory in {}",
                watch);
    }

    /**
     * <p>Builds a custom {@link BufferedImage} backed directly by the decoded
     * bytes returned from the library with no copying.</p>
     */
    private BufferedImage newBufferedImage(int width, int height,
                                           int numBands,
                                           ColorSpace colorSpace,
                                           byte[] imageBytes) {
        final Stopwatch watch = new Stopwatch();
        // Create a ColorModel
        int[] bits = new int[numBands];
        Arrays.fill(bits, 8);
        ColorModel colorModel = new ComponentColorModel(
                colorSpace, bits, false, false, 1, 0);
        // Create the image
        WritableRaster raster = newWritableRaster(
                width, height, numBands, imageBytes);
        BufferedImage image = new BufferedImage(
                colorModel, raster, false, new Hashtable<>());

        LOGGER.trace("newBufferedImage() completed in {}", watch);
        return image;
    }

    private WritableRaster newWritableRaster(int width, int height,
                                             int numBands,
                                             byte[] decompressedBytes) {
        final Stopwatch watch = new Stopwatch();
        int[] scanlineStride = new int[numBands];
        for (int i = 0; i < numBands; i++) {
            scanlineStride[i] = i;
        }
        SampleModel sampleModel = new PixelInterleavedSampleModel(
                DataBuffer.TYPE_BYTE, width, height,
                numBands, width * numBands, scanlineStride);
        WritableRaster raster = WritableRaster.createWritableRaster(
                sampleModel,
                new DataBufferByte(decompressedBytes, width * height * numBands),
                new Point(0, 0));
        LOGGER.trace("newWritableRaster() completed in {}", watch);
        return raster;
    }

    private void readHeader() throws IOException {
        if (width > 0) {
            return;
        }
        bufferImageData();
        initTJInstance();

        tj3DecompressHeader(tjInstance, imageSegment, imageLength);
        width  = tj3Get(tjInstance, TJPARAM_JPEGWIDTH());
        height = tj3Get(tjInstance, TJPARAM_JPEGHEIGHT());
        if (width <= 0 || height <= 0) {
            MemorySegment errorSegment = tj3GetErrorStr(tjInstance);
            String message             = errorSegment.getString(0);
            throw new SourceFormatException(message);
        }
    }

    private void initTJInstance() {
        tjInstance = tj3Init(TJINIT_DECOMPRESS());
        tj3Set(tjInstance, TJPARAM_FASTUPSAMPLE(), isFastUpsampling() ? 1 : 0);
        tj3Set(tjInstance, TJPARAM_FASTDCT(), isFastDCT() ? 1 : 0);
    }

    private void initMetadataReader() throws IOException {
        setupInputStream();
        metadataReader.setSource(inputStream);
    }

    private boolean isFastDCT() {
        return Configuration.forApplication()
                .getBoolean(Key.TURBOJPEGDECODER_FAST_DCT.key(), false);
    }

    private boolean isFastUpsampling() {
        return Configuration.forApplication()
                .getBoolean(Key.TURBOJPEGDECODER_FAST_UPSAMPLING.key(), false);
    }

}