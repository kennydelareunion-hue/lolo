# 📱 Installation de TermuxDevCenter

## ✅ Compilation Terminée avec Succès!

L'APK a été compilé et signé avec succès. Tous les services backend sont opérationnels.

## 📍 Emplacement de l'APK

L'APK est disponible à deux endroits:

1. **Dossier Downloads (recommandé pour l'installation):**
   ```
   ~/storage/downloads/TermuxDevCenter.apk
   ```

2. **Dossier du projet:**
   ```
   ~/claudeapk/TermuxDevCenter.apk
   ```

## 🔧 Installation Manuelle

### Option 1: Via le Gestionnaire de Fichiers (Recommandé)

1. Ouvrez votre **Gestionnaire de Fichiers** Android
2. Naviguez vers **Downloads** ou **Téléchargements**
3. Trouvez le fichier **TermuxDevCenter.apk** (11 MB)
4. Tapez dessus pour lancer l'installation
5. Autorisez l'installation depuis des sources inconnues si demandé
6. Suivez les instructions à l'écran

### Option 2: Via termux-open

```bash
termux-open ~/storage/downloads/TermuxDevCenter.apk
```

Cette commande ouvrira l'APK avec l'installeur Android par défaut.

### Option 3: Via adb (si connecté à un ordinateur)

```bash
adb install ~/storage/downloads/TermuxDevCenter.apk
```

## 🚀 Après l'Installation

### 1. Vérifier que les services sont actifs

Avant de lancer l'application, assurez-vous que tous les services backend sont en cours d'exécution:

```bash
~/claudeapk/start-all-services.sh
```

Vous devriez voir:
- ✅ OmniRoute actif
- ✅ Omni-Exec actif  
- ✅ Termux Bridge actif sur http://127.0.0.1:8080

### 2. Lancer l'Application

1. Ouvrez **TermuxDevCenter** depuis votre lanceur d'applications
2. L'application devrait automatiquement se connecter aux services
3. Vous verrez l'interface avec les onglets:
   - 🏠 Dashboard
   - 📁 Files
   - 💻 Terminal
   - 🤖 OpenCode

## 🔍 Vérification de la Connexion

### Tester le Bridge manuellement:

```bash
curl http://127.0.0.1:8080/health
```

Réponse attendue:
```json
{"status":"ok","timestamp":...,"service":"Termux Bridge"}
```

### Vérifier les processus actifs:

```bash
ps aux | grep -E "omniroute|omni-exec|bridge" | grep -v grep
```

Vous devriez voir au moins 3-4 processus actifs.

## 🛠️ Dépannage

### L'application ne se connecte pas

1. **Redémarrer les services:**
   ```bash
   pkill -f 'omniroute|omni-exec|bridge'
   ~/claudeapk/start-all-services.sh
   ```

2. **Vérifier les logs:**
   ```bash
   # Bridge
   tail -f ~/claudeapk/termux-bridge/bridge.log
   
   # OmniRoute
   tail -f ~/.omniroute/omniroute.log
   
   # Omni-Exec
   tail -f ~/.config/opencode/mcp-servers/omni-exec/server.log
   ```

### Erreur "Parse error" lors de l'installation

L'APK est peut-être corrompu. Recopiez-le:
```bash
cp ~/claudeapk/TermuxDevCenter/app/build/outputs/apk/release/app-release-signed.apk ~/storage/downloads/TermuxDevCenter.apk
```

### L'installation est bloquée par Android

1. Allez dans **Paramètres** → **Sécurité**
2. Activez **Sources inconnues** ou **Installer des applications inconnues**
3. Autorisez votre gestionnaire de fichiers à installer des applications

## 📊 Informations Techniques

- **Package:** com.termux.devcenter
- **Version:** 1.0
- **Taille:** 11 MB
- **Signature:** v2 et v3 (vérifiée ✓)
- **Architecture:** Universal APK
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

## 🌟 Fonctionnalités

### Dashboard
- Vue d'ensemble du système
- Informations sur les projets
- Statistiques d'utilisation

### Gestionnaire de Fichiers
- Navigation dans le système de fichiers
- Création/suppression de fichiers et dossiers
- Lecture et édition de fichiers

### Terminal
- Exécution de commandes Termux
- Historique des commandes
- Support des couleurs ANSI

### OpenCode Interface
- Communication avec l'IA
- Exécution de tâches de développement
- Gestion de projets

## 📞 Support

Si vous rencontrez des problèmes:

1. Vérifiez les logs des services
2. Redémarrez Termux et les services
3. Réinstallez l'APK si nécessaire

## 🎉 Prochaines Étapes

Une fois l'application installée et connectée:
1. Explorez l'interface utilisateur
2. Testez les différentes fonctionnalités
3. Créez ou importez vos projets
4. Utilisez OpenCode pour développer avec l'IA

Bonne utilisation! 🚀
