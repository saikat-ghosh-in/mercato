package com.mercato.Controller;

import com.mercato.Payloads.Response.ImageUploadResponse;
import com.mercato.Service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileService fileService;

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadProductImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileService.uploadImage(file);
        return ResponseEntity.ok(new ImageUploadResponse(
                imageUrl,
                "Image uploaded successfully"
        ));
    }

}
