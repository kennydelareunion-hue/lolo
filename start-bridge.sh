#!/data/data/com.termux/files/usr/bin/bash

# Termux Bridge Startup Script
# This script starts the Termux Bridge HTTP server

BRIDGE_DIR="$HOME/claudeapk/termux-bridge"
LOG_FILE="$BRIDGE_DIR/server.log"

echo "🚀 Starting Termux Bridge Server..."

# Check if already running
if pgrep -f "node.*server.js" > /dev/null; then
    echo "⚠️  Bridge server is already running"
    echo "   Use 'pkill -f server.js' to stop it first"
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed"
    echo "   Install with: pkg install nodejs"
    exit 1
fi

# Check if bridge directory exists
if [ ! -d "$BRIDGE_DIR" ]; then
    echo "❌ Bridge directory not found: $BRIDGE_DIR"
    exit 1
fi

cd "$BRIDGE_DIR"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Start server in background
echo "✓ Starting server on http://127.0.0.1:8080"
nohup node server.js > "$LOG_FILE" 2>&1 &

sleep 2

# Check if server started successfully
if pgrep -f "node.*server.js" > /dev/null; then
    echo "✓ Bridge server started successfully"
    echo "📋 Logs: $LOG_FILE"
    echo ""
    echo "Test with: curl http://127.0.0.1:8080/health"
else
    echo "❌ Failed to start server"
    echo "Check logs: cat $LOG_FILE"
    exit 1
fi
