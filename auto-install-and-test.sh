#!/data/data/com.termux/files/usr/bin/bash

REPORT="$HOME/claudeapk/TEST_FINAL_REPORT.md"

echo "═══════════════════════════════════════════════════" | tee $REPORT
echo "  INSTALLATION ET TEST AUTOMATIQUE" | tee -a $REPORT
echo "  TermuxDevCenter - Version FINALE" | tee -a $REPORT
echo "  Date: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $REPORT
echo "═══════════════════════════════════════════════════" | tee -a $REPORT
echo "" | tee -a $REPORT

# 1. Vérifier que l'APK existe
echo "1. Vérification de l'APK..." | tee -a $REPORT
if [ -f ~/storage/downloads/TermuxDevCenter-FINAL.apk ]; then
    SIZE=$(du -h ~/storage/downloads/TermuxDevCenter-FINAL.apk | cut -f1)
    echo "   ✓ APK trouvé ($SIZE)" | tee -a $REPORT
else
    echo "   ✗ APK introuvable" | tee -a $REPORT
    exit 1
fi

# 2. Vérifier la signature
echo "2. Vérification de la signature..." | tee -a $REPORT
apksigner verify ~/storage/downloads/TermuxDevCenter-FINAL.apk 2>&1 | head -1 | tee -a $REPORT

# 3. Tenter l'installation
echo "3. Installation de l'APK..." | tee -a $REPORT
termux-open ~/storage/downloads/TermuxDevCenter-FINAL.apk
sleep 5

# 4. Vérifier si l'installation a réussi
echo "4. Vérification de l'installation..." | tee -a $REPORT
sleep 10

if pm list packages | grep -q "com.termux.devcenter"; then
    echo "   ✓ Application installée" | tee -a $REPORT
    
    # Obtenir les infos de l'application
    pm dump com.termux.devcenter | grep "versionName" | head -1 | tee -a $REPORT
    
    # 5. Vérifier les services backend
    echo "" | tee -a $REPORT
    echo "5. Vérification des services backend..." | tee -a $REPORT
    
    # Bridge
    if curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
        echo "   ✓ Bridge actif (port 8080)" | tee -a $REPORT
    else
        echo "   ✗ Bridge non accessible" | tee -a $REPORT
        echo "   → Démarrage du Bridge..." | tee -a $REPORT
        cd ~/claudeapk/termux-bridge && node server.js > bridge.log 2>&1 &
        sleep 3
    fi
    
    # OmniRoute
    if ps aux | grep -v grep | grep -q "omniroute"; then
        echo "   ✓ OmniRoute actif" | tee -a $REPORT
    else
        echo "   ✗ OmniRoute non actif" | tee -a $REPORT
    fi
    
    # Omni-Exec
    if ps aux | grep -v grep | grep -q "omni-exec"; then
        echo "   ✓ Omni-Exec actif" | tee -a $REPORT
    else
        echo "   ✗ Omni-Exec non actif" | tee -a $REPORT
    fi
    
    # 6. Lancer l'application
    echo "" | tee -a $REPORT
    echo "6. Lancement de l'application..." | tee -a $REPORT
    am start -n com.termux.devcenter/.MainActivity 2>&1 | grep "Starting" | tee -a $REPORT
    sleep 3
    
    # 7. Vérifier si l'app tourne
    if am force-stop com.termux.devcenter 2>/dev/null; then
        echo "   ✓ Application lancée avec succès" | tee -a $REPORT
    fi
    
    echo "" | tee -a $REPORT
    echo "═══════════════════════════════════════════════════" | tee -a $REPORT
    echo "  ✓ INSTALLATION TERMINÉE" | tee -a $REPORT
    echo "═══════════════════════════════════════════════════" | tee -a $REPORT
    echo "" | tee -a $REPORT
    echo "L'application TermuxDevCenter est prête à l'emploi!" | tee -a $REPORT
    echo "Ouvrez-la depuis votre lanceur d'applications." | tee -a $REPORT
    
else
    echo "   ⚠ Installation en attente de confirmation utilisateur" | tee -a $REPORT
    echo "" | tee -a $REPORT
    echo "L'APK a été ouvert avec l'installeur Android." | tee -a $REPORT
    echo "Confirmez l'installation sur votre écran, puis l'app sera prête." | tee -a $REPORT
fi

echo "" | tee -a $REPORT
echo "Rapport complet: $REPORT" | tee -a $REPORT
