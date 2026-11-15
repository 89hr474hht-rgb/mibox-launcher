# Système de design commun (LaunchOS et LaunchOS TV)

Un seul jeu de valeurs pour les deux applis, dans [`mibox/design`](../design/README.md). Les deux builds y prennent
leurs ressources et trois classes Java ; une valeur changée là change les deux applis.

## Principes

- **Noir OLED** pour le fond, trois surfaces plates au-dessus, une ligne fine pour séparer. Pas de dégradé, de halo,
  de verre ni de reflet.
- **Un accent par appli** : or pour LaunchOS, corail pour LaunchOS TV (`lo_accent`, défini dans chaque appli). Le
  crème de la marque sert au texte principal.
- **Un seul focus** : l'élément choisi devient blanc avec un texte sombre, et grandit de 5 % (2,5 % pour une large
  ligne). Une vignette d'image reçoit un contour blanc.
- **Des animations rares** : deux courbes (`Motion.ENTER` pour ce qui arrive, `Motion.MOVE` pour ce qui se déplace),
  trois durées (120, 220, 360 ms). Rien ne tourne en boucle. Animations d'Android coupées : rien ne bouge.

## Transitions (LaunchOS 2.1)

- Un écran du module Mi Box s'ouvre en fondu en montant de 2,5 % (220 ms) et se ferme en descendant (160 ms) ; un
  écran ouvert depuis une ligne des Réglages entre par la droite et repart par la droite. Le module les pose sur
  chaque fenêtre ; le thème du launcher d'origine n'est pas modifié.
- Une page de réglages arrive du côté vers lequel la télécommande s'est déplacée.
- Les menus du haut s'ouvrent en descendant de la pastille et se ferment de même, Retour compris.
- LaunchOS TV : la barre du haut s'efface dans les rangées et revient en haut ; le reste du langage est le même.
- Un choix parmi quelques valeurs est un `SegmentBar` : une piste sombre, une pastille cernée de l'accent qui glisse
  (220 ms) jusqu'à la valeur choisie. Un interrupteur glisse de même. Un réglage qui change l'écran se montre en
  aperçu avant OK (l'aperçu de l'accueil de LaunchOS).

## Valeurs

| Jeton | Valeur | Usage |
|---|---|---|
| `lo_black` | #000000 | Fond des écrans |
| `lo_surface_1/2/3` | gris très sombres | Panneaux, champs, éléments choisis |
| `lo_text`, `lo_text_2`, `lo_text_3` | crème à 100, 72 et 62 % | Texte principal, secondaire, discret |
| `lo_focus`, `lo_on_focus` | blanc, presque noir | Élément qui a le focus |
| `lo_error`, `lo_ok` | rouge, vert | États |
| Tailles de texte | 40, 28, 20, 16, 14 sp (13 pour les étiquettes) | Affichage, titre, intitulé, texte, petit |
| Réglages (lo_settings.xml) | 20, 16, 14, 12 sp (11 pour les étiquettes) ; catégories 224 dp, page 520 dp, lignes 42 dp | Plus petit que le reste : une page de réglages se lit de près, et la page ne remplit pas l'écran |
| Rayons | 12 dp (ligne), 20 dp (panneau), pilule | |
| Espacements | pas de 4 et 8 dp ; cadre de page 48 × 27 dp | Zone sûre de Google TV |

## Pièces prêtes à l'emploi

- Fonds : `lo_focus_row`, `lo_focus_button`, `lo_button_primary`, `lo_focus_chip`, `lo_focus_tile`, `lo_panel`,
  `lo_field`, `lo_track`, `lo_progress`.
- Couleurs selon le focus : `lo_text_on_focus`, `lo_text_2_on_focus`, `lo_icon_on_focus`, `lo_text_on_accent`.
- Java (`com.gathin.launchos.design`) : `Motion`, `Focus.lift(vue)`, `Monogram` (le « lo » qui s'écrit au démarrage),
  `SegmentBar` (les couleurs de la piste et de la pastille lui sont passées par l'appli).
- Icônes : 24 dp, au trait de 1,75, blanches, teintées par la ligne qui les porte.
- Carte de mise en avant (LaunchOS TV, Films et Séries) : carte arrondie de 18 dp en retrait des bords
  (`hero_card_bg`), image dedans, voile à gauche, boutons et puces translucides (`Button.Glass`, `Chip.Glass` :
  blanc à 28 % au repos, blancs une fois sélectionnés), étiquette en bas à droite (`hero_badge_bg`), page anthracite
  (`browse_bg`). Elle se replie sur son texte en coupant son contour, sans refaire la mise en page à chaque image.
- Saisie d'un code (LaunchOS TV, `PinDialog`) : quatre cases, celle en cours blanche ; Haut et Bas changent le
  chiffre, Gauche et Droite la case, OK valide ; un code refusé secoue les cases et le dit en couleur d'accent.
- Barre du haut (LaunchOS TV) : centrée ; la section affichée porte une pastille grise (`nav_tab_bg`, `nav_tab_text`).

## Règles pour une nouvelle vue

1. Prendre une couleur, une taille et un fond parmi les jetons ; aucune valeur en dur.
2. Le focus passe par un fond `lo_focus_*` et `Focus.lift`, jamais par une animation maison.
3. Une animation n'accompagne qu'un changement (ouverture, sélection) et vérifie `Motion.on()`.
4. Un texte visible vient des ressources, en français et en anglais.
