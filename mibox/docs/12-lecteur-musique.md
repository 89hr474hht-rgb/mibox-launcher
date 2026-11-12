# 12 — Lecteur de musique de LaunchOS

LaunchOS 1.7.0 a ajouté un lecteur de musique, et la 1.8.0 l'a redessiné. Il lit les fichiers audio de la box et des
clés USB, et affiche des paroles synchronisées qui s'éclairent mot par mot, avec leur traduction en petit dessous.
Tout est fait avec les outils d'Android (MediaPlayer, l'index des médias, MediaSession). Aucune bibliothèque n'est
ajoutée, et le lecteur ne fait aucun appel réseau.

## Ce que voit la famille

- **Le hub**, à l'ouverture du lecteur.
  - Derrière, en très grand, le nom d'album que donnent les étiquettes des pistes (ID3 `TALB`), dans n'importe quelle
    écriture, ou à défaut le nom du dossier. Il est bien plus grand que l'écran, gravé dans un blanc si sombre qu'on le
    devine, et le menu flotte par-dessus. Il glisse lentement d'un bout à l'autre et revient (environ 30 s dans chaque
    sens), en commençant par là où il se lit (la droite pour l'arabe), si bien que tout le mot passe. Une lumière
    traverse ses lettres de temps en temps. L'arabe est composé dans la police Naskh d'Android. Le mot sculpté est
    dessiné une fois dans une image à demi-taille, que chaque image ne fait que déplacer.
  - Un menu typographique, avec un trait d'or devant l'entrée choisie :
    - « En lecture » ou « Reprendre » ;
    - Favoris ;
    - chaque collection ;
    - Rechercher ;
    - Options des paroles.
  - En bas à droite, ce qui joue.
  - OK ouvre une entrée. Retour depuis une page revient au hub, et Retour depuis le hub ferme le lecteur.
- **Une page**, celle d'une collection, des favoris ou de la recherche.
  - Un menu d'icônes fines à gauche. Il s'ouvre avec les noms quand on y va.
  - Au milieu, le nom de la section, une recherche et les pistes.
  - À droite, ce qui joue :
    - la pochette aux couleurs de la collection, avec des barres qui dansent ;
    - le titre, le cœur des favoris et le bouton des options ;
    - les paroles ;
    - en bas, la barre de lecture (Gauche/Droite déplacent de 10 s quand elle a le focus), précédent, lecture et suivant.
  - « En lecture » fait glisser les pistes hors de l'écran : les paroles prennent toute la largeur.
- **Les paroles.**
  - La ligne chantée est en blanc : ses mots s'éclairent un à un pendant qu'ils sont chantés, le reste attend en gris.
  - La traduction est dessous, les lignes voisines sont estompées et s'effacent vers les bords.
  - Une pause entre deux lignes montre trois points qui respirent.
  - Une ligne longue remonte pour tenir en entier.
- **Les options des paroles** :
  - la taille du texte (petite, moyenne, grande) ;
  - la traduction (affichée ou masquée) ;
  - le mot par mot (activé ou non) ;
  - le décalage. Une enceinte Bluetooth retarde le son : Gauche/Droite décalent les paroles par pas de 0,1 s, jusqu'à
    3 s, et OK remet à zéro.
- **Les favoris.** Le cœur pendant la lecture, ou un appui long sur une piste. Ils sont gardés par nom de dossier et de
  fichier, et reviennent quand la clé est rebranchée.
- **La carte au-dessus du dock**, à droite.
  - Elle est centrée sur le nom de l'appli sélectionnée.
  - Elle affiche des barres qui dansent, « En lecture », le titre, la ligne en cours et une fine progression.
  - Haut depuis le dock y mène, OK ouvre le lecteur, et Lecture/Pause marche dessus.
  - Un nom d'appli très long la fait s'effacer le temps qu'il est affiché.
- **La bulle par-dessus les autres applis.** Pendant la lecture, quand une autre appli est devant (les menus de
  YouTube, les réglages de la box…), une petite bulle en haut à droite montre ce qui joue.
  - Elle ne prend ni le focus ni les touches.
  - Elle part dès la pause, ou dès qu'un écran de LaunchOS ou l'écran de veille revient.
  - Elle se décale de quelques pixels chaque minute, pour l'écran OLED.
- **L'écran de veille OLED.** Pendant la lecture, la ligne de paroles et sa traduction prennent la place de la date
  sous l'heure.
- **Réglages → Musique** :
  - ouvrir le lecteur ;
  - la carte au-dessus du dock ;
  - la bulle dans les autres applis ;
  - l'accès aux fichiers audio ;
  - les espaces de stockage.

## Comment le lecteur range la musique

- Chaque **dossier** qui contient des fichiers audio devient une **collection**, nommée comme le dossier. Elle
  apparaît d'elle-même quand la clé est branchée.
- Les pistes sont rangées par **nom de fichier**, dans l'ordre naturel : `2` avant `10`, et `001 …`, `002 …` dans
  l'ordre.
- Le **titre** vient des étiquettes du fichier (ID3 `TIT2`). Sinon, c'est le nom du fichier sans son numéro de tête.
- Une collection se lit jusqu'au bout. La dernière piste et sa position sont gardées.
- La **recherche** ignore les accents et la casse, et retrouve aussi un numéro : « 2 » trouve `002 - …`.

## Paroles synchronisées

Le format est le LRC (`[mm:ss.xx]texte`). Le lecteur lit aussi :
- `[offset:±ms]`, `[ti:]`, `[ar:]`, `[al:]` ;
- plusieurs temps sur une même ligne.

Deux lignes au **même instant** forment une paire : la première est affichée en grand, la seconde en petit dessous.
Les applis de musique font ainsi pour les paroles traduites.

**Mot par mot** : le format LRC « enrichi » place un temps devant chaque mot (`<mm:ss.xx>`, ou `[mm:ss.xx]` une fois
le texte commencé). Un temps collé à la fin d'un mot dit quand il se termine, avant une pause. Sans ces temps, la
ligne s'éclaire à un rythme régulier, jusqu'à la ligne suivante.

```
[00:12.40]<00:12.40>Première <00:12.90>ligne<00:13.60> <00:14.10>ici<00:14.80>
[00:12.40]First line, translated
[00:17.05]Ligne suivante
[00:17.05]Next line
```

Le lecteur cherche les paroles à deux endroits, dans cet ordre :

1. **Un fichier `.lrc` du même nom** à côté de la piste (`Titre.mp3` et `Titre.lrc`), quand Android le laisse lire.
2. **Les paroles enregistrées dans le MP3** (étiquette ID3v2 `USLT`, texte au format LRC). Beaucoup d'outils de
   musique savent les écrire.

Il faut le second endroit sur une clé USB : depuis Android 11, une appli qui n'a que l'accès « médias » lit les
fichiers audio, mais pas les fichiers texte rangés à côté. Pour que les paroles suivent sur la box, il faut donc les
mettre **dans** le MP3. Un `.lrc` à côté reste utile pour les autres lecteurs.

**Rapidité** :
- Les lignes sont mises en page quand elles arrivent à l'écran, pas toutes à l'ouverture de la piste.
- Les paroles des dernières pistes sont gardées en mémoire.
- La vue ne se redessine qu'en cas de besoin : environ 25 fois par seconde pendant qu'un mot s'allume, sinon seulement
  à la ligne suivante.
- La position suit le son en douceur, entre deux lectures du lecteur.

## Permissions (édition Mi Box seulement)

| Permission | Pourquoi |
|---|---|
| `READ_EXTERNAL_STORAGE` (≤ Android 12), `READ_MEDIA_AUDIO` (13 +) | Lire les fichiers audio. Android le demande à l'écran (« Autoriser l'accès » du lecteur, ou Réglages → Musique). Sous Android 11, la fenêtre parle de « photos et médias » : c'est le nom de cette permission, mais le lecteur ne lit que l'audio. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continuer la lecture derrière l'écran de veille ou une autre appli (`MusicService`). |
| `SYSTEM_ALERT_WINDOW` | La bulle par-dessus les autres applis. Elle s'accorde dans l'écran d'Android « Afficher par-dessus d'autres applis », que Réglages → Musique ouvre. Sans elle, il n'y a pas de bulle. |

Le build CleanYourTV d'origine (sans `-Pmibox`) ne change pas : rien de tout cela n'y est.

## Comportement avec le reste de la box

- **Une autre appli qui joue du son**, par exemple un film, met la musique en pause (audio focus). La bulle part avec.
  Une annonce courte baisse seulement la musique.
- **La box qui se met en veille** (minuteur de veille, bouton d'alimentation) met la musique en pause.
- **Les touches média de la télécommande** marchent partout grâce à la MediaSession.
- **Une clé retirée pendant la lecture** arrête la lecture, puis la bibliothèque est relue.

## Fichiers

`mibox/src/main/java/com/gathin/tvhome/mibox/` :
- `MusicLibrary` : l'index des médias, les collections, les volumes, les favoris, le tri et la recherche ;
- `MusicPlayer` : la lecture, la file, l'audio focus, la session, la position lissée, le décalage et la mémoire ;
- `MusicService` : la lecture au premier plan ;
- `Lrc` (temps par ligne et par mot), `Id3Lyrics`, `LyricsStyle` (les options) ;
- les vues : `LyricsView`, `EqualizerView`, `ShineText` (le grand mot du hub) et `MusicArt` (les couleurs) ;
- `MusicActivity` (hub, menu, pages, options) ;
- `MusicCard` (l'accueil) ;
- `PlayingBubble` (la bulle) ;
- `MusicSettings` (les Réglages) ;
- `OledClockView` : les paroles sur l'écran de veille.

Les tests sont `LrcTest` (dont le mot par mot), `Id3LyricsTest` et `MusicLibraryTest`. Les essais à la télécommande
sont dans les sections « C octodecies » et « C novodecies » du [plan de tests](02-plan-de-tests.md).
