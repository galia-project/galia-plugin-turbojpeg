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

package is.galia.plugin.turbojpeg.test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestUtils {

    public static Path getFixture(String filename) {
        return getFixturePath().resolve(filename);
    }

    /**
     * @return Path of the fixtures directory.
     */
    public static Path getFixturePath() {
        return Paths.get("src", "test", "resources");
    }

    /**
     * Saves the given image as a PNG in the user's desktop folder (if macOS)
     * or home folder.
     */
    public static void save(BufferedImage image) {
        // Get the calling class name
        StackWalker.StackFrame stackFrame = StackWalker.getInstance()
                .walk(s -> s.skip(1).findFirst())
                .get();
        String testClassName = stackFrame.getClassName();
        String[] parts = testClassName.split("\\.");
        testClassName = parts[parts.length - 1];
        // Get the user home dir
        String home = System.getProperty("user.home");
        Path parentDir = Path.of(home);
        // If we are on a Mac, use the desktop
        Path desktopDir = Path.of(home, "Desktop");
        if (Files.isDirectory(desktopDir)) {
            parentDir = desktopDir;
        }
        Path dir = parentDir.resolve(testClassName);
        try {
            Files.createDirectories(dir);
            ImageIO.write(image, "PNG",
                    dir.resolve(stackFrame.getMethodName() + ".png").toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Saves the given image bytes in the user's desktop folder (if macOS)
     * or home folder with the given extension.
     */
    public static void save(byte[] imageBytes, String extension) {
        // Get the calling class name
        StackWalker.StackFrame stackFrame = StackWalker.getInstance()
                .walk(s -> s.skip(1).findFirst())
                .get();
        String testClassName = stackFrame.getClassName();
        String[] parts = testClassName.split("\\.");
        testClassName = parts[parts.length - 1];
        // Get the user home dir
        String home = System.getProperty("user.home");
        Path parentDir = Path.of(home);
        // If we are on a Mac, use the desktop
        Path desktopDir = Path.of(home, "Desktop");
        if (Files.isDirectory(desktopDir)) {
            parentDir = desktopDir;
        }
        Path dir = parentDir.resolve(testClassName);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(stackFrame.getMethodName() + "." + extension), imageBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TestUtils() {}

}
