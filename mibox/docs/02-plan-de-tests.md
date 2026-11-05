# Plan de tests de compatibilité — avant / après

Appareil : Xiaomi Mi Box S 2e gén. (`MiTV-AFKR0`, build `RTT0.211222.001.773`), branchée sur une Samsung OLED 2023, avec une enceinte JBL en Bluetooth.

## Règles

- Chaque test est joué **avant toute modification** (état de référence), puis **après chaque étape sensible**. Il n'y a jamais deux modifications sensibles entre deux campagnes de tests.
- Une application qui **s'ouvre** n'est pas une application **compatible**. Le statut « Test réussi » exige que **tous** les critères de la ligne soient vérifiés.
- Colonne **Méthode** : `ADB` = je peux le vérifier moi-même par une commande en lecture seule. `MANUEL` = vous seul pouvez le constater (son entendu, image, télécommande physique). `MIXTE` = vous agissez, je confirme par ADB.
- Je ne marquerai jamais « Test réussi » un test `MANUEL` sans votre retour explicite.

**Statuts :** ✅ Test réussi · 🟡 Test partiel · ❌ Échec · ⬜ Non testé · 👁 Validation manuelle nécessaire

Étapes de modification prévues, chacune suivie d'une campagne de tests :

| Étape | Modification |
|---|---|
| E0 | Référence : aucune modification |
| E1 | Installation du launcher (sans le rendre HOME) |
| E2 | Launcher défini comme HOME |
| E3 | Désactivation de `launcherx` (Google TV) |
| E4 | Désactivation de `setupwraith` |
| E5+ | Désactivations optionnelles, **une catégorie à la fois** |

---

## A. Bluetooth et audio (prioritaire, à votre demande)

| ID | Test | Procédure | Critère de réussite | Méthode | E0 | E1 | E2 | E3 | E4 |
|---|---|---|---|---|---|---|---|---|---|
| BT-01 | Pile Bluetooth active | `dumpsys bluetooth_manager` | état `ON`, adaptateur présent | ADB | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-02 | Télécommande Xiaomi appairée et réactive | Naviguer avec la télécommande Xiaomi | navigation, OK, Retour, Home et volume répondent | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-03 | JBL toujours appairée | Liste des appareils associés | la JBL figure dans les appareils « bonded » | ADB | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-04 | Lecture audio sur la JBL | Lancer une vidéo YouTube avec la JBL connectée | son sur la JBL, **pas** sur la TV, sans coupure pendant 5 min | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-05 | Reconnexion JBL après veille | JBL allumée, Mi Box en veille 10 min, puis réveil | la JBL se reconnecte seule en moins d'une minute, le son revient | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-06 | Reconnexion JBL après redémarrage | JBL allumée, redémarrage complet de la Mi Box | reconnexion automatique, son sur la JBL | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-07 | Reconnexion JBL après coupure secteur | Débrancher la Mi Box 30 s, rebrancher | idem BT-06 | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| BT-08 | Retour du son sur la TV | Éteindre la JBL pendant une lecture | le son repasse sur les haut-parleurs de la Samsung | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| VOL-01 | Volume en mode JBL | Volume + et − sur la télécommande Xiaomi, JBL connectée | le volume de la JBL change, la TV n'affiche pas son propre volume | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| VOL-02 | Volume en mode TV (Xiaomi) | Volume + et − sur la télécommande Xiaomi, sans JBL | le volume de la Samsung change (barre de volume Samsung) | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| VOL-03 | Volume en mode TV (Samsung) | Volume + et − sur la télécommande Samsung | le volume de la Samsung change | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| VOL-04 | Muet | Touche muet de chaque télécommande, dans les deux modes | coupe le son et le rétablit | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

## B. HDMI, CEC et télécommandes

| ID | Test | Procédure | Critère de réussite | Méthode | E0 | E1 | E2 | E3 | E4 |
|---|---|---|---|---|---|---|---|---|---|
| CEC-01 | Service CEC actif | `dumpsys hdmi_control` | CEC activé, la TV (adresse logique 0) est détectée | ADB | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CEC-02 | Télécommande Samsung → Mi Box | Naviguer dans le launcher avec les flèches, OK et Retour Samsung | toutes ces touches fonctionnent | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CEC-03 | Allumage groupé | Réveiller la Mi Box avec sa télécommande | la Samsung s'allume et bascule sur la bonne entrée HDMI | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CEC-04 | Extinction groupée | Éteindre la Samsung | la Mi Box passe en veille (selon le réglage CEC) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CEC-05 | Lecture/pause Samsung | Touche lecture/pause Samsung pendant une vidéo | pause, puis reprise | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| RC-01 | Bouton Home (Xiaomi) | Appui depuis une app | retour **au launcher en vigueur à cette étape** | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| RC-02 | Retour répété | 10 appuis sur Retour depuis l'accueil | reste sur l'accueil, **jamais** d'écran Google TV (à partir de E3) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| RC-03 | Boutons raccourcis | Netflix, YouTube, Prime, Apps | ouvrent l'application prévue (cassés à partir de E3 tant que le launcher ne les gère pas) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| RC-04 | Bouton micro / Assistant | Appui long micro, « quelle heure est-il » | l'Assistant répond (si vous l'utilisez) | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| HD-01 | Résolution et HDR | Paramètres affichage, puis une vidéo HDR | sortie 4K, HDR10/HDR10+ reconnu par la Samsung (bandeau d'info Samsung) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| HD-02 | Format audio HDMI | Réglage sortie audio, puis vidéo Dolby Digital | son correct, pas de silence ni de grésillement | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

## C. Réseau, Cast et système

| ID | Test | Procédure | Critère de réussite | Méthode | E0 | E1 | E2 | E3 | E4 |
|---|---|---|---|---|---|---|---|---|---|
| NET-01 | Wi-Fi connecté | `dumpsys wifi`, `ping` | connecté, Internet validé | ADB | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| NET-02 | Propriétaire du réseau Wi-Fi | `dumpsys wifi`, réseaux enregistrés | savoir si `setupwraith` « possède » un réseau (bloquant pour E4) | ADB | ⬜ | — | — | — | — |
| NET-03 | Wi-Fi après redémarrage | Redémarrage | reconnexion automatique, sans redemander le mot de passe | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| NET-04 | Réseau local | Plex/VLC vers un serveur du réseau (si vous en avez un) | le contenu est listé et se lit | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CAST-01 | Chromecast | Caster une vidéo YouTube depuis le téléphone | lecture sur la TV, contrôle depuis le téléphone | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| CAST-02 | Chromecast après redémarrage | Redémarrer, puis CAST-01 | idem | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-01 | Launcher HOME au démarrage | Démarrage à froid | arrive directement sur le bon launcher | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-02 | Veille et réveil | Veille 10 min, puis réveil | retour sur l'accueil ou l'app en cours, réseau OK | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-03 | Coupure secteur | Débrancher 30 s, rebrancher | démarrage, bonne heure, « Internet » OK (risque R7) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-04 | USB | Clé USB avec vidéo et musique | détectée, lisible | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-05 | Paramètres accessibles | Ouvrir Paramètres, Réseau, Bluetooth, Applis | tout s'ouvre, rien ne plante | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-06 | Play Store et mises à jour d'apps | Ouvrir le Play Store, mettre à jour une app | OK | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SYS-07 | Trois redémarrages consécutifs | Trois cycles complets | SYS-01, NET-03, BT-06 et CEC-03 OK à chaque cycle | MIXTE | ⬜ | — | ⬜ | ⬜ | ⬜ |

## C bis. SFR TV (conservation impérative)

| ID | Test | Procédure | Critère de réussite | Méthode | E0 | E1 | E2 | E3 | E4 |
|---|---|---|---|---|---|---|---|---|---|
| SFR-01 | Toujours installé et activé | `pm list packages -e` | `com.sfr.androidtv.launcher` et `.selfcare` présents et actifs | ADB | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-02 | Lancement depuis le launcher en vigueur | Ouvrir la tuile SFR TV | l'application s'ouvre sur son écran normal | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-03 | TV en direct | Regarder une chaîne 5 min | image, son, zapping chaîne +/− | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-04 | Replay / guide des programmes | Ouvrir le guide, lancer un replay | lecture OK | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-05 | Home depuis SFR TV | Bouton Home pendant une chaîne | retour sur **votre** launcher (à partir de E2/E4), pas sur l'accueil SFR | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-06 | Sortie de veille | Veille 10 min depuis votre launcher, puis réveil | **votre** launcher reste au premier plan (SFR ne prend pas la main) | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-07 | Rôle HOME après mise à jour ou redémarrage de SFR | Redémarrage, ou réinstallation de la même version de SFR TV si nécessaire | votre launcher reste l'écran d'accueil (`dumpsys role`), pas de sélecteur | ADB | — | — | ⬜ | ⬜ | ⬜ |
| SFR-08 | Son sur la JBL | Chaîne en direct avec la JBL connectée | son sur la JBL | MANUEL | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| SFR-09 | Retour depuis SFR TV | Retour répété dans SFR TV | sortie vers votre launcher, jamais vers Google TV | MIXTE | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

## C ter. Désinstallation de Razorbill (étape R, avant E1)

| ID | Test | Critère | Méthode | Après R |
|---|---|---|---|---|
| RZ-01 | Package absent | `pm list packages` ne le liste plus | ADB | ✅ Absent du profil 0. Absent aussi du profil 10, où il n'avait jamais été installé. Inconnu de `dumpsys package` |
| RZ-02 | Accueil inchangé | le bouton Home affiche Google TV | MIXTE | 🟡 Côté ADB : HOME = launcherx, rôle HOME = launcherx. 👁 Appui sur Home à confirmer par vous |
| RZ-03 | SFR TV, Netflix, YouTube | se lancent normalement | MANUEL | 🟡 SFR TV : installé, activé, même version (7.1.0), même processus qu'avant, services actifs, toujours lançable. 👁 Lecture d'une chaîne à confirmer. Netflix et YouTube : packages inchangés, 👁 lancement à confirmer |
| RZ-04 | Redémarrage | Google TV au démarrage, Wi-Fi, télécommande et Bluetooth OK | MIXTE | ✅ Côté ADB (redémarrage le 5/11 à 21 h 20, démarrage terminé en moins d'une minute) : Google TV au premier plan, rôle HOME = launcherx, 118 packages et 0 désactivé, Wi-Fi + Internet validé, heure correcte, Bluetooth `ON`, télécommande reconnectée, CEC actif, SFR TV relancé tout seul **sans prendre le premier plan**, environ 876 Mo de RAM libre. ADB réseau revient seul après redémarrage. 👁 Image et télécommande à confirmer par vous |

Observation : l'adresse physique CEC reste invalide après le redémarrage (R17).

Exécution : 5 novembre 2026, 21 h 16. Une seule commande de modification envoyée (`pm uninstall com.razorbill.launcher` → `Success`). Journal : `audits/20261105-211617-razorbill/log.txt`.

## D. Applications

La liste est à ajuster après l'audit, selon les applications réellement installées et celles que vous utilisez.

Critères, à vérifier dans l'ordre : ① installée · ② se lance · ③ navigable à la télécommande · ④ connectée au compte · ⑤ lecture vidéo · ⑥ son (TV **et** JBL) · ⑦ qualité attendue (HD/4K/HDR) · ⑧ OK après redémarrage.

| App | Package | ① | ② | ③ | ④ | ⑤ | ⑥ | ⑦ | ⑧ | Statut E0 |
|---|---|---|---|---|---|---|---|---|---|---|
| Netflix | `com.netflix.ninja` | | | | | | | | | ⬜ |
| YouTube | `com.google.android.youtube.tv` | | | | | | | | | ⬜ |
| YouTube Music | `com.google.android.youtube.tvmusic` | | | | | | | | | ⬜ |
| Prime Video | `com.amazon.amazonvideo.livingroom` | | | | | | | | | ⬜ |
| Disney+ | à confirmer | | | | | | | | | ⬜ |
| Max | `com.wbd.stream` | | | | | | | | | ⬜ |
| MyCANAL | à confirmer | | | | | | | | | ⬜ |
| Molotov | à confirmer | | | | | | | | | ⬜ |
| Plex | à confirmer | | | | | | | | | ⬜ |
| VLC | `org.videolan.vlc` | | | | | | | | | ⬜ |
| Spotify | `com.spotify.tv.android` | | | | | | | | | ⬜ |
| Kodi | `org.xbmc.kodi` | | | | | | | | | ⬜ |
| IPTV Smarters | `com.nst.iptvsmarterstvbox` | | | | | | | | | ⬜ |
| IronTV Max | `com.mbm_soft.irontvmax` | | | | | | | | | ⬜ |
| **SFR TV** | `com.sfr.androidtv.launcher` | | | | | | | | | ⬜ (voir C bis) |

**DRM :** le niveau Widevine (L1 attendu) sera vérifié au moyen des informations de lecture des applications (par exemple la résolution affichée par Netflix). Aucune vérification ne nécessite de modifier l'appareil.
