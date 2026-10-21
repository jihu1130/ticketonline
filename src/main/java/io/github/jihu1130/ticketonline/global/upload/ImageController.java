package io.github.jihu1130.ticketonline.global.upload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "사진", description = "상품 대표 사진, 좌석 배치도 업로드")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    public record ImageResponse(String url) {
    }

    @Operation(summary = "사진 업로드",
            description = "jpg/png/webp, 5MB 이하. 돌려받은 url을 상품 등록 요청에 넣는다")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@RequestPart("file") MultipartFile file) {
        return new ImageResponse(imageService.store(file));
    }
}
