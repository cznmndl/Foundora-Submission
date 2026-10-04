package com.foundora.service;

import com.foundora.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StorageServiceTest {

    private static final String BASE = "https://example.supabase.co";

    private MockRestServiceServer server;
    private StorageService storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new StorageService(builder, new SupabaseProperties(BASE, "sb_secret_test", "item-images"));
    }

    @Test
    void uploadsImageWithSecretKeyAndReturnsPublicUrl() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        server.expect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getURI().toString())
                        .matches(BASE + "/storage/v1/object/item-images/items/[0-9a-f-]{36}\\.png"))
                .andExpect(header("apikey", "sb_secret_test"))
                .andExpect(header("Authorization", "Bearer sb_secret_test"))
                .andExpect(header("Content-Type", "image/png"))
                .andExpect(content().bytes(png))
                .andRespond(withSuccess("{\"Key\":\"item-images/items/x.png\"}", MediaType.APPLICATION_JSON));

        // The client's filename extension is ignored; the stored name comes from the content type.
        String url = storage.uploadImage(new MockMultipartFile("imageFile", "photo.html", "image/png", png));

        assertThat(url).matches(BASE + "/storage/v1/object/public/item-images/items/[0-9a-f-]{36}\\.png");
        server.verify();
    }

    @Test
    void returnsNullWhenNoPhotoWasChosen() {
        assertThat(storage.uploadImage(null)).isNull();
        assertThat(storage.uploadImage(new MockMultipartFile("imageFile", "", "application/octet-stream", new byte[0]))).isNull();
        server.verify();
    }

    @Test
    void rejectsNonImageUploads() {
        var html = new MockMultipartFile("imageFile", "x.png", "text/html", "<script>".getBytes());

        assertThatThrownBy(() -> storage.uploadImage(html)).isInstanceOf(ResponseStatusException.class);
        server.verify();
    }
}
