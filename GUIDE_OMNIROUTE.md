# Utiliser OmniRoute (Claude via Kiro) et Omni-Exec depuis Termux Dev Center

L'application parle directement à :
- **OmniRoute** (`http://localhost:20128/v1/...`) pour discuter avec Claude ;
- **Omni-Exec** (serveur MCP, `http://127.0.0.1:20129/mcp` par défaut) pour que Claude exécute des commandes.

## 1. Une seule fois, dans Termux

Autoriser l'application à démarrer les services :

```bash
mkdir -p ~/.termux
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```

## 2. Clé API OmniRoute (corrige « 401 Authentication required »)

Le 401 signifie qu'OmniRoute **tourne** mais exige une clé.

1. Onglet **OmniRoute** → connectez-vous avec le mot de passe du dashboard.
2. Icône **clé** (API Manager) → créez une clé et copiez-la.
3. **Réglages** → « Clé API OmniRoute » → collez → **Enregistrer et tester**.

## 3. Omni-Exec

1. Accueil → ligne « Omni-Exec (MCP) » → **Démarrer** (ou lancez-le vous-même dans Termux).
2. Si l'URL par défaut ne répond pas, trouvez le port et le chemin réels :
   ```bash
   grep -nE "listen|PORT|app\.(post|get|use|all)\(" ~/.config/opencode/mcp-servers/omni-exec/http-server.js
   ```
   puis mettez l'URL correspondante dans **Réglages › URL MCP d'Omni-Exec** (ex. `http://127.0.0.1:20129/mcp`).
3. Dans **Chat IA**, la puce « Omni-Exec : N outil(s) » confirme que Claude a accès aux commandes.

Chaque commande demandée par Claude affiche une fenêtre **Exécuter / Refuser / Toujours autoriser**
(désactivable dans Réglages). L'onglet **Terminal** passe aussi par Omni-Exec.

## Dépannage

| Message | Solution |
| --- | --- |
| Impossible de joindre OmniRoute | Bouton « Démarrer », ou `omniroute` dans Termux. Journal : `~/.omniroute/omniroute.log` |
| OmniRoute : clé API requise (401) | Étape 2 ci-dessus |
| Aucun modèle disponible | Aucun fournisseur connecté dans l'onglet OmniRoute |
| Impossible de joindre Omni-Exec | Démarrez-le ; journal : `~/.termux-dev-center/omni-exec.log` |
| Omni-Exec a répondu 404 / réponse inattendue | Mauvais chemin : corrigez l'URL MCP (étape 3.2) |
| Omni-Exec a répondu 400 : Missing sessionId | Corrigé en 1.3.0 (transport SSE détecté automatiquement) : mettez l'app à jour |
| Journal Omni-Exec : EADDRINUSE | Omni-Exec tourne déjà, rien à faire. Pour le relancer : `pkill -f http-server.js` puis Démarrer |

## Choisir le modèle (Chat IA)

Touchez la carte du modèle en haut du chat :
- onglets **★ Favoris / Pro / Gratuit / Tous**, recherche (ex. `claude kiro`), modèles regroupés par fournisseur ;
- « Seulement mes comptes connectés » (activé par défaut) masque les modèles sans compte actif ;
- **Claude Sonnet 4.5 via Kiro** (`kr/claude-sonnet-4.5`) est en favori et choisi par défaut ;
- chaque réponse indique le modèle, le fournisseur et le badge PRO/GRATUIT.

« Gratuit » vient du catalogue officiel d'OmniRoute (`assets/free_models.json`) ;
pour le mettre à jour : `python3 scripts/update_free_models.py`.

## Afficher seulement mes fournisseurs (ex. Kiro)

Sélecteur de modèle › **Fournisseurs affichés** : cochez uniquement ceux que vous avez connectés.
Par défaut seul **kiro** est coché s'il existe (sinon tout sauf les fournisseurs « sans clé » d'OmniRoute et les combos auto).

## Modèles Hoplite

Réglages › **Hoplite** : collez votre clé `hop_…` (hoplite.sh › Settings › Account › API keys), enregistrez, choisissez le projet.
Onglet **Hoplite** du sélecteur : chaque conversation crée un thread Hoplite (agent + sandbox, crédits Hoplite) ;
la réponse arrive en quelques minutes, avec un lien vers le thread. Si l'agent attend une approbation, ouvrez le lien.

## Dictée vocale, fichiers et historique (Chat IA)

- 🎤 **Micro** : dictée continue ; les pauses ne coupent pas l'enregistrement (le moteur Android est relancé
  automatiquement après chaque silence). « Terminer » insère le texte pour le relire, « Envoyer » l'envoie directement.
  Utilise la reconnaissance vocale Google du téléphone (connexion internet selon la langue).
- 📎 **Trombone** : tout type de fichier (10 max). Images → envoyées au modèle (redimensionnées) ; fichiers texte/code →
  contenu inclus ; ZIP → liste + fichiers texte inclus ; autres (PDF, APK…) → copie dans
  `Téléchargements/TermuxDevCenter/` que Claude peut ouvrir via Omni-Exec (`~/storage/downloads/TermuxDevCenter/`,
  après `termux-setup-storage`). Les modèles Hoplite reçoivent le texte mais pas les images.
- 🕘 **Historique** (icône en haut de l'app, dans Chat IA et sur l'Accueil) : chaque conversation est enregistrée avec
  son modèle et sa mémoire ; un appui la rouvre et la suite de la discussion reprend là où elle s'était arrêtée.
  Chat IA rouvre automatiquement la dernière conversation. « Nouvelle conversation » garde l'ancienne dans l'historique.
