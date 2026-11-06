# Changelog — mibox-launcher

Versions du fork. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions : [SemVer](https://semver.org/lang/fr/).
Les changements de CleanYourTV lui-même sont dans son propre historique Git.

## [Non publié]

## [1.3.0] - 2026-11-06

Le launcher s'appelle désormais **LaunchOS**.

### Ajouté
- Nom LaunchOS partout dans l'appli, nouvelle icône et nouvelle bannière (un « L » blanc sur un carré bleu-violet-rose).
- Écran de démarrage : au premier accueil après l'allumage de la box (jamais en revenant à l'accueil), le logo apparaît avec un petit rebond, le nom s'écrit de gauche à droite et une barre se remplit pendant le chargement, sur un fond de couleurs qui dérivent. Il reste au moins 4 secondes, puis s'efface pendant que l'accueil se met en place. Les touches de la télécommande attendent la fin.
- Boutons de la télécommande : nouvelle page dans les réglages pour choisir ce qu'ouvrent les boutons Netflix, YouTube et Prime Video (une appli, ou rien). À gauche, la télécommande du Mi Box S 2e génération redessinée d'après la photo de Xiaomi (bouton marche orange, Assistant, pavé rond, TV+, Retour, Accueil, pastilles blanches Netflix, Prime Video, YouTube et APPS, volume). Elle se redresse en entrant, ses boutons apparaissent un à un, puis elle flotte et se balance légèrement sous un reflet. Le bouton en cours de réglage se soulève, s'éclaire dans sa couleur sous un projecteur qui glisse d'un bouton à l'autre, avec une lumière qui fait le tour de son bord ; quand on choisit une appli, il s'enfonce et des ondes sortent du haut de la télécommande. Les lignes de la page montrent les mêmes pastilles. Par défaut, YouTube ouvre YouTube et les deux autres ne font rien. Il faut activer une fois « Boutons de la télécommande LaunchOS » dans Paramètres → Accessibilité.

### Modifié
- LaunchOS est en français quelle que soit la langue de la box (réglage `mibox_french`), et les derniers textes d'origine non traduits le sont (Applications, Réseau, Affichage, Fond d'écran…). Les écrans d'Android gardent la langue de la box.
- Police : Inter remplace Outfit dans tout le launcher, plus nette et proche de celle d'Apple.
- Page des mises à jour refaite : le logo au centre d'un halo qui montre l'état (ondes pendant la recherche, anneau de téléchargement, coche verte à jour, flèche violette s'il y a une nouvelle version), l'état en une ligne, et à droite des cartes (nouveautés, à propos, historique).
- Carte « Mise à jour disponible » sur l'accueil : nouveau rendu (logo, bord dégradé, puce de version) ; elle glisse depuis la droite avec un petit rebond et repart de la même façon.
- Fond animé de l'accueil bien plus visible : lumières plus fortes, mouvements plus amples et plus rapides, et une légère respiration.
- Nom de l'appli sélectionnée en surbrillance : sous une tuile, texte foncé sur une pastille blanche ; au-dessus du dock, nom plus épais avec un halo et un reflet qui le traverse à chaque changement.

## [1.2.0] - 2026-11-06

### Ajouté
- Pastille réseau : OK ouvre un menu sous la pastille. Il indique si le câble Ethernet est branché, si le Wi-Fi est activé et connecté, si Internet répond, l'adresse IP de la box et le DNS privé, et se met à jour pendant qu'il est ouvert. « Paramètres réseau… » ouvre les réglages d'Android. Un appui long sur la pastille ouvre toujours les réglages Wi-Fi.
- Infos sur l'app : appui long sur une app, puis « Infos sur l'application ». Une fenêtre affiche la version, la date d'installation et de mise à jour, la provenance (Play Store, préinstallée, installée à la main), la version d'Android visée et la taille. « Ouvrir » lance l'app ; « Plus d'infos… » ouvre la page d'Android (forcer l'arrêt, stockage, autorisations).
- Accueil : le fond aux couleurs de l'app sélectionnée s'anime doucement (trois halos qui dérivent lentement), puis glisse vers les couleurs de l'app suivante. Arrêté quand l'accueil n'est pas affiché, ou quand les animations d'Android sont désactivées.
- Horloge : quand l'heure change, les chiffres qui changent roulent comme un compteur.

### Modifié
- Réglages du launcher : nouvelle présentation, avec une icône de couleur par réglage, des sections (Général, Écran d'accueil, Horloge, Apparence, Démarrage), des choix sous forme de boutons, et la version en titre.
- Limite de taille de l'APK publié portée à 720 Ko (environ 670 Ko pour cette version, à cause des icônes et des nouveaux écrans).

## [1.1.1] - 2026-11-05

### Modifié
- Bluetooth : un petit menu s'ouvre directement sous la pastille, comme les autres menus du launcher, au lieu d'une page à part. Les appareils connectés sont en tête, avec leur icône dans un disque bleu. En bas : « Associer un appareil… » et « Réglages Bluetooth… ».

## [1.1.0] - 2026-11-05

### Ajouté
- Pastille Bluetooth en haut de l'accueil : affiche l'enceinte ou le casque connecté, et défile si le nom est long.
- Page Bluetooth : appareils associés et leur état, « Associer un appareil », « Paramètres de l'appareil ».
- Le bouton Paramètres ouvre les réglages du launcher : mises à jour en tête, Bluetooth, puis Paramètres de l'appareil.

### Modifié
- Première mise à jour publiée sur GitHub.
- Côté Mac : le script d'installation contrôle lui-même l'installation ; nouveau script d'envoi de la clé dans les secrets GitHub.

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
