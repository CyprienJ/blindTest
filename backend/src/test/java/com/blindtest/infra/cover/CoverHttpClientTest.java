package com.blindtest.infra.cover;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CoverHttpClientTest {

    @Test
    void allowsHttpsImagesOnKnownCoverHosts() {
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create(
                "https://cdn-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg"))).isTrue();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create(
                "https://is1-ssl.mzstatic.com/image/thumb/Music/x/600x600bb.jpg"))).isTrue();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create(
                "https://coverartarchive.org/release-group/1/front-500"))).isTrue();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create(
                "https://ia800100.us.archive.org/0/items/mbid-1/front.jpg"))).isTrue();
    }

    @Test
    void rejectsOtherSchemesHostsPortsAndCredentials() {
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("http://cdn-images.dzcdn.net/a.jpg"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://evil.example/a.jpg"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://notdzcdn.net/a.jpg"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://archive.org.evil.example/a"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://archive.org:8443/a"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://user@archive.org/a"))).isFalse();
        assertThat(CoverHttpClient.isAllowedImageUri(URI.create("https://127.0.0.1/a.jpg"))).isFalse();
    }
}
