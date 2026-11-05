# Changelog — mibox-launcher

Versions du fork. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions : [SemVer](https://semver.org/lang/fr/).
Les changements de CleanYourTV lui-même sont dans son propre historique Git.

## [Non publié]

### Ajouté
- Import de CleanYourTV (upstream `Gathin23/cleanyourtv`) avec son historique complet.
- `mibox/` : rapport d'analyse, audit de la Mi Box S 2e gén. (build 773), inventaire classé des 119 packages, plan de tests avant/après, architecture du fork.
- `mibox/scripts/` : audit ADB en lecture seule (liste blanche de commandes) ; désinstallation vérifiée de l'ancien launcher Razorbill.
- `.github/README.md` : page d'accueil du fork.

### Modifié
- Architecture : fork de CleanYourTV au lieu d'un launcher écrit de zéro (correction de la recommandation initiale).

### Retiré
- Ancien launcher Razorbill (v0.5–v0.11), conservé dans la branche `legacy-razorbill`.
