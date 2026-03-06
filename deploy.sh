#!/usr/bin/env bash
# =============================================================================
# YCSB Web UI - One-click deployment script
# Usage:
#   ./deploy.sh start [--port 8080] [--rebuild]
#   ./deploy.sh stop [--clean]
#   ./deploy.sh restart [--rebuild]
#   ./deploy.sh status
#   ./deploy.sh build
#   ./deploy.sh logs [--follow]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOGS_DIR="$SCRIPT_DIR/logs"
PID_FILE="$LOGS_DIR/webui.pid"
LOG_FILE="$LOGS_DIR/webui.log"
WEBUI_JAR="$SCRIPT_DIR/webui/target/webui-0.18.0-SNAPSHOT.jar"
DEFAULT_PORT=8080
WEBUI_MAX_HEAP=256m

# ---- Helpers ----

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] ✓ $*"; }
err()  { echo "[$(date '+%H:%M:%S')] ✗ $*" >&2; }
die()  { err "$*"; exit 1; }

check_java() {
    command -v java >/dev/null 2>&1 || die "Java not found. Please install JDK 8+."
    local version
    version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    log "Java version: $version"
}

check_maven() {
    command -v mvn >/dev/null 2>&1 || die "Maven not found. Please install Maven 3.x."
    local version
    version=$(mvn -v 2>/dev/null | head -1)
    log "Maven: $version"
}

get_pid() {
    [ -f "$PID_FILE" ] && cat "$PID_FILE" || echo ""
}

is_running() {
    local pid
    pid=$(get_pid)
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

check_port() {
    local port=$1
    if lsof -i ":$port" -sTCP:LISTEN >/dev/null 2>&1; then
        die "Port $port is already in use. Use --port to specify another port."
    fi
}

# ---- Build ----

build_module() {
    local name=$1
    local dir="$SCRIPT_DIR/$name"
    local build_sh="$dir/build.sh"
    if [ -f "$build_sh" ]; then
        log "Building $name..."
        # Run in a subshell so cd doesn't affect the parent
        ( cd "$dir" && bash build.sh ) || die "Failed to build $name"
        ok "Built $name"
    else
        log "No build.sh found for $name, skipping."
    fi
}

build_webui() {
    log "Building webui..."
    ( cd "$SCRIPT_DIR/webui" && mvn package -DskipTests -q ) || die "Failed to build webui"
    ok "Built webui: $WEBUI_JAR"
}

cmd_build() {
    check_java
    check_maven
    build_module "obkv-hbase"
    build_module "obkv-table"
    build_webui
    ok "All builds complete"
}

# ---- Start ----

cmd_start() {
    local port=$DEFAULT_PORT
    local rebuild=false

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --port)   port="$2"; shift 2 ;;
            --rebuild) rebuild=true; shift ;;
            *)        shift ;;
        esac
    done

    check_java

    if is_running; then
        err "webui is already running (PID=$(get_pid)). Use 'restart' to restart."
        exit 1
    fi

    check_port "$port"
    mkdir -p "$LOGS_DIR"

    if $rebuild || [ ! -f "$WEBUI_JAR" ]; then
        check_maven
        if $rebuild || [ ! -f "$SCRIPT_DIR/obkv-hbase/build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
            build_module "obkv-hbase"
        fi
        if $rebuild || [ ! -f "$SCRIPT_DIR/obkv-table/build/obkv-table-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
            build_module "obkv-table"
        fi
        build_webui
    fi

    log "Starting webui on port $port..."
    nohup java \
        -Xmx${WEBUI_MAX_HEAP} \
        -Xms128m \
        -Dserver.port="$port" \
        -Dspring.profiles.active=prod \
        -jar "$WEBUI_JAR" \
        >> "$LOG_FILE" 2>&1 &

    local pid=$!
    echo "$pid" > "$PID_FILE"
    log "webui started (PID=$pid), waiting for health check..."

    # Health check (max 30s)
    local count=0
    until curl -sf "http://localhost:$port/api/modules" >/dev/null 2>&1; do
        sleep 1
        count=$((count + 1))
        if [ $count -ge 30 ]; then
            err "Health check failed after 30s. Check logs: $LOG_FILE"
            exit 1
        fi
    done

    ok "webui is running at http://localhost:$port"
    ok "Logs: $LOG_FILE"
}

# ---- Stop ----

cmd_stop() {
    local clean=false
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --clean) clean=true; shift ;;
            *)       shift ;;
        esac
    done

    if ! is_running; then
        log "webui is not running."
    else
        local pid
        pid=$(get_pid)
        log "Stopping webui (PID=$pid)..."
        kill "$pid" 2>/dev/null || true

        local count=0
        while kill -0 "$pid" 2>/dev/null; do
            sleep 1
            count=$((count + 1))
            if [ $count -ge 15 ]; then
                log "Force killing PID $pid..."
                kill -9 "$pid" 2>/dev/null || true
                break
            fi
        done

        rm -f "$PID_FILE"
        ok "webui stopped"
    fi

    if $clean; then
        log "Cleaning run artifacts and Maven build output..."
        if [ -d "$SCRIPT_DIR/webui-runs" ]; then
            rm -rf "$SCRIPT_DIR/webui-runs"/*
            ok "Cleaned webui-runs/"
        fi
        if [ -d "$LOGS_DIR" ]; then
            rm -f "$LOGS_DIR"/*.log "$LOGS_DIR"/*.pid 2>/dev/null || true
            ok "Cleaned logs/"
        fi
        for dir in "$SCRIPT_DIR/target" "$SCRIPT_DIR/core/target" "$SCRIPT_DIR/obkv-table/target" "$SCRIPT_DIR/obkv-hbase/target" "$SCRIPT_DIR/webui/target" \
                   "$SCRIPT_DIR/core/build" "$SCRIPT_DIR/obkv-table/build" "$SCRIPT_DIR/obkv-hbase/build" "$SCRIPT_DIR/webui/build"; do
            if [ -d "$dir" ]; then
                rm -rf "$dir"
                ok "Cleaned $dir"
            fi
        done
        ok "Clean complete"
    fi
}

# ---- Status ----

cmd_status() {
    if is_running; then
        local pid
        pid=$(get_pid)
        ok "webui is RUNNING (PID=$pid)"
        # Try to get port from process
        local port
        port=$(lsof -a -p "$pid" -iTCP -sTCP:LISTEN 2>/dev/null | awk 'NR>1{print $9}' | head -1)
        [ -n "$port" ] && log "Listening on: $port"
    else
        log "webui is STOPPED"
        if [ -f "$PID_FILE" ]; then
            log "(stale PID file found; removing)"
            rm -f "$PID_FILE"
        fi
    fi
}

# ---- Logs ----

cmd_logs() {
    if [ "${1:-}" = "--follow" ] || [ "${1:-}" = "-f" ]; then
        tail -f "$LOG_FILE"
    else
        tail -200 "$LOG_FILE" 2>/dev/null || log "No log file found at $LOG_FILE"
    fi
}

# ---- Restart ----

cmd_restart() {
    cmd_stop
    sleep 1
    cmd_start "$@"
}

# ---- Main ----

COMMAND="${1:-help}"
shift || true

case "$COMMAND" in
    start)   cmd_start "$@" ;;
    stop)    cmd_stop "$@" ;;
    restart) cmd_restart "$@" ;;
    status)  cmd_status ;;
    build)   cmd_build ;;
    logs)    cmd_logs "$@" ;;
    help|--help|-h)
        echo "Usage: $0 {start|stop|restart|status|build|logs}"
        echo ""
        echo "  start [--port 8080] [--rebuild]  Start the webui service"
        echo "  stop [--clean]                    Stop the webui service; --clean also removes webui-runs/, logs/, all Maven target/ and build/ dirs"
        echo "  restart [--rebuild]               Restart the webui service"
        echo "  status                            Show running status"
        echo "  build                             Build all JARs (hbase, table, webui)"
        echo "  logs [--follow]                   Show webui logs"
        ;;
    *)
        err "Unknown command: $COMMAND"
        echo "Run '$0 help' for usage."
        exit 1
        ;;
esac
