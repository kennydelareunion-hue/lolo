#!/data/data/com.termux/files/usr/bin/bash

# Script de surveillance de la compilation
BUILD_LOG="$HOME/claudeapk/TermuxDevCenter/gradle_build.log"
INTERVAL=15

echo "=== Surveillance de la compilation ==="
echo "Log: $BUILD_LOG"
echo "Intervalle: ${INTERVAL}s"
echo ""

while true; do
    # Vérifier si les processus gradle sont encore actifs
    GRADLE_COUNT=$(ps aux | grep -E "gradle|kotlin" | grep -v grep | wc -l)
    
    echo "[$(date '+%H:%M:%S')] Processus actifs: $GRADLE_COUNT"
    
    if [ -f "$BUILD_LOG" ]; then
        # Afficher les dernières lignes du log
        echo "Dernières tâches:"
        tail -5 "$BUILD_LOG" | grep "^>" || echo "  (compilation en cours...)"
        
        # Vérifier si BUILD SUCCESSFUL ou FAILED apparaît
        if grep -q "BUILD SUCCESSFUL" "$BUILD_LOG"; then
            echo ""
            echo "✓ COMPILATION RÉUSSIE!"
            echo ""
            echo "Résumé final:"
            tail -20 "$BUILD_LOG"
            break
        elif grep -q "BUILD FAILED" "$BUILD_LOG"; then
            echo ""
            echo "✗ COMPILATION ÉCHOUÉE"
            echo ""
            echo "Erreurs:"
            tail -30 "$BUILD_LOG"
            break
        fi
    fi
    
    echo "---"
    sleep $INTERVAL
done

# Rechercher l'APK généré
echo ""
echo "=== Recherche de l'APK ==="
find "$HOME/claudeapk/TermuxDevCenter" -name "*.apk" -type f 2>/dev/null | while read apk; do
    SIZE=$(du -h "$apk" | cut -f1)
    echo "  ✓ $apk ($SIZE)"
done
