package io.github.jihu1130.ticketonline.global.upload;

import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 상품 사진(대표 사진, 좌석 배치도)을 서버 폴더에 저장하고 조회 주소를 돌려준다.
 * - jpg / png / webp만 허용. 파일 이름이 아니라 실제 파일 앞부분(시그니처)으로 판단한다.
 * - 저장 이름은 서버가 새로 만든다 (사용자가 보낸 파일 이름은 쓰지 않음)
 */
@Service
public class ImageService {

    public static final String URL_PREFIX = "/uploads/";
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path uploadDir;

    public ImageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    /** 저장 후 "/uploads/202610/uuid.png" 형태의 주소를 돌려준다. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("사진 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("사진은 5MB 이하만 올릴 수 있습니다.");
        }
        String extension = detectExtension(file);

        String folder = YearMonth.now().format(MONTH);
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Path dir = Files.createDirectories(uploadDir.resolve(folder));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("사진 저장 실패", e);
        }
        return URL_PREFIX + folder + "/" + fileName;
    }

    /** 이 서버에 올린 사진 주소인지 확인 (상품 등록 시 외부 주소를 막기 위해) */
    public static boolean isUploadedUrl(String url) {
        return url != null && url.matches("^/uploads/\\d{6}/[0-9a-f\\-]{36}\\.(jpg|png|webp)$");
    }

    private String detectExtension(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw invalid("사진 파일을 읽을 수 없습니다.");
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return "png";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        throw invalid("jpg, png, webp 사진만 올릴 수 있습니다.");
    }

    private static CustomException invalid(String message) {
        return new CustomException(ErrorCode.INVALID_INPUT, message);
    }
}
