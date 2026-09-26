#!/data/data/com.termux/files/usr/bin/bash

# Script de compilation de Termux Dev Center
# Lance la compilation Gradle en arrière-plan

PROJECT_DIR="$HOME/claudeapk/TermuxDevCenter"
LOG_FILE="$PROJECT_DIR/build.log"
APK_OUTPUT="$PROJECT_DIR/app/build/outputs/apk/debug"

echo "🚀 Compilation de Termux Dev Center"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$PROJECT_DIR" || exit 1

# Clean previous build
echo "🧹 Nettoyage des builds précédents..."
rm -rf app/build/outputs/apk/

# Start build in background
echo "📦 Compilation en cours (cela peut prendre 5-10 minutes)..."
echo "📋 Logs: $LOG_FILE"
echo ""

nohup gradle assembleDebug > "$LOG_FILE" 2>&1 &
BUILD_PID=$!

echo "⏳ PID: $BUILD_PID"
echo ""
echo "Commandes utiles:"
echo "  • Suivre les logs: tail -f $LOG_FILE"
echo "  • Vérifier le processus: ps -p $BUILD_PID"
echo "  • Arrêter: kill $BUILD_PID"
echo ""
echo "Le script va surveiller la compilation..."

# Monitor build
while kill -0 $BUILD_PID 2>/dev/null; do
    sleep 10
    if [ -f "$LOG_FILE" ]; then
        LAST_LINE=$(tail -n 1 "$LOG_FILE")
        echo "  ⟳ $LAST_LINE"
    fi
done

wait $BUILD_PID
BUILD_EXIT_CODE=$?

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $BUILD_EXIT_CODE -eq 0 ]; then
    echo "✅ COMPILATION RÉUSSIE!"
    echo ""
    
    # Find APK
    APK_FILE=$(find "$APK_OUTPUT" -name "*.apk" -type f | head -n 1)
    
    if [ -n "$APK_FILE" ]; then
        APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
        echo "📱 APK généré:"
        echo "   $APK_FILE"
        echo "   Taille: $APK_SIZE"
        echo ""
        
        # Copy to Downloads
        DOWNLOADS="$HOME/storage/downloads"
        if [ -d "$DOWNLOADS" ]; then
            cp "$APK_FILE" "$DOWNLOADS/TermuxDevCenter-v1.0.apk"
            echo "📥 Copié vers: $DOWNLOADS/TermuxDevCenter-v1.0.apk"
        fi
    else
        echo "⚠️  APK introuvable dans $APK_OUTPUT"
    fi
else
    echo "❌ COMPILATION ÉCHOUÉE (code: $BUILD_EXIT_CODE)"
    echo ""
    echo "Dernières lignes du log:"
    tail -n 20 "$LOG_FILE"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
