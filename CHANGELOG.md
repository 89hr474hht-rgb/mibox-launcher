# Changelog — mibox-launcher

Versions du fork. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions : [SemVer](https://semver.org/lang/fr/).
Les changements de CleanYourTV lui-même sont dans son propre historique Git.

## [Non publié]

## [2.2.0] - 2026-11-15

Les réglages de taille et d'opacité se voient avant d'être choisis.

### Ajouté
- Un aperçu animé de l'écran d'accueil à côté de la disposition (Réglages › Écran d'accueil) : les vraies applis
  dans le dock, l'horloge au format choisi, la lueur de fond. Il suit la sélection : en passant sur 5 colonnes, sur
  2 rangées sous le dock ou sur 30 % d'opacité, il montre le résultat, marqué « Aperçu · OK pour garder » ; les
  tuiles grandissent ou rétrécissent, le dock remonte, la barre s'estompe. OK garde le choix, en quitter la ligne
  remet ce qui est choisi. Il reprend les calculs de taille de l'accueil, et un test vérifie qu'ils restent les mêmes.

### Modifié
- Chaque réglage à plusieurs valeurs (colonnes, rangées, opacité, format de l'heure, veille) est un sélecteur sur une
  piste sombre ; une pastille cernée de doré glisse jusqu'à la valeur choisie. Venant du dessus ou du dessous, la
  télécommande arrive sur la valeur choisie, et entrer dans une page aussi.
- Aucun réglage, aucune clé ni aucun identifiant ne change ; le launcher d'origine reste identique à l'octet.

## [2.1.0] - 2026-11-15

Refonte ciblée : l'accueil ne change pas ; les réglages, les menus du haut et les transitions sont repensés.

### Modifié
- Réglages en cinq catégories au lieu de sept : Écran d'accueil (disposition, horloge, applis), Apparence, Veille,
  Musique et une nouvelle page Système (Wi-Fi et réseau, Bluetooth, télécommande, démarrage, mises à jour,
  appareil). Chaque page a des sections titrées et s'ouvre sur son titre.
- Plus compact : lignes de 48 dp, texte de 16 sp, aides plus discrètes, colonne des catégories plus étroite.
- Une icône par fonction (seize nouvelles) : plus d'engrenage, d'horloge ou de maison répétés.
- Menus Wi-Fi, Bluetooth et minuteur : une seule présentation et une seule navigation pour les trois ; ils se
  ferment avec le même mouvement qu'à l'ouverture, Retour compris, et gardent la ligne choisie quand la liste change.
- Transitions : Réglages, le lecteur et l'aperçu de la veille apparaissent en fondu en montant un peu ; Mises à
  jour et Boutons de la télécommande entrent par la droite et repartent par la droite avec Retour. Une page de
  réglages arrive du côté vers lequel on se déplace. Animations d'Android coupées : tout s'affiche à l'instant.
- Veille : sans la permission d'Android, les choix de l'économiseur sont grisés et le message dit comment faire.

### Interne
- Aucun réglage enregistré ne change : `PreferenceKeysTest` fige la liste des fichiers et clés de préférences.

## [2.0.0] - 2026-11-14

Mise à niveau : un seul système de design pour LaunchOS et LaunchOS TV, un lecteur plus fiable, du code rangé.
Aucune fonction retirée.

### Modifié
- Apparence : noir OLED, surfaces plates, texte crème, accent or ; Inter et une échelle de tailles (rien sous 14 sp
  hors étiquettes) ; un seul focus, blanc avec texte sombre, et un léger agrandissement. Plus de dégradés, de halos,
  de verre ni d'animations perpétuelles. Le système est partagé avec LaunchOS TV (`mibox/design`).
- Accueil : la lueur derrière les tuiles est fixe et sombre, elle change de couleur en un tiers de seconde quand on
  passe à une autre appli, puis ne redessine plus rien. Le nom de l'appli n'a plus de reflet ni de capsule en verre.
  Les pastilles du haut ont le focus commun.
- Réglages : icônes au trait fin, lignes de 56 dp, coches dans l'accent, colonnes et marges sur la grille commune.
- Lecteur : hub, listes, panneau des options et carte d'accueil sur les jetons communs ; bouton lecture en or.
  Les pochettes sont des aplats d'une teinte sourde. Le mot de fond du hub est gravé une fois, hors du fil principal,
  et la lumière passe une seule fois à chaque retour sur le hub.
- Menus Bluetooth et réseau : apparition courte sans rebond, disque de l'appareil connecté dans l'accent.
- Télécommande illustrée (boutons des applis) : immobile, elle ne s'anime que quand le bouton choisi change.
- Mises à jour : l'anneau autour du logo dit ce qui se passe (recherche, téléchargement, prêt, erreur) ; les messages
  d'erreur disent quoi faire ensuite.
- Animations : deux courbes et trois durées pour tout ; rien ne bouge quand les animations d'Android sont coupées.

### Corrigé
- Lecteur : une erreur de lecture ou un fichier illisible arrêtent proprement la lecture au lieu de bloquer le
  service ; en pause, le focus audio est rendu et le service s'arrête après 10 minutes ; la lecture reprend après le
  premier parcours de la clé au démarrage.
- Réglages : la page Musique cesse d'écouter la bibliothèque quand l'écran passe en arrière-plan, ce qui ne garde plus l'écran en mémoire.
- L'écran de démarrage se passe avec n'importe quelle touche.
- L'horloge de veille OLED ne se réveille plus que toutes les 5 secondes quand rien ne joue.

### Interne
- `UpstreamViews` : toutes les recherches de vues et de styles du CleanYourTV d'origine passent par un seul endroit, qui
  journalise un nom introuvable ; un test vérifie ces noms à chaque build.
- `Logs` : les erreurs avalées sont journalisées (classe et type seulement, jamais de contenu).
- `MusicActivity` découpée (`MusicHub`, `TrackListAdapter`, `LyricsOptionsPanel`) ; `MusicPrefs` et `Receivers`
  regroupent les préférences et les récepteurs.

## [1.8.0] - 2026-11-12

### Ajouté
- Lecteur de musique : un hub à l'ouverture. Derrière, bien plus grand que l'écran et gravé dans un blanc si sombre qu'on le devine, le nom d'album des étiquettes des fichiers (dans n'importe quelle écriture ; l'arabe en Naskh), ou à défaut le nom du dossier ; il glisse lentement d'un bout à l'autre pour se montrer en entier, et une lumière passe sur ses lettres. Devant, un menu : en lecture ou reprendre, Favoris, chaque collection, Rechercher, options des paroles. Retour depuis une page ramène au hub.
- Paroles mot par mot : la ligne chantée est grise et s'éclaire mot à mot pendant qu'elle est chantée, d'un blanc à bord doux. Les temps par mot du format LRC enrichi (`<mm:ss.xx>`) sont lus ; sans eux, la ligne s'éclaire à un rythme régulier. Trois points pendant une pause.
- Options des paroles : taille du texte, traduction affichée ou non, mot par mot, et décalage (± 3 s par pas de 0,1 s) pour une enceinte Bluetooth qui retarde le son.
- Favoris : le cœur pendant la lecture, ou un appui long sur une piste. Ils reviennent quand la clé est rebranchée.
- Menu latéral dans le lecteur (icônes fines, qui s'ouvre avec leurs noms) et mode « En lecture » où les paroles prennent toute la largeur.
- Bulle par-dessus les autres applis pendant la lecture, pour voir d'où vient le son ; elle se décale un peu chaque minute pour l'écran OLED. Elle demande l'autorisation « Afficher par-dessus d'autres applis » (Réglages → Musique).
- Nouvelle identité : le monogramme « lo » (une ligne du L au o, un point d'or) pour l'icône, la bannière et le logo.
- Nouvel écran de démarrage sur noir : le monogramme s'écrit d'un trait, son point se pose, puis le nom vient à côté ; seul le point respire pendant le chargement.

### Modifié
- Le lecteur et la carte de l'accueil sont redessinés : carte plus compacte, centrée sur le nom de l'appli, avec des barres qui dansent et la ligne de paroles ; textes plus légers.
- Les paroles ne sont plus toutes mises en page à l'ouverture d'une piste, seulement celles à l'écran : une piste de plusieurs centaines de lignes s'affiche tout de suite. Elles sont redessinées seulement quand un mot s'allume.

## [1.7.0] - 2026-11-11

### Ajouté
- Lecteur de musique : une carte au-dessus du dock (à droite) montre ce qui joue et la ligne de paroles en cours ; OK ouvre le lecteur. Chaque dossier de fichiers audio de la box ou d'une clé USB devient une collection, ses pistes dans l'ordre des noms de fichiers, avec une recherche.
- Paroles synchronisées, comme dans les applis de musique : la ligne en cours en grand, sa traduction en petit dessous, le texte qui défile avec le son. Elles viennent d'un fichier `.lrc` du même nom ou de l'intérieur du MP3 ; les écritures de droite à gauche sont prises en charge.
- Pendant la lecture, l'écran de veille OLED affiche la ligne de paroles en cours sous l'heure.
- Réglages → Musique : ouvrir le lecteur, la carte sur l'accueil, l'accès aux fichiers audio, et les espaces de stockage (stockage interne, clés USB) avec leur place libre et le nombre de pistes.

### Modifié
- La colonne des catégories des réglages défile quand elles ne tiennent pas toutes à l'écran.

## [1.6.0] - 2026-11-08

### Ajouté
- Veille pour l'écran OLED : après un moment sans télécommande, un écran entièrement noir avec l'heure (et la date, ou le temps restant du minuteur de veille). Le texte change de place chaque minute en fondu, pour ne jamais marquer la dalle, et se fait plus discret la nuit (22 h – 7 h). Réglages → Veille : l'activer, quand il démarre (5 à 30 min), quand la télé s'éteint (1 h, 2 h, 4 h ou jamais), ce qu'il affiche (Horloge, Heure et date, Discret, Noir) et un aperçu. L'activer d'ici demande la permission WRITE_SECURE_SETTINGS (une fois par ADB) ; sinon on le choisit dans les réglages Android de l'économiseur.

### Modifié
- Pages de LaunchOS (Mises à jour, Boutons de la télécommande, minuteur) sur un noir pur, comme les réglages : sur un écran OLED, le fond reste éteint.
- Réglages de LaunchOS en deux colonnes sur fond noir : les catégories à gauche (Général, Écran d'accueil, Horloge, Veille, Apparence, Démarrage), la page de celle qui est choisie à droite, en fondu. Droite entre dans la page, Gauche revient à la catégorie.

## [1.5.2] - 2026-11-06

### Modifié
- Boutons du haut de l'accueil : celui qu'on sélectionne s'ouvre plus lentement (0,42 s au lieu de 0,22 s), pour qu'on voie son nom arriver ; le précédent se referme en 0,3 s.

## [1.5.1] - 2026-11-06

### Corrigé
- SFR TV : l'autorisation donnée à LaunchOS après sa mise à jour est prise en compte dès le retour sur l'accueil. En 1.5.0, il fallait désactiver puis réactiver « Boutons de la télécommande LaunchOS » dans Accessibilité (ou redémarrer la box). De même, si l'autorisation est retirée, LaunchOS cesse aussitôt de surveiller SFR TV.

## [1.5.0] - 2026-11-06

### Corrigé
- SFR TV : plus de message « L'interface n'est pas encore compatible avec le mode Accessibilité », et la touche OK refonctionne. SFR TV refuse tout service d'accessibilité actif ; LaunchOS retire donc les siens tant que SFR TV est à l'écran, et les remet dès qu'on revient sur LaunchOS (Accueil, Retour). Pendant ce temps, les boutons Netflix, YouTube et Prime Video de la télécommande ne font rien. Le minuteur de veille marche toujours par-dessus SFR TV. Il faut avoir accordé une fois à LaunchOS l'autorisation de modifier les réglages d'accessibilité (par ADB) ; sans elle, rien ne change.

## [1.4.0] - 2026-11-06

### Ajouté
- Minuteur de veille : un nouveau bouton en haut de l'accueil. OK propose 15 min, 30 min, 45 min, 1 h, 1 h 30 ou 2 h (et « Arrêter le minuteur » quand il tourne). Aucun compte à rebours à l'écran ; seul le bouton, quand il est sélectionné, indique « Veille dans 42 min ». Une minute avant la fin, même dans une autre appli, une pastille en haut compte les secondes et un écran propose « Ajouter 30 min » (déjà sélectionné) ou « Mettre en veille » ; Retour arrête le minuteur. À zéro, la box se met en veille et HDMI-CEC éteint la télé. Il utilise « Boutons de la télécommande LaunchOS » dans Accessibilité, déjà activé. Éteindre la télé ou redémarrer la box arrête le minuteur.

### Modifié
- Boutons du haut de l'accueil (Wi-Fi, Bluetooth, Minuteur, Réglages) : de simples ronds avec leur icône. Celui qui est sélectionné s'allonge et affiche son nom, et se referme quand on passe au suivant. La date a de nouveau la place de s'afficher en entier.
- Réglages de LaunchOS : icône de curseurs de réglage à la place de la maison.
- APK plus léger (environ 530 Ko au lieu de 735 Ko) : seules les traductions française et anglaise sont gardées, LaunchOS étant toujours en français.

## [1.3.2] - 2026-11-06

### Modifié
- Nom de l'appli sélectionnée au-dessus du dock : une capsule de verre l'entoure et suit sa largeur ; à chaque changement elle rebondit légèrement et un reflet traverse le nom.
- Écran de démarrage : il apparaît dès la toute première image de l'accueil (plus de flash de l'accueil avant lui), son fond est visible tout de suite, et son animation ne démarre que quand il est vraiment à l'écran (au démarrage, l'accueil peut mettre plusieurs secondes à s'afficher, ce qui faisait rater le début).

## [1.3.1] - 2026-11-06

### Modifié
- Page des boutons de la télécommande : la télécommande zoome doucement sur le bouton sélectionné, un peu plus pendant le choix de l'appli, puis dézoome quand le choix part (avec les ondes).

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
