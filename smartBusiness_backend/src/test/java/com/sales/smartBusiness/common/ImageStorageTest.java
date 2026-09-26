package com.sales.smartBusiness.common;

import com.sales.smartBusiness.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validation only — {@code store}/{@code read}/{@code delete} need a real filesystem, covered by integration tests. */
class ImageStorageTest {

    private final ImageStorage imageStorage = new ImageStorage("target/test-uploads");

    private MockMultipartFile pngFile(int sizeInBytes) {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[sizeInBytes]);
    }

    @Test
    @DisplayName("a PNG within the size limit is accepted")
    void acceptsValidImage() {
        assertThatCode(() -> imageStorage.assertValid(pngFile(100), 1_000_000)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty file is refused")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imageStorage.assertValid(empty, 1_000_000))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("choose an image");
    }

    @Test
    @DisplayName("a non-image content type is refused")
    void rejectsWrongType() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> imageStorage.assertValid(file, 1_000_000))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PNG, JPEG or WEBP");
    }

    @Test
    @DisplayName("a file over the size limit is refused")
    void rejectsOversizedFile() {
        assertThatThrownBy(() -> imageStorage.assertValid(pngFile(1_500_000), 1_000_000))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 MB");
    }
}
