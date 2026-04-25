package com.auditx.riskengine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "risk_rules")
public class RiskRuleDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String ruleName;
    private String ruleType; // "FAILED_LOGIN_THRESHOLD" | "GEO_ANOMALY" | "CUSTOM"
    private Double weight;
    private boolean active;
    private Integer threshold;       // for FAILED_LOGIN_THRESHOLD: count of failures
    private Integer windowMinutes;   // rolling window in minutes

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getThreshold() { return threshold; }
    public void setThreshold(Integer threshold) { this.threshold = threshold; }

    public Integer getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(Integer windowMinutes) { this.windowMinutes = windowMinutes; }
}
