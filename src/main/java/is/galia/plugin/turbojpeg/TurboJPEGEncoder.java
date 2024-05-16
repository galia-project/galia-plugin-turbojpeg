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

import is.galia.codec.Encoder;
import is.galia.image.Format;
import is.galia.operation.Color;
import is.galia.operation.Encode;
import is.galia.plugin.Plugin;
import is.galia.plugin.turbojpeg.config.Key;
import is.galia.processor.Java2DUtils;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.libjpegturbo.turbojpeg.turbojpeg_h.C_POINTER;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJINIT_COMPRESS;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_FASTDCT;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_OPTIMIZE;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_PROGRESSIVE;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_QUALITY;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_SCANLIMIT;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPARAM_SUBSAMP;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_BGR;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_GRAY;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJPF_RGB;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_411;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_420;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_422;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_440;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_441;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_444;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.TJSAMP_GRAY;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Compress8;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Destroy;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Init;
import static org.libjpegturbo.turbojpeg.turbojpeg_h.tj3Set;

/**
 * <p>Implementation using the Java Foreign Function & Memory API to call into
 * the TurboJPEG library.</p>
 */
public class TurboJPEGEncoder implements Encoder, Plugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TurboJPEGEncoder.class);

    private static final int DEFAULT_QUALITY = 50;

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    private Arena arena;
    private MemorySegment tjInstance;
    private Encode encode;

    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Arrays.stream(Key.values())
                .map(Key::toString)
                .filter(k -> k.contains(TurboJPEGEncoder.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPluginName() {
        return TurboJPEGEncoder.class.getSimpleName();
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
        arena = Arena.ofConfined();
    }

    //endregion
    //region Encoder methods

    @Override
    public void close() {
        if (tjInstance != null) {
            tj3Destroy(tjInstance);
        }
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Set.of(TurboJPEGDecoder.FORMAT);
    }

    @Override
    public void setArena(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void setEncode(Encode encode) {
        this.encode = encode;
    }

    @Override
    public void encode(RenderedImage image,
                       OutputStream outputStream) throws IOException {
        final Stopwatch elapsedTimer = new Stopwatch();
        final int width              = image.getWidth();
        final int height             = image.getHeight();
        final int quality            = getQuality();
        int pixelFormat, subsampling;
        if (image.getSampleModel().getNumBands() > 3) {
            image = removeAlpha(image);
        }
        if (image.getSampleModel().getNumBands() > 1) {
            pixelFormat = (image.getData().getDataBuffer() instanceof DataBufferByte) ?
                    TJPF_BGR() : TJPF_RGB();
            subsampling = getSubsampling();
        } else {
            pixelFormat = TJPF_GRAY();
            subsampling = TJSAMP_GRAY();
        }
        MemorySegment inputImage = copyImageIntoNativeMemory(image);

        initTJInstance();
        tj3Set(tjInstance, TJPARAM_SUBSAMP(), subsampling);
        tj3Set(tjInstance, TJPARAM_QUALITY(), quality);
        tj3Set(tjInstance, TJPARAM_FASTDCT(), isUsingFastDCT() ? 1 : 0);

        MemorySegment jpegPtr         = arena.allocate(C_POINTER);
        MemorySegment jpegSizeSegment = arena.allocate(ValueLayout.JAVA_LONG);
        final Stopwatch lapTimer      = new Stopwatch();
        tj3Compress8(tjInstance, inputImage, width, 0, height, pixelFormat,
                jpegPtr, jpegSizeSegment);
        LOGGER.trace("Compressed image in {}", lapTimer);

        MemorySegment encodedImage = jpegPtr.get(C_POINTER, 0);
        long jpegSize = jpegSizeSegment.get(ValueLayout.JAVA_LONG, 0);

        if (encode.getXMP().isPresent()) {
            outputStream = new SegmentInjectingOutputStream(
                    encode.getXMP().get(), outputStream);
        }
        byte[] bytes = encodedImage.asSlice(0, jpegSize)
                .toArray(ValueLayout.JAVA_BYTE);
        outputStream.write(bytes);
        LOGGER.trace("write(): total time: {}", elapsedTimer);
    }

    //endregion
    //region Private methods

    private MemorySegment copyImageIntoNativeMemory(RenderedImage image) {
        final Stopwatch watch       = new Stopwatch();
        final int width             = image.getWidth();
        final int height            = image.getHeight();
        final int numBands          = image.getSampleModel().getNumBands();
        final Raster raster         = image.getData();
        final DataBuffer heapBuffer = raster.getDataBuffer();
        final long imageSize        = (long) numBands * width * height;
        MemorySegment imageSegment  = arena.allocate(imageSize);

        if (heapBuffer instanceof DataBufferByte && numBands >= 3) {
            imageSegment.asByteBuffer()
                    .put(((DataBufferByte) heapBuffer).getData());
        } else if (numBands > 1) {
            for (int i = 0, y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        byte sample = (byte) raster.getSample(x, y, b);
                        imageSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                    }
                }
            }
        } else {
            for (int i = 0, y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte sample = (byte) raster.getSample(x, y, 0);
                    imageSegment.set(ValueLayout.JAVA_BYTE, i++, sample);
                }
            }
        }
        LOGGER.trace("Copied image into native memory in {}", watch);
        return imageSegment;
    }

    private void initTJInstance() {
        tjInstance = tj3Init(TJINIT_COMPRESS());
        tj3Set(tjInstance, TJPARAM_FASTDCT(), isUsingFastDCT() ? 1 : 0);
        tj3Set(tjInstance, TJPARAM_PROGRESSIVE(), isProgressive() ? 1 : 0);
        tj3Set(tjInstance, TJPARAM_OPTIMIZE(), isEntropyCodingOptimized() ? 1 : 0);
        tj3Set(tjInstance, TJPARAM_SCANLIMIT(), 100);
    }

    /**
     * Removes the alpha channel from the given image, considering the return
     * value of {@link Encode#getBackgroundColor()}.
     *
     * @param image Image to remove alpha from.
     * @return      Flattened image.
     */
    private RenderedImage removeAlpha(RenderedImage image) {
        Color bgColor = encode.getBackgroundColor();
        if (bgColor == null) {
            bgColor = Color.WHITE;
        }
        return Java2DUtils.removeAlpha(image, bgColor);
    }

    private int getQuality() {
        return encode.getOptions()
                .getInt(Key.TURBOJPEGENCODER_QUALITY.key(), DEFAULT_QUALITY);
    }

    private int getSubsampling() {
        String str = encode.getOptions()
                .getString(Key.TURBOJPEGENCODER_SUBSAMPLING.key(), "444");
        return switch (str) {
            case "411" -> TJSAMP_411();
            case "420" -> TJSAMP_420();
            case "422" -> TJSAMP_422();
            case "440" -> TJSAMP_440();
            case "441" -> TJSAMP_441();
            case "444" -> TJSAMP_444();
            default -> throw new IllegalArgumentException(
                    "Unknown subsampling: " + str);
        };
    }

    private boolean isEntropyCodingOptimized() {
        return encode.getOptions()
                .getBoolean(Key.TURBOJPEGENCODER_OPTIMIZE_CODING.key(), false);
    }

    private boolean isProgressive() {
        return encode.getOptions()
                .getBoolean(Key.TURBOJPEGENCODER_PROGRESSIVE.key(), true);
    }

    private boolean isUsingFastDCT() {
        return encode.getOptions()
                .getBoolean(Key.TURBOJPEGENCODER_FAST_DCT.key(), false);
    }

}
