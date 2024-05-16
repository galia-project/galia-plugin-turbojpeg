# TurboJPEG Plugin for Galia

Provides TurboJPEGDecoder and TurboJPEGEncoder.

See the [TurboJPEG Plugin page on the website](https://galia.is/plugins/turbojpeg/)
for more information.

## Development

The native binding to TurboJPEG was generated using jextract 22:

```sh
jextract --target-package org.libjpegturbo.turbojpeg \
    --output /path/to/src/main/java \
    /path/to/include/turbojpeg.h
```

# License

See the file [LICENSE.txt](LICENSE.txt) for license information.
