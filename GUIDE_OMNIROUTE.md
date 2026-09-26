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
