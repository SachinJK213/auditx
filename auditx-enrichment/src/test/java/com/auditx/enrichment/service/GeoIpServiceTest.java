package com.auditx.enrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoIpServiceTest {

    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec<?> uriSpec;
    private WebClient.RequestHeadersSpec<?> headersSpec;
    private WebClient.ResponseSpec responseSpec;

    private GeoIpService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        headersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.build()).thenReturn(webClient);

        service = new GeoIpService(builder);
        ReflectionTestUtils.setField(service, "geoIpUrl", "http://ip-api.com/json");
    }

    @Test
    void lookup_privateIp_returnsPrivateGeoLocation() {
        StepVerifier.create(service.lookup("192.168.1.10"))
                .assertNext(geo -> {
                    assertThat(geo.privateIp()).isTrue();
                    assertThat(geo.country()).isNull();
                    assertThat(geo.city()).isNull();
                })
                .verifyComplete();

        verify(webClient, never()).get();
    }

    @Test
    void lookup_localhost_returnsPrivate() {
        StepVerifier.create(service.lookup("127.0.0.1"))
                .assertNext(geo -> assertThat(geo.privateIp()).isTrue())
                .verifyComplete();

        verify(webClient, never()).get();
    }

    @Test
    void lookup_172private_returnsPrivate() {
        StepVerifier.create(service.lookup("172.18.0.1"))
                .assertNext(geo -> assertThat(geo.privateIp()).isTrue())
                .verifyComplete();

        verify(webClient, never()).get();
    }

    @SuppressWarnings("unchecked")
    @Test
    void lookup_publicIp_callsApi() {
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(GeoIpResponse.class))
                .thenReturn(Mono.just(new GeoIpResponse(
                        "success", "United States", "US", "Mountain View",
                        37.4, -122.1, "Google LLC")));

        StepVerifier.create(service.lookup("8.8.8.8"))
                .assertNext(geo -> {
                    assertThat(geo.privateIp()).isFalse();
                    assertThat(geo.country()).isEqualTo("United States");
                    assertThat(geo.countryCode()).isEqualTo("US");
                    assertThat(geo.city()).isEqualTo("Mountain View");
                    assertThat(geo.lat()).isEqualTo(37.4);
                    assertThat(geo.lon()).isEqualTo(-122.1);
                    assertThat(geo.isp()).isEqualTo("Google LLC");
                })
                .verifyComplete();

        verify(webClient).get();
    }

    @SuppressWarnings("unchecked")
    @Test
    void lookup_apiError_returnsEmptyGeoLocation() {
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(GeoIpResponse.class))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(service.lookup("8.8.8.8"))
                .assertNext(geo -> {
                    assertThat(geo.privateIp()).isFalse();
                    assertThat(geo.country()).isNull();
                    assertThat(geo.city()).isNull();
                    assertThat(geo.isp()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void lookup_nullIp_returnsPrivate() {
        StepVerifier.create(service.lookup(null))
                .assertNext(geo -> assertThat(geo.privateIp()).isTrue())
                .verifyComplete();

        verify(webClient, never()).get();
    }
}
