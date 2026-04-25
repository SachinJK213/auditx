#!/bin/bash
# Run this once after docker compose up to create the Kibana index pattern and saved searches.
# Usage: bash elk/setup-kibana.sh

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
ES_URL="${ES_URL:-http://localhost:9200}"

echo "Waiting for Kibana to be ready..."
until curl -s "$KIBANA_URL/api/status" | grep -q '"level":"available"'; do
  sleep 5
done
echo "Kibana is ready."

# Create index pattern: auditx-logs-*
curl -s -X POST "$KIBANA_URL/api/saved_objects/index-pattern/auditx-logs" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "title": "auditx-logs-*",
      "timeFieldName": "@timestamp"
    }
  }' | jq .

# Set as default index pattern
curl -s -X POST "$KIBANA_URL/api/kibana/settings" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"changes": {"defaultIndex": "auditx-logs"}}' | jq .

# Create saved search: Errors only
curl -s -X POST "$KIBANA_URL/api/saved_objects/search/auditx-errors" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "title": "AUDITX - Errors",
      "description": "All ERROR level log entries across AUDITX services",
      "hits": 0,
      "columns": ["service", "traceId", "tenantId", "log_message"],
      "sort": [["@timestamp", "desc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"query\":{\"query_string\":{\"query\":\"level:ERROR\"}},\"index\":\"auditx-logs\"}"
      }
    }
  }' | jq .

# Create saved search: by tenant
curl -s -X POST "$KIBANA_URL/api/saved_objects/search/auditx-by-tenant" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "title": "AUDITX - By Tenant",
      "description": "Filter logs by tenantId",
      "hits": 0,
      "columns": ["service", "tenantId", "traceId", "userId", "eventId", "log_message"],
      "sort": [["@timestamp", "desc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"query\":{\"match_all\":{}},\"index\":\"auditx-logs\"}"
      }
    }
  }' | jq .

echo ""
echo "Setup complete. Open Kibana at $KIBANA_URL"
echo "  -> Discover -> select index pattern: auditx-logs-*"
