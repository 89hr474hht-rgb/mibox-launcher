# Changelog — LaunchOS TV

Versions de LaunchOS TV (`mibox/tv`). Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions :
[SemVer](https://semver.org/lang/fr/). Les versions sont publiées avec un tag `tv-vX.Y.Z` ; ces notes s'affichent
dans l'écran Mises à jour de l'appli.

## [Non publié]

## [0.1.0] - 2026-11-06

Première version.

### Ajouté
- Écran Compte : serveur, identifiant et mot de passe Xtream, testés auprès du serveur avant d'être gardés. Un lien M3U collé dans le champ Serveur remplit l'identifiant et le mot de passe. Le compte reste sur la box, chiffré.
- Chaînes, Films et Séries par catégorie, avec logos et affiches.
- Fiche série : saisons, épisodes et résumés.
- Lecture plein écran : en direct, CH+/CH− ou Haut/Bas pour zapper dans la catégorie, et nouvelles tentatives si le flux coupe ; films et épisodes avec commandes (10 s en arrière, 30 s en avant).
- Messages clairs en français quand le compte est refusé, le serveur injoignable ou un flux illisible.
- Mises à jour automatiques, comme LaunchOS : vérification chaque jour sur GitHub, téléchargement, contrôle de l'empreinte SHA-256 et de la signature, puis confirmation d'Android.
