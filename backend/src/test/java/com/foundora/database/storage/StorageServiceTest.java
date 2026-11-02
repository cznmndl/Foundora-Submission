package com.foundora.database.storage;

import com.foundora.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StorageServiceTest {

    private static final String BASE = "https://example.supabase.co";
    private static final String OBJECTS = BASE + "/storage/v1/object/item-images/items/";

    private MockRestServiceServer server;
    private StorageService storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new StorageService(builder, new SupabaseProperties(BASE, "sb_secret_test", "item-images"));
    }

    @Test
    void storesJpegUnderRandomNameWithSecretKey() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        server.expect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getURI().toString())
                        .matches(OBJECTS + "[0-9a-f-]{36}\\.jpg"))
                .andExpect(header("apikey", "sb_secret_test"))
                .andExpect(header("Authorization", "Bearer sb_secret_test"))
                .andExpect(header("Content-Type", "image/jpeg"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().bytes(jpeg))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String path = storage.storePhoto(jpeg);

        assertThat(path).matches("items/[0-9a-f-]{36}\\.jpg");
        server.verify();
    }

    @Test
    void storesCommentPhotosInTheirOwnFolder() {
        server.expect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getURI().toString())
                        .matches(BASE + "/storage/v1/object/item-images/comments/[0-9a-f-]{36}\\.jpg"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/storage/v1/object/item-images/comments/c.jpg"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        assertThat(storage.storePhoto(StorageService.COMMENTS, new byte[]{1})).matches("comments/[0-9a-f-]{36}\\.jpg");
        storage.deletePhoto("comments/c.jpg");
        assertThatThrownBy(() -> storage.storePhoto("secrets", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void downloadsWithSecretKey() {
        server.expect(requestTo(OBJECTS + "abc.jpg"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "sb_secret_test"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));

        StorageService.StoredPhoto photo = storage.downloadPhoto("items/abc.jpg");

        assertThat(photo.bytes()).containsExactly(1, 2, 3);
        assertThat(photo.contentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void deleteTreatsAMissingObjectAsDeleted() {
        server.expect(requestTo(OBJECTS + "gone.jpg")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"not_found\"}"));
        server.expect(requestTo(OBJECTS + "gone2.jpg")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        storage.deletePhoto("items/gone.jpg");
        storage.deletePhoto("items/gone2.jpg");
        server.verify();
    }

    @Test
    void deleteReportsOtherFailures() {
        server.expect(requestTo(OBJECTS + "x.jpg")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> storage.deletePhoto("items/x.jpg")).isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void quietDeleteSwallowsFailures() {
        server.expect(requestTo(OBJECTS + "x.jpg")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        storage.deletePhotoQuietly("items/x.jpg");
        server.verify();
    }

    @Test
    void refusesPathsOutsideTheItemsFolder() {
        assertThatThrownBy(() -> storage.downloadPhoto("../secrets.json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deletePhoto("items/../../x.jpg")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
