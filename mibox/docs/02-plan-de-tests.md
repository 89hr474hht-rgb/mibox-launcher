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

## C quater. Mises à jour du launcher (F2 + F3)

Statuts possibles : Test réussi / Test partiel / Échec / Non testé / Validation manuelle nécessaire. Côté Mac, les vérifications automatiques ont déjà tourné (tests unitaires, lint, contrôle de l'APK). Elles ne remplacent aucun test ci-dessous.

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| UPD-01 | Version installée | Réglages du launcher → « Mises à jour du launcher » | La page affiche `1.0.0` | MANUEL | Non testé |
| UPD-02 | Autorisation d'installer | `appops get com.gathin.tvhome REQUEST_INSTALL_PACKAGES` | `allow` ; la page n'affiche pas l'avertissement orange | ADB + MANUEL | Non testé |
| UPD-03 | Vérification manuelle | « Vérifier maintenant », sans release plus récente | « Le launcher est à jour », sans erreur | MANUEL | Non testé |
| UPD-04 | Pastille | Publier `1.0.1`, puis revenir à l'accueil | Pastille en haut à droite, version et résumé ; la télécommande navigue entre les deux boutons | MANUEL | Non testé |
| UPD-05 | Plus tard | « Plus tard » ou Retour | La pastille disparaît et ne revient pas avant 24 h ; l'accueil reste utilisable | MANUEL | Non testé |
| UPD-06 | Installation | « Mettre à jour » | Progression, puis vérification, puis confirmation Android ; après « Installer » : retour à l'accueil, message « Launcher mis à jour : version 1.0.1 », historique « installée » | MANUEL | Non testé |
| UPD-07 | Annulation | Refuser la confirmation Android | Historique « annulée », la version 1.0.0 reste installée et fonctionne | MANUEL | Non testé |
| UPD-08 | Ignorer | « Ignorer cette version » | Plus de pastille pour cette version ; « La proposer à nouveau » la fait revenir | MANUEL | Non testé |
| UPD-09 | Refus d'un APK modifié | Release de test avec un fichier `.sha256` faux | « Empreinte SHA-256 différente : fichier refusé », rien n'est installé | MANUEL | Non testé |
| UPD-10 | Pas de réseau | Wi-Fi coupé, « Vérifier maintenant » | Message « Impossible de joindre GitHub », aucun plantage | MANUEL | Non testé |
| UPD-11 | Vérification quotidienne | `dumpsys jobscheduler \| grep -A3 com.gathin.tvhome` | Une tâche périodique de 24 h, contrainte réseau, persistante | ADB | Non testé |
| UPD-12 | Rien d'autre ne change | `pm list packages -d` avant et après une mise à jour | Liste identique : une mise à jour ne désactive rien | ADB | Non testé |

## C quinquies. Réglages et Bluetooth du launcher (1.1.0, 1.1.1)

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| LST-01 | Bouton Paramètres | Accueil → Paramètres | Les réglages du launcher s'ouvrent : « Mises à jour du launcher » en tête, « Paramètres de l'appareil » en dernier | MANUEL | Non testé |
| LST-02 | Paramètres de l'appareil | Dernière ligne | Les paramètres d'Android TV s'ouvrent | MANUEL | Non testé |
| BTP-01 | Pastille sans audio | Aucune enceinte connectée | La pastille affiche « Bluetooth » | MANUEL | Non testé |
| BTP-02 | Pastille avec la JBL | Allumer la JBL Go 3 | Après quelques secondes : « JBL Go 3 » | MANUEL | Non testé |
| BTP-03 | Nom long | Connecter un appareil au nom long (AirPods) | Le nom défile, puis s'arrête ; il redéfile quand la pastille est sélectionnée | MANUEL | Non testé |
| BTP-04 | Navigation | Haut, Bas, Gauche et Droite depuis la pastille | Bas revient sur la tuile d'origine, Haut reste dans la barre | MANUEL | Non testé |
| BTP-05 | Menu Bluetooth | OK sur la pastille | Un menu s'ouvre sous la pastille : appareils connectés en tête (disque bleu, point vert), puis les autres ; « Associer un appareil… » ouvre l'appairage de Google TV ; Retour ferme le menu | MANUEL | Non testé |
| BTP-06 | Rien ne change | `pm list packages -d` et `dumpsys bluetooth_manager` avant et après | La page ne fait que lire : aucun changement | ADB | Non testé |

## C sexies. Nouveautés 1.2.0

Vérifié sur l'émulateur Android TV 12 (1920 × 1080, même densité que la box) avec l'APK de release : réglages, menu réseau, infos sur l'app, fond animé et horloge s'affichent et réagissent comme décrit ci-dessous. **Rien n'est encore testé sur la box.**

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| SET-01 | Nouvelle page de réglages | Accueil → Réglages du launcher | Titre, « Édition Mi Box · version 1.2.0 », sections avec icônes de couleur ; la sélection (fond blanc) suit la télécommande du haut en bas, sans ligne inaccessible | MANUEL | Non testé |
| SET-02 | Réglages toujours actifs | Changer « Applis par ligne », puis revenir à l'accueil ; remettre la valeur d'origine | L'accueil suit le réglage ; le choix actif est encadré | MANUEL | Non testé |
| NETM-01 | Menu réseau | OK sur la pastille réseau | Un menu s'ouvre sous la pastille : Ethernet, Wi-Fi, Internet, adresse IP, DNS privé, avec un point vert pour ce qui fonctionne | MANUEL | Non testé |
| NETM-02 | Passage en Ethernet | Brancher le câble Ethernet, menu ouvert | La ligne Ethernet passe à « Connecté » et l'état en haut à « Ethernet » | MANUEL | Non testé |
| NETM-03 | Réglages réseau | « Paramètres réseau… » ; appui long sur la pastille | Le premier ouvre « Réseau et Internet » d'Android ; l'appui long ouvre les réglages Wi-Fi comme avant | MANUEL | Non testé |
| APPI-01 | Infos sur l'app | Appui long sur une app → « Infos sur l'application » | Fenêtre avec icône, nom, version, dates, provenance, Android visé, taille | MANUEL | Non testé |
| APPI-02 | Boutons | « Ouvrir », puis « Plus d'infos… », puis Retour | L'app se lance ; la page d'Android s'ouvre ; Retour ferme la fenêtre et revient à l'accueil | MANUEL | Non testé |
| GLOW-01 | Fond animé | Rester 30 s sur l'accueil, puis changer d'app | Le fond bouge doucement aux couleurs de l'app, puis glisse vers celles de la suivante ; la navigation reste fluide | MANUEL | Non testé |
| GLOW-02 | Pas d'effet en arrière-plan | Lancer une vidéo, puis `dumpsys gfxinfo com.gathin.tvhome` | Aucune image dessinée par le launcher pendant la vidéo | ADB | Non testé |
| CLK-01 | Animation de l'heure | Regarder l'horloge au changement de minute | Les chiffres qui changent roulent vers le haut, les autres ne bougent pas, rien ne reste en double | MANUEL | Non testé |

## C septies. Accueil sans Google TV et sans commande vocale (E4, E3, E11)

Plan détaillé : [07-plan-accueil-et-voix.md](07-plan-accueil-et-voix.md). Les tests RC, SFR, BT, CEC, NET et
SYS des sections précédentes sont repassés après E3 (colonne E3).

Exécution : 6 novembre 2026, vers 11 h 55, box en Ethernet, launcher 1.2.0. Contrôle préalable
`precheck-accueil.sh` : 12 OK, 0 point à regarder. Sauvegarde : `audits/20261106-115424-avant-e4-e3/`.
E4 puis E3, chacun par `tools/disable.sh` (enregistrements de retour arrière dans `~/.cleanyourtv/`).
Côté ADB après E3 : seuls `setupwraith` et `launcherx` sont désactivés, liste rouge active, Internet par
Ethernet, CEC activé, Bluetooth `ON`. Pas de redémarrage pour l'instant. Aucune capture d'écran.

Stockage, le même jour, à votre demande : `pm trim-caches` (caches des applis seulement, recréés au besoin).
Espace libre sur `/data` : 877 Mo avant, 1,6 Go après. Aucun fichier ni aucune donnée d'appli supprimés.

Suite, le même jour vers 12 h 30, télé libre, à votre demande. Vous avez supprimé les 7 fichiers APK du
dossier Téléchargements (environ 525 Mo) ; caches vidés de nouveau. Espace libre : 2,1 Go. Sauvegarde :
`audits/20261106-122834-avant-e5-e11/`. Puis, chacune par `tools/disable.sh` avec le contrôle préalable entre
deux étapes (12 OK à chaque fois) : E11, E5, E6, E7, E8, et E10 après avoir choisi l'horloge du launcher comme
écran de veille (`screensaver_components` = `com.gathin.tvhome/.ClockDream`, ancienne valeur
`com.google.android.apps.tv.dreamx/.service.Backdrop`). Puis un redémarrage (HOME-03).

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| HOME-01 | Home après E3 | `cmd package resolve-activity` (HOME), puis bouton rond depuis une appli | `com.gathin.tvhome/.MainActivity` ; le bouton rond ouvre votre launcher | ADB + MANUEL | Test réussi : côté ADB, Home = `com.gathin.tvhome/.MainActivity`, rôle HOME = `com.gathin.tvhome` ; bouton rond confirmé par vous le 6/11 |
| HOME-02 | Boutons raccourcis | Netflix, YouTube, Prime, Apps | Ne font rien (attendu jusqu'à E9), rien ne plante | MANUEL | Non testé |
| HOME-03 | Après redémarrage | Redémarrage, puis `precheck-accueil.sh` | Home = votre launcher, mêmes paquets désactivés, Ethernet, télécommande et JBL revenus | MIXTE | Test partiel : redémarrage du 6/11 à 12 h 30, côté ADB Home = votre launcher, les 17 paquets désactivés le restent, Ethernet, Bluetooth `ON`, CEC activé. Télécommande et JBL à confirmer par vous |
| VOIX-01 | Micro coupé | Après E11, appui sur le bouton micro | Rien ne se passe ; le reste de la télécommande répond | MANUEL | Non testé |
| VOIX-02 | Retour possible | `restore.sh mibox/lists/E11-assistant.txt` (seulement si vous voulez la voix) | Le micro relance l'Assistant | MIXTE | Non testé |

## C octies. Nettoyage invisible, écran de veille et bouton YouTube (E5 à E10)

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| NET-E5 | Après E5 à E8 | `precheck-accueil.sh` après chaque étape, puis après le redémarrage | 12 OK, Home = votre launcher, Ethernet, liste rouge active | ADB | Test réussi : 12 OK après chaque étape et après le redémarrage du 6/11 |
| VEILLE-01 | Écran de veille | Laisser la box sans toucher la télécommande jusqu'à l'écran de veille | L'horloge du launcher s'affiche ; une touche ramène là où vous étiez | MANUEL | Non testé |
| YT-01 | Bouton YouTube (E9, 1.3.0) | Après avoir activé « Boutons de la télécommande LaunchOS » (Accessibilité) : bouton YouTube depuis l'accueil, puis depuis une autre appli | YouTube s'ouvre une seule fois | MANUEL | Non testé |
| YT-02 | Netflix et Prime | Boutons Netflix et Prime Video | Ne font rien, comme vous l'avez choisi ; rien ne plante | MANUEL | Non testé |

## C nonies. LaunchOS 1.3.0

Vérifié sur l'émulateur Android TV (1920 × 1080) avec l'APK de release signé : écran de démarrage après un
redémarrage, page des mises à jour (recherche puis « à jour »), réglages renommés, page des boutons de la
télécommande et sa liste d'applis, service des boutons chargé par Android. Sur l'émulateur, Android ouvre YouTube
pour les trois touches même sans le service, et les touches simulées par ADB ne passent pas par lui : le remappage
ne peut être prouvé qu'avec la vraie télécommande. La carte « Mise à jour disponible » n'a pas pu être affichée
(aucune version plus récente publiée). **Rien n'est encore testé sur la box.**

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| LOS-01 | Nom et icône | Paramètres → Applications ; rangée d'applis d'Android TV | « LaunchOS », nouvelle icône et bannière | MANUEL | Non testé |
| BOOT-01 | Écran de démarrage | Redémarrer la box | Logo, nom, barre, au moins 4 s, puis l'accueil ; les touches ne font rien pendant ce temps | MANUEL | Test partiel : vu sur l'émulateur après un redémarrage, pas sur la box |
| BOOT-02 | Pas au retour à l'accueil | Ouvrir une appli, puis bouton rond | L'accueil revient directement, sans écran de démarrage | MANUEL | Non testé |
| FONT-01 | Police | Accueil, réglages, menus, mises à jour | Inter partout, rien de coupé ni de mal aligné | MANUEL | Test partiel : émulateur seulement |
| UPD-01 | Page des mises à jour | Réglages → Mises à jour de LaunchOS → Vérifier maintenant | Ondes autour du logo pendant au moins 1,6 s, puis coche verte « LaunchOS est à jour » ; la sélection reste sur le bouton | MANUEL | Test partiel : émulateur seulement |
| UPD-02 | Carte de mise à jour | À la prochaine version publiée | La carte glisse depuis la droite, le logo rebondit ; « Plus tard » ou Retour la fait repartir | MANUEL | Non testé |
| GLOW-03 | Fond plus visible | Rester sur l'accueil, changer d'app | Couleurs nettement visibles et en mouvement, navigation fluide | MANUEL | Non testé |
| NAME-01 | Nom en surbrillance | Se déplacer dans le dock, puis dans les rangées en dessous | Grand nom avec halo et reflet au-dessus du dock ; pastille blanche sous les tuiles des rangées | MANUEL | Non testé |
| RK-01 | Activer le service | Paramètres → Accessibilité → « Boutons de la télécommande LaunchOS » → Activer | L'avertissement orange disparaît de la page des boutons | MANUEL | Non testé |
| RK-02 | Remappage | Régler le bouton Netflix sur une autre appli, puis appuyer sur le vrai bouton | L'appli choisie s'ouvre une seule fois ; remettre « Aucune action » ensuite si vous le souhaitez | MANUEL | Validation manuelle nécessaire |
| RK-03 | Autres touches intactes | Volume, OK, Retour, Home, micro avec le service actif | Tout répond comme avant | MANUEL | Non testé |
| RK-04 | Dessin de la télécommande | Réglages de LaunchOS → Boutons de la télécommande, passer d'une ligne à l'autre, choisir une appli | La télécommande ressemble à la vraie ; le bouton de la ligne sélectionnée se soulève et s'éclaire, le projecteur le suit ; le choix d'une appli l'enfonce et fait sortir des ondes du haut. La télécommande zoome un peu sur le bouton sélectionné, plus pendant le choix, sans être coupée. Fluide sur la box | MANUEL | Test partiel (émulateur, 2026-11-06) |
| FR-01 | Tout en français | Ouvrir l'accueil, les réglages de LaunchOS, Mises à jour, Boutons de la télécommande | Aucun texte de LaunchOS en anglais (les noms d'applis et les écrans d'Android suivent la langue de la box) | MANUEL | Test partiel (émulateur réglé en anglais, 2026-11-06) |
| NAME-02 | Capsule du nom | Accueil, passer d'une appli à l'autre dans le dock | Le nom est dans une capsule de verre qui suit sa largeur et rebondit légèrement ; le texte reste aligné sur le dock | MANUEL | Test partiel (émulateur, 2026-11-06) |
| BOOT-03 | Démarrage dès la première image | Redémarrer la box | Aucun flash de l'accueil avant l'écran LaunchOS ; on voit tout : logo, nom, barre, puis l'accueil | MANUEL | Test partiel (émulateur, vidéo du démarrage, 2026-11-06) |

## C decies. LaunchOS TV 0.1.0 (appli séparée, compte Xtream)

Vérifié sur l'émulateur Android TV (1920 × 1080, même densité que la box) avec l'APK de release signé, contre le
faux serveur `mibox/tv/mock/xtream_mock.py` (compte demo / demo). **Jamais contre ton serveur, rien sur la box.**
Sur la box, les tests `MANUEL` ci-dessous sont à faire par toi, avec ton compte, quand personne ne regarde : un
abonnement limité à une connexion coupe l'autre lecture.

| ID | Test | Procédure | Critère de réussite | Méthode | Résultat |
|---|---|---|---|---|---|
| TV-01 | Saisie du compte | Compte → serveur, identifiant, mot de passe → Enregistrer et tester | « Connecté », date de fin d'abonnement, connexions, formats | MANUEL | Test partiel (émulateur, faux serveur) |
| TV-02 | Mauvais identifiants | Changer le mot de passe → Enregistrer et tester | Message rouge « refusé » ; l'ancien compte reste enregistré | MANUEL | Test partiel (émulateur, faux serveur) |
| TV-03 | Compte gardé | Mettre à jour ou relancer l'appli | Le compte est toujours là, sans le retaper | MANUEL | Test partiel (émulateur : gardé après réinstallation par-dessus) |
| TV-04 | Chaînes | Chaînes → catégories → logos | Catégories et chaînes de ton fournisseur, logos affichés ou initiales | MANUEL | Test partiel (émulateur, faux serveur) |
| TV-05 | Direct et zapping | OK sur une chaîne, puis CH+/CH− ou Haut/Bas | Image et son ; nom de la chaîne 4 s ; zapping dans la catégorie | MANUEL | Test partiel (émulateur, faux serveur ; son non vérifié) |
| TV-06 | Films | Films → catégorie → OK sur une affiche | Le film se lance ; OK affiche les commandes, Gauche/Droite recule de 10 s et avance de 30 s | MANUEL | Test partiel (émulateur, faux serveur) |
| TV-07 | Séries | Séries → OK sur une série → saison → épisode | Saisons, épisodes et résumés ; l'épisode se lance | MANUEL | Test partiel (émulateur, faux serveur, épisodes en liste et en tableau, avec redirections) |
| TV-08 | Serveur coupé | Lancer un film ou une chaîne sans réseau | Message « Lecture impossible » clair ; OK réessaie ; le direct réessaie seul deux fois puis en HLS | MANUEL | Test partiel (émulateur, faux serveur arrêté) |
| TV-09 | Son | Direct et film, sur la Samsung puis sur la JBL | Son correct, y compris Dolby (AC3/EAC3) si ton fournisseur en envoie | MANUEL | Non testé |
| TV-10 | Gros catalogue | Parcourir de grosses catégories de ton fournisseur | Listes fluides, pas de fermeture de l'appli | MANUEL | Non testé |
| TV-11 | Adresse introuvable | Serveur `serveur.invalid:8080` | Message « Adresse du serveur introuvable » | MANUEL | Non testé |
| TV-12 | Tuile sur l'accueil | Accueil LaunchOS, bibliothèque d'applis | « LaunchOS TV » apparaît avec sa bannière | MANUEL | Test partiel (émulateur) |
| TV-13 | Menu Actualiser | Actualiser → Tout actualiser, puis Retour | Chaque ligne passe à « À jour » ; de retour, la section se recharge | MANUEL | Test partiel (émulateur) |
| TV-14 | Guide sur les chaînes | Chaînes, une catégorie | Le programme en cours et une barre sous les chaînes qui ont un guide ; rien sous les autres | MANUEL | Test partiel (émulateur) |
| TV-15 | Guide en direct | Une chaîne, puis OK | Heures, titre en cours, barre d'avancement et « Ensuite » | MANUEL | Test partiel (émulateur) |
| TV-16 | Guide de ton fournisseur | Plusieurs chaînes françaises | Titres lisibles (accents compris) et heures justes | MANUEL | Non testé |
| TV-UPD-01 | Écran Mises à jour | Mises à jour (en bas à gauche) → Rechercher maintenant | « LaunchOS TV est à jour » et la date ; pas de cadre rouge sur la box (installation autorisée) | MANUEL | Test partiel (émulateur, vrai GitHub, aucune version TV publiée ; cadre rouge attendu sur l'émulateur) |
| TV-UPD-02 | Mise à jour proposée | 0.1.0 installée sur la box, 0.2.0 publiée | Bandeau en haut à droite et « Mise à jour ! » dans le menu ; Installer → téléchargement, vérification, confirmation d'Android ; le compte est gardé | MANUEL | Non testé |
| TV-UPD-03 | LaunchOS pas gêné | Réglages de LaunchOS → Mises à jour → Vérifier | LaunchOS ne propose jamais une version de LaunchOS TV | MANUEL | Non testé |

Retour du propriétaire le 6 novembre 2026 : « J'ai testé et ça fonctionne », sur la box avec son compte, sans
détail test par test. Les lignes ci-dessus gardent leur statut tant que chaque point n'est pas confirmé.

## C undecies. LaunchOS 1.4.0 : boutons ronds et minuteur de veille

Vérifié sur l'émulateur Android TV 12 (1920 × 1080) avec l'APK de release signé. Pour ne pas attendre 15 minutes,
une version de test locale (jamais publiée) proposait 2 minutes. Le service d'accessibilité de LaunchOS est actif
sur la box (lu par ADB), et HDMI-CEC aussi (`hdmi_control_enabled` et `hdmi_control_auto_device_off_enabled` à 1).

| ID | Test | Action | Attendu | Type | Résultat |
|---|---|---|---|---|---|
| L14-01 | Boutons ronds | Haut depuis le dock, puis Droite le long de la barre | Seul le bouton sélectionné s'allonge avec son nom ; le précédent se referme ; les ronds sont alignés ; la date s'affiche en entier | MANUEL | Test partiel (émulateur) |
| L14-02 | Icône Réglages | Barre du haut | Curseurs à la place de la maison | MANUEL | Test partiel (émulateur) |
| L14-03 | Menu Minuteur | OK sur Minuteur | 15 min à 2 h ; « Arrêter le minuteur » quand il tourne ; le bouton dit « Veille dans … » | MANUEL | Test partiel (émulateur) |
| L14-04 | Dernière minute | Minuteur lancé, regarder une appli | À 60 s, pastille en haut et écran « Ajouter 30 min » par-dessus l'appli | MANUEL | Test partiel (émulateur, par-dessus LaunchOS TV) |
| L14-05 | Ajouter 30 min | OK sur l'écran de la dernière minute | L'écran part, le bouton dit « Veille dans 31 min » | MANUEL | Test partiel (émulateur) |
| L14-06 | Retour | Retour sur l'écran de la dernière minute | Minuteur arrêté, la box ne se met pas en veille | MANUEL | Voir ci-dessous |
| L14-07 | Mise en veille | Laisser finir | La box passe en veille, la Samsung s'éteint (CEC), la JBL se coupe | MANUEL | Test partiel (émulateur : veille « Asleep ») ; télé et JBL non testées |
| L14-08 | Par-dessus Netflix / SFR TV | Minuteur pendant une vidéo | L'écran de la dernière minute s'affiche, la vidéo reprend après « Ajouter 30 min » | MANUEL | Non testé |
| L14-09 | Réveil | Réveiller la box après la veille | Accueil normal, minuteur arrêté, BT et CEC comme avant (BT-05, CEC-03) | MANUEL | Non testé |

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
