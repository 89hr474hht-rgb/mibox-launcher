# Changelog — mibox-launcher

Versions du fork. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions : [SemVer](https://semver.org/lang/fr/).
Les changements de CleanYourTV lui-même sont dans son propre historique Git.

## [Non publié]

## [1.0.1] - 2026-11-05

### Modifié
- Première version publiée sur GitHub, pour tester les mises à jour automatiques. Rien ne change à l'écran.
- Script d'installation : contrôle lui-même l'installation quand Google TV reste l'accueil.

### Ajouté
- Script d'envoi de la clé de signature dans les secrets GitHub, à lancer depuis le Terminal.

## [1.0.0] - 2026-11-05

### Ajouté
- Import de CleanYourTV (upstream `Gathin23/cleanyourtv`) avec son historique complet.
- `mibox/` : rapport d'analyse, audit de la Mi Box S 2e gén. (build 773), inventaire classé des 119 packages, plan de tests avant/après, architecture du fork.
- `mibox/scripts/` : audit ADB en lecture seule (liste blanche de commandes) ; désinstallation vérifiée de l'ancien launcher Razorbill.
- `.github/README.md` : page d'accueil du fork.
- Édition Mi Box du launcher (`-Pmibox`, version 1.0.0) : mises à jour depuis les releases GitHub. Vérification au démarrage et toutes les 24 h, pastille en haut à droite de l'accueil, page « Mises à jour du launcher » dans les réglages (notes, progression, erreurs, historique, ignorer une version). Avant installation : SHA-256, package, version et certificat identique à celui du launcher installé, puis confirmation Android.
- Signature avec la clé du fork, `versionCode` calculé à partir de la version SemVer (`launcher/mibox/VERSION`).
- `.github/workflows/release.yml` : à chaque tag `vX.Y.Z`, publication d'une release signée, après contrôle de la version, de la clé, de la taille et des notes.
- `mibox/scripts/install/install-launcher.sh` : installation de l'édition Mi Box via le script de CleanYourTV, sans désactiver Google TV.

### Modifié
- Architecture : fork de CleanYourTV au lieu d'un launcher écrit de zéro (correction de la recommandation initiale).

### Retiré
- Ancien launcher Razorbill (v0.5–v0.11), conservé dans la branche `legacy-razorbill`.
