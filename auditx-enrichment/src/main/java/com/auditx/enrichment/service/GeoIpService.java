package com.auditx.enrichment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GeoIpService {

    private static final Set<String> PRIVATE_PREFIXES = Set.of(
            "10.", "192.168.", "127.", "0.", "169.254.", "::1"
    );
    private static final Pattern PRIVATE_172 = Pattern.compile("^172\\.(1[6-9]|2\\d|3[01])\\.");

    private final WebClient webClient;

    @Value("${enrichment.geo-ip-url:http://ip-api.com/json}")
    private String geoIpUrl;

    public GeoIpService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<GeoLocation> lookup(String ip) {
        if (isPrivate(ip)) {
            return Mono.just(new GeoLocation(null, null, null, null, null, null, true));
        }
        return webClient.get()
                .uri(geoIpUrl + "/" + ip + "?fields=status,country,countryCode,city,lat,lon,org")
                .retrieve()
                .bodyToMono(GeoIpResponse.class)
                .map(r -> new GeoLocation(r.country(), r.countryCode(), r.city(), r.lat(), r.lon(), r.org(), false))
                .onErrorReturn(new GeoLocation(null, null, null, null, null, null, false));
    }

    private boolean isPrivate(String ip) {
        if (ip == null) return true;
        for (String prefix : PRIVATE_PREFIXES) {
            if (ip.startsWith(prefix)) return true;
        }
        return PRIVATE_172.matcher(ip).find();
    }
}
