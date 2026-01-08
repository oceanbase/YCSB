#!/bin/bash
#
# =============================================================================
# HBase-style test$cf table creation script
# =============================================================================
#
# This script generates CREATE TABLE SQL for ycsb_test$cf table with:
# - Range partitions by G (ABS(T)) column
# - Key subpartitions by K_PREFIX column
# - Dynamic partition policy enabled
#
# Usage:
#   ./create_table.sh <range_partition_count> <key_subpartition_count> [start_timestamp] [partition_duration_ms] [output_file]
#
# Parameters:
#   range_partition_count    : Number of range partitions (required)
#   key_subpartition_count   : Number of key subpartitions per range partition (required)
#   start_timestamp          : Start timestamp (ms) or date string (optional, default: current time)
#   partition_duration_ms   : Time span for each range partition in milliseconds (optional, default: 1 month = 2592000000 ms)
#   output_file              : Optional output SQL file path (default: auto-generated filename)
#
# Examples:
#   ./create_table.sh 4 40
#   ./create_table.sh 4 40 1704067200000
#   ./create_table.sh 4 40 1704067200000 2592000000
#   ./create_table.sh 4 40 '2024-01-01 00:00:00' 2592000000 output.sql
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

show_help() {
  cat <<'EOF'
Usage:
  create_table.sh <range_partition_count> <key_subpartition_count> [start_timestamp] [partition_duration_ms] [output_file]

Parameters:
  range_partition_count    : Number of range partitions (required)
  key_subpartition_count   : Number of key subpartitions per range partition (required)
  start_timestamp          : Start timestamp (ms) or date string (optional, default: current time)
  partition_duration_ms   : Time span for each range partition in milliseconds (optional, default: 1 month = 2592000000 ms)
  output_file              : Optional output SQL file path (default: auto-generated filename)

Examples:
  create_table.sh 1 40
  create_table.sh 1 40 1704067200000
  create_table.sh 1 40 1704067200000 2592000000
  create_table.sh 1 40 '2024-01-01 00:00:00' 2592000000 output.sql
EOF
}

if [[ $# -lt 2 ]]; then
  show_help
  exit 1
fi

RANGE_PARTITION_COUNT="$1"
KEY_SUBPARTITION_COUNT="$2"

# Default values
# One month in milliseconds: 30 days * 24 hours * 60 minutes * 60 seconds * 1000 ms
DEFAULT_PARTITION_DURATION_MS=2592000000

# Parse optional parameters
START_TIMESTAMP=""
PARTITION_DURATION_MS=""
OUTPUT_FILE=""

# Get current time for comparison (in milliseconds)
CURRENT_TS_MS=$(($(date +%s) * 1000))

# Parse remaining arguments
if [[ $# -ge 3 ]]; then
  # Check if arg3 is a number (could be timestamp or duration)
  if [[ "$3" =~ ^[0-9]+$ ]]; then
    # Check if it's a reasonable timestamp (after 2000-01-01 and not too far in future)
    # Timestamps after 2000-01-01 are > 946684800000
    # If it's larger than current time + 10 years, it's likely a duration
    FUTURE_LIMIT=$((CURRENT_TS_MS + 10 * 365 * 24 * 60 * 60 * 1000))
    if [[ "$3" -gt 946684800000 ]] && [[ "$3" -lt $FUTURE_LIMIT ]]; then
      # Likely a timestamp
      START_TIMESTAMP="$3"
      if [[ $# -ge 4 ]]; then
        if [[ "$4" =~ ^[0-9]+$ ]]; then
          PARTITION_DURATION_MS="$4"
          OUTPUT_FILE="${5:-}"
        else
          OUTPUT_FILE="$4"
        fi
      fi
    else
      # Likely a duration (could be small number or very large number)
      PARTITION_DURATION_MS="$3"
      OUTPUT_FILE="${4:-}"
    fi
  elif [[ "$3" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2} ]]; then
    # Date string - definitely a timestamp
    START_TIMESTAMP="$3"
    if [[ $# -ge 4 ]]; then
      if [[ "$4" =~ ^[0-9]+$ ]]; then
        PARTITION_DURATION_MS="$4"
        OUTPUT_FILE="${5:-}"
      else
        OUTPUT_FILE="$4"
      fi
    fi
  else
    # Not a number or date string - must be output file
    OUTPUT_FILE="$3"
  fi
fi

# Validate required parameters
if ! [[ "$RANGE_PARTITION_COUNT" =~ ^[0-9]+$ ]] || [[ "$RANGE_PARTITION_COUNT" -le 0 ]]; then
  echo "Error: range_partition_count must be a positive integer" >&2
  exit 1
fi

if ! [[ "$KEY_SUBPARTITION_COUNT" =~ ^[0-9]+$ ]] || [[ "$KEY_SUBPARTITION_COUNT" -le 0 ]]; then
  echo "Error: key_subpartition_count must be a positive integer" >&2
  exit 1
fi

# Set defaults if not provided
if [[ -z "$PARTITION_DURATION_MS" ]]; then
  PARTITION_DURATION_MS="$DEFAULT_PARTITION_DURATION_MS"
fi

# Validate partition_duration_ms
if ! [[ "$PARTITION_DURATION_MS" =~ ^[0-9]+$ ]] || [[ "$PARTITION_DURATION_MS" -le 0 ]]; then
  echo "Error: partition_duration_ms must be a positive integer" >&2
  exit 1
fi

# Get current time in milliseconds
get_current_timestamp_ms() {
  local ts_sec
  ts_sec=$(date +%s)
  echo $((ts_sec * 1000))
}

# Parse start timestamp
parse_start_timestamp_ms() {
  local start="$1"
  if [[ "$start" =~ ^[0-9]+$ ]]; then
    echo "$start"
    return 0
  fi

  if [[ "$start" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2} ]]; then
    local ts_sec=""
    if [[ "${OSTYPE:-}" == "darwin"* ]]; then
      ts_sec=$(date -j -f "%Y-%m-%d %H:%M:%S" "$start" +%s 2>/dev/null || true)
      if [[ -z "$ts_sec" ]]; then
        ts_sec=$(date -j -f "%Y-%m-%d" "$start" +%s 2>/dev/null || true)
      fi
    else
      ts_sec=$(date -d "$start" +%s 2>/dev/null || true)
    fi
    if [[ -n "$ts_sec" ]]; then
      echo $((ts_sec * 1000))
      return 0
    fi
  fi

  echo "Error: Invalid start_timestamp format: $start" >&2
  echo "Please use either a millisecond timestamp (e.g., 1704067200000) or a date string (e.g., '2024-01-01 00:00:00')." >&2
  return 1
}

convert_timestamp_to_date() {
  local ts_ms="$1"
  local ts_sec=$((ts_ms / 1000))
  if [[ "${OSTYPE:-}" == "darwin"* ]]; then
    date -r "$ts_sec" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || date -j -f "%s" "$ts_sec" "+%Y-%m-%d %H:%M:%S"
  else
    date -d "@$ts_sec" "+%Y-%m-%d %H:%M:%S"
  fi
}

# Parse or use current time as start timestamp
if [[ -n "$START_TIMESTAMP" ]]; then
  START_TS_MS="$(parse_start_timestamp_ms "$START_TIMESTAMP")"
else
  START_TS_MS="$(get_current_timestamp_ms)"
fi

# Generate output file name if not provided
# Default behavior: output to both stdout and a file
if [[ -z "$OUTPUT_FILE" ]]; then
  OUTPUT_FILE="${SCRIPT_DIR}/ycsb_test_cf_r${RANGE_PARTITION_COUNT}_k${KEY_SUBPARTITION_COUNT}_d${PARTITION_DURATION_MS}.sql"
fi

# Calculate derived parameters
PARTITION_DURATION_DAYS=$((PARTITION_DURATION_MS / 1000 / 60 / 60 / 24))
PARTITION_DURATION_HOURS=$((PARTITION_DURATION_MS / 1000 / 60 / 60))
PARTITION_DURATION_MINUTES=$((PARTITION_DURATION_MS / 1000 / 60))
START_TIMESTAMP_FORMATTED="$(convert_timestamp_to_date "$START_TS_MS")"

# Prepare parameter display strings
if [[ -z "$START_TIMESTAMP" ]]; then
  START_TIMESTAMP_DISPLAY="<default: current time>"
else
  START_TIMESTAMP_DISPLAY="$START_TIMESTAMP"
fi

if [[ "$PARTITION_DURATION_MS" = "$DEFAULT_PARTITION_DURATION_MS" ]]; then
  PARTITION_DURATION_DISPLAY="${PARTITION_DURATION_MS} (default: 1 month)"
else
  PARTITION_DURATION_DISPLAY="$PARTITION_DURATION_MS"
fi

# Generate SQL output
generate_sql() {
  cat <<EOF
-- =============================================================================
-- HBase-style ycsb_test\$cf table creation SQL
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Input Parameters:
--   range_partition_count    : ${RANGE_PARTITION_COUNT}
--   key_subpartition_count   : ${KEY_SUBPARTITION_COUNT}
--   start_timestamp          : ${START_TIMESTAMP_DISPLAY}
--   partition_duration_ms    : ${PARTITION_DURATION_DISPLAY}
--
-- Derived Parameters:
--   start_ts_ms              : ${START_TS_MS}
--   start_timestamp_formatted: ${START_TIMESTAMP_FORMATTED}
--   partition_duration_days   : ${PARTITION_DURATION_DAYS}
--   partition_duration_hours  : ${PARTITION_DURATION_HOURS}
--   partition_duration_minutes: ${PARTITION_DURATION_MINUTES}
--
-- Table Structure:
--   - Range partitions by G column (G = ABS(T))
--   - Key subpartitions by K_PREFIX column (K_PREFIX = substring(K, 1, 5))
--   - Dynamic partition policy enabled
--
-- =============================================================================

CREATE TABLEGROUP ycsb_test;

CREATE TABLE \`ycsb_test\$cf\` (
  \`K\` varbinary(1024) NOT NULL,
  \`Q\` varbinary(256) NOT NULL,
  \`T\` bigint(20) NOT NULL,
  \`V\` varbinary(10240) DEFAULT NULL,
  \`G\` bigint(20) GENERATED ALWAYS AS (ABS(T)),
  \`K_PREFIX\` varbinary(1024) generated always as (substring(\`K\`, 1, 18)),
  PRIMARY KEY (\`K\`, \`Q\`, \`T\`)
) TABLEGROUP =  ycsb_test
  kv_attributes ='{"HBase": {}}'
  enable_macro_block_bloom_filter = True
  DYNAMIC_PARTITION_POLICY(
    ENABLE = true,
    TIME_UNIT = 'month',
    PRECREATE_TIME = '1 month',
    EXPIRE_TIME = '1 month',
    BIGINT_PRECISION = 'ms')
  PARTITION BY RANGE COLUMNS(\`G\`) SUBPARTITION BY KEY(\`K_PREFIX\`) SUBPARTITIONS ${KEY_SUBPARTITION_COUNT} (
EOF

  # Generate partition definitions
  # First partition boundary: start_time + 1 * partition_duration (e.g., start_time + 1 month)
  # Subsequent partitions: start_time + (i+1) * partition_duration
  local i
  for ((i = 0; i < RANGE_PARTITION_COUNT; i++)); do
    # Partition boundary = start_time + (partition_index + 1) * duration
    # p0: start_time + 1 * duration, p1: start_time + 2 * duration, etc.
    local partition_boundary=$((START_TS_MS + (i + 1) * PARTITION_DURATION_MS))
    local partition_boundary_date
    partition_boundary_date="$(convert_timestamp_to_date "$partition_boundary")"
    
    if [[ "$i" -eq $((RANGE_PARTITION_COUNT - 1)) ]]; then
      # Last partition: no comma
      echo "    PARTITION \`p${i}\` VALUES LESS THAN (${partition_boundary})  -- ${partition_boundary_date} (${partition_boundary} ms)"
    else
      # Other partitions: comma before comment
      echo "    PARTITION \`p${i}\` VALUES LESS THAN (${partition_boundary}),  -- ${partition_boundary_date} (${partition_boundary} ms)"
    fi
  done

  cat <<EOF
  );
EOF
}

# Output SQL: default to both stdout and file
# Use tee to output to both stdout and file simultaneously
generate_sql | tee "$OUTPUT_FILE"
echo "SQL also saved to: $OUTPUT_FILE" >&2
