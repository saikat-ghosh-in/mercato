package com.mercato.hardening;

import com.mercato.Service.FileService;
import com.mercato.ExceptionHandler.InvalidFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class FileSafetyTest {
    @TempDir Path root;
    FileService service(String base) {
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "uploadDir", root.resolve("images").toString());
        ReflectionTestUtils.setField(service, "baseUrl", base);
        return service;
    }

    @Test
    void renamedExecutableIsRejectedDespiteImageExtension() {
        assertThrows(InvalidFileException.class, () -> service("http://localhost").uploadImage(
            new MockMultipartFile("file", "photo.png", "image/png", "this is not an image".getBytes())));
    }

    @Test
    void imageUrlHasOneImagesSegmentAndOwnedImageCanBeDeleted() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1sAAAAASUVORK5CYII=");
        FileService service = service("http://localhost/images/");
        String url = service.uploadImage(new MockMultipartFile("file", "PHOTO.PNG", "image/png", png));
        assertTrue(url.startsWith("http://localhost/images/"));
        assertFalse(url.contains("/images/images/"));
        String name = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(Files.exists(root.resolve("images").resolve(name)));
        service.deleteImage("https://untrusted.invalid/images/" + name);
        assertTrue(Files.exists(root.resolve("images").resolve(name)));
        service.deleteImage(url);
        assertFalse(Files.exists(root.resolve("images").resolve(name)));
    }

    @Test
    void traversalCannotDeleteOutsideUploadDirectory() throws Exception {
        Files.createDirectories(root.resolve("images"));
        Path protectedFile = root.resolve("keep.txt");
        Files.writeString(protectedFile, "keep");
        service("http://localhost").deleteImage("http://localhost/images/../keep.txt");
        assertTrue(Files.exists(protectedFile));
    }
}
