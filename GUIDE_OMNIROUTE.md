# Utiliser OmniRoute (Claude via Kiro) depuis Termux Dev Center

L'application parle directement à l'API d'OmniRoute (`http://localhost:20128/v1/...`).
OmniRoute doit simplement tourner en arrière-plan dans Termux : plus besoin d'OpenCode ni de taper des commandes.

## 1. Une seule fois, dans Termux

Autoriser l'application à lancer OmniRoute pour vous :

```bash
mkdir -p ~/.termux
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```

## 2. Dans l'application

1. **Accueil** → carte OmniRoute → **Démarrer OmniRoute** (accepter la permission « Exécuter des commandes dans Termux »).
2. Onglet **OmniRoute** : c'est le dashboard OmniRoute intégré (`/dashboard/providers`).
   - Connectez-vous avec le mot de passe du dashboard.
   - Connectez/vérifiez votre fournisseur (Kiro…).
   - Icône clé → **API Manager** : créez une clé API et copiez-la.
3. **Paramètres** → section OmniRoute : collez la clé, puis **Enregistrer et tester la connexion**.
4. Onglet **Chat IA** : choisissez le modèle (Claude Sonnet 4.5 est présélectionné s'il est disponible) et discutez.

Si une connexion de fournisseur refuse de s'ouvrir dans l'application (certains logins Google bloquent les WebView),
utilisez l'icône « Ouvrir dans le navigateur » de l'onglet OmniRoute.

## Dépannage

| Message | Solution |
| --- | --- |
| Impossible de joindre OmniRoute | OmniRoute n'est pas lancé : bouton « Démarrer OmniRoute », ou `omniroute` dans Termux. Journal : `~/.omniroute/omniroute.log` |
| OmniRoute a répondu 401 | Clé API absente ou invalide dans Paramètres |
| Aucun modèle disponible | Aucun fournisseur connecté dans l'onglet OmniRoute |
