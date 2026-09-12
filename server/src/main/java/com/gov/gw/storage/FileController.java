package com.gov.gw.storage;

import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final StorageService storage;

    public FileController(StorageService storage) {
        this.storage = storage;
    }

    /** 通用上传（附件等），返回 {key, name, size} */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "misc") String dir) throws Exception {
        if (file.isEmpty()) throw new ApiException("文件为空");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/\\s]+", "_");
        String key = dir + "/" + UUID.randomUUID() + "-" + safeName;
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("name", original);
        data.put("size", file.getSize());
        return ApiResponse.ok(data);
    }

    /** 下载：/api/files/{key...} */
    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request) {
        String key = request.getRequestURI().substring(request.getRequestURI().indexOf("/api/files/") + 11);
        if (key.isBlank()) throw ApiException.notFound("文件");
        InputStream in = storage.get(key);
        String filename = key.substring(key.lastIndexOf('/') + 1);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(guessType(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .body(new InputStreamResource(in));
    }

    private MediaType guessType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
