#!/data/data/com.termux/files/usr/bin/bash

echo "═══════════════════════════════════════════════════"
echo "  NETTOYAGE COMPLET ET RECONSTRUCTION"
echo "  TermuxDevCenter - Mode Autonome"
echo "═══════════════════════════════════════════════════"
echo ""

# 1. Forcer la fermeture de l'application
echo "1. Arrêt forcé de l'application..."
am force-stop com.termux.devcenter 2>/dev/null
sleep 2

# 2. Nettoyer le cache et les données
echo "2. Nettoyage du cache..."
pm clear com.termux.devcenter 2>/dev/null
sleep 2

# 3. Désinstaller via adb
echo "3. Désinstallation de l'ancienne version..."
adb shell pm uninstall com.termux.devcenter 2>/dev/null || echo "   Note: désinstallation manuelle nécessaire"

# 4. Nettoyer le projet
echo "4. Nettoyage du projet..."
cd ~/claudeapk/TermuxDevCenter
rm -rf app/build/
rm -rf build/
rm -rf .gradle/
rm -rf app/.cxx/

# 5. Nettoyer le cache Gradle global
echo "5. Nettoyage du cache Gradle..."
find ~/.gradle/caches/ -name "*termux*devcenter*" -type d -exec rm -rf {} + 2>/dev/null
find ~/.gradle/caches/ -name "*.lock" -delete 2>/dev/null

echo ""
echo "✓ Nettoyage terminé"
echo "═══════════════════════════════════════════════════"
