# Changelog — LaunchOS TV

Versions de LaunchOS TV (`mibox/tv`). Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions :
[SemVer](https://semver.org/lang/fr/). Les versions sont publiées avec un tag `tv-vX.Y.Z` ; ces notes s'affichent
dans l'écran Mises à jour de l'appli.

## [Non publié]

## [0.13.0] - 2026-11-15

### Ajouté
- Connexion avec l'identifiant seul : le mot de passe est facultatif. Laissé vide, l'identifiant sert aussi de mot de
  passe, la forme des comptes à code unique de certains fournisseurs. Si le fournisseur refuse, l'écran le dit et
  demande le mot de passe qu'il a donné. Une connexion refusée ne remplace pas le compte enregistré.

## [0.12.0] - 2026-11-15

Un code parental, des favoris rangés à la main, et un Accueil assorti à Films et Séries.

### Ajouté
- Code parental (Réglages › Chaînes et guide) : 4 chiffres, tapés avec les flèches (Haut et Bas : chiffre, Gauche et
  Droite : case). Avec un code, les contenus adultes restent masqués : il est demandé pour les montrer, pour réafficher
  un groupe pour adultes, et ils se cachent à nouveau dès que l'appli quitte l'écran. Le code peut être changé ou
  supprimé (en donnant l'ancien) ; le créer, le changer ou le supprimer remasque les contenus adultes. Il est gardé
  sur la box seulement, haché (SHA-256 avec sel), jamais en clair.
- Favoris rangés à la main : appui long sur OK dans l'étagère Favoris › Monter, Descendre, Mettre en premier ; le
  panneau reste ouvert pour enchaîner, et la liste suit.
- Favoris : mes chaînes seulement (Réglages › Chaînes et guide) : l'étagère ne garde que les chaînes ajoutées à la
  main, sans les plus regardées.

### Modifié
- Accueil : la mise en avant est la même carte arrondie que dans Films et Séries, sur la page anthracite, avec les
  boutons translucides.

## [0.11.0] - 2026-11-15

Films et Séries prennent un nouveau visage, et les favoris se regardent plus vite.

### Ajouté
- Ouvrir sur › Favoris : l'appli s'ouvre sur une chaîne favorite prise au hasard, en plein écran ; Haut et Bas passent
  d'un favori à l'autre, Retour mène à l'étagère Favoris. Sans favori, elle s'ouvre sur la TV.
- Dans l'étagère Favoris, avec le mini-lecteur allumé, Haut et Bas dans la liste changent la chaîne du mini-lecteur
  dès que la sélection s'arrête.

### Modifié
- Films et Séries : la mise en avant est une carte arrondie, en retrait des bords, sur une page anthracite. L'image du
  titre la remplit ; le texte, les boutons Regarder et Plus d'infos (translucides, blancs une fois sélectionnés), et une
  étiquette en bas à droite (« Ajouté il y a 3 jours », « Mis à jour hier ») sont dedans, avec la recherche et la liste
  complète en haut à droite. Les rangées apparaissent sous la carte ; en y descendant, la carte se replie sur son texte.
- La barre du haut est centrée ; la section affichée a une pastille grise au lieu d'un texte coloré.

### Corrigé
- Le mini-lecteur suit sa place quand la fiche de la chaîne au-dessus change de hauteur.

## [0.10.0] - 2026-11-15

### Ajouté
- Ouvrir sur : l'appli peut s'ouvrir sur l'Accueil (comme avant), sur la TV ou sur la dernière chaîne regardée, qui
  démarre tout de suite ; Haut et Bas zappent dans toutes les chaînes dès que la liste est lue.
- Décalage du guide, de −2 h à +2 h, pour un fournisseur dont les horaires sont décalés.
- Inverser Haut et Bas pour zapper ; les touches Chaîne + et − ne changent pas.
- Format de l'image : Adapté (comme avant), Zoom ou Étiré, pour la TV, les films et les séries.
- Actualiser les listes toutes les 6 h (comme avant), 12 h ou 24 h.

### Modifié
- Réglages en cinq catégories : Général (compte, démarrage, mises à jour), Chaînes et guide, Films et séries,
  Lecture, Données (listes, cache, historique). Chaque page a des sections titrées et des lignes plus compactes.
- Les réglages à plusieurs valeurs se choisissent côte à côte, avec une pastille qui glisse jusqu'au choix ; les
  interrupteurs glissent ; les pages arrivent du côté où va la télécommande, comme dans LaunchOS.
- Chaque réglage existant garde sa clé et sa valeur ; les nouveaux reprennent par défaut le comportement d'avant.
- Les Réglages sont plus petits, comme ceux de LaunchOS : texte, lignes, icônes, interrupteurs et jauges réduits,
  et la page garde une largeur fixe au lieu de remplir tout l'écran.
- Plus rapide : l'écran de démarrage dure 1,5 s au lieu de 2,5 s, et l'appli cachée dessous n'est plus dessinée
  tant qu'il la couvre, ce qui laisse la box charger les listes. Une chaîne ou un film démarre dès 1,5 s de vidéo
  reçue au lieu de 2,5 s (3 s au lieu de 5 après une coupure).

## [0.9.0] - 2026-11-15

### Ajouté
- Une barre de navigation en haut : Réglages à gauche, puis Rechercher, Accueil, TV, Films et Séries, et le petit
  logo à droite. Elle s'efface quand on descend dans les rangées et revient en haut.
- TV rangée pour le direct : Favoris, Récentes, puis un rayon par thème (Généralistes dans l'ordre de la TNT, Info,
  Sport, Cinéma, Jeunesse…), un rayon par autre pays, et les groupes du fournisseur tels quels, un cran plus loin.
- Une chaîne proposée en plusieurs versions (HD, SD, 4K, ou dans deux groupes) n'apparaît qu'une fois, avec ses
  versions indiquées ; chacune reste disponible depuis l'appui long (« Regarder en 4K »).
- Onglets En direct et Guide en tête de la section TV.

### Modifié
- Le menu latéral disparaît ; les pages commencent sous la barre.
- Le classement n'est qu'une présentation : rien n'est changé dans les listes du fournisseur, et deux chaînes
  différentes ne sont jamais réunies parce que leurs noms se ressemblent.

## [0.8.0] - 2026-11-14

Mise à niveau : le système de design commun avec LaunchOS, une appli plus stable et plus discrète. Aucune fonction
retirée.

### Ajouté
- Bouton Réessayer quand une page ne charge pas (Chaînes, Accueil, Films, Séries, Guide TV, une série).
- Les suppressions (compte, favoris, points de reprise) demandent un second appui sur OK, pendant 5 secondes.

### Modifié
- Apparence : noir OLED, surfaces plates, accent corail, Inter et une échelle de tailles (texte du guide en 16 sp) ;
  un seul focus blanc, deux styles de bouton, un seul champ. Plus de verre, de lueurs ni de dégradés. Les fiches Film
  et Série ont la même mise en page.
- Chaînes : le format de flux qui a marché (TS ou HLS) est retenu et essayé en premier pour la chaîne suivante.
- Navigation plus fluide entre les rangées et dans le menu latéral ; le guide réutilise ses cases au lieu de les
  recréer.
- Messages d'erreur en phrases simples ; le code technique n'apparaît qu'entre parenthèses.
- L'écran de démarrage laisse passer la touche qui le ferme.

### Sécurité et vie privée
- Supprimer le compte efface aussi l'historique, les favoris, les groupes, le guide, le catalogue et les images.
- Un serveur en https ne peut plus rediriger vers du http.
- Pas de saisie automatique sur le formulaire de connexion ; pas de transfert vers un autre appareil.
- Les images sont demandées avec un agent générique ; leur cache est plafonné à 64 Mo.
- La bande-annonce ne s'ouvre que pour une adresse YouTube.

### Interne
- Les synchronisations du catalogue ont leur propre fil ; l'historique est enregistré en JSON nommé, avec une
  migration automatique ; nouveaux tests (HTTP, bandes-annonces, cache, historique, guide).

## [0.7.0] - 2026-11-12

### Ajouté
- Écran de démarrage à l'ouverture de l'appli : sur noir, le monogramme s'écrit d'un trait, son point corail se pose et « LaunchOS TV » vient à côté, puis l'appli se pose derrière. Environ 2,5 s ; une touche le passe.

### Modifié
- Nouvelle icône et nouvelle bannière : le monogramme « lo » de LaunchOS, avec un point corail, sur fond presque noir. Le logo de l'en-tête suit.

## [0.6.0] - 2026-11-08

Refonte de l'interface : LaunchTV OS. Thème sombre premium sur `#0A0A0A`, panneaux en verre, focus agrandi avec une bordure bleue, squelettes de chargement.

### Ajouté
- Accueil : un tableau de bord, ouvert au démarrage. En haut, ce qui est en cours (ou la dernière nouveauté) sur son image en plein écran ; en dessous, Reprendre la lecture, Vos chaînes, les derniers films et les dernières séries.
- Guide TV : une grille horaire. Les chaînes à gauche, les heures en haut, les programmes au milieu et une ligne à l'heure actuelle ; Droite au bout avance d'une demi-heure. OK lance la chaîne.
- Lecteur : un affichage maison. Titre et heure en haut, barre de lecture fine, commandes au centre, langue audio, sous-titres et mini-lecteur à droite ; il se cache après 3 secondes. Gauche et Droite reculent de 10 s ou avancent de 30 s même quand il est caché ; bouton Épisode suivant.
- Réglages en deux colonnes (catégories à gauche, page à droite), avec une page Mise à jour & Cache : Chaînes, Films et Séries deviennent des jauges pendant l'actualisation (qui continue si on quitte la page), et Vider le cache annonce la place libérée.
- Fiche d'un film : l'affiche floutée en fond, les infos à gauche, l'affiche et les actions à droite, et la bande-annonce quand le fournisseur en donne une.
- Films et séries en grille : l'en-tête reste en haut, le nombre de colonnes suit l'écran, et les genres les plus courants servent de filtres.

### Modifié
- Connexion : une carte en verre au milieu de l'écran, le logo à gauche et le formulaire à droite, sur les affiches déjà gardées sur la box (assombries à 15 %). Un œil affiche le mot de passe ; Se connecter tourne pendant la vérification, puis devient Continuer. Elle s'ouvre seule au premier lancement sans compte.
- Le mini-lecteur de Chaînes est en bas à droite ; rien d'important n'est placé dans ce coin.

### Corrigé
- Survoler Chaînes dans le menu ne fait plus partir la sélection dans la page pendant son chargement.

## [0.5.0] - 2026-11-08

### Ajouté
- Rangement intelligent des groupes, à partir de leur nom : la France d'abord (puis Belgique, Suisse, Québec, puis les autres pays), et dans chaque pays les généralistes, l'info, le sport, le cinéma, les séries, la jeunesse, les documentaires… Chaque groupe a l'icône de son thème. Réglages → Rangement des groupes : Intelligent, A à Z ou Fournisseur.
- Ordre des chaînes « Numéros TNT » : TF1, France 2, France 3, Canal+… à leur numéro, puis les autres de A à Z. Aussi A à Z ou Fournisseur.
- Doublons regroupés : une chaîne proposée en SD, HD et FHD n'apparaît qu'une fois, dans la meilleure qualité (à couper dans Réglages).
- Groupes pour adultes masqués d'office, repérés à leur nom ; un groupe peut être réaffiché dans Groupes des chaînes, et le réglage se coupe.
- Appui long sur OK sur une chaîne : Ajouter aux favoris (elle passe en tête de Favoris, avant les plus regardées) ou Masquer cette chaîne. Réglages → Chaînes masquées pour les réafficher.

### Modifié
- Fond noir pur partout (pages, réglages, dégradés derrière les affiches) : sur l'écran OLED, ce qui est noir reste éteint.
- Menu de gauche plus court : Rechercher, Chaînes, Films, Séries et Réglages. Compte, Actualiser et Mises à jour sont en haut de Réglages, qui regroupe tout par sections (Compte et application, Chaînes, Films et séries, Lecture et affichage, Historique) ; un point sur Réglages signale une mise à jour.

## [0.4.0] - 2026-11-06

### Corrigé
- Menu de gauche : Bas ne s'arrêtait plus à Séries. Actualiser, Réglages, Mises à jour et Compte sont de nouveau joignables à la télécommande.
- Films et Séries : en entrant depuis le menu pendant que le catalogue charge, la sélection arrive sur « Regarder » et non plus sur « Rechercher ».

### Modifié
- Nouvelle apparence des écrans Actualiser, Réglages, Mises à jour, Compte et Groupes : en-tête avec icône, lignes avec icône rangées par sections, la ligne choisie en blanc comme dans le menu.
- Réglages : de vrais interrupteurs pour le mini-lecteur, l'épisode suivant et les noms nettoyés.
- Mises à jour : l'état dans un encadré avec icône (à jour, nouvelle version, erreur) et l'historique ligne par ligne.
- Compte : icônes dans les champs et les boutons, résultat du test dans un encadré.
- Tout voir : les tris ont leur icône. Recherche : en-tête avec icône.
- Fiche film : le bouton s'appelle « Regarder », comme sur l'affiche. Le résumé d'un film apparaît en fondu.

## [0.3.0] - 2026-11-06

### Ajouté
- Films et Séries en plein écran, comme sur une Apple TV : les nouveautés à l'affiche avec leur image en grand, en fondu de l'une à l'autre toutes les 8 secondes ; Regarder et Plus d'infos ; puis des rangées « Reprendre », « Récemment ajoutés » et une par groupe, chacune avec « Tout voir ». L'image de fond suit la sélection.
- Fiche film : image, année, durée, genre, note, réalisateur, acteurs, résumé ; Regarder ou Reprendre là où tu t'étais arrêté.
- Tout voir : une grille triée par Récents, A → Z, Mieux notés ou Année (l'ordre choisi est gardé).
- Recherche dans les chaînes, les films et les séries pendant la frappe, sans accents ni majuscules ; les titres qui commencent par le mot viennent d'abord, et un même titre présent dans plusieurs groupes n'apparaît qu'une fois.
- Chaînes : un groupe Favoris en haut, avec les chaînes que tu regardes le plus (au temps passé, à partir d'une minute), puis Toutes les chaînes, puis les groupes de A à Z.
- Mini-lecteur : Retour depuis le plein écran garde la chaîne en petit en haut à droite ; tu continues à parcourir, OK sur une autre chaîne la remet en grand. Retour encore une fois le ferme.
- Séries : épisode suivant lancé tout seul à la fin d'un épisode, et la fiche propose l'épisode où tu en es.
- Groupes : appui long sur OK (ou Réglages → Groupes) pour renommer, masquer ou épingler en haut un groupe ; « Tout rétablir » pour revenir aux groupes du fournisseur.
- Réglages : mini-lecteur, épisode suivant automatique et noms nettoyés, chacun à activer ou couper.

### Modifié
- Noms nettoyés : « FR: TF1 FHD », « |FR| Film (2019) 4K » ou « Série (VOSTFR) » deviennent « TF1 », « Film » et « Série », avec la qualité ou la langue en petite étiquette et l'année à part. Pour les groupes, le préfixe que la plupart partagent (« FR | ») disparaît, ainsi que les étoiles, les majuscules et la numérotation ; « UK | Sport » garde le sien.
- Menu de gauche : une fine bande d'icônes séparée des colonnes, qui s'ouvre par-dessus la page seulement quand on y entre (Gauche depuis le bord d'une page) ; Droite revient où tu étais. Haut et Bas restent dans la colonne où tu es.
- Les listes complètes sont lues au fil de l'eau, pour tenir dans la mémoire de la box même avec un gros catalogue.

### Corrigé
- Un direct qui coupe quelques secondes après chaque reprise ne réessaie plus sans fin : après deux essais et un essai en HLS, un message s'affiche.

## [0.2.0] - 2026-11-06

### Ajouté
- Menu Actualiser (à gauche, sous Séries) : recharge les chaînes, les films, les séries et le guide des programmes, un par un ou tout d'un coup, et vide les logos et affiches gardés sur la box. Chaque ligne dit quand elle a été chargée.
- Guide des programmes : le programme en cours sous chaque chaîne, avec une barre d'avancement ; en direct, l'heure, le titre et le programme suivant.

### Modifié
- Les listes sont gardées pour toute l'appli et se rechargent seules après 6 heures ; le guide se recharge à la fin de chaque programme.

## [0.1.0] - 2026-11-06

Première version.

### Ajouté
- Écran Compte : serveur, identifiant et mot de passe Xtream, testés auprès du serveur avant d'être gardés. Un lien M3U collé dans le champ Serveur remplit l'identifiant et le mot de passe. Le compte reste sur la box, chiffré.
- Chaînes, Films et Séries par catégorie, avec logos et affiches.
- Fiche série : saisons, épisodes et résumés.
- Lecture plein écran : en direct, CH+/CH− ou Haut/Bas pour zapper dans la catégorie, et nouvelles tentatives si le flux coupe ; films et épisodes avec commandes (10 s en arrière, 30 s en avant).
- Messages clairs en français quand le compte est refusé, le serveur injoignable ou un flux illisible.
- Mises à jour automatiques, comme LaunchOS : vérification chaque jour sur GitHub, téléchargement, contrôle de l'empreinte SHA-256 et de la signature, puis confirmation d'Android.
