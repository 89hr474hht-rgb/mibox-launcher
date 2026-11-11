# 12 — Lecteur de musique de LaunchOS

LaunchOS 1.7.0 ajoute un lecteur de musique à l'écran d'accueil. Il lit les fichiers audio de la box et des clés
USB, et affiche des paroles synchronisées, la ligne en cours en grand et sa traduction en petit dessous. Tout est
fait avec les outils d'Android (MediaPlayer, l'index des médias, MediaSession). Aucune bibliothèque n'est ajoutée,
et le lecteur ne fait aucun appel réseau.

## Ce que voit la famille

- **Carte au-dessus du dock**, à droite, à la hauteur du nom de l'appli sélectionnée.
  - Elle affiche la piste en cours, la ligne de paroles chantée et une fine barre de progression.
  - Haut depuis le dock y mène, OK ouvre le lecteur, et Lecture/Pause marche dessus.
  - Elle suit le dock quand il bouge et s'efface avec le nom de l'appli quand la grille défile.
  - Après un redémarrage, elle propose de reprendre la dernière piste.
- **Écran du lecteur.**
  - À gauche : une pastille par collection, une recherche et la liste des pistes.
  - À droite : la piste en cours, la barre de lecture (Gauche/Droite déplacent de 10 s quand elle a le focus),
    précédent / lecture / suivant, puis les paroles qui défilent avec le son.
  - La ligne chantée est en grand, la traduction en dessous, et les autres lignes sont estompées.
  - Les écritures de droite à gauche sont mises en page de droite à gauche, et tout est centré.
- **Écran de veille OLED.** Pendant la lecture, la ligne de paroles et sa traduction prennent la place de la date
  sous l'heure. Le bloc change toujours de place chaque minute.
- **Réglages → Musique** :
  - Ouvrir le lecteur ;
  - Lecteur au-dessus du dock (on/off) ;
  - Accès aux fichiers audio ;
  - la liste des espaces de stockage (stockage interne, clés USB), avec leur place libre et le nombre de pistes
    trouvées.

## Comment le lecteur range la musique

- Chaque **dossier** qui contient des fichiers audio devient une **collection**, nommée comme le dossier.
  Elle apparaît d'elle-même quand la clé est branchée.
- Les pistes sont rangées par **nom de fichier**, dans l'ordre naturel : `2` avant `10`, et `001 …`, `002 …`
  dans l'ordre.
- Le **titre** vient des étiquettes du fichier (ID3 `TIT2`). Sinon, c'est le nom du fichier sans son numéro de
  tête.
- Une collection se lit jusqu'au bout. La dernière piste et sa position sont gardées.
- La **recherche** ignore les accents et la casse, et retrouve aussi un numéro : « 2 » trouve `002 - …`.

## Paroles synchronisées

Le format est le LRC (`[mm:ss.xx]texte`), avec `[offset:±ms]`, `[ti:]`, `[ar:]`, `[al:]` et plusieurs temps sur
une même ligne. Deux lignes au **même instant** forment une paire : la première est affichée en grand, la seconde
en petit dessous. Les applis de musique font ainsi pour les paroles traduites.

```
[00:12.40]Première ligne
[00:12.40]First line, translated
[00:17.05]Ligne suivante
[00:17.05]Next line
```

Le lecteur cherche les paroles à deux endroits, dans cet ordre :

1. **Un fichier `.lrc` du même nom** à côté de la piste (`Titre.mp3` et `Titre.lrc`), quand Android le laisse
   lire.
2. **Les paroles enregistrées dans le MP3** (étiquette ID3v2 `USLT`, texte au format LRC). Beaucoup d'outils de
   musique savent les écrire.

Il faut le second endroit sur une clé USB : depuis Android 11, une appli qui n'a que l'accès « médias » lit les
fichiers audio, mais pas les fichiers texte rangés à côté. Pour que les paroles suivent sur la box, il faut donc
les mettre **dans** le MP3. Un `.lrc` à côté reste utile pour les autres lecteurs.

## Permissions (édition Mi Box seulement)

| Permission | Pourquoi |
|---|---|
| `READ_EXTERNAL_STORAGE` (≤ Android 12), `READ_MEDIA_AUDIO` (13 +) | Lire les fichiers audio. Android le demande à l'écran (bouton « Autoriser l'accès » du lecteur, ou Réglages → Musique). Sous Android 11, la fenêtre parle de « photos et médias » : c'est le nom de cette permission, mais le lecteur ne lit que l'audio. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continuer la lecture derrière l'écran de veille ou les menus (`MusicService`). |

Le build CleanYourTV d'origine (sans `-Pmibox`) ne change pas : rien de tout cela n'y est.

## Comportement avec le reste de la box

- **Une autre appli qui joue du son**, par exemple un film, met la musique en pause (audio focus). Une annonce
  courte la baisse seulement.
- **La box qui se met en veille** (minuteur de veille, bouton d'alimentation) met la musique en pause.
- **Les touches média de la télécommande** marchent partout grâce à la MediaSession.
- **Une clé retirée pendant la lecture** arrête la lecture, puis la bibliothèque est relue.

## Fichiers

`mibox/src/main/java/com/gathin/tvhome/mibox/` :
- `MusicLibrary` : l'index des médias, les collections, les volumes, le tri et la recherche ;
- `MusicPlayer` : la lecture, la file, l'audio focus, la session et la mémoire de la dernière piste ;
- `MusicService` : la lecture au premier plan ;
- `Lrc`, `Id3Lyrics` : les paroles ;
- `LyricsView` ;
- `MusicActivity` ;
- `MusicCard` (accueil) ;
- `MusicSettings` (Réglages) ;
- `OledClockView` : les paroles sur l'écran de veille.

Les tests sont `LrcTest`, `Id3LyricsTest` et `MusicLibraryTest`. Les essais à la télécommande sont dans la section
« C octodecies » du [plan de tests](02-plan-de-tests.md).
