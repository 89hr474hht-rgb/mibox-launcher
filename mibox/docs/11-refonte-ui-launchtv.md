# 11 — Refonte de l'interface de LaunchTV OS (LaunchOS TV)

Branche de travail : `refonte/launchtv-ui`. L'état d'avant la refonte est gardé par le tag
`backup/avant-refonte-ui` (même commit que `main` au départ et que la version publiée `tv-v0.5.0`).
Pour revenir en arrière : `git switch main` (la branche n'est jamais fusionnée sans accord), ou
`git reset --hard backup/avant-refonte-ui` sur la branche. Sur la box, la version 0.5.0 publiée reste
installable depuis sa release.

Méthode : une vue à la fois. Chaque vue est construite, testée (tests unitaires, lint, build), puis
validée par le propriétaire avant de passer à la suivante.

| # | Vue | État |
|---|---|---|
| 6 | Paramètres et maintenance (master-detail, Mise à jour et cache) | Codée, à valider sur la box |
| 1 | Connexion | À faire |
| 2 | Accueil | À faire |
| 3 | Films et séries | À faire |
| 4 | Live TV et guide | À faire |
| 5 | Lecteur (OSD) | À faire |

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
├── 1. Connexion            AccountActivity  → LoginActivity (vue 1)
│   ├── LoginBackdrop        affiches assombries à 15 %, en mosaïque lente
│   ├── BrandPanel           logo et titre en gras (gauche)
│   └── LoginForm            URL, identifiant, mot de passe (œil), bouton avec état de chargement
│
├── 2. Accueil              MainActivity + BrowsePage (vue 2)
│   ├── SideRail             barre latérale fermée, s'ouvre au focus (existe : rail)
│   ├── HeroHeader           tiers haut, image plein écran, dégradé vers le noir
│   ├── ShelfRow ×n          carrousels : Reprendre, Derniers ajouts… (RecyclerView horizontales)
│   │   └── PosterCard       focus × 1,05 + bordure, squelette tant que l'image n'est pas là
│   └── MiniPlayer           mini-lecteur ancré en bas à droite
│
├── 3. Films et séries      GridActivity, MovieActivity, SeriesActivity (vue 3)
│   ├── FilterBar            en-tête fixe des filtres
│   ├── PosterGrid           grille 2:3 fluide
│   └── DetailSheet          fond = affiche floutée (pré-calculée), métadonnées à gauche, actions à droite
│
├── 4. Live TV              LivePage (vue 4)
│   ├── ChannelColumn        chaînes à gauche
│   ├── TimeAxis             heures en haut
│   ├── EpgGrid              programmes, avec la ligne « maintenant »
│   └── (repli)              liste optimisée si la grille est trop lourde pour la box
│
├── 5. Lecteur              PlayerActivity, LivePlayer, Players (vue 5)
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

L'ancien écran Actualiser (`RefreshActivity`) n'est plus ouvert depuis les Réglages. Il reste dans le
code tant que la vue 6 n'est pas validée, pour pouvoir revenir en arrière.

### Tests (section « C septdecies » à ajouter au plan de tests après validation)

| Test | Comment | Statut |
|---|---|---|
| TV6-01 | Les catégories s'affichent à gauche ; Haut/Bas change la page de droite | Non testé |
| TV6-02 | Droite et OK entrent dans la page ; Gauche et Retour reviennent à la catégorie ; Retour ferme | Non testé |
| TV6-03 | Chaînes / Films / Séries : la jauge se remplit, devient verte, puis revient au repos avec le nouveau nombre | Non testé |
| TV6-04 | Quitter les Réglages pendant une actualisation, revenir : la jauge a continué | Non testé |
| TV6-05 | Vider le cache : toast « Cache vidé · … libérés » en haut, la taille des images revient à ~0 | Non testé |
| TV6-06 | Squelettes visibles un court instant à l'ouverture de Mise à jour & Cache | Non testé |
| TV6-07 | Tous les réglages de 0.5.0 marchent comme avant (rangement, ordre, doublons, adultes, masquées, lecture, historique) | Non testé |
| TV6-08 | Calcul de la progression (`SyncStoreTest`) | Test réussi (tests unitaires) |
