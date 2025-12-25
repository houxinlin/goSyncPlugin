package dev.coolrequest.gosync;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LrzszUtils {
    private static final String WINDOWS = "/windows/lrzsz_0.12.21rc_windows_x86_64.zip";
    private static final Path HOME = Paths.get(System.getProperty("user.home"), ".config", ".cool-request", "gosync");

    public static void unzip(InputStream zipFileStream, String destDir) throws IOException {
        Path destPath = Paths.get(destDir);
        if (!Files.exists(destPath)) {
            Files.createDirectories(destPath);
        }

        try (ZipInputStream zipIn = new ZipInputStream(zipFileStream)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path filePath = destPath.resolve(entry.getName()).normalize();
                if (!filePath.startsWith(destPath)) {
                    throw new IOException("Entry is outside of the target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(filePath))) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zipIn.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    private static void un() {
        if (isWindows()) {
            if (!HOME.toFile().exists()) {
                try {
                    Files.createDirectories(HOME);
                } catch (IOException ignored) {

                }
            }
            InputStream resourceAsStream = LrzszUtils.class.getResourceAsStream(WINDOWS);
            try {
                unzip(resourceAsStream, HOME.resolve("windows").toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean canExecute() {
        un();
        return true;
    }

    public static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("win");
    }

    public static String getExecutePath() {
        if (isWindows()) {
            Path resolve = HOME.resolve("windows/sz.exe");
            if (!Files.exists(resolve)) {
                un();
            }
            return resolve.toString();
        }
        Optional<String> szPath = findExecutable("sz");
        if (szPath.isPresent()) {
            return szPath.get();
        }
        Optional<String> lrzszSzPath = findExecutable("lrzsz-sz");
        return lrzszSzPath.orElse(null);

    }

    private static Optional<String> findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }

        String[] paths = pathEnv.split(":");
        return Arrays.stream(paths)
                .map(Paths::get)
                .map(p -> p.resolve(command))
                .filter(Files::exists)
                .map(Path::toString)
                .findFirst();
    }

}
