package com.mercato.Service;

import com.mercato.ExceptionHandler.FileUploadException;
import com.mercato.ExceptionHandler.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
class ReviewMediaStorage {
    private static final Logger log = LoggerFactory.getLogger(ReviewMediaStorage.class);
    private static final long PHOTO_LIMIT = 3 * 1024 * 1024;
    private static final long VIDEO_LIMIT = 10 * 1024 * 1024;
    private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ");

    private final FileService images;
    private final Path directory;

    ReviewMediaStorage(FileService images, @Value("${file.upload.dir}") String uploadDirectory) {
        this.images = images;
        this.directory = Path.of(uploadDirectory).resolve("reviews");
    }

    record Media(List<String> photoUrls, String videoUrl) {}

    Media store(List<MultipartFile> photos, List<MultipartFile> videos) {
        if (photos.size() > 3 || videos.size() > 1) {
            throw new InvalidFileException("A review can include up to three photos and one video.");
        }
        for (MultipartFile photo : photos) {
            if (photo.getSize() > PHOTO_LIMIT) throw new InvalidFileException("Each photo must be 3 MB or smaller.");
            images.validateFile(photo);
        }
        MultipartFile video = videos.isEmpty() ? null : videos.get(0);
        if (video != null) validateVideo(video);
        if (photos.isEmpty() && video == null) return new Media(List.of(), null);

        List<Path> files = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                for (Path file : files) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException exception) {
                        log.warn("Could not remove a rolled-back review upload", exception);
                    }
                }
            }
        });

        try {
            Files.createDirectories(directory);
            List<String> photoUrls = new ArrayList<>();
            for (MultipartFile photo : photos) {
                photoUrls.add(write(photo, images.getExtension(photo.getOriginalFilename()), files));
            }
            return new Media(photoUrls, video == null ? null : write(video, ".mp4", files));
        } catch (IOException exception) {
            throw new FileUploadException("Failed to save review attachments", exception);
        }
    }

    private String write(MultipartFile upload, String extension, List<Path> files) throws IOException {
        String name = UUID.randomUUID() + extension;
        Path file = directory.resolve(name);
        try (var stream = upload.getInputStream(); var output = Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            files.add(file);
            stream.transferTo(output);
        }
        return "/images/reviews/" + name;
    }

    private void validateVideo(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > VIDEO_LIMIT) {
            throw new InvalidFileException("Choose a non-empty MP4 video of 10 MB or less.");
        }
        try (var stream = file.getInputStream()) {
            byte[] header = stream.readNBytes(16);
            if (header.length == 16 && new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) {
                long boxSize = Integer.toUnsignedLong(ByteBuffer.wrap(header).getInt());
                String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
                if (boxSize >= 16 && boxSize <= file.getSize() && MP4_BRANDS.contains(brand)) return;
            }
        } catch (IOException exception) {
            throw new InvalidFileException("Could not read the video file.");
        }
        throw new InvalidFileException("Choose a supported MP4 video.");
    }
}
