#!/data/data/com.termux/files/usr/bin/bash

echo "════════════════════════════════════════════════════════════"
echo "   🚀 COMPILATION FINALE - TERMUX DEV CENTER"
echo "════════════════════════════════════════════════════════════"
echo ""

PROJECT_DIR="$HOME/claudeapk/TermuxDevCenter"
DOWNLOADS="$HOME/storage/downloads"
APK_NAME="TermuxDevCenter-v1.0.apk"

cd "$PROJECT_DIR" || exit 1

# Tuer les processus Gradle existants qui pourraient bloquer
echo "🧹 Nettoyage des processus Gradle existants..."
pkill -f "gradle.*assembleDebug" 2>/dev/null
pkill -f "GradleDaemon" 2>/dev/null
sleep 3

# Clean build
echo "🧹 Nettoyage du projet..."
rm -rf app/build/
rm -rf build/
rm -rf .gradle/
gradle clean 2>&1 | tail -10

echo ""
echo "📦 Compilation de l'APK (peut prendre 10-15 minutes)..."
echo "⏳ Veuillez patienter..."
echo ""

# Compilation avec sortie visible
gradle assembleDebug 2>&1 | while read line; do
    echo "   $line"
    # Afficher uniquement les lignes importantes
    if [[ "$line" == *"BUILD SUCCESSFUL"* ]] || \
       [[ "$line" == *"BUILD FAILED"* ]] || \
       [[ "$line" == *"error"* ]] || \
       [[ "$line" == *"Downloading"* ]] || \
       [[ "$line" == *"Task"* ]]; then
        :
    fi
done

echo ""
echo "════════════════════════════════════════════════════════════"

# Vérifier si la compilation a réussi
APK_PATH=$(find "$PROJECT_DIR/app/build/outputs/apk" -name "*.apk" -type f 2>/dev/null | head -n 1)

if [ -n "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    
    echo "✅ COMPILATION RÉUSSIE !"
    echo ""
    echo "📱 APK généré:"
    echo "   Fichier: $(basename "$APK_PATH")"
    echo "   Taille: $APK_SIZE"
    echo "   Chemin: $APK_PATH"
    echo ""
    
    # Copier vers Downloads
    if [ -d "$DOWNLOADS" ]; then
        cp "$APK_PATH" "$DOWNLOADS/$APK_NAME"
        
        if [ -f "$DOWNLOADS/$APK_NAME" ]; then
            echo "✅ APK copié vers Downloads !"
            echo "   📥 $DOWNLOADS/$APK_NAME"
            echo ""
            echo "🎉 VOUS POUVEZ MAINTENANT INSTALLER L'APK !"
            echo ""
            echo "Options d'installation:"
            echo "  1. Ouvrir avec: termux-open $DOWNLOADS/$APK_NAME"
            echo "  2. Aller dans Downloads sur Android et installer"
            echo ""
        else
            echo "⚠️  Erreur lors de la copie vers Downloads"
        fi
    else
        echo "⚠️  Dossier Downloads non trouvé"
        echo "   Exécutez: termux-setup-storage"
        echo "   Puis relancez ce script"
    fi
    
    echo "════════════════════════════════════════════════════════════"
    echo ""
    echo "📋 PROCHAINES ÉTAPES:"
    echo ""
    echo "1. Démarrer le Bridge Termux:"
    echo "   ~/claudeapk/start-bridge.sh"
    echo ""
    echo "2. Installer l'APK depuis Downloads"
    echo ""
    echo "3. Ouvrir Termux Dev Center sur votre téléphone"
    echo ""
    echo "4. Profiter de votre application ! 🎉"
    echo ""
    
    exit 0
else
    echo "❌ COMPILATION ÉCHOUÉE"
    echo ""
    echo "Aucun APK trouvé dans:"
    echo "  $PROJECT_DIR/app/build/outputs/apk/"
    echo ""
    echo "📋 Vérifier les erreurs dans les logs ci-dessus"
    echo ""
    echo "Commandes de dépannage:"
    echo "  cd ~/claudeapk/TermuxDevCenter"
    echo "  gradle assembleDebug --stacktrace"
    echo ""
    exit 1
fi
