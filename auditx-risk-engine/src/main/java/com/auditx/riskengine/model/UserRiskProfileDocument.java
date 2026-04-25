package com.auditx.riskengine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_risk_profiles")
@CompoundIndex(name = "tenant_user_idx", def = "{'tenantId': 1, 'userId': 1}", unique = true)
public class UserRiskProfileDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String userId;
    private Double cumulativeScore;     // weighted moving average across all events
    private Integer eventCount;
    private Integer highRiskEventCount; // events with score > 70
    private Instant lastUpdated;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Double getCumulativeScore() { return cumulativeScore; }
    public void setCumulativeScore(Double cumulativeScore) { this.cumulativeScore = cumulativeScore; }

    public Integer getEventCount() { return eventCount; }
    public void setEventCount(Integer eventCount) { this.eventCount = eventCount; }

    public Integer getHighRiskEventCount() { return highRiskEventCount; }
    public void setHighRiskEventCount(Integer highRiskEventCount) { this.highRiskEventCount = highRiskEventCount; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
