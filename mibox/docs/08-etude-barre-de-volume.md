# Étude : comment mettre la barre de volume LaunchOS à la place des barres actuelles

**Statut :** étude, plus un prototype testé sur l'émulateur Android TV du Mac, sur la branche
`volume-bar-proto`. Rien n'est publié, et rien n'est modifié sur la box.

Les relevés sur la box ont été faits le 2026-11-06, en lecture seule : `settings get`, `getprop`, `cmd -l`, et
quelques lignes filtrées de `dumpsys audio` et `dumpsys hdmi_control`. Le code cité est celui d'AOSP Android 11
(`android-11.0.0_r48`), la version de la box.

Cette étude fait suite au premier essai décrit dans `mibox/docs/06-barre-de-volume.md`, sur la branche
`volume-hud`. Cet essai interceptait les touches de volume, et Android TV ne le permet pas.

## En bref

Il y a deux barres différentes selon d'où sort le son.

| Le son sort par… | La barre qu'on voit aujourd'hui | Peut-on mettre celle de LaunchOS ? |
|---|---|---|
| **La JBL (Bluetooth)** | Celle d'Android, une capsule à droite | **Oui.** Android ne permet pas de désactiver la sienne sans root, mais LaunchOS peut la **recouvrir** entièrement. Le prototype marche. |
| **Les haut-parleurs de la Samsung** | Celle de **Samsung** (Android masque la sienne) | **Non, sauf à couper HDMI-CEC** entier, ce qui est sur ta liste rouge. Le détail est plus bas. |

## Pourquoi deux cas

| Relevé sur la box | Valeur |
|---|---|
| `ro.hdmi.device_type` | 4 (la box est un « lecteur » HDMI-CEC) |
| `hdmi_control_enabled` | 1 (HDMI-CEC actif) |
| `mHdmiCecSink` | true (la Samsung répond en CEC) |
| `mHdmiCecVolumeControlEnabled` | true |
| `mAvrcpAbsVolSupported` | false (avec la JBL, c'est la box qui atténue le son) |
| `ro.config.media_vol_steps` | 15 |

**Haut-parleurs de la télé.** Quand la télé répond en CEC, Android 11 passe la sortie HDMI en « volume plein »
(`updateHdmiCecSinkLocked`).
- Volume + et − ne changent plus le son de la box. Ils sont **envoyés à la Samsung** en touches CEC
  (`sendVolumeKeyEvent`), et la Samsung affiche sa barre.
- Android retire lui-même l'affichage de son panneau pour cette sortie (`sendVolumeUpdate` enlève `FLAG_SHOW_UI`).
- La box ne connaît pas le niveau de la Samsung, et aucun signal de volume n'arrive aux applis. LaunchOS ne pourrait
  même pas afficher une barre en plus.

**JBL.** Le son sort en Bluetooth, qui n'est pas en « volume plein ». La box baisse elle-même le son, sur 15 crans,
et Android affiche son panneau. Les applis reçoivent le changement (`VOLUME_CHANGED_ACTION`) et peuvent lire le
niveau.

## « Quitte à désactiver la barre actuelle » : ce qui est possible

### La barre d'Android (cas JBL)

| Moyen | Résultat |
|---|---|
| Désactiver SystemUI (`pm disable-user com.android.systemui`) | **Exclu.** Plus de fenêtres système, risque de box inutilisable. C'est un paquet cœur, refusé par les outils. |
| Désactiver seulement le composant « volume » | **Impossible.** `VolumeUI` n'est pas un composant Android déclaré, mais une classe interne de SystemUI : `pm disable` ne peut pas la viser. |
| Un overlay (RRO) qui change SystemUI | **Impossible sans root** : il doit être signé par Xiaomi ou installé dans la partition système. |
| Devenir le « contrôleur de volume » d'Android (`setVolumeController`) | **Impossible** : permission `STATUS_BAR_SERVICE`, réservée au système. |
| Un réglage caché, ou une commande `cmd audio` | Rien. Sur la box, `cmd audio` n'existe pas (« No shell command implementation »). |
| **La recouvrir avec la barre LaunchOS** | **Possible, et le prototype marche.** Voir plus bas. |

Conclusion : on ne peut pas désactiver la barre d'Android, mais on peut la rendre invisible en posant la nôtre
par-dessus. Le résultat à l'écran revient au même.

### La barre de Samsung (cas haut-parleurs de la télé)

| Moyen | Résultat |
|---|---|
| Couper seulement le contrôle du volume par CEC (`hdmi_control_volume_control_enabled`) | **À ne pas faire.** La sortie HDMI resterait en « volume plein », et rien ne serait envoyé à la télé : Volume + et − de la télécommande Xiaomi **ne feraient plus rien**. Dans la première version de cette étude, j'avais présenté cette piste à tort comme une solution. |
| Forcer la sortie HDMI en volume réglable (`setDeviceVolumeBehavior`) | **Impossible sans root** : permission `MODIFY_AUDIO_ROUTING`, réservée au système, et pas de commande shell sur Android 11 (`cmd audio` et `cmd hdmi_control` ne répondent pas). |
| Masquer la barre dans les réglages de la Samsung | À ma connaissance, Samsung n'a pas d'option pour ça. À vérifier dans le menu de ta télé si tu veux, mais je ne compterais pas dessus. |
| **Couper HDMI-CEC entièrement** | **Ça marcherait**, mais au prix de HDMI-CEC. La box réglerait elle-même le son HDMI, Android afficherait sa barre, et LaunchOS la recouvrirait. On perd l'allumage et l'extinction de la télé avec la box, le passage automatique sur la bonne entrée HDMI, et le volume de la Samsung avec la télécommande Xiaomi. Il faudrait aussi régler une fois la Samsung à un niveau fixe. **HDMI-CEC est sur ta liste rouge**, donc je ne le ferai que si tu changes cette règle. |
| Root ou firmware modifié | Exclu par les règles du projet. |

## Le prototype (branche `volume-bar-proto`)

Ce qu'il fait :
- **`VolumeBar`** : une capsule LaunchOS verticale, à l'endroit exact du panneau d'Android (bord droit, centrée),
  un peu plus grande et opaque. Elle montre :
  - le niveau en chiffres ;
  - une barre épaisse qui se remplit en dégradé bleu, violet et rose, avec un ressort ;
  - un haut-parleur dont les ondes suivent le niveau, barré au muet, ou une enceinte quand la JBL est connectée ;
  - un effet élastique quand on force en haut ou en bas.
  Elle reste 3,3 s, un peu plus que le panneau d'Android, puis s'efface.
- **La fenêtre** est un overlay d'accessibilité. Dans Android 11, cette couche (niveau 31) passe au-dessus du panneau
  de volume (niveau 22). La fenêtre ne prend ni le focus ni les touches : la télécommande marche comme avant.
- **Le déclencheur** est le message de changement de volume, qui arrive en général en moins de 100 ms. En secours, il
  y a l'ouverture de la fenêtre de volume d'Android, que le service « Boutons de la télécommande LaunchOS » détecte.
  Pour ça, il ne lit que le **nom de classe** de la fenêtre qui s'ouvre, jamais son contenu ni ce qui passe à l'écran.

Résultats sur l'émulateur (Android TV, 1920×1080, même densité que la box) :

| Test | Résultat |
|---|---|
| La barre LaunchOS recouvre entièrement celle d'Android | Test réussi |
| Le niveau, l'icône et le remplissage suivent le volume | Test réussi |
| Les touches de la télécommande restent utilisables | Test réussi (la fenêtre ne prend pas le focus) |
| Aucune trace de la barre d'Android pendant que la nôtre est affichée | Test réussi (après passage en fond opaque) |
| Au premier appui, la barre d'Android ne se voit pas du tout | **Test partiel.** Elle peut se voir un instant avant la nôtre : de 0,05 à 0,5 s selon les mesures sur l'émulateur, qui est lent quand il enregistre l'écran. À mesurer sur la box. |
| Appui sur + au maximum (ou − à zéro) | **Test partiel.** Le volume ne change pas, donc aucun message n'arrive. La nôtre ne couvre celle d'Android qu'à l'arrivée du signal de fenêtre, environ 0,7 s plus tard. |
| Sur la box, avec la JBL | Non testé |

Limites connues :
- **Taille** : avec le prototype, l'APK fait 737 828 octets, soit 548 octets au-dessus de la limite de `release.yml`
  (737 280). Pour le publier, il faudra gagner de la place, par exemple en allégeant la police, ou relever la limite.
- Le service « Boutons de la télécommande LaunchOS » reçoit en plus le nom de classe des fenêtres qui s'ouvrent. Il
  ne lit toujours pas le contenu de l'écran : `canRetrieveWindowContent` reste à `false`.

## Ce qu'il me faut de toi

1. **Le test d'une minute**, sur un créneau libre :
   - JBL éteinte, appuie sur Volume + : la barre de Samsung apparaît-elle seule ?
   - JBL connectée, appuie sur Volume + : la barre d'Android apparaît-elle à droite ?
2. **Ta décision sur les haut-parleurs de la télé** : on garde HDMI-CEC et la barre de Samsung, ce que je recommande,
   ou tu acceptes de perdre HDMI-CEC pour avoir la barre LaunchOS partout ?
3. Si le test 1 confirme le cas JBL : j'intègre la barre dans une version, en option « Barre de volume LaunchOS »
   dans les réglages, après avoir gagné la place nécessaire. Puis on la teste sur la box avec la JBL.
