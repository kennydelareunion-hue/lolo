#!/data/data/com.termux/files/usr/bin/bash

echo "═══════════════════════════════════════════════════"
echo "  CONFIGURATION DES SERVICES - TERMUXDEVCENTER"
echo "  Vérification des adresses et ports"
echo "═══════════════════════════════════════════════════"
echo ""

# 1. Termux Bridge
echo "1️⃣  TERMUX BRIDGE"
if curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "   ✓ Actif"
    echo "   URL: http://127.0.0.1:8080"
    echo "   Port: 8080"
    echo ""
else
    echo "   ✗ Non actif - Démarrage..."
    cd ~/claudeapk/termux-bridge
    node server.js > bridge.log 2>&1 &
    sleep 3
    if curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
        echo "   ✓ Démarré avec succès"
        echo "   URL: http://127.0.0.1:8080"
        echo "   Port: 8080"
        echo ""
    fi
fi

# 2. OmniRoute
echo "2️⃣  OMNIROUTE"
if ps aux | grep -v grep | grep -q "omniroute"; then
    echo "   ✓ Actif"
    # OmniRoute utilise généralement le port 3000 ou un socket
    # Vérifier les ports possibles
    for port in 3000 8000 5000; do
        if curl -s http://127.0.0.1:$port > /dev/null 2>&1; then
            echo "   URL: http://127.0.0.1:$port"
            echo "   Port: $port"
            break
        fi
    done
    if ! curl -s http://127.0.0.1:3000 > /dev/null 2>&1 && \
       ! curl -s http://127.0.0.1:8000 > /dev/null 2>&1 && \
       ! curl -s http://127.0.0.1:5000 > /dev/null 2>&1; then
        echo "   Note: OmniRoute actif mais pas d'endpoint HTTP détecté"
        echo "   (Communication via MCP/autres protocoles)"
    fi
    echo ""
else
    echo "   ✗ Non actif"
    echo ""
fi

# 3. Omni-Exec
echo "3️⃣  OMNI-EXEC"
if ps aux | grep -v grep | grep -q "omni-exec.*http-server"; then
    echo "   ✓ Actif"
    # Vérifier le port par défaut d'Omni-Exec
    if [ -f ~/.config/opencode/mcp-servers/omni-exec/http-server.js ]; then
        PORT=$(grep -o "port.*[0-9]\\+" ~/.config/opencode/mcp-servers/omni-exec/http-server.js | grep -o "[0-9]\\+" | head -1)
        if [ ! -z "$PORT" ]; then
            echo "   URL: http://127.0.0.1:$PORT"
            echo "   Port: $PORT"
        else
            echo "   Port: (à déterminer depuis les logs)"
        fi
    fi
    echo ""
else
    echo "   ✗ Non actif"
    echo ""
fi

echo "═══════════════════════════════════════════════════"
echo ""
echo "📝 CONFIGURATION RECOMMANDÉE POUR L'APPLICATION:"
echo ""
echo "Dans les paramètres de TermuxDevCenter, entrez:"
echo ""
echo "┌─────────────────────────────────────────────────┐"
echo "│ TERMUX BRIDGE                                   │"
echo "│ URL: http://127.0.0.1:8080                      │"
echo "│ ou:  http://localhost:8080                      │"
echo "└─────────────────────────────────────────────────┘"
echo ""
echo "┌─────────────────────────────────────────────────┐"
echo "│ OPENCODE / OMNIROUTE                            │"
echo "│ Utilise le Bridge comme intermédiaire           │"
echo "│ Pas de configuration directe nécessaire         │"
echo "└─────────────────────────────────────────────────┘"
echo ""
echo "┌─────────────────────────────────────────────────┐"
echo "│ OMNI-EXEC                                       │"
echo "│ Utilise le Bridge pour l'exécution              │"
echo "│ Pas de configuration directe nécessaire         │"
echo "└─────────────────────────────────────────────────┘"
echo ""
