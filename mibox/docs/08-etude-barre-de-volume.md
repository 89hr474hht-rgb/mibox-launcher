# Étude : comment avoir une barre de volume personnalisée

**Statut : étude, rien n'est modifié sur la box.** Les relevés ci-dessous ont été faits le 2026-11-06 en ADB, en
lecture seule (`settings get`, `getprop`, et quelques lignes filtrées de `dumpsys audio` et `dumpsys hdmi_control`).

Cette étude fait suite au premier essai décrit dans `mibox/docs/06-barre-de-volume.md`, sur la branche `volume-hud`.
Cet essai interceptait les touches de volume, et Android TV ne le permet pas.

## En bref

- **Sur la JBL (Bluetooth)**, une barre LaunchOS est faisable sans root. C'est la box qui règle le volume, LaunchOS
  connaît le niveau, et sa barre peut se poser **par-dessus** celle d'Android et la cacher.
- **Sur les haut-parleurs de la Samsung**, ce n'est pas la box qui règle le volume. Elle transmet + et − à la télé
  par HDMI-CEC, et c'est la Samsung qui affiche **sa** barre. LaunchOS ne connaît pas le niveau et ne peut pas cacher
  la barre de Samsung. La seule façon d'y arriver est de faire régler le volume par la box au lieu de la télé (piste C).
  Cela change le fonctionnement du son et touche au réglage HDMI-CEC : c'est à toi de décider.
- **Avant d'écrire du code**, il faut un test d'une minute (voir « Ce qu'il faut vérifier avec toi ») : quelle barre
  apparaît aujourd'hui quand tu appuies sur Volume +, avec et sans la JBL ?

## Comment le volume marche sur ta box

| Relevé (lecture seule) | Valeur | Ce que ça veut dire |
|---|---|---|
| `ro.hdmi.device_type` | 4 | La box est un « lecteur » HDMI-CEC (playback). |
| `hdmi_control_enabled` | 1 | HDMI-CEC est actif. |
| `dumpsys audio` → `mHdmiCecSink` | true | La Samsung répond en CEC. |
| `dumpsys audio` → `mHdmiCecVolumeControlEnabled` | true | Le volume peut être envoyé à la télé par CEC. |
| `dumpsys audio` → `mUseFixedVolume` | false | Le volume de la box n'est pas bloqué en général. |
| `dumpsys audio` → `mIsSingleVolume` | true | Un seul volume pour tout (normal sur une box TV). |
| `ro.config.media_vol_steps` | 15 | 15 crans de volume côté box. |
| `dumpsys audio` → `mAvrcpAbsVolSupported` | false | Avec la JBL, c'est la box qui atténue le son (pas la JBL). |
| `dumpsys audio` → `mVolumeController` | présent | La barre d'Android est celle de SystemUI. |

Dans le code d'Android 11 (`AudioService`, `android-11.0.0_r48`), quand la télé répond en CEC (`mHdmiCecSink`), la
sortie HDMI passe en « volume plein ». Chaque Volume + ou − est alors **envoyé à la télé** sous forme de touche CEC
(`HdmiPlaybackClient.sendVolumeKeyEvent`), au lieu de baisser le son de la box. C'est pour ça que la Samsung affiche
sa propre barre.

Avec la JBL, le son sort en Bluetooth (`bt_a2dp`), qui n'est pas en volume plein : c'est la box qui baisse le son, et
Android affiche son panneau.

Par ailleurs, la télécommande Xiaomi a aussi de l'infrarouge. Si Google TV l'a configurée pour régler le volume de la
télé en infrarouge, les touches de volume ne passent même pas par la box. C'est l'une des choses à vérifier.

## Ce qui est impossible sans root (déjà prouvé)

- **Recevoir les touches Volume + et −** : sur Android TV, elles sont traitées avant les services d'accessibilité
  (`PhoneWindowManager`, voir le document 06). Seul Muet arrive.
- **Supprimer le panneau de volume d'Android** : il appartient à SystemUI. Le modifier demande une appli système ou
  un overlay signé par le fabricant. Désactiver SystemUI est exclu (liste rouge, la box ne marcherait plus).
- **Devenir le « contrôleur de volume » d'Android** (`AudioManager.setVolumeController`) : la permission
  `STATUS_BAR_SERVICE` est réservée au système.
- **Changer la barre de volume de la Samsung** : c'est le logiciel de la télé.

## Les pistes possibles

### A. Une barre LaunchOS qui recouvre celle d'Android (recommandée, sans risque)

Le principe :
- LaunchOS a déjà un service d'accessibilité, « Boutons de la télécommande LaunchOS ». Il peut aussi afficher une
  fenêtre par-dessus tout, de type `TYPE_ACCESSIBILITY_OVERLAY`.
- Dans Android 11 (`WindowManagerPolicy`), cette couche est au niveau 31, au-dessus du panneau de volume
  (`TYPE_VOLUME_OVERLAY`, niveau 22). Notre barre peut donc **recouvrir exactement** le panneau d'Android, qui reste
  là mais caché dessous.
- Le service sait quand le panneau d'Android apparaît et disparaît, et où il est, grâce aux changements de fenêtres
  (`TYPE_WINDOWS_CHANGED`, `getWindows()`). Il sait aussi quand le volume change (`VOLUME_CHANGED_ACTION`,
  `STREAM_MUTE_CHANGED_ACTION`) et lit le niveau (`AudioManager.getStreamVolume`). Pas besoin de capture d'écran ni de
  lire le contenu des applis.
- La barre ne prend ni le focus ni les touches (`FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCHABLE`), donc la télécommande
  continue de marcher normalement.
- Le dessin existe déjà sur la branche `volume-hud` : une capsule façon Dynamic Island, à reprendre avec la police
  Inter. Elle peut montrer la sortie (télé ou JBL), le niveau en 15 crans et le muet.

Ce que ça donne :
- **JBL** : une vraie barre LaunchOS, avec le bon niveau, à la place de celle d'Android.
- **Haut-parleurs de la télé** : rien de mieux. La Samsung affiche toujours sa barre, et la box ne connaît pas le
  niveau.

Risques :
- Le panneau d'Android peut se voir une fraction de seconde avant d'être recouvert. C'est à mesurer sur la box.
- Si Android place son panneau ailleurs que prévu, le recouvrement est partiel. Le service lit sa position à chaque
  fois, ce qui limite ce risque.
- Rien n'est désactivé sur la box. Pour revenir en arrière, il suffit de couper l'option dans les réglages de
  LaunchOS.

### B. Une petite barre « en plus », sans recouvrir

C'est la même chose que A, mais la barre de LaunchOS s'affiche à côté de celle d'Android. C'est plus simple, mais il y
a deux barres à l'écran, ce que tu ne voulais pas. Je ne le recommande pas.

### C. Faire régler le volume par la box au lieu de la Samsung (à décider)

Il faut couper « contrôle du volume par HDMI-CEC » (`mHdmiCecVolumeControlEnabled`) **sans couper HDMI-CEC
lui-même**. Le réglage est dans les paramètres d'Android TV, ou se fait en ADB avec ton accord.

Après ça, la box atténue elle-même le son envoyé en HDMI. La Samsung ne reçoit plus de touches de volume, donc
n'affiche plus sa barre. La piste A marche alors aussi pour les haut-parleurs de la télé.

Contreparties :
- **Deux volumes** : il faut régler une fois la Samsung à un niveau confortable (avec sa propre télécommande). Ensuite
  on n'utilise plus que celui de la box.
- **15 crans** côté box, au lieu des 100 de la Samsung : les pas sont plus gros.
- **Son « bitstream »** (Dolby Atmos envoyé tel quel à la télé) : la box ne peut pas l'atténuer. Aujourd'hui
  `sys.audio.passthrough` vaut `false`, donc a priori non concerné, mais c'est à vérifier avec Netflix.
- Ça touche au réglage HDMI-CEC, qui est sur la liste rouge. Ce n'est pas une désactivation de HDMI-CEC (allumage,
  changement de source et télécommande restent en CEC), mais c'est une **décision à prendre par toi**, après un test
  sur un créneau libre, avec retour arrière immédiat.

### D. Avec root ou un firmware modifié

Un overlay de SystemUI ou une SystemUI modifiée, c'est la solution « propre ». Mais c'est exclu par les règles du
projet : pas de root, rien de flashé.

## Ce qu'il faut vérifier avec toi (1 minute, sur un créneau libre)

| N° | Geste | Ce que tu me dis |
|---|---|---|
| V1 | Son sur la télé (JBL éteinte) : appuie sur Volume + de la télécommande Xiaomi | Quelle barre apparaît : celle de Samsung, celle d'Android (barre fine à droite), ou les deux ? |
| V2 | JBL allumée et connectée : Volume + | Même question. |
| V3 | Paramètres → Télécommandes et accessoires : y a-t-il un réglage « boutons de la télécommande » ou « contrôle du volume » en infrarouge ? | Ce qui est choisi (ne rien changer). |

Pendant V1 et V2, je peux lire en ADB, en lecture seule, le niveau de volume de la box, pour voir s'il bouge. C'est
seulement si tu es d'accord, et sans regarder ce qui passe à l'écran.

## Le plan proposé

1. **Prototype sur l'émulateur** du Mac : la barre LaunchOS recouvre le panneau d'Android
   (`cmd media_session volume --show`). On vérifie le recouvrement, le temps d'apparition et que les touches marchent.
2. **Les tests V1 à V3** avec toi.
3. **Si V2 montre la barre d'Android** : la barre LaunchOS pour la JBL, en option « Barre de volume LaunchOS » dans
   les réglages, désactivée par défaut. Puis une version à installer et des tests sur la box.
4. **Seulement si tu le décides** : la piste C pour les haut-parleurs de la télé, testée sur un créneau libre, puis
   annulée ou gardée.

Taille : la capsule de `volume-hud` pèse quelques Ko. L'APK est à 735 Ko pour une limite de 737 Ko, donc il faudra
gagner de la place ailleurs (par exemple réduire la police) ou relever la limite de `release.yml`. C'est une
décision à prendre au moment de l'intégration.
