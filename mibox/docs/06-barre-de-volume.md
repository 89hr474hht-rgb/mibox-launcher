# Barre de volume personnalisée : étude et blocage

**Statut : en attente.** Le code se trouve sur la branche `volume-hud`. Il n'est pas publié et n'est pas installé sur la box.

## Ce qui est fait

- `VolumeHudService` : un service d'accessibilité qui reçoit les touches de volume. Il ne lit rien à l'écran.
- `VolumeHudView` : la capsule noire en haut au centre.
  - Elle s'ouvre comme la Dynamic Island de l'iPhone.
  - La barre épaisse se remplit de blanc.
  - Elle s'étire comme un élastique quand on force au maximum ou au minimum.
  - Le texte utilise la police Outfit.
- Les ressorts sont faits maison et testés (`Spring`, `VolumeLevels`, `VolumeHudTest`).
- Les icônes viennent de Material Symbols Rounded (licence Apache 2.0).

Tout a été vérifié sur un émulateur Android TV 12 (API 31, 1920×1080, densité 320), avec une télécommande Bluetooth virtuelle (outil `hid`) :
- la capsule, le remplissage et l'icône barrée au muet s'affichent bien ;
- le service reste actif après `adb install -r`, c'est-à-dire après une mise à jour.

## Le blocage

Sur un téléviseur, Android traite **Volume +** et **Volume −** avant les services d'accessibilité :
- `KeyboardInterceptor.getEventDelay()` appelle `PhoneWindowManager.interceptKeyBeforeDispatching()` ;
- sur un téléviseur (`mUseTvRouting`), cette méthode applique le volume (`dispatchDirectAudioEvent`) puis renvoie `-1`, ce qui jette la touche.

Le service ne voit donc jamais ces deux touches, et Android affiche son propre panneau de volume. Seule la touche **Muet** arrive jusqu'au service.

C'est vrai dans AOSP Android 11 (`android-11.0.0_r48`) comme dans Android 12 (`android-12.0.0_r34`). C'est aussi ce qu'on a observé sur l'émulateur.

La box utilise le panneau standard d'Android (`com.android.systemui.volume.VolumeUI`, d'après `dumpsys`, en lecture seule).

## Ce qui reste possible sans root

| Piste | Résultat |
|---|---|
| Écouter `android.media.VOLUME_CHANGED_ACTION` et afficher notre barre | Possible, mais **en plus** du panneau d'Android : deux barres à l'écran. |
| Masquer le panneau d'Android | Impossible sans modifier SystemUI, donc sans root. |
| Une session média qui capte le volume | Elle prendrait les touches média des autres applis : refusé. |
