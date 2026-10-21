package io.github.jihu1130.ticketonline.global.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    @TempDir
    Path tempDir;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(tempDir.toString());
    }

    @Test
    void store_savesPngAndReturnsUploadUrl() throws Exception {
        String url = imageService.store(new MockMultipartFile("file", "poster.png", "image/png", PNG));

        assertThat(url).startsWith("/uploads/").endsWith(".png");
        assertThat(ImageService.isUploadedUrl(url)).isTrue();
        Path saved = tempDir.resolve(url.substring("/uploads/".length()));
        assertThat(Files.readAllBytes(saved)).isEqualTo(PNG);
    }

    @Test
    void store_detectsTypeFromContentNotFileName() {
        // 이름은 .png지만 실제로는 jpg
        String url = imageService.store(new MockMultipartFile("file", "fake.png", "image/png", JPG));
        assertThat(url).endsWith(".jpg");

        assertThat(imageService.store(new MockMultipartFile("file", "a.webp", "image/webp", WEBP)))
                .endsWith(".webp");
    }

    @Test
    void store_ignoresUserFileName() {
        String url = imageService.store(new MockMultipartFile("file", "../../evil.png", "image/png", PNG));
        assertThat(url).doesNotContain("evil").doesNotContain("..");
    }

    @Test
    void store_rejectsNonImage() {
        MockMultipartFile html = new MockMultipartFile("file", "a.png", "image/png",
                "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> imageService.store(html))
                .hasMessageContaining("jpg, png, webp")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void store_rejectsEmptyFile() {
        assertThatThrownBy(() -> imageService.store(new MockMultipartFile("file", new byte[0])))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void store_rejectsTooLargeFile() {
        byte[] big = new byte[(int) ImageService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> imageService.store(new MockMultipartFile("file", "big.png", "image/png", big)))
                .hasMessageContaining("5MB");
    }

    @Test
    void isUploadedUrl_rejectsOtherUrls() {
        assertThat(ImageService.isUploadedUrl("https://example.com/a.png")).isFalse();
        assertThat(ImageService.isUploadedUrl("/uploads/../secret.png")).isFalse();
        assertThat(ImageService.isUploadedUrl(null)).isFalse();
    }
}
