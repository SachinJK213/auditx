package com.auditx.enrichment.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "enriched_events")
public class EnrichedEventDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String eventId;
    private String userId;
    private String action;
    private String sourceIp;
    private String outcome;
    private Instant timestamp;
    private Double riskScore;
    private String country;
    private String countryCode;
    private String city;
    private Double lat;
    private Double lon;
    private String isp;
    private boolean privateIp;
    private Instant enrichedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getIsp() { return isp; }
    public void setIsp(String isp) { this.isp = isp; }

    public boolean isPrivateIp() { return privateIp; }
    public void setPrivateIp(boolean privateIp) { this.privateIp = privateIp; }

    public Instant getEnrichedAt() { return enrichedAt; }
    public void setEnrichedAt(Instant enrichedAt) { this.enrichedAt = enrichedAt; }
}
