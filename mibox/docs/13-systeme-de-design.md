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

## Valeurs

| Jeton | Valeur | Usage |
|---|---|---|
| `lo_black` | #000000 | Fond des écrans |
| `lo_surface_1/2/3` | gris très sombres | Panneaux, champs, éléments choisis |
| `lo_text`, `lo_text_2`, `lo_text_3` | crème à 100, 72 et 62 % | Texte principal, secondaire, discret |
| `lo_focus`, `lo_on_focus` | blanc, presque noir | Élément qui a le focus |
| `lo_error`, `lo_ok` | rouge, vert | États |
| Tailles de texte | 40, 28, 20, 16, 14 sp (13 pour les étiquettes) | Affichage, titre, intitulé, texte, petit |
| Rayons | 12 dp (ligne), 20 dp (panneau), pilule | |
| Espacements | pas de 4 et 8 dp ; cadre de page 48 × 27 dp | Zone sûre de Google TV |

## Pièces prêtes à l'emploi

- Fonds : `lo_focus_row`, `lo_focus_button`, `lo_button_primary`, `lo_focus_chip`, `lo_focus_tile`, `lo_panel`,
  `lo_field`, `lo_track`, `lo_progress`.
- Couleurs selon le focus : `lo_text_on_focus`, `lo_text_2_on_focus`, `lo_icon_on_focus`, `lo_text_on_accent`.
- Java (`com.gathin.launchos.design`) : `Motion`, `Focus.lift(vue)`, `Monogram` (le « lo » qui s'écrit au démarrage).
- Icônes : 24 dp, au trait de 1,75, blanches, teintées par la ligne qui les porte.

## Règles pour une nouvelle vue

1. Prendre une couleur, une taille et un fond parmi les jetons ; aucune valeur en dur.
2. Le focus passe par un fond `lo_focus_*` et `Focus.lift`, jamais par une animation maison.
3. Une animation n'accompagne qu'un changement (ouverture, sélection) et vérifie `Motion.on()`.
4. Un texte visible vient des ressources, en français et en anglais.
