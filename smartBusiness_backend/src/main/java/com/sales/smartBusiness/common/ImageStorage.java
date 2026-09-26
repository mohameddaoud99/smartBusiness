package com.sales.smartBusiness.common;

import com.sales.smartBusiness.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Reads and writes small images (a product picture, a company logo) under
 * {@code {domain}/{ownerId}/{kind}.{ext}}. The caller deals only in the relative path
 * and content type it gets back, both meant to be stored on the owning entity.
 * <p>
 * {@code company/CompanyImageStorage} predates this class and stores its files under a
 * different layout — left as is rather than migrated, so existing logos and stamps on
 * disk keep resolving. New features needing image storage use this one.
 */
@Component
public class ImageStorage {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final Path root;

    public ImageStorage(@Value("${smartbusiness.uploads.dir}") String uploadsDir) {
        this.root = Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    /** Rejects anything that should never reach {@link #store}, with a message fit for the user. */
    public void assertValid(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose an image to upload");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessRuleException("Only PNG, JPEG or WEBP images are allowed");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("Image must not exceed " + (maxBytes / 1_000_000) + " MB");
        }
    }

    /**
     * Stores the file, replacing any previous file of the same kind — even one with a
     * different extension, so switching from a PNG to a JPEG leaves no orphan.
     *
     * @return the path to store on the entity, relative to the uploads root
     */
    public String store(String domain, Long ownerId, String kind, MultipartFile file) throws IOException {
        Path ownerDir = root.resolve(domain).resolve(String.valueOf(ownerId));
        Files.createDirectories(ownerDir);
        deleteExisting(ownerDir, kind);

        Path target = ownerDir.resolve(kind + extensionFor(file.getContentType()));
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

    private void deleteExisting(Path ownerDir, String kind) throws IOException {
        try (var files = Files.list(ownerDir)) {
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
