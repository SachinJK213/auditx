package com.auditx.enrichment.service;

public record GeoLocation(
        String country,
        String countryCode,
        String city,
        Double lat,
        Double lon,
        String isp,
        boolean privateIp
) {}
