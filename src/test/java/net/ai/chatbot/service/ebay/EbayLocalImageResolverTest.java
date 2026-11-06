package net.ai.chatbot.service.ebay;

import net.ai.chatbot.service.storage.LocalUserStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayLocalImageResolverTest {

    @TempDir
    Path root;

    private LocalUserStorageService storage() {
        LocalUserStorageService storage = new LocalUserStorageService();
        ReflectionTestUtils.setField(storage, "storageRoot", root.toString());
        ReflectionTestUtils.setField(storage, "publicBaseUrl", "https://subratapc.net");
        return storage;
    }

    /** Port 1 refuses connections, so the loopback fallback finds nothing. */
    private EbayLocalImageResolver resolver(LocalUserStorageService storage) {
        return new EbayLocalImageResolver(storage, "http://api.jadeordersmedia.com",
                "https://app.example.test", "https://subratapc.net", 1);
    }

    @Test
    void readsTheStoredFileForEachUrlShape() throws Exception {
        LocalUserStorageService storage = storage();
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
        LocalUserStorageService.StoredFile saved = storage.saveUpload(
                "user_36z3oFaJOz9KfToNtKajVLYrGve", "shuvra.dev12@gmail.com", png, "png");
        EbayLocalImageResolver resolver = resolver(storage);

        for (String url : new String[] {
                saved.publicUrl(),
                "http://localhost:8080/v1/api/public/user-files/" + saved.fileName(),
                "http://127.0.0.1:8080/api/public/user-files/" + saved.fileName(),
                "https://api.jadeordersmedia.com/v1/api/public/user-files/" + saved.fileName(),
                "https://app.example.test/v1/api/public/media/" + saved.fileName() + "?t=1",
                "https://subratapc.net/storage/" + saved.relativePath(),
                "https://sgenamkaqezylpyiqbsb.supabase.co/storage/v1/object/public/media/" + saved.relativePath() }) {
            assertTrue(resolver.isLocal(url), url);
            Optional<EbayListingFields.DecodedImage> image = resolver.load(url);
            assertTrue(image.isPresent(), url);
            assertArrayEquals(png, image.get().bytes(), url);
            assertEquals("image/png", image.get().contentType(), url);
            assertEquals("png", image.get().extension(), url);
        }
    }

    @Test
    void reportsAMissingFileAsLocalButEmpty() {
        EbayLocalImageResolver resolver = resolver(storage());
        String url = "https://subratapc.net/v1/api/public/user-files/d32d19f5-ffb2-4870-ac18-dc08009f27eb.png";
        assertTrue(resolver.isLocal(url));
        assertTrue(resolver.load(url).isEmpty());
    }

    @Test
    void ignoresPublicUrls() {
        EbayLocalImageResolver resolver = resolver(storage());
        assertFalse(resolver.isLocal("https://i.ebayimg.com/00/s/abc.jpg"));
        assertTrue(resolver.load("https://cdn.example.com/abc.png").isEmpty());
    }

    @Test
    void refusesRelativePathsOutsideUserStorage() {
        LocalUserStorageService storage = storage();
        assertTrue(storage.findRelative("users/../secret/images/a.png").isEmpty());
        assertTrue(storage.findRelative("users/u1/other/a.png").isEmpty());
        assertTrue(storage.findRelative("config/a.png").isEmpty());
    }
}
