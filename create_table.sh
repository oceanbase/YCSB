#!/bin/bash
#
# =============================================================================
# Table creation script for HBase, Table and Timeseries models
# =============================================================================
#
# This script generates CREATE TABLE SQL for HBase, Table and Timeseries models
# with support for single-level (range) and double-level (range-key) partitions
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Default values
DEFAULT_PARTITION_DURATION_MS=86400000  # 1 day in milliseconds
DEFAULT_TABLE_NAME_HBASE="ycsb_test"
DEFAULT_FAMILY_HBASE="cf"
DEFAULT_TABLE_NAME_TS="ycsb_test"
DEFAULT_FAMILY_TS="ts_cf"

# Global variables
MODE=""
PARTITION_TYPE=""
MAX_KEY=""
PARTITION_COUNT=""
KEY_LENGTH=""
START_TIMESTAMP=""
PARTITION_DURATION_MS=""
KEY_SUBPARTITION_COUNT=""
TABLE_NAME=""
FAMILY=""
OUTPUT_FILE=""

# =============================================================================
# Help function
# =============================================================================

show_help() {
  cat <<'EOF'
Usage:
  create_table.sh [OPTIONS]

Options:
  --mode MODE                    Model type: 'hbase', 'table' or 'timeseries' (optional, default: hbase)
  --type TYPE                    Partition type: 'first_part' or 'sec_part' (optional, default: first_part)
  
  For first-level partition (--type first_part):
    --max_key MAX_KEY            Maximum key value (required)
    --partition_count COUNT      Number of partitions (required)
    --key_length LENGTH          Key length for formatting (required)
  
  For second-level partition (--type sec_part):
    --start_timestamp TS         Start timestamp (ms) or date string (optional, default: current time)
    --partition_duration_ms MS  Partition time span in milliseconds (optional, default: 86400000 = 1 day)
    --key_subpartition_count N  Number of key subpartitions (required)
  
  Common options:
    --table_name NAME           Table name (optional, default: ycsb_test/ycsb_table)
    --family FAMILY             Column family name (optional, only for hbase/timeseries)
    --output_file FILE          Output SQL file path (optional, auto-generated)
    --help                      Show this help message

Examples:
  # Table model first-level partition
  ./create_table.sh --mode table --max_key 1000 --partition_count 4 --key_length 12
  
  # HBase first-level partition (using defaults)
  ./create_table.sh --max_key 1000 --partition_count 4 --key_length 12
  
  # HBase second-level partition
  ./create_table.sh --mode hbase --type sec_part --key_subpartition_count 40 --start_timestamp 1704067200000
EOF
}
}

# =============================================================================
# Parameter parsing
# =============================================================================

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --mode)
        MODE="$2"
        shift 2
        ;;
      --type)
        PARTITION_TYPE="$2"
        shift 2
        ;;
      --max_key)
        MAX_KEY="$2"
        shift 2
        ;;
      --partition_count)
        PARTITION_COUNT="$2"
        shift 2
        ;;
      --key_length)
        KEY_LENGTH="$2"
        shift 2
        ;;
      --start_timestamp)
        START_TIMESTAMP="$2"
        shift 2
        ;;
      --partition_duration_ms)
        PARTITION_DURATION_MS="$2"
        shift 2
        ;;
      --key_subpartition_count)
        KEY_SUBPARTITION_COUNT="$2"
        shift 2
        ;;
      --table_name)
        TABLE_NAME="$2"
        shift 2
        ;;
      --family)
        FAMILY="$2"
        shift 2
        ;;
      --output_file)
        OUTPUT_FILE="$2"
        shift 2
        ;;
      --help|-h)
        show_help
        exit 0
        ;;
      *)
        echo "Error: Unknown option: $1" >&2
        show_help
        exit 1
        ;;
    esac
  done

  # Set defaults
  if [[ -z "$MODE" ]]; then
    MODE="hbase"
  fi

  if [[ -z "$PARTITION_TYPE" ]]; then
    PARTITION_TYPE="first_part"
  fi

  # Validate mode
  if [[ "$MODE" != "hbase" && "$MODE" != "table" && "$MODE" != "timeseries" ]]; then
    echo "Error: --mode must be 'hbase', 'table' or 'timeseries'" >&2
    exit 1
  fi

  # Validate partition type
  if [[ "$PARTITION_TYPE" != "first_part" && "$PARTITION_TYPE" != "sec_part" ]]; then
    echo "Error: --type must be 'first_part' or 'sec_part'" >&2
    exit 1
  fi

  # Validate first-level partition parameters
  if [[ "$PARTITION_TYPE" == "first_part" ]]; then
    if [[ -z "$MAX_KEY" ]]; then
      echo "Error: --max_key is required for first-level partition" >&2
      exit 1
    fi
    if ! [[ "$MAX_KEY" =~ ^[0-9]+$ ]] || [[ "$MAX_KEY" -le 0 ]]; then
      echo "Error: --max_key must be a positive integer" >&2
      exit 1
    fi

    if [[ -z "$PARTITION_COUNT" ]]; then
      echo "Error: --partition_count is required for first-level partition" >&2
      exit 1
    fi
    if ! [[ "$PARTITION_COUNT" =~ ^[0-9]+$ ]] || [[ "$PARTITION_COUNT" -le 0 ]]; then
      echo "Error: --partition_count must be a positive integer" >&2
      exit 1
    fi

    if [[ -z "$KEY_LENGTH" ]]; then
      echo "Error: --key_length is required for first-level partition" >&2
      exit 1
    fi
    if ! [[ "$KEY_LENGTH" =~ ^[0-9]+$ ]] || [[ "$KEY_LENGTH" -le 0 ]]; then
      echo "Error: --key_length must be a positive integer" >&2
      exit 1
    fi
  fi

  # Validate second-level partition parameters
  if [[ "$PARTITION_TYPE" == "sec_part" ]]; then
    if [[ -z "$KEY_SUBPARTITION_COUNT" ]]; then
      echo "Error: --key_subpartition_count is required for second-level partition" >&2
      exit 1
    fi
if ! [[ "$KEY_SUBPARTITION_COUNT" =~ ^[0-9]+$ ]] || [[ "$KEY_SUBPARTITION_COUNT" -le 0 ]]; then
      echo "Error: --key_subpartition_count must be a positive integer" >&2
  exit 1
fi

    # Set default partition_duration_ms if not provided
if [[ -z "$PARTITION_DURATION_MS" ]]; then
  PARTITION_DURATION_MS="$DEFAULT_PARTITION_DURATION_MS"
fi
if ! [[ "$PARTITION_DURATION_MS" =~ ^[0-9]+$ ]] || [[ "$PARTITION_DURATION_MS" -le 0 ]]; then
      echo "Error: --partition_duration_ms must be a positive integer" >&2
  exit 1
fi
  fi

  # Set default table name and family
  if [[ -z "$TABLE_NAME" ]]; then
    if [[ "$MODE" == "hbase" ]]; then
      TABLE_NAME="$DEFAULT_TABLE_NAME_HBASE"
    else
      TABLE_NAME="$DEFAULT_TABLE_NAME_TS"
    fi
  fi

  if [[ -z "$FAMILY" ]]; then
    if [[ "$MODE" == "hbase" ]]; then
      FAMILY="$DEFAULT_FAMILY_HBASE"
    else
      FAMILY="$DEFAULT_FAMILY_TS"
    fi
  fi
}

# =============================================================================
# Utility functions
# =============================================================================

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

# Format key as string with specified length (left-padded with zeros)
format_key() {
  local key_value="$1"
  local length="$2"
  printf "%0${length}d" "$key_value"
}

# Generate output filename
generate_filename() {
  local table="$1"
  local mode="$2"
  local range_count="${3:-0}"
  local key_count="${4:-0}"
  local current_ts=$(date +%s)
  
  echo "${table}_${mode}_r${range_count}_k${key_count}_${current_ts}.sql"
}

# =============================================================================
# SQL Generation Functions
# =============================================================================

# Generate Table model single-level partition SQL
generate_table_single_partition() {
  local table_name="$1"
  local max_key="$2"
  local partition_count="$3"
  local key_length="$4"
  
  local step=$((max_key / partition_count))
  local full_table_name="\`${table_name}\`"
  
  cat <<EOF
-- =============================================================================
-- Table model single-level range partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Max key: ${max_key}
--   Partition count: ${partition_count}
--   Key length: ${key_length}
--
-- =============================================================================

CREATE TABLE ${full_table_name} (
  \`ycsb_key\` varbinary(1024) NOT NULL,
  \`ycsb_value\` varbinary(1048576) DEFAULT NULL,
  PRIMARY KEY (\`ycsb_key\`)
) PARTITION BY RANGE COLUMNS(\`ycsb_key\`) (
EOF

  local i
  for ((i = 0; i < partition_count; i++)); do
    local boundary=$(((i + 1) * step))
    local formatted_key=$(format_key "$boundary" "$key_length")
    
    if [[ "$i" -eq $((partition_count - 1)) ]]; then
      echo "  PARTITION \`p${i}\` VALUES LESS THAN (MAXVALUE)"
    else
      echo "  PARTITION \`p${i}\` VALUES LESS THAN ('${formatted_key}'),"
    fi
  done

  cat <<EOF
);
EOF
}

# Generate Table model key partition SQL
generate_table_key_partition() {
  local table_name="$1"
  local subpartition_count="$2"
  
  local full_table_name="\`${table_name}\`"
  
  cat <<EOF
-- =============================================================================
-- Table model key partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Partition count: ${subpartition_count}
--
-- =============================================================================

CREATE TABLE ${full_table_name} (
  \`ycsb_key\` varbinary(1024) NOT NULL,
  \`ycsb_value\` varbinary(1048576) DEFAULT NULL,
  PRIMARY KEY (\`ycsb_key\`)
) PARTITION BY KEY(\`ycsb_key\`) PARTITIONS ${subpartition_count};
EOF
}

# Generate HBase single-level partition SQL
generate_hbase_single_partition() {
  local table_name="$1"
  local family="$2"
  local max_key="$3"
  local partition_count="$4"
  local key_length="$5"
  
  local step=$((max_key / partition_count))
  local full_table_name="\`${table_name}\$${family}\`"
  
  cat <<EOF
-- =============================================================================
-- HBase single-level range partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Family: ${family}
--   Max key: ${max_key}
--   Partition count: ${partition_count}
--   Key length: ${key_length}
--
-- =============================================================================

CREATE TABLE ${full_table_name} (
  \`K\` varbinary(1024) NOT NULL,
  \`Q\` varbinary(256) NOT NULL,
  \`T\` bigint(20) NOT NULL,
  \`V\` varbinary(1048576) DEFAULT NULL,
  PRIMARY KEY (\`K\`, \`Q\`, \`T\`)
) PARTITION BY RANGE COLUMNS(\`K\`) (
EOF

  local i
  for ((i = 0; i < partition_count; i++)); do
    local boundary=$(((i + 1) * step))
    local formatted_key=$(format_key "$boundary" "$key_length")
    
    if [[ "$i" -eq $((partition_count - 1)) ]]; then
      echo "  PARTITION \`p${i}\` VALUES LESS THAN (MAXVALUE)"
    else
      echo "  PARTITION \`p${i}\` VALUES LESS THAN ('${formatted_key}'),"
    fi
  done

  cat <<EOF
);
EOF
}

# Generate HBase double-level partition SQL
generate_hbase_double_partition() {
  local table_name="$1"
  local family="$2"
  local start_ts_ms="$3"
  local partition_duration_ms="$4"
  local key_subpartition_count="$5"
  local range_partition_count="${6:-1}"
  
  local full_table_name="\`${table_name}\$${family}\`"
  local start_ts_formatted=$(convert_timestamp_to_date "$start_ts_ms")
  
  cat <<EOF
-- =============================================================================
-- HBase double-level range-key partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Family: ${family}
--   Start timestamp: ${start_ts_ms} (${start_ts_formatted})
--   Partition duration: ${partition_duration_ms} ms
--   Key subpartition count: ${key_subpartition_count}
--   Range partition count: ${range_partition_count}
--
-- =============================================================================

CREATE TABLE ${full_table_name} (
  \`K\` varbinary(1024) NOT NULL,
  \`Q\` varbinary(256) NOT NULL,
  \`T\` bigint(20) NOT NULL,
  \`V\` varbinary(1048576) DEFAULT NULL,
  \`G\` bigint(20) GENERATED ALWAYS AS (ABS(\`T\`)),
  \`K_PREFIX\` varbinary(1024) generated always as (substring(\`K\`, 1, 16)),
  PRIMARY KEY (\`K\`, \`Q\`, \`T\`)
) PARTITION BY RANGE COLUMNS(\`G\`) SUBPARTITION BY KEY(\`K_PREFIX\`) SUBPARTITIONS ${key_subpartition_count} (
EOF

  local i
  # Generate range partitions
  # First partition (i=0): boundary = start_timestamp + partition_duration
  # Second partition (i=1): boundary = start_timestamp + 2 * partition_duration
  # And so on...
  for ((i = 0; i < range_partition_count; i++)); do
    local partition_boundary=$((start_ts_ms + (i + 1) * partition_duration_ms))
    local partition_boundary_date=$(convert_timestamp_to_date "$partition_boundary")
    echo "  PARTITION \`p${i}\` VALUES LESS THAN (${partition_boundary}),  -- ${partition_boundary_date} (${partition_boundary} ms)"
  done
  
  # Add MAXVALUE partition
  echo "  PARTITION \`p${range_partition_count}\` VALUES LESS THAN (MAXVALUE)"

  cat <<EOF
);
EOF
}

# Generate Timeseries single-level partition SQL
generate_timeseries_single_partition() {
  local table_name="$1"
  local family="$2"
  local max_key="$3"
  local partition_count="$4"
  local key_length="$5"
  
  local step=$((max_key / partition_count))
  local full_table_name="\`${table_name}\$${family}\`"
  
  cat <<EOF
-- =============================================================================
-- Timeseries single-level range partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Family: ${family}
--   Max key: ${max_key}
--   Partition count: ${partition_count}
--   Key length: ${key_length}
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS ${full_table_name} (
  \`K\` varbinary(1024) NOT NULL,
  \`T\` bigint(20) NOT NULL,
  \`S\` bigint(20) NOT NULL,
  \`V\` json NOT NULL,
  PRIMARY KEY (\`K\`, \`T\`, \`S\`)
) PARTITION BY RANGE COLUMNS(\`K\`) (
EOF

  local i
  for ((i = 0; i < partition_count; i++)); do
    local boundary=$(((i + 1) * step))
    local formatted_key=$(format_key "$boundary" "$key_length")
    
    if [[ "$i" -eq $((partition_count - 1)) ]]; then
      echo "  PARTITION \`p${i}\` VALUES LESS THAN (MAXVALUE)"
    else
      echo "  PARTITION \`p${i}\` VALUES LESS THAN ('${formatted_key}'),"
    fi
  done

  cat <<EOF
);
EOF
}

# Generate Timeseries double-level partition SQL
generate_timeseries_double_partition() {
  local table_name="$1"
  local family="$2"
  local start_ts_ms="$3"
  local partition_duration_ms="$4"
  local key_subpartition_count="$5"
  local range_partition_count="${6:-1}"
  
  local full_table_name="\`${table_name}\$${family}\`"
  local start_ts_formatted=$(convert_timestamp_to_date "$start_ts_ms")
  
  cat <<EOF
-- =============================================================================
-- Timeseries double-level range-key partition table
-- Generated by: $0
-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================================
--
-- Parameters:
--   Table name: ${table_name}
--   Family: ${family}
--   Start timestamp: ${start_ts_ms} (${start_ts_formatted})
--   Partition duration: ${partition_duration_ms} ms
--   Key subpartition count: ${key_subpartition_count}
--   Range partition count: ${range_partition_count}
--
-- =============================================================================

CREATE TABLE IF NOT EXISTS ${full_table_name} (
  \`K\` varbinary(1024) NOT NULL,
  \`T\` bigint(20) NOT NULL,
  \`S\` bigint(20) NOT NULL,
  \`V\` json NOT NULL,
  \`G\` bigint(20) GENERATED ALWAYS AS (ABS(\`T\`)),
  \`K_PREFIX\` varbinary(1024) generated always as (substring(\`K\`, 1, 16)),
  PRIMARY KEY (\`K\`, \`T\`, \`S\`)
) PARTITION BY RANGE COLUMNS(\`G\`) SUBPARTITION BY KEY(\`K_PREFIX\`) SUBPARTITIONS ${key_subpartition_count} (
EOF

  local i
  # Generate range partitions
  # First partition (i=0): boundary = start_timestamp + partition_duration
  # Second partition (i=1): boundary = start_timestamp + 2 * partition_duration
  # And so on...
  for ((i = 0; i < range_partition_count; i++)); do
    local partition_boundary=$((start_ts_ms + (i + 1) * partition_duration_ms))
    local partition_boundary_date=$(convert_timestamp_to_date "$partition_boundary")
    echo "  PARTITION \`p${i}\` VALUES LESS THAN (${partition_boundary}),  -- ${partition_boundary_date} (${partition_boundary} ms)"
  done
  
  # Add MAXVALUE partition
  echo "  PARTITION \`p${range_partition_count}\` VALUES LESS THAN (MAXVALUE)"

  cat <<EOF
  );
EOF
}

# =============================================================================
# Main logic
# =============================================================================

main() {
  # Parse arguments
  if [[ $# -eq 0 ]]; then
    show_help
    exit 1
  fi

  parse_args "$@"

  # Parse or use current time as start timestamp for second-level partition
  local start_ts_ms=""
  if [[ "$PARTITION_TYPE" == "sec_part" ]]; then
    if [[ -n "$START_TIMESTAMP" ]]; then
      start_ts_ms=$(parse_start_timestamp_ms "$START_TIMESTAMP")
    else
      start_ts_ms=$(get_current_timestamp_ms)
    fi
  fi

  # Generate output filename if not provided
  if [[ -z "$OUTPUT_FILE" ]]; then
    local range_count=0
    local key_count=0
    local start_ts=0
    local duration=0
    
    if [[ "$PARTITION_TYPE" == "first_part" ]]; then
      range_count="$PARTITION_COUNT"
      key_count=0
      start_ts=0
      duration=0
    else
      range_count=1  # Default for second-level partition
      key_count="$KEY_SUBPARTITION_COUNT"
      start_ts="$start_ts_ms"
      duration="$PARTITION_DURATION_MS"
    fi
    
    OUTPUT_FILE="${SCRIPT_DIR}/$(generate_filename "$TABLE_NAME" "$MODE" "$range_count" "$key_count" "$start_ts" "$duration")"
  fi

  # Generate SQL based on mode and type
  if [[ "$MODE" == "table" ]]; then
    if [[ "$PARTITION_TYPE" == "first_part" ]]; then
      generate_table_single_partition "$TABLE_NAME" "$MAX_KEY" "$PARTITION_COUNT" "$KEY_LENGTH" | tee "$OUTPUT_FILE"
    else
      generate_table_key_partition "$TABLE_NAME" "$KEY_SUBPARTITION_COUNT" | tee "$OUTPUT_FILE"
    fi
  elif [[ "$MODE" == "hbase" ]]; then
    if [[ "$PARTITION_TYPE" == "first_part" ]]; then
      generate_hbase_single_partition "$TABLE_NAME" "$FAMILY" "$MAX_KEY" "$PARTITION_COUNT" "$KEY_LENGTH" | tee "$OUTPUT_FILE"
    else
      generate_hbase_double_partition "$TABLE_NAME" "$FAMILY" "$start_ts_ms" "$PARTITION_DURATION_MS" "$KEY_SUBPARTITION_COUNT" | tee "$OUTPUT_FILE"
    fi
  else
    if [[ "$PARTITION_TYPE" == "first_part" ]]; then
      generate_timeseries_single_partition "$TABLE_NAME" "$FAMILY" "$MAX_KEY" "$PARTITION_COUNT" "$KEY_LENGTH" | tee "$OUTPUT_FILE"
    else
      generate_timeseries_double_partition "$TABLE_NAME" "$FAMILY" "$start_ts_ms" "$PARTITION_DURATION_MS" "$KEY_SUBPARTITION_COUNT" | tee "$OUTPUT_FILE"
    fi
  fi

echo "SQL also saved to: $OUTPUT_FILE" >&2
}

# Run main function
main "$@"
