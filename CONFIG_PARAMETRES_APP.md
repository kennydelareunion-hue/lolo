# 📱 CONFIGURATION DES PARAMÈTRES - TERMUXDEVCENTER

**Date:** 26 Septembre 2026 - 00:48
**Tous les services sont actifs et prêts**

═══════════════════════════════════════════════════════════════

## 🎯 CONFIGURATION SIMPLE (RECOMMANDÉE)

Dans l'application TermuxDevCenter, allez dans **Paramètres** et entrez:

### 🌉 TERMUX BRIDGE (Principal)
```
IP/Host:  127.0.0.1
Port:     8080
URL:      http://127.0.0.1:8080
```

**ℹ️ Note:** Le Bridge gère toutes les communications. Les autres services passent par lui.

═══════════════════════════════════════════════════════════════

## 📋 CONFIGURATION DÉTAILLÉE PAR SERVICE

### 1. TERMUX BRIDGE ⭐ (OBLIGATOIRE)
**Service principal de communication**

| Paramètre | Valeur |
|-----------|--------|
| **Type** | HTTP REST API |
| **IP/Host** | `127.0.0.1` ou `localhost` |
| **Port** | `8080` |
| **URL complète** | `http://127.0.0.1:8080` |
| **Protocol** | HTTP |

**Dans l'app, entrez:**
```
Serveur Bridge: http://127.0.0.1:8080
```

### 2. OPENCODE / OMNIROUTE 🤖
**Service d'IA - Communication via Bridge**

| Paramètre | Valeur |
|-----------|--------|
| **Configuration** | Via Bridge (automatique) |
| **IP directe** | Non nécessaire |
| **Port direct** | Non nécessaire |

**Dans l'app:**
```
Utilise automatiquement le Bridge
Pas de configuration séparée requise
```

### 3. OMNI-EXEC 💻
**Exécution de commandes - Via Bridge**

| Paramètre | Valeur |
|-----------|--------|
| **Configuration** | Via Bridge (automatique) |
| **IP directe** | Non nécessaire |
| **Port direct** | Non nécessaire |

**Dans l'app:**
```
Les commandes passent par le Bridge
Pas de configuration séparée requise
```

### 4. MCP (Model Context Protocol)
**Communication avancée - Intégré**

| Paramètre | Valeur |
|-----------|--------|
| **Configuration** | Automatique via OmniRoute |
| **IP directe** | Non applicable |

═══════════════════════════════════════════════════════════════

## 🔧 EXEMPLE DE CONFIGURATION DANS L'APP

### Écran Paramètres (Settings)

```
┌─────────────────────────────────────────────────┐
│                                                 │
│  ⚙️  PARAMÈTRES SERVEUR                        │
│                                                 │
│  ┌───────────────────────────────────────┐     │
│  │ Termux Bridge                         │     │
│  │                                       │     │
│  │ URL du serveur:                       │     │
│  │ http://127.0.0.1:8080                │     │
│  │                                       │     │
│  │ [Tester la connexion]                 │     │
│  └───────────────────────────────────────┘     │
│                                                 │
│  Status: ✅ Connecté                            │
│                                                 │
└─────────────────────────────────────────────────┘
```

### Si l'app demande des champs séparés:

```
┌─────────────────────────────────────────────────┐
│  Serveur Bridge:                                │
│  ┌───────────────────────────────────────┐     │
│  │ Adresse IP:  127.0.0.1               │     │
│  └───────────────────────────────────────┘     │
│  ┌───────────────────────────────────────┐     │
│  │ Port:        8080                     │     │
│  └───────────────────────────────────────┘     │
│                                                 │
│  [✓] Utiliser HTTP                             │
│  [ ] Utiliser HTTPS                            │
│                                                 │
│  [Sauvegarder]                                 │
└─────────────────────────────────────────────────┘
```

═══════════════════════════════════════════════════════════════

## ✅ VÉRIFICATION DE LA CONFIGURATION

### Test 1: Depuis Termux
```bash
curl http://127.0.0.1:8080/health
```

**Résultat attendu:**
```json
{"status":"ok","timestamp":1790379000000,"service":"Termux Bridge"}
```

### Test 2: Depuis l'application
1. Entrez: `http://127.0.0.1:8080`
2. Tapez sur "Tester la connexion"
3. Vous devriez voir: ✅ "Connexion réussie"

═══════════════════════════════════════════════════════════════

## 🔍 ALTERNATIVES ET VARIANTES

### Si 127.0.0.1 ne fonctionne pas:

**Essayez localhost:**
```
http://localhost:8080
```

**Ou l'IP de loopback IPv6:**
```
http://[::1]:8080
```

**Ou l'IP locale de Termux:**
```bash
# Trouver l'IP locale
ifconfig | grep "inet " | grep -v "127.0.0.1"
```

Utilisez l'IP trouvée (ex: 192.168.x.x) avec le port 8080

═══════════════════════════════════════════════════════════════

## 🚨 DÉPANNAGE

### Problème: "Connexion refusée"

**Solution 1: Vérifier que le Bridge est actif**
```bash
curl http://127.0.0.1:8080/health
```

**Solution 2: Redémarrer le Bridge**
```bash
cd ~/claudeapk/termux-bridge
pkill -f "node server.js"
node server.js > bridge.log 2>&1 &
sleep 2
curl http://127.0.0.1:8080/health
```

**Solution 3: Vérifier les logs**
```bash
tail -f ~/claudeapk/termux-bridge/bridge.log
```

### Problème: "Timeout"

**Vérifier que l'app a la permission INTERNET:**
```bash
pm dump com.termux.devcenter | grep permission | grep INTERNET
```

**Redémarrer l'application:**
```bash
am force-stop com.termux.devcenter
am start -n com.termux.devcenter/.MainActivity
```

### Problème: Services non actifs

**Démarrer tous les services:**
```bash
~/claudeapk/start-all-services.sh
```

═══════════════════════════════════════════════════════════════

## 📊 ARCHITECTURE DE COMMUNICATION

```
┌─────────────────────────────────────┐
│     TermuxDevCenter (Android)       │
│         Configuration:              │
│    http://127.0.0.1:8080           │
└───────────────┬─────────────────────┘
                │
                │ HTTP
                ▼
┌─────────────────────────────────────┐
│      TERMUX BRIDGE (Port 8080)      │
│    Routes toutes les requêtes       │
└────┬──────────┬──────────┬──────────┘
     │          │          │
     │          │          │
     ▼          ▼          ▼
┌─────────┐ ┌──────────┐ ┌──────────┐
│ Files   │ │Commands  │ │OpenCode  │
│ System  │ │(Omni-    │ │(Omni-    │
│         │ │ Exec)    │ │ Route)   │
└─────────┘ └──────────┘ └──────────┘
```

**Important:** Vous n'avez besoin de configurer QUE le Bridge !

═══════════════════════════════════════════════════════════════

## 🎯 RÉSUMÉ EN 3 ÉTAPES

### Étape 1: Vérifier que le Bridge est actif
```bash
curl http://127.0.0.1:8080/health
```

### Étape 2: Ouvrir TermuxDevCenter

### Étape 3: Dans Paramètres, entrer:
```
http://127.0.0.1:8080
```

**C'est tout !** 🎉

═══════════════════════════════════════════════════════════════

## 💡 CONFIGURATION AVANCÉE (OPTIONNELLE)

Si l'application demande plus de détails:

### Endpoints disponibles:
```
Base URL:           http://127.0.0.1:8080

Health:             GET  /health
Liste fichiers:     POST /files/list
Lire fichier:       POST /files/read
Écrire fichier:     POST /files/write
Supprimer:          POST /files/delete
Créer dossier:      POST /files/mkdir
Renommer:           POST /files/rename
Copier:             POST /files/copy
Liste projets:      GET  /projects/list
Exécuter commande:  POST /command/execute
```

### Headers requis:
```
Content-Type: application/json
Accept: application/json
```

═══════════════════════════════════════════════════════════════

## 📞 AIDE RAPIDE

**Commande tout-en-un pour vérifier:**
```bash
echo "Bridge:" && curl -s http://127.0.0.1:8080/health && \
echo "" && echo "Services:" && \
ps aux | grep -E "bridge|omniroute|omni-exec" | grep -v grep | wc -l && echo "processus actifs"
```

**Si vous voyez des problèmes:**
```bash
~/claudeapk/start-all-services.sh
```

═══════════════════════════════════════════════════════════════

**Configuration créée le:** 26 Septembre 2026 - 00:48
**Pour:** TermuxDevCenter v1.0.0
**Système:** Android (Termux)

🎊 **Entrez simplement http://127.0.0.1:8080 dans les paramètres !** 🎊

═══════════════════════════════════════════════════════════════
