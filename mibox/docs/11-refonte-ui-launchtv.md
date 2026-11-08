# 11 — Refonte de l'interface de LaunchTV OS (LaunchOS TV)

Branche de travail : `refonte/launchtv-ui`. L'état d'avant la refonte est gardé par le tag
`backup/avant-refonte-ui` (même commit que `main` au départ et que la version publiée `tv-v0.5.0`).
Pour revenir en arrière : `git switch main` (la branche n'est jamais fusionnée sans accord), ou
`git reset --hard backup/avant-refonte-ui` sur la branche. Sur la box, la version 0.5.0 publiée reste
installable depuis sa release.

Méthode prévue : une vue à la fois, validée par le propriétaire avant la suivante. Le propriétaire a ensuite
demandé de finir toutes les vues d'un coup, avec l'émulateur pour les essais.

| # | Vue | État |
|---|---|---|
| 6 | Paramètres et maintenance (master-detail, Mise à jour et cache) | Codée, vue à l'émulateur |
| 1 | Connexion (`AccountActivity`, `PosterWall`) | Codée, vue à l'émulateur |
| 2 | Accueil (`HomePage`) | Codée, vue à l'émulateur |
| 3 | Films et séries (`GridActivity`, `MovieActivity`, `Blur`) | Codée, vue à l'émulateur |
| 4 | Live TV et guide (`GuideActivity`, `Timeline`, mini-lecteur en bas à droite) | Codée, vue à l'émulateur |
| 5 | Lecteur (`Osd`) | Codée, vue à l'émulateur |

Toutes les vues sont dans LaunchOS TV 0.6.0. Elles ont été essayées sur l'émulateur Android TV avec le faux
serveur du dépôt, pas encore sur la box : voir la section « C septdecies » du plan de tests.

## 1. Compréhension du cahier des charges

- **Cible.** Une seule cible : la TV, pilotée à la télécommande (D-pad, OK, Retour). Il n'y a donc pas de
  barre du bas pour mobile ; la navigation principale est une barre latérale, fermée par défaut, qui
  s'ouvre quand elle a le focus. C'est déjà le cas du rail de l'app.
- **Thème.** Sombre premium sur `#0A0A0A`, panneaux en verre (fonds translucides, bordures fines). Les
  couleurs, les rayons et les marges sont des *design tokens* (`colors_ds.xml`, `dimens_ds.xml`).
  Seuls les nouveaux écrans s'en servent, les anciens gardent leurs couleurs jusqu'à leur refonte.
- **Mini-lecteur (PiP).** Il s'ancre en bas à droite. La zone morte, mini-lecteur plus 24 dp de marge,
  mesure 356 × 222 dp depuis le coin. Aucun élément important n'y est placé. `SafeScrollView` fait
  défiler la ligne qui a le focus jusqu'à ce qu'elle sorte de cette zone.
- **Chargement.** Pendant qu'une image, une liste ou une valeur se charge, un squelette (`SkeletonView`)
  occupe sa place. Il ne s'anime que lorsqu'il est visible.
- **Focus.** Sur les cartes : agrandissement × 1,05 et bordure de couleur. Sur les lignes larges des
  réglages : × 1,02, pour que le texte ne déborde pas.

### Ce qui change par rapport au cahier des charges, et pourquoi

| Demande | Ce qui est fait | Raison |
|---|---|---|
| Flou d'arrière-plan (glassmorphism) | Verre sans flou en direct : fond translucide, bordure, reflet en haut, lueurs douces derrière | Le flou en direct (`RenderEffect`) n'existe qu'à partir d'Android 12 (API 31). La Mi Box est en Android 11 (API 30). Pour les vues 2 et 3, l'affiche floutée sera calculée une fois, en tâche de fond, sur une image réduite. Elle sera gardée en cache, ce qui ne coûte rien pendant la navigation. |
| Fond `#0A0A0A` | Appliqué aux nouveaux écrans | Les pages actuelles sont en noir pur, pour l'OLED. Sur la dalle, `#0A0A0A` reste presque éteint, et le verre a besoin d'un fond qui ne soit pas tout à fait noir. Si le noir pur est préféré, c'est une seule valeur à changer (`ds_bg`). |
| Mini-lecteur en bas à droite | Zone réservée dès maintenant | Aujourd'hui, le mini-lecteur de l'accueil est en haut à droite. Il descendra dans le coin bas droit avec les vues 2 et 4. |
| Barre du bas sur mobile | Pas faite | L'app n'existe que sur la TV. |

## 2. Pile technique

La pile ne change pas : Java, vues Android classiques, `minSdk` 26, Media3 1.4.1 pour la lecture,
RecyclerView pour les listes. Aucune nouvelle dépendance.

Les alternatives écartées :

- **Jetpack Compose (Compose for TV)** : 2 à 3 Mo de plus dans l'APK, plus de mémoire et un premier
  affichage plus lent sur la Mi Box (2 Go de RAM partagés avec le système et SFR TV). Il faudrait aussi
  Kotlin et réécrire tout l'écran d'accueil. Le gain visuel est nul : tout ce que le cahier des charges
  demande se fait avec des vues.
- **Leanback** : déprécié par Google et difficile à styliser comme demandé.
- **Glide ou Coil** pour les images : `Images.java` fait déjà le cache mémoire et disque à la bonne
  taille, sans dépendance.

## 3. Arborescence des composants

Tout reste dans le paquet `com.gathin.launchos.tv`. Le code repose sur la visibilité « package » de
Java ; des sous-paquets obligeraient à tout rendre public. Les fichiers sont donc rangés par rôle,
reconnaissable à leur nom. ★ = nouveau dans cette itération.

```
LaunchTV OS
├── Design system (ui/ds)
│   ├── values/colors_ds.xml ★        jetons : fond, verre, bordures, textes, accent, ok, erreur, squelette
│   ├── values/dimens_ds.xml ★        marges de page, rayons, zone morte du mini-lecteur
│   ├── values/themes.xml             + DsButton, DsButtonPrimary ★
│   ├── drawable/ds_*.xml ★           page, panneau de verre, catégorie, ligne, tuile, jauges, boutons, toast
│   ├── SkeletonView ★                barre de chargement animée (shader, pas de bitmap)
│   ├── GaugeButton ★                 bouton → jauge de progression → succès / erreur → repos
│   ├── GlassToast ★                  message en haut de l'écran, jamais focusable, lu à voix haute
│   ├── SafeScrollView ★              défilement qui garde le focus hors du coin du mini-lecteur
│   ├── Rows (+ addGlass ★)           lignes icône / titre / état / interrupteur / flèche
│   └── Ui                            dp, agrandissement au focus, initiales, métadonnées
│
├── État et données
│   ├── SyncStore ★                   source unique de l'état des actualisations (voir § 4)
│   ├── Library                       catalogue par section (chaînes, films, séries), en mémoire 6 h
│   ├── Epg                           guide des programmes par chaîne
│   ├── Images                        cache des logos et affiches (mémoire + disque)
│   ├── History, Channels, Groups, Options   favoris, reprises, chaînes masquées, groupes, réglages
│   ├── AccountStore, Account         le compte, chiffré sur la box
│   ├── Xtream, Http (+ Progress ★), JsonStream   l'API du fournisseur, lue en flux
│   └── Bg                            deux fils de travail, résultat rendu au fil principal
│
├── 1. Connexion            AccountActivity ★ (refaite)
│   ├── PosterWall ★         affiches du cache assombries à 15 %, qui défilent lentement
│   ├── panneau de marque    logo et titre en gras (gauche)
│   └── formulaire           URL, identifiant, mot de passe (œil), bouton avec état de chargement
│
├── 2. Accueil              MainActivity + HomePage ★
│   ├── SideRail             barre latérale fermée, s'ouvre au focus (existe : rail)
│   ├── HeroHeader           tiers haut, image plein écran, dégradé vers le noir
│   ├── ShelfRow ×n          carrousels : Reprendre, Derniers ajouts… (RecyclerView horizontales)
│       └── PosterCard       focus × 1,05 + bordure, squelette tant que l'image n'est pas là
│
├── 3. Films et séries      GridActivity, MovieActivity (refaites), Blur ★
│   ├── FilterBar            en-tête fixe des filtres
│   ├── PosterGrid           grille 2:3 fluide
│   └── DetailSheet          fond = affiche floutée (pré-calculée), métadonnées à gauche, actions à droite
│
├── 4. Live TV              LivePage (mini-lecteur en bas à droite) + GuideActivity ★, Timeline ★
│   ├── ChannelColumn        chaînes à gauche
│   ├── TimeAxis             heures en haut
│   ├── EpgGrid              programmes, avec la ligne « maintenant »
│   └── (repli)              liste optimisée si la grille est trop lourde pour la box
│
├── 5. Lecteur              PlayerActivity + Osd ★ (le direct garde son bandeau, LivePlayer)
│   ├── OsdHeader            titre et heure
│   ├── OsdFooter            barre de lecture fine, commandes au centre
│   ├── OsdTrailing          audio, sous-titres, mini-lecteur (à l'extrême droite)
│   └── AutoHide             disparition après 3 s sans touche
│
└── 6. Paramètres           SettingsActivity ★ (refaite)
    ├── MasterPanel (30 %)   panneau de verre : marque, 6 catégories, version
    │   └── CategoryItem     icône, nom, point « mise à jour disponible »
    └── DetailPane (70 %)    SafeScrollView, une page par catégorie
        ├── Compte
        ├── Chaînes          rangement, ordre, doublons, adultes, masquées, groupes
        ├── Films et séries  groupes
        ├── Lecture          mini-lecteur, épisode suivant, noms simplifiés
        ├── Mise à jour & Cache ★
        │   ├── GaugeButton ×3   Chaînes, Films, Séries
        │   ├── Tout actualiser
        │   ├── CachePanel       logos et affiches, guide, listes (squelettes pendant la mesure)
        │   ├── Vider le cache   → GlassToast « Cache vidé · 48,2 Mo libérés »
        │   └── Mises à jour de l'app
        └── Historique       effacer favoris et reprises
```

## 4. Gestion d'état : un « store » par domaine, à flux unidirectionnel

Le principe : les écrans ne lancent pas eux-mêmes le travail et ne le possèdent pas. Ils demandent à un
store, puis écoutent.

1. **Une source de vérité par domaine.** `SyncStore` est un singleton de processus. Il garde pour
   chaque section un état `Job` *immuable* : phase (`IDLE`, `RUNNING`, `DONE`, `FAILED`), progression
   de 0 à 1 (ou −1 si elle est inconnue), octets reçus, erreur, horodatage.
2. **Le travail ne bloque jamais l'interface.** Le téléchargement et la lecture JSON en flux
   tournent sur les fils de `Bg`, le résultat est rendu au fil principal. La liste est lue au fur et à
   mesure qu'elle arrive. La mémoire reste basse, rien n'est mis en mémoire tampon en entier.
3. **Une progression lissée.** `Http` compte les octets lus (`Http.Progress`, environ tous les 64 Ko)
   dans deux champs `volatile`. Le store les lit au rythme de l'écran (toutes les 90 ms) et publie un
   nouvel état. Une connexion rapide ne déclenche donc pas des milliers de redessins.
4. **Une estimation honnête.** Le pourcentage vient de la taille annoncée par le serveur. S'il n'en
   annonce pas, le store prend la taille de la dernière lecture faite sur la box, sans jamais dépasser
   95 % avant la fin. Sans aucune référence, la jauge passe en mode indéterminé : une lumière balaie la
   jauge et le texte dit les Mo reçus. Ce calcul est une fonction pure, testée (`SyncStoreTest`).
5. **L'abonnement suit le cycle de vie.** Un écran s'abonne dans `onStart` et reçoit tout de suite
   l'état courant. Il se désabonne dans `onStop`. Une actualisation continue donc si on quitte les
   Réglages, et la jauge reprend où elle en est au retour. Il n'y a ni fuite ni rappel vers un écran
   détruit.
6. **Le cache des données reste le même.** `Library` invalide l'ancienne liste par un compteur
   d'époque (une réponse tardive d'une lecture abandonnée est ignorée) et par `generation()`, qui dit à
   l'accueil qu'il doit se reconstruire.

Pourquoi pas `ViewModel` + `LiveData`, `StateFlow` ou RxJava : il faudrait AndroidX Lifecycle et, pour
`StateFlow`, Kotlin et les coroutines. Cela fait des dépendances et du poids pour ce que 150 lignes de
Java font ici. Le même modèle (`Store` + `Listener` + état immuable) servira pour l'EPG de la vue 4 et
pour l'accueil.

## 5. Vue 6 : Paramètres et maintenance

Fichiers : `SettingsActivity.java`, `activity_settings.xml`, `item_ds_category.xml`,
`item_ds_row.xml`, `view_ds_pane_header.xml` et `pane_sync_cache.xml`, plus les composants du design
system ci-dessus.

- **Répartition 30/70.** À gauche, le panneau de verre avec les catégories ; à droite, la page de la
  catégorie qui a le focus. Quand le focus change de catégorie, la page apparaît en fondu avec un
  glissement de 24 dp (180 ms).
- **Télécommande.**
  - Droite ou OK sur une catégorie entre dans la page.
  - Gauche depuis le premier élément d'une ligne, ou Retour, revient à la catégorie.
  - Retour depuis les catégories ferme les Réglages.
  - Le focus ne sort jamais d'une page, ni par le haut ni par le bas.
- **Mise à jour et cache.**
  - **Chaînes, Films, Séries** sont trois `GaugeButton`. Au repos, chacun affiche le nombre de titres et
    l'heure du dernier chargement. Sur OK, il devient une jauge qui se remplit, avec le pourcentage dans
    son coin. Ensuite, il passe au vert pendant 3 s (« 1 234 chaînes · 56 groupes ») puis revient au
    repos, ou au rouge avec la raison de l'échec.
  - **Tout actualiser** lance les trois actualisations et vide le guide des programmes.
  - **Cache** montre la taille des logos et affiches, le guide et les listes en mémoire, avec des
    squelettes pendant la mesure.
  - **Vider le cache** libère les images (mémoire et disque) et le guide. Les listes et les réglages ne
    sont pas touchés. Un toast de verre en haut de l'écran annonce « Cache vidé · X Mo libérés ».
  - **Mises à jour** ouvre l'écran des mises à jour de l'app. Un point sur la catégorie signale une
    version disponible.
- **Zone du mini-lecteur.** Toutes les actions sont à gauche ou en haut de la page. `SafeScrollView`
  calcule la part de son bas qui tombe dans la zone morte, et ajoute autant de marge pour que la
  dernière ligne puisse remonter au-dessus.

## 6. Vues 1 à 5

- **Connexion.** Carte de verre de 820 dp au centre de l'écran. À gauche le logo et « LaunchTV OS » en gras ;
  à droite le formulaire, un œil qui montre ou masque le mot de passe, et Se connecter. Le bouton tourne
  pendant la vérification, puis devient Continuer. En fond, `PosterWall` fait défiler doucement les affiches
  déjà en cache sur la box, à 15 %. Il ne télécharge rien ; au tout premier lancement, il n'y a que les lueurs.
  La connexion s'ouvre seule une fois quand il n'y a pas de compte.
- **Accueil.** Nouvelle entrée Accueil dans le menu de gauche, ouverte au démarrage.
  - Le héros occupe le tiers haut : ce qui est en cours (Reprendre) ou la dernière nouveauté, sur son image en
    plein écran, avec un dégradé vers le noir.
  - En dessous, les rangées : Reprendre la lecture, Vos chaînes, Derniers films, Dernières séries.
  - Le héros suit la carte qui a le focus après une courte pause. Des squelettes occupent la place tant que
    les listes chargent.
- **Films et séries.**
  - La grille a un en-tête de verre fixe et des colonnes calculées selon l'écran. Les genres les plus
    courants servent de filtres ; ils n'apparaissent que si le fournisseur donne des genres.
  - Fiche d'un film : l'affiche floutée en fond (`Blur` : réduite à 48 px puis trois passes de flou, hors du
    fil principal). Infos à gauche ; à droite un panneau de verre avec l'affiche, Regarder ou Reprendre,
    Depuis le début et la bande-annonce (YouTube) quand le fournisseur en donne une.
- **Guide TV.**
  - Une grille horaire de deux heures à partir de la demi-heure en cours, avec les groupes en pastilles
    au-dessus.
  - Les chaînes sont à gauche, les heures en haut, et une ligne bleue marque l'heure actuelle.
  - Droite au bout de la grille avance d'une demi-heure, jusqu'à six heures. OK lance la chaîne dans
    Chaînes.
  - Le guide d'une chaîne n'est demandé (12 programmes) que quand sa ligne est à l'écran. Trois demandes au
    plus partent en même temps, et le résultat est gardé 30 min.
  - Si les performances de la box ne suffisent pas, le repli prévu est la liste de Chaînes, qui existe déjà.
- **Lecteur.** `Osd` remplace les commandes de Media3.
  - En haut, le titre (et la saison et l'épisode) et l'heure.
  - En bas, une barre fine avec la partie déjà chargée, les commandes au centre (−10 s, lecture/pause,
    +30 s, épisode suivant), puis Audio, Sous-titres et Mini-lecteur à droite.
  - Il disparaît après 3 s. Caché, Gauche et Droite reculent ou avancent tout de suite. Les touches média
    marchent toujours.
  - Le mini-lecteur du film utilise le PiP d'Android, et son bouton n'apparaît que si la box le permet.
- **Mini-lecteur des chaînes.** Il est passé en bas à droite de la page Chaînes, à 26 dp des bords. La zone
  morte (356 × 222 dp depuis le coin) ne contient rien d'important.

L'ancien écran Actualiser (`RefreshActivity`) n'est plus ouvert depuis les Réglages. Il reste dans le
code tant que la refonte n'est pas validée, pour pouvoir revenir en arrière.

## 7. Tests

Les tests de toutes les vues sont dans la section « C septdecies » du
[plan de tests](02-plan-de-tests.md) (TV6-01 à TV6-18).
