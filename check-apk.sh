#!/data/data/com.termux/files/usr/bin/bash

# Script rapide - Vérifier si APK existe déjà et copier

echo "🔍 Recherche de l'APK compilé..."

APK=$(find ~/claudeapk/TermuxDevCenter/app/build/outputs/apk -name "*.apk" -type f 2>/dev/null | head -n 1)

if [ -n "$APK" ]; then
    echo "✅ APK trouvé: $APK"
    
    DOWNLOADS="$HOME/storage/downloads"
    if [ -d "$DOWNLOADS" ]; then
        cp "$APK" "$DOWNLOADS/TermuxDevCenter-v1.0.apk"
        echo "✅ Copié vers: $DOWNLOADS/TermuxDevCenter-v1.0.apk"
        echo ""
        echo "🎉 PRÊT À INSTALLER !"
        echo ""
        echo "Installer avec: termux-open $DOWNLOADS/TermuxDevCenter-v1.0.apk"
    else
        echo "⚠️  Dossier Downloads non accessible"
        echo "Exécutez: termux-setup-storage"
    fi
else
    echo "❌ APK non trouvé"
    echo ""
    echo "La compilation est peut-être encore en cours."
    echo "Lancez la compilation complète avec:"
    echo "  ~/claudeapk/build-final.sh"
fi
