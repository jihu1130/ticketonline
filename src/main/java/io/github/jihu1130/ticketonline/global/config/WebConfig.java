package io.github.jihu1130.ticketonline.global.config;

import io.github.jihu1130.ticketonline.global.upload.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 업로드한 사진을 /uploads/** 주소로 보여 준다. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ImageService imageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImageService.URL_PREFIX + "**")
                .addResourceLocations(imageService.getUploadDir().toUri().toString());
    }
}
