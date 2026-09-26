#!/data/data/com.termux/files/usr/bin/bash

# Script de démarrage de tous les services pour TermuxDevCenter
# Date: 2026-09-25

echo "╔════════════════════════════════════════════════╗"
echo "║  TermuxDevCenter - Démarrage des Services     ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# Fonction pour vérifier si un processus tourne
check_process() {
    local name="$1"
    if ps aux | grep -v grep | grep -q "$name"; then
        echo "✓ $name est déjà en cours d'exécution"
        return 0
    else
        echo "✗ $name n'est pas en cours d'exécution"
        return 1
    fi
}

# Fonction pour attendre qu'un port soit actif
wait_for_port() {
    local port="$1"
    local service="$2"
    local max_attempts=10
    local attempt=0
    
    echo -n "  Attente de $service sur le port $port..."
    while [ $attempt -lt $max_attempts ]; do
        if curl -s http://127.0.0.1:$port/health >/dev/null 2>&1 || nc -z 127.0.0.1 $port 2>/dev/null; then
            echo " ✓"
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
        echo -n "."
    done
    echo " ✗ (timeout)"
    return 1
}

echo "📋 Vérification des services existants..."
echo ""

# 1. Vérifier OmniRoute
echo "1️⃣  OmniRoute"
if ! check_process "omniroute"; then
    echo "   Démarrage d'OmniRoute..."
    omniroute > ~/.omniroute/omniroute.log 2>&1 &
    sleep 3
fi

# 2. Vérifier Omni-Exec
echo ""
echo "2️⃣  Omni-Exec"
if ! check_process "omni-exec.*http-server"; then
    echo "   Démarrage d'Omni-Exec..."
    node ~/.config/opencode/mcp-servers/omni-exec/http-server.js > ~/.config/opencode/mcp-servers/omni-exec/server.log 2>&1 &
    sleep 2
fi

# 3. Vérifier Termux Bridge
echo ""
echo "3️⃣  Termux Bridge"
if ! check_process "termux-bridge.*server.js"; then
    echo "   Démarrage du Bridge..."
    cd ~/claudeapk/termux-bridge
    node server.js > bridge.log 2>&1 &
    sleep 2
    cd - > /dev/null
fi

echo ""
echo "════════════════════════════════════════════════"
echo "🔍 Vérification des connexions..."
echo ""

# Vérifier le Bridge (port 8080)
if curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "✓ Bridge actif sur http://127.0.0.1:8080"
    BRIDGE_STATUS=$(curl -s http://127.0.0.1:8080/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    echo "  Status: $BRIDGE_STATUS"
else
    echo "✗ Bridge non accessible sur le port 8080"
fi

echo ""
echo "════════════════════════════════════════════════"
echo "📊 Résumé des processus actifs:"
echo ""

ps aux | grep -E "omniroute|omni-exec|bridge.*server.js" | grep -v grep | while read line; do
    pid=$(echo $line | awk '{print $2}')
    cmd=$(echo $line | awk '{for(i=11;i<=NF;i++) printf "%s ", $i}')
    echo "  PID $pid: $cmd"
done

echo ""
echo "════════════════════════════════════════════════"
echo "✅ Tous les services sont configurés!"
echo ""
echo "Pour tester la connexion depuis l'application:"
echo "  • Ouvrez TermuxDevCenter sur votre appareil"
echo "  • L'application devrait se connecter automatiquement"
echo "  • URL du Bridge: http://127.0.0.1:8080"
echo ""
echo "Pour arrêter tous les services:"
echo "  pkill -f 'omniroute|omni-exec|bridge'"
echo ""
echo "Logs disponibles:"
echo "  • OmniRoute: ~/.omniroute/omniroute.log"
echo "  • Omni-Exec: ~/.config/opencode/mcp-servers/omni-exec/server.log"
echo "  • Bridge: ~/claudeapk/termux-bridge/bridge.log"
echo "════════════════════════════════════════════════"
