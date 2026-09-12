package com.gov.gw.seal;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import com.gov.gw.org.UserEntity;
import com.gov.gw.storage.StorageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/seals")
public class SealController {
    private final SealRepo sealRepo;
    private final SealService sealService;
    private final SealImageGenerator generator;
    private final StorageService storage;
    private final AuthService authService;

    public SealController(SealRepo sealRepo, SealService sealService, SealImageGenerator generator,
                          StorageService storage, AuthService authService) {
        this.sealRepo = sealRepo;
        this.sealService = sealService;
        this.generator = generator;
        this.storage = storage;
        this.authService = authService;
    }

    /** 我可用的章 */
    @GetMapping
    public ApiResponse<List<Seal>> list() {
        return ApiResponse.ok(sealService.visibleTo(authService.current()));
    }

    public record SealReq(@NotBlank String name, @NotBlank String ownerType, @NotNull Long ownerId) {}

    @PostMapping
    public ApiResponse<Seal> create(@RequestBody SealReq req) {
        UserEntity me = authService.current();
        boolean selfUserSeal = Seal.OWNER_USER.equals(req.ownerType()) && req.ownerId().equals(me.getId());
        if (!Boolean.TRUE.equals(me.getAdmin()) && !selfUserSeal) {
            throw ApiException.forbidden("仅管理员可维护单位/他人签章");
        }
        if (!Seal.OWNER_ORG.equals(req.ownerType()) && !Seal.OWNER_USER.equals(req.ownerType())) {
            throw new ApiException("ownerType 须为 ORG 或 USER");
        }
        Seal seal = new Seal();
        seal.setName(req.name());
        seal.setOwnerType(req.ownerType());
        seal.setOwnerId(req.ownerId());
        return ApiResponse.ok(sealRepo.save(seal));
    }

    /** 生成演示章图（环形名称 + 五角星） */
    @PostMapping("/{id}/generate")
    public ApiResponse<Seal> generate(@PathVariable Long id) {
        Seal seal = sealRepo.findById(id).orElseThrow(() -> ApiException.notFound("签章"));
        requireSealManager(seal);
        byte[] png = generator.generate(seal.getName(),
                Seal.OWNER_ORG.equals(seal.getOwnerType()) ? "专用章" : "");
        String key = "seal/" + id + ".png";
        storage.put(key, new java.io.ByteArrayInputStream(png), png.length);
        seal.setImageKey(key);
        return ApiResponse.ok(sealRepo.save(seal));
    }

    /** 上传自定义章图（PNG） */
    @PostMapping("/{id}/image")
    public ApiResponse<Seal> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws Exception {
        Seal seal = sealRepo.findById(id).orElseThrow(() -> ApiException.notFound("签章"));
        requireSealManager(seal);
        String key = "seal/" + id + ".png";
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize());
        }
        seal.setImageKey(key);
        return ApiResponse.ok(sealRepo.save(seal));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<InputStreamResource> image(@PathVariable Long id) {
        Seal seal = sealRepo.findById(id).orElseThrow(() -> ApiException.notFound("签章"));
        if (seal.getImageKey() == null) throw ApiException.notFound("章图");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.get(seal.getImageKey())));
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<Seal> toggle(@PathVariable Long id) {
        authService.requireAdmin();
        Seal seal = sealRepo.findById(id).orElseThrow(() -> ApiException.notFound("签章"));
        seal.setEnabled(!seal.getEnabled());
        return ApiResponse.ok(sealRepo.save(seal));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authService.requireAdmin();
        sealRepo.deleteById(id);
        return ApiResponse.ok();
    }

    private void requireSealManager(Seal seal) {
        UserEntity me = authService.current();
        boolean self = Seal.OWNER_USER.equals(seal.getOwnerType()) && seal.getOwnerId().equals(me.getId());
        if (!Boolean.TRUE.equals(me.getAdmin()) && !self) {
            throw ApiException.forbidden("仅管理员或章属本人可维护章图");
        }
    }
}
