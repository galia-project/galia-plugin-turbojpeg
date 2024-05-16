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

package is.galia.plugin.turbojpeg.config;

import is.galia.operation.Encode;

public enum Key {

    TURBOJPEGDECODER_FAST_DCT       (Encode.OPTION_PREFIX + "TurboJPEGDecoder.fast_dct"),
    TURBOJPEGDECODER_FAST_UPSAMPLING(Encode.OPTION_PREFIX + "TurboJPEGDecoder.fast_upsampling"),

    TURBOJPEGENCODER_FAST_DCT       (Encode.OPTION_PREFIX + "TurboJPEGEncoder.fast_dct"),
    TURBOJPEGENCODER_OPTIMIZE_CODING(Encode.OPTION_PREFIX + "TurboJPEGEncoder.optimize_coding"),
    TURBOJPEGENCODER_QUALITY        (Encode.OPTION_PREFIX + "TurboJPEGEncoder.quality"),
    TURBOJPEGENCODER_PROGRESSIVE    (Encode.OPTION_PREFIX + "TurboJPEGEncoder.progressive"),
    TURBOJPEGENCODER_SUBSAMPLING    (Encode.OPTION_PREFIX + "TurboJPEGEncoder.subsampling");

    private final String key;

    Key(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key();
    }

}
