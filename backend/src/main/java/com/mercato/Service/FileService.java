package com.mercato.Service;

import com.mercato.ExceptionHandler.FileUploadException;
import com.mercato.ExceptionHandler.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.base.url}")
    private String baseUrl;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};

    public String uploadImage(MultipartFile file) {
        validateFile(file);

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename);
            String filename = UUID.randomUUID() + extension;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(filename);
            try (InputStream stream = file.getInputStream()) {
                Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return imageBaseUrl() + "/" + filename;
        } catch (IOException e) {
            throw new FileUploadException("Failed to upload image", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            if (!imageUrl.startsWith(imageBaseUrl() + "/")) return;
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            if (!filename.matches("[a-fA-F0-9-]{36}\\.(jpg|jpeg|png|webp)")) return;
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = root.resolve(filename).normalize();
            if (!filePath.getParent().equals(root)) throw new InvalidFileException("Invalid image path");

            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            throw new FileUploadException("Failed to delete image", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File size exceeds maximum limit of 5MB");
        }

        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);

        boolean isAllowed = false;
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (extension.equalsIgnoreCase(allowedExt)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new InvalidFileException("File type not allowed. Only JPG, PNG, and WebP are supported.");
        }
        try (InputStream stream = file.getInputStream()) {
            byte[] header = stream.readNBytes(12);
            String ext = extension.toLowerCase(java.util.Locale.ROOT);
            boolean jpeg = header.length >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216 && (header[2] & 255) == 255;
            boolean png = header.length >= 8 && header[0] == (byte) 137 && header[1] == 80 && header[2] == 78 && header[3] == 71
                    && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10;
            boolean webp = header.length >= 12 && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            if (!(ext.equals(".png") && png || (ext.equals(".jpg") || ext.equals(".jpeg")) && jpeg || ext.equals(".webp") && webp))
                throw new InvalidFileException("File contents do not match a supported image format");
        } catch (IOException e) {
            throw new InvalidFileException("Could not read image file");
        }
    }

    private String imageBaseUrl() {
        String root = baseUrl.replaceAll("/+$", "");
        return root.endsWith("/images") ? root : root + "/images";
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new InvalidFileException("Invalid filename");
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(java.util.Locale.ROOT);
    }
}
