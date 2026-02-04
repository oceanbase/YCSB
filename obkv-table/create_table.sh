#!/bin/bash
#
# =============================================================================
# Table creation script (kv_table)
# =============================================================================
#
# This script merges:
# - create_partitioned_table.sh : range partitions by ycsb_key
# - create_kv_table.sh          : range partitions by ts + key subpartitions by pmid
#
# The created table name is ALWAYS: kv_table
#
# Modes:
# - range     : ycsb_key RANGE partitions (string boundaries with zero padding)  [DEFAULT]
# - key_range : ts RANGE partitions + pmid KEY subpartitions
#
# Usage:
#   ./create_table.sh [--mode range] <num_partitions> <max_key> [key_length]
#
#   ./create_table.sh --mode key_range <range_partition_count> <key_subpartition_count> \
#     <start_timestamp> <partition_duration_ms>
#
# Notes:
# - range: partitions are generated as string boundaries with zero padding.
# - key_range: start_timestamp can be millis or 'YYYY-MM-DD HH:MM:SS' (or 'YYYY-MM-DD').
#
# =============================================================================

set -euo pipefail

TABLE_NAME="kv_table"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

show_help() {
  cat <<'EOF'
Usage:
  create_table.sh [--mode range] [--fields <field_count>] <num_partitions> <max_key> [key_length]
  create_table.sh --mode key_range [--fields <field_count>] <range_partition_count> <key_subpartition_count> <start_timestamp> <partition_duration_ms>

Modes:
  range (default):
    - Range partitions by ycsb_key (string boundaries with zero padding).
    - Primary key: ycsb_key
    - Columns: ycsb_key + field0..field{N-1}

  key_range:
    - Range partitions by ycsb_ts, with key subpartitions by ycsb_id.
    - Primary key: (ycsb_id, ycsb_ts)
    - Columns: ycsb_id, ycsb_ts + field0..field{N-1}

Options:
  -h, --help        Show this help message
  --fields <N>      Number of non-PK columns (field0..field{N-1}). Default: 1

Notes:
  - Table name is always: kv_table
  - This script ALWAYS writes SQL to a generated file (no stdout SQL output):
    - range     : kv_table_range_max<max_key>_p<num_partitions>_len<key_length>_f<field_count>.sql
    - key_range : kv_table_r<range_partition_count>_k<key_subpartition_count>_f<field_count>.sql
  - Output files are always written under the obkv-table directory (script directory).

Examples:
  create_table.sh 4 1000
  create_table.sh --mode range --fields 10 8 10000 16
  create_table.sh --mode key_range --fields 5 4 3 '2024-01-01 00:00:00' 31536000000
EOF
}

OUTPUT_FILE=""
MODE="range"
OUTPUT_INITIALIZED=0
FIELD_COUNT=1

if [[ $# -eq 0 ]]; then
  show_help
  exit 1
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --fields)
      FIELD_COUNT="${2:-}"
      shift 2
      ;;
    -h|--help)
      show_help
      exit 0
      ;;
    *)
      break
      ;;
  esac
done

if [[ -z "$MODE" ]]; then
  MODE="range"
fi

if ! [[ "$FIELD_COUNT" =~ ^[0-9]+$ ]] || [[ "$FIELD_COUNT" -lt 0 ]]; then
  echo "Error: --fields must be a non-negative integer" >&2
  exit 1
fi

output_line() {
  if [[ -z "$OUTPUT_FILE" ]]; then
    echo "Internal error: OUTPUT_FILE is not set." >&2
    exit 1
  fi
  echo "$1" >> "$OUTPUT_FILE"
}

init_output_file() {
  if [[ -n "$OUTPUT_FILE" && "$OUTPUT_INITIALIZED" -eq 0 ]]; then
    : > "$OUTPUT_FILE"
    OUTPUT_INITIALIZED=1
  fi
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

emit_field_columns() {
  local field_count="$1"
  local field_type="$2"
  local not_null="$3" # "true" / "false"

  local i
  for ((i = 0; i < field_count; i++)); do
    if [[ "$not_null" == "true" ]]; then
      output_line "    field${i} ${field_type} NOT NULL,"
    else
      output_line "    field${i} ${field_type},"
    fi
  done
}

emit_header() {
  output_line "-- ============================================================================="
  output_line "-- kv_table creation SQL"
  output_line "-- Generated by: $0"
  output_line "-- Generation time: $(date '+%Y-%m-%d %H:%M:%S')"
  output_line "-- Mode: ${MODE}"
  output_line "-- Fields: ${FIELD_COUNT}"

  # Remaining args are key/value pairs for a neatly aligned parameter section.
  if [[ $# -gt 0 ]]; then
    output_line "-- Parameters:"
    local max_key_len=0
    local args=("$@")
    local i
    for ((i = 0; i < ${#args[@]}; i += 2)); do
      local k="${args[i]}"
      if [[ ${#k} -gt $max_key_len ]]; then
        max_key_len=${#k}
      fi
    done

    for ((i = 0; i < ${#args[@]}; i += 2)); do
      local k="${args[i]}"
      local v="${args[i+1]}"
      local line
      line="$(printf -- "--   %-${max_key_len}s : %s" "$k" "$v")"
      output_line "$line"
    done
  fi

  output_line "-- ============================================================================="
  output_line ""
}

create_range() {
  if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: create_table.sh [--mode range] [--fields <field_count>] <num_partitions> <max_key> [key_length]" >&2
    exit 1
  fi

  local num_partitions="$1"
  local max_key="$2"
  local key_length="${3:-12}"

  if ! [[ "$num_partitions" =~ ^[0-9]+$ ]] || [[ "$num_partitions" -le 0 ]]; then
    echo "Error: num_partitions must be a positive integer" >&2
    exit 1
  fi
  if ! [[ "$max_key" =~ ^[0-9]+$ ]] || [[ "$max_key" -le 0 ]]; then
    echo "Error: max_key must be a positive integer" >&2
    exit 1
  fi
  if ! [[ "$key_length" =~ ^[0-9]+$ ]] || [[ "$key_length" -le 0 ]]; then
    echo "Error: key_length must be a positive integer" >&2
    exit 1
  fi

  if [[ -z "$OUTPUT_FILE" ]]; then
    OUTPUT_FILE="${SCRIPT_DIR}/kv_table_range_max${max_key}_p${num_partitions}_len${key_length}_f${FIELD_COUNT}.sql"
  fi
  init_output_file

  local step=$(( max_key / num_partitions ))
  if [[ "$step" -le 0 ]]; then
    step=1
  fi

  emit_header \
    "num_partitions" "${num_partitions}" \
    "max_key" "${max_key}" \
    "key_length" "${key_length}"

  output_line "CREATE TABLE ${TABLE_NAME} ("
  output_line "    ycsb_key varbinary(1024) NOT NULL,"
  if [[ "$FIELD_COUNT" -gt 0 ]]; then
    emit_field_columns "$FIELD_COUNT" "varbinary(1024)" "true"
  fi
  output_line "    PRIMARY KEY (ycsb_key)"
  output_line ")"
  output_line "PARTITION BY RANGE COLUMNS(ycsb_key) ("

  local i
  for ((i = 0; i < num_partitions; i++)); do
    local upper_bound=$(( step * (i + 1) ))
    local padded_upper
    padded_upper=$(printf "%0${key_length}d" "$upper_bound")

    if [[ "$i" -eq $((num_partitions - 1)) ]]; then
      output_line "    PARTITION p${i} VALUES LESS THAN (MAXVALUE)"
    else
      output_line "    PARTITION p${i} VALUES LESS THAN ('${padded_upper}'),"
    fi
  done

  output_line ");"
}

create_key_range() {
  # Args: range_partition_count key_subpartition_count start_timestamp partition_duration_ms
  if [[ $# -ne 4 ]]; then
    echo "Usage: create_table.sh --mode key_range [--fields <field_count>] <range_partition_count> <key_subpartition_count> <start_timestamp> <partition_duration_ms>" >&2
    exit 1
  fi

  local range_partition_count="$1"
  local key_subpartition_count="$2"
  local start_timestamp="$3"
  local partition_duration_ms="$4"

  if ! [[ "$range_partition_count" =~ ^[0-9]+$ ]] || [[ "$range_partition_count" -le 0 ]]; then
    echo "Error: range_partition_count must be a positive integer" >&2
    exit 1
  fi
  if ! [[ "$key_subpartition_count" =~ ^[0-9]+$ ]] || [[ "$key_subpartition_count" -le 0 ]]; then
    echo "Error: key_subpartition_count must be a positive integer" >&2
    exit 1
  fi
  if ! [[ "$partition_duration_ms" =~ ^[0-9]+$ ]] || [[ "$partition_duration_ms" -le 0 ]]; then
    echo "Error: partition_duration_ms must be a positive integer" >&2
    exit 1
  fi

  if [[ -z "$OUTPUT_FILE" ]]; then
    OUTPUT_FILE="${SCRIPT_DIR}/kv_table_r${range_partition_count}_k${key_subpartition_count}_f${FIELD_COUNT}.sql"
  fi
  init_output_file

  local start_ts_ms
  start_ts_ms="$(parse_start_timestamp_ms "$start_timestamp")"

  emit_header \
    "range_partition_count" "${range_partition_count}" \
    "key_subpartition_count" "${key_subpartition_count}" \
    "start_timestamp" "${start_timestamp}" \
    "start_ts_ms" "${start_ts_ms}" \
    "start_timestamp_formatted" "$(convert_timestamp_to_date "$start_ts_ms")" \
    "partition_duration_ms" "${partition_duration_ms}" \
    "partition_duration_days" "$((partition_duration_ms / 1000 / 60 / 60 / 24))"

  output_line "CREATE TABLE ${TABLE_NAME} ("
  output_line "    ycsb_id CHAR(36) NOT NULL,"
  output_line "    ycsb_ts TIMESTAMP(6) NOT NULL,"
  if [[ "$FIELD_COUNT" -gt 0 ]]; then
    emit_field_columns "$FIELD_COUNT" "varbinary(1024)" "false"
  fi
  output_line "    PRIMARY KEY (ycsb_id, ycsb_ts)"
  output_line ")"
  output_line "PARTITION BY RANGE COLUMNS (ycsb_ts)"
  output_line "SUBPARTITION BY KEY(ycsb_id) SUBPARTITIONS ${key_subpartition_count}"
  output_line "("

  local i
  for ((i = 0; i < range_partition_count; i++)); do
    local partition_end_ts_ms=$((start_ts_ms + (i + 1) * partition_duration_ms))
    local partition_end_date
    partition_end_date="$(convert_timestamp_to_date "$partition_end_ts_ms")"

    if [[ "$i" -eq $((range_partition_count - 1)) ]]; then
      output_line "    PARTITION p${i} VALUES LESS THAN (MAXVALUE)"
    else
      output_line "    PARTITION p${i} VALUES LESS THAN ('${partition_end_date}'),"
    fi
  done

  output_line ");"
}

case "$MODE" in
  range)
    create_range "$@"
    ;;
  key_range)
    create_key_range "$@"
    ;;
  *)
    echo "Error: unknown mode '$MODE'" >&2
    echo "" >&2
    show_help >&2
    exit 1
    ;;
esac

echo "SQL saved to: $OUTPUT_FILE"


