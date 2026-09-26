#!/data/data/com.termux/files/usr/bin/bash

LOG_FILE="$HOME/claudeapk/build_clean.log"

echo "═══════════════════════════════════════════════════" | tee -a $LOG_FILE
echo "  CONSTRUCTION PROPRE DE TERMUXDEVCENTER" | tee -a $LOG_FILE
echo "  $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $LOG_FILE
echo "═══════════════════════════════════════════════════" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

cd ~/claudeapk/TermuxDevCenter

# Arrêter tous les processus Gradle en conflit
echo "→ Arrêt des processus Gradle..." | tee -a $LOG_FILE
pkill -f gradle 2>/dev/null
sleep 3

# Nettoyer les locks
echo "→ Nettoyage des verrous..." | tee -a $LOG_FILE
find ~/.gradle/caches/ -name "*.lock" -delete 2>/dev/null

# Build propre
echo "→ Compilation Release (cela peut prendre 3-5 minutes)..." | tee -a $LOG_FILE
gradle clean assembleRelease --no-daemon --stacktrace >> $LOG_FILE 2>&1

# Vérifier le résultat
if [ $? -eq 0 ]; then
    echo "" | tee -a $LOG_FILE
    echo "✓ BUILD SUCCESSFUL" | tee -a $LOG_FILE
    
    # Signer l'APK
    echo "→ Signature de l'APK..." | tee -a $LOG_FILE
    cd app/build/outputs/apk/release
    
    apksigner sign \
        --ks ~/caloria.jks \
        --ks-key-alias caloria \
        --ks-pass pass:caloria123 \
        --out TermuxDevCenter-FINAL.apk \
        app-release-unsigned.apk >> $LOG_FILE 2>&1
    
    if [ $? -eq 0 ]; then
        echo "✓ APK signé avec succès" | tee -a $LOG_FILE
        
        # Vérifier la signature
        apksigner verify --verbose TermuxDevCenter-FINAL.apk >> $LOG_FILE 2>&1
        
        if [ $? -eq 0 ]; then
            echo "✓ Signature vérifiée" | tee -a $LOG_FILE
            
            # Copier dans Downloads
            cp TermuxDevCenter-FINAL.apk ~/storage/downloads/
            echo "✓ APK copié dans Downloads" | tee -a $LOG_FILE
            
            # Informations
            SIZE=$(du -h TermuxDevCenter-FINAL.apk | cut -f1)
            echo "" | tee -a $LOG_FILE
            echo "═══════════════════════════════════════════════════" | tee -a $LOG_FILE
            echo "  APK FINAL PRÊT" | tee -a $LOG_FILE
            echo "  Fichier: TermuxDevCenter-FINAL.apk" | tee -a $LOG_FILE
            echo "  Taille: $SIZE" | tee -a $LOG_FILE
            echo "  Emplacement: ~/storage/downloads/" | tee -a $LOG_FILE
            echo "═══════════════════════════════════════════════════" | tee -a $LOG_FILE
        else
            echo "✗ ERREUR: Vérification de signature échouée" | tee -a $LOG_FILE
        fi
    else
        echo "✗ ERREUR: Signature échouée" | tee -a $LOG_FILE
    fi
else
    echo "" | tee -a $LOG_FILE
    echo "✗ BUILD FAILED" | tee -a $LOG_FILE
    echo "Voir les détails dans: $LOG_FILE" | tee -a $LOG_FILE
fi

echo "" | tee -a $LOG_FILE
echo "Log complet: $LOG_FILE" | tee -a $LOG_FILE
