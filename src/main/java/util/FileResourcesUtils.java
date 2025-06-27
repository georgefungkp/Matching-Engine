package util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for file operations including resource loading, directory management,
 * and file content reading/writing.
 */
public class FileResourcesUtils {
    private static final Logger log = LogManager.getLogger(FileResourcesUtils.class);

    /**
     * Example usage of the utility methods in this class.
     * 
     * @param args Command line arguments (not used)
     * @throws URISyntaxException If the resource URI is malformed
     */
    public static void main(String[] args) throws URISyntaxException {
        String fileName = "json/file1.json";

        // Demonstrate using the resource stream
        log.debug("Loading resource as stream: {}", fileName);
        try (InputStream is = getResourceAsStream(fileName)) {
            List<String> content = readAllLines(is);
            content.forEach(log::debug);
        } catch (IOException e) {
            log.error("Error reading resource stream", e);
        }

        // Demonstrate using the resource as a file
        log.debug("Loading resource as file: {}", fileName);
        try {
            File file = getResourceAsFile(fileName);
            List<String> lines = readAllLines(file);
            lines.forEach(System.out::println);
        } catch (Exception e) {
            log.error("Error reading resource file", e);
        }
    }

    /**
     * Gets a file from the resources folder as an InputStream.
     * This method works in all environments (IDE, tests, JAR files).
     *
     * @param resourcePath Path to the resource, relative to the resources directory
     * @return InputStream of the resource
     * @throws IllegalArgumentException If the resource cannot be found
     */
    public static InputStream getResourceAsStream(String resourcePath) {
        ClassLoader classLoader = FileResourcesUtils.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        return inputStream;
    }

    /**
     * Gets a file from the resources folder as a File object.
     * Note: This may not work for resources inside JAR files.
     * Use getResourceAsStream for more reliable resource access.
     *
     * @param resourcePath Path to the resource, relative to the resources directory
     * @return File object representing the resource
     * @throws URISyntaxException If the resource URI is malformed
     * @throws IllegalArgumentException If the resource cannot be found
     */
    public static File getResourceAsFile(String resourcePath) throws URISyntaxException {
        ClassLoader classLoader = FileResourcesUtils.class.getClassLoader();
        URL resource = classLoader.getResource(resourcePath);

        if (resource == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        return new File(resource.toURI());
    }

    /**
     * Reads all lines from an InputStream using UTF-8 encoding.
     *
     * @param inputStream The input stream to read from
     * @return List of strings, each representing a line from the input
     * @throws IOException If an I/O error occurs
     */
    public static List<String> readAllLines(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    /**
     * Reads all lines from a file using UTF-8 encoding.
     *
     * @param file The file to read from
     * @return List of strings, each representing a line from the file
     */
    public static List<String> readAllLines(File file) {
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read file: {}", file.getPath(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Writes text content to a file using UTF-8 encoding.
     * Creates parent directories if they don't exist.
     *
     * @param filePath Path where the file should be written
     * @param content The content to write to the file
     * @throws IOException If an I/O error occurs
     */
    public static void writeToFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);

        // Create parent directories if they don't exist
        Files.createDirectories(path.getParent());

        // Write the content
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        log.debug("Content written to file: {}", filePath);
    }

    /**
     * Ensures that a directory exists at the specified path, creating it if necessary.
     * This method is particularly useful for creating log directories or other application 
     * directories before they are needed by the application.
     *
     * @param dirPath The path of the directory to ensure exists. Can be relative or absolute.
     * @throws SecurityException If a security manager exists and its {@code checkWrite} 
     *         method denies write access to the directory
     */
    public static void ensureDirectoryExists(String dirPath) {
        File dir = new File(dirPath);

        // Only attempt to create the directory if it doesn't already exist
        if (!dir.exists()) {
            log.debug("Directory does not exist, creating: {}", dirPath);

            // mkdirs() creates both the directory and any necessary parent directories
            boolean created = dir.mkdirs();

            if (!created) {
                // This could happen due to permissions, concurrent deletion, or other I/O issues
                log.error("Failed to create directory: {}. Check permissions and path validity.", dirPath);
            } else {
                log.debug("Successfully created directory: {}", dirPath);
            }
        } else {
            // Directory already exists, verify it's actually a directory
            if (!dir.isDirectory()) {
                log.warn("Path exists but is not a directory: {}", dirPath);
            } else {
                log.debug("Directory already exists: {}", dirPath);
            }
        }
    }

    /**
     * Deletes a file or directory and all its contents recursively.
     *
     * @param path Path to the file or directory to delete
     * @return true if the deletion was successful, false otherwise
     */
    public static boolean deleteRecursively(File path) {
        if (!path.exists()) {
            return true;
        }

        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteRecursively(file)) {
                        return false;
                    }
                }
            }
        }

        boolean deleted = path.delete();
        if (!deleted) {
            log.warn("Failed to delete: {}", path.getAbsolutePath());
        }

        return deleted;
    }
}
