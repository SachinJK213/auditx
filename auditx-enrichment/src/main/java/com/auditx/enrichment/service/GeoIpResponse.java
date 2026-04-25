package com.auditx.enrichment.service;

public record GeoIpResponse(
        String status,
        String country,
        String countryCode,
        String city,
        Double lat,
        Double lon,
        String org
) {}
