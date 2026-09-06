#!/usr/bin/env bash
# Aircargo Load Test Script
# Usage: ./load-test.sh [--url URL] [--users N] [--duration SEC] [--ramp SEC] [--token TOKEN]

set -euo pipefail

# Defaults
BASE_URL="${BASE_URL:-https://api.yourdomain.com}"
USERS="${USERS:-50}"
DURATION="${DURATION:-60}"
RAMP="${RAMP:-10}"
TOKEN="${TOKEN:-}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${BLUE}[LOAD]${NC} $*"; }
pass() { echo -e "${GREEN}[PASS]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }

# Parse args
while [[ $# -gt 0 ]]; do
  case $1 in
    --url) BASE_URL="$2"; shift 2 ;;
    --users) USERS="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --ramp) RAMP="$2"; shift 2 ;;
    --token) TOKEN="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# Check dependencies
for cmd in curl jq bc; do
  command -v $cmd >/dev/null || { fail "Missing dependency: $cmd"; }
done

# If no token provided, try to login
if [[ -z "$TOKEN" ]]; then
  log "No token provided, attempting login..."
  read -p "Email: " EMAIL
  read -s -p "Password (optional, press Enter for email-only): " PASS
  echo ""
  
  if [[ -n "$PASS" ]]; then
    TOKEN=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r .token)
  else
    TOKEN=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$EMAIL\"}" | jq -r .token)
  fi
  
  [[ -n "$TOKEN" && "$TOKEN" != "null" ]] || fail "Login failed"
  pass "Login successful"
fi

# Endpoints to test (method, path, weight)
ENDPOINTS=(
  "GET|/api/users|10"
  "GET|/api/flights?page=0&size=20|15"
  "GET|/api/mawbs?page=0&size=20|15"
  "GET|/api/bookings?page=0&size=20|15"
  "GET|/api/ulds?page=0&size=20|10"
  "GET|/api/receipts?page=0&size=20|10"
  "GET|/api/sites|5"
  "GET|/api/airlines|5"
  "GET|/api/aircraft-types|5"
  "GET|/actuator/health|10"
  "GET|/actuator/prometheus|5"
)

# Results file
RESULTS_DIR="/tmp/aircargo-load-$(date +%s)"
mkdir -p "$RESULTS_DIR"
RESULTS_FILE="$RESULTS_DIR/results.csv"
echo "timestamp,endpoint,method,status_code,latency_ms,success" > "$RESULTS_FILE"

# Worker function
worker() {
  local worker_id=$1
  local end_time=$(($(date +%s) + DURATION))
  
  while [[ $(date +%s) -lt $end_time ]]; do
    # Pick weighted endpoint
    local rand=$((RANDOM % 100))
    local cum=0
    local selected=""
    
    for ep in "${ENDPOINTS[@]}"; do
      IFS='|' read -r method path weight <<< "$ep"
      cum=$((cum + weight))
      if [[ $rand -lt $cum ]]; then
        selected="$method|$path"
        break
      fi
    done
    
    IFS='|' read -r method path <<< "$selected"
    local url="$BASE_URL$path"
    local start=$(date +%s%3N)
    
    if [[ "$method" == "GET" ]]; then
      response=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$url" 2>/dev/null || echo -e "\n000")
    else
      response=$(curl -s -w "\n%{http_code}" -X "$method" -H "Authorization: Bearer $TOKEN" "$url" 2>/dev/null || echo -e "\n000")
    fi
    
    local status=$(echo "$response" | tail -1)
    local end=$(date +%s%3N)
    local latency=$((end - start))
    local success="false"
    
    if [[ "$status" =~ ^2[0-9]{2}$ ]]; then
      success="true"
    fi
    
    echo "$(date -u +%Y-%m-%dT%H:%M:%S),$path,$method,$status,$latency,$success" >> "$RESULTS_FILE"
    
    # Small delay to not overwhelm
    sleep 0.01
  done
}

# Ramp up
log "Starting load test: $USERS users, ${DURATION}s duration, ${RAMP}s ramp"
log "Target: $BASE_URL"
log "Results: $RESULTS_FILE"

PIDS=()
for ((i=1; i<=USERS; i++)); do
  worker $i &
  PIDS+=($!)
  if [[ $((i % 10)) -eq 0 ]]; then
    sleep $((RAMP / (USERS / 10)))
  fi
done

# Progress bar
log "Running..."
for ((elapsed=0; elapsed<DURATION; elapsed+=5)); do
  sleep 5
  local completed=$(wc -l < "$RESULTS_FILE")
  local rate=$(echo "scale=1; ($completed - 1) / ($elapsed + 5)" | bc)
  echo -ne "\r  Elapsed: ${elapsed}s/${DURATION}s | Requests: $completed | Rate: ${rate} req/s"
done
echo ""

# Wait for all workers
for pid in "${PIDS[@]}"; do
  wait $pid 2>/dev/null || true
done

# Analyze results
log "Analyzing results..."

TOTAL=$(($(wc -l < "$RESULTS_FILE") - 1))
SUCCESS=$(awk -F, '$6=="true"' "$RESULTS_FILE" | wc -l)
FAILED=$((TOTAL - SUCCESS))
ERROR_RATE=$(echo "scale=2; $FAILED * 100 / $TOTAL" | bc)

# Latency percentiles
LATENCIES=$(awk -F, 'NR>1 {print $5}' "$RESULTS_FILE" | sort -n)
P50=$(echo "$LATENCIES" | awk -v p=50 'NR==int(p/100*NR){print; exit}')
P95=$(echo "$LATENCIES" | awk -v p=95 'NR==int(p/100*NR){print; exit}')
P99=$(echo "$LATENCIES" | awk -v p=99 'NR==int(p/100*NR){print; exit}')
AVG=$(echo "$LATENCIES" | awk '{sum+=$1} END {if(NR>0) print sum/NR; else print 0}')

# Status code breakdown
echo ""
echo "=== RESULTS ==="
echo "Total requests:     $TOTAL"
echo "Successful:         $SUCCESS"
echo "Failed:             $FAILED"
echo "Error rate:         ${ERROR_RATE}%"
echo "Avg latency:        ${AVG}ms"
echo "P50 latency:        ${P50}ms"
echo "P95 latency:        ${P95}ms"
echo "P99 latency:        ${P99}ms"
echo ""
echo "Status codes:"
awk -F, 'NR>1 {print $4}' "$RESULTS_FILE" | sort | uniq -c | sort -rn

# Per-endpoint breakdown
echo ""
echo "Per-endpoint:"
awk -F, 'NR>1 {
  ep=$2; 
  lat[ep]+=$5; 
  cnt[ep]++; 
  if($6=="true") ok[ep]++; else err[ep]++
} END {
  for(e in cnt) {
    printf "%-35s %6d req  %6.1fms avg  %5.1f%% err\n", e, cnt[e], lat[e]/cnt[e], (err[e]*100/cnt[e])
  }
}' "$RESULTS_FILE" | sort

# Thresholds
echo ""
echo "=== THRESHOLDS ==="
PASS=true

if (( $(echo "$ERROR_RATE > 1" | bc -l) )); then
  fail "Error rate ${ERROR_RATE}% > 1%"
  PASS=false
else
  pass "Error rate ${ERROR_RATE}% ≤ 1%"
fi

if (( $(echo "$P95 > 1000" | bc -l) )); then
  fail "P95 latency ${P95}ms > 1000ms"
  PASS=false
else
  pass "P95 latency ${P95}ms ≤ 1000ms"
fi

if (( $(echo "$P99 > 2000" | bc -l) )); then
  fail "P99 latency ${P99}ms > 2000ms"
  PASS=false
else
  pass "P99 latency ${P99}ms ≤ 2000ms"
fi

# Save detailed report
cat > "$RESULTS_DIR/summary.json" <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "config": {
    "base_url": "$BASE_URL",
    "users": $USERS,
    "duration_sec": $DURATION,
    "ramp_sec": $RAMP
  },
  "results": {
    "total_requests": $TOTAL,
    "successful": $SUCCESS,
    "failed": $FAILED,
    "error_rate_pct": $ERROR_RATE,
    "avg_latency_ms": $AVG,
    "p50_ms": $P50,
    "p95_ms": $P95,
    "p99_ms": $P99
  },
  "passed": $PASS
}
EOF

echo ""
echo "Report saved: $RESULTS_DIR/summary.json"
echo "Raw data:     $RESULTS_FILE"

if $PASS; then
  pass "LOAD TEST PASSED"
  exit 0
else
  fail "LOAD TEST FAILED"
  exit 1
fi