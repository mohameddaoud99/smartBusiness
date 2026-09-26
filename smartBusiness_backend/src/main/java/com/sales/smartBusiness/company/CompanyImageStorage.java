package com.sales.smartBusiness.company;

import com.sales.smartBusiness.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads and writes the logo/stamp files under {@code companies/{companyId}/{kind}.{ext}}.
 * Only this class touches the filesystem — the rest of the company module deals in
 * relative paths and content types, both stored on the {@link Company} row.
 */
@Component
public class CompanyImageStorage {

    private final Path root;

    public CompanyImageStorage(@Value("${smartbusiness.uploads.dir}") String uploadsDir) {
        this.root = Paths.get(uploadsDir, "companies").toAbsolutePath().normalize();
    }

    /**
     * Stores the file, replacing any previous file of the same kind — even one with a
     * different extension, so switching from a PNG logo to a JPEG one leaves no orphan.
     *
     * @return the path to store on the entity, relative to the uploads root
     */
    public String store(Long companyId, String kind, MultipartFile file) throws IOException {
        Path companyDir = root.resolve(String.valueOf(companyId));
        Files.createDirectories(companyDir);
        deleteExisting(companyDir, kind);

        Path target = companyDir.resolve(kind + extensionFor(file.getContentType()));
        file.transferTo(target);

        return root.relativize(target).toString().replace('\\', '/');
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    /** Best effort: a leftover file on disk is not worth failing the request over. */
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
        }
    }

    private void deleteExisting(Path companyDir, String kind) throws IOException {
        try (var files = Files.list(companyDir)) {
            for (Path candidate : (Iterable<Path>) files::iterator) {
                if (candidate.getFileName().toString().startsWith(kind + ".")) {
                    Files.deleteIfExists(candidate);
                }
            }
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            // relativePath always comes from our own store() — this would mean data corruption
            throw new IllegalStateException("Stored path escapes the uploads directory: " + relativePath);
        }
        return resolved;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> throw new BusinessRuleException("Only PNG, JPEG or WEBP images are allowed");
        };
    }
}
