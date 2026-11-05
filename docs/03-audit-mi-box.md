# Audit de la Mi Box S 2e gén. — état de référence (E0)

Date : 5 novembre 2026, 21 h 06
Méthode : `scripts/audit/audit.sh`, 34 commandes **en lecture seule**, aucune refusée et aucune modification. Données brutes : `audits/20261105-210658/` (local, exclu de Git).
Ce document ne contient volontairement ni adresse IP, ni nom de réseau Wi-Fi, ni adresse MAC, ni numéro de série.

---

## 1. Identité et matériel (mesurés)

| Élément | Valeur mesurée | Conséquence pour le projet |
|---|---|---|
| Fabricant / modèle / appareil | Xiaomi / `MiTV-AFKR0` / `jaws` | Confirmé : Mi Box S 2e gén. |
| Android | 11 (SDK 30), build de production `user`, `release-keys` | `PackageInstaller` sans installation silencieuse (API < 31) |
| Build | `RTT0.211222.001.773`, compilé le 18 novembre 2024 | Non validé par CleanYourTV, mais **les 16 packages de leur liste 772 sont tous présents à l'identique** |
| Correctif de sécurité | 2024-11-05 | — |
| SoC | Amlogic, plateforme `sc2` (famille S905X4), 4× Cortex-A55 | — |
| Noyau | 5.4.233, CPU ARMv8 | — |
| **Espace utilisateur** | **32 bits uniquement** (`armeabi-v7a`) | Le launcher ne doit embarquer **aucune bibliothèque native 64 bits seule**. Du Kotlin pur ne pose aucun problème |
| RAM | 2,0 Go au total, **environ 710 Mo disponibles** au moment de l'audit | Budget du launcher : viser **moins de 40 Mo** (Google TV en consomme environ 107 Mo à lui seul) |
| Stockage `/data` | 4,3 Go, **828 Mo libres (82 % utilisés)** | ⚠️ Serré pour les mises à jour Play et le téléchargement d'APK (voir R16) |
| Partitions système | Toutes pleines et en lecture seule (`/system`, `/product`, `/vendor`…) | Confirme qu'il est impossible de *supprimer* Google TV |
| Écran (interface) | 1920×1080, densité 320 (xhdpi) | Maquettes du launcher en 960×540 dp |
| Démarrage vérifié | `green`, vbmeta `locked`, `ro.oem_unlock_supported=0` | **Le bootloader ne peut pas être déverrouillé.** Root et flash sont exclus de fait, et Widevine L1 est préservé |
| `ro.adb.secure` | `1` | ADB réseau exige l'autorisation à l'écran. Correct |
| Play Protect (installations ADB) | `verifier_verify_adb_installs=0` | Vérification désactivée sur cet appareil. Ce n'est pas bloquant, simplement à savoir |
| Langue / fuseau | fr-FR / Europe/Paris | — |
| Heure automatique | `auto_time=1`, `auto_time_zone=1`, DNS privé non forcé | Bonne configuration contre le risque R7 (coupure de courant) |

---

## 2. Écran d'accueil : **5 candidats HOME** (point important)

| Priorité | Composant | Origine | Commentaire |
|---|---|---|---|
| 2 | `com.google.android.apps.tv.launcherx/.home.HomeActivity` | Système | Google TV, **propriétaire actuel de HOME** |
| 1 | `com.google.android.tungsten.setupwraith/.RecoveryActivity` | Système | Prendrait HOME si launcherx était désactivé seul (risque R3 confirmé) |
| 0 | `com.razorbill.launcher/.MainActivity` | **Installé depuis un APK** le 22/08/2026, v0.11.0 | ❓ **À identifier avec vous** |
| 0 | `com.sfr.androidtv.launcher` (SFR TV) | **Installé depuis un APK** le 16/09/2025, v7.1.0 | ❓ Se déclare comme écran d'accueil. **Tourne en arrière-plan : environ 74 Mo de RAM** |
| -1000 | `com.android.tv.settings/.system.FallbackHome` | Système | Écran de secours d'Android, à ne jamais toucher |

### 2.1 Pourquoi Google TV s'affiche alors que Razorbill est « par défaut » (constaté le 5/11, 21 h 12)

- Dans les préférences Android, **le rôle HOME appartient à `com.razorbill.launcher`**, avec une préférence « toujours » (`mAlways=true`).
- Pourtant, c'est **Google TV qui s'affiche**. La raison : quand Android choisit l'écran d'accueil, il **ignore la préférence de l'utilisateur dès que le meilleur candidat a une priorité strictement supérieure aux autres**. Or seules les applications système peuvent déclarer une priorité au-dessus de 0. Google TV a la priorité **2** et l'assistant de configuration **1**.

**Conséquence majeure pour le projet :** sur cette box, **aucun launcher tiers ne peut s'afficher sur le bouton Home tant que `launcherx` ET `setupwraith` sont activés**, quel que soit le réglage choisi. C'est cohérent avec la note CleanYourTV : « `set-home-activity` répond Success mais ne prend effet qu'une fois setupwraith désactivé ». Les étapes E3 et E4 sont donc **obligatoires** pour atteindre votre objectif, et non optionnelles.

Une fois les deux désactivés, il restera à égalité, en priorité 0 : votre launcher, SFR TV, et Razorbill tant qu'il est installé. **C'est alors la préférence Android (rôle HOME) qui tranche.** Votre launcher pourra donc être prioritaire **sans désactiver SFR TV**.

### 2.2 Second utilisateur Android

`pm list users` révèle un second profil, **`10:new_user`**, non démarré, en plus du propriétaire (0). Razorbill n'y est pas installé. Les désactivations `--user 0` ne s'appliquent **pas** à ce profil : si quelqu'un bascule dessus, il retrouvera Google TV. **Question : connaissez-vous ce profil** (profil enfant Google TV, essai passé…) ?

**Nouveau risque R15 :** Android efface le choix d'écran d'accueil par défaut quand **une nouvelle application déclarant HOME est installée ou mise à jour**. Avec trois launchers tiers à égalité de priorité (Razorbill, SFR TV et le vôtre), une simple mise à jour de SFR TV pourrait faire apparaître le sélecteur « Choisir une application d'accueil » au lieu de votre launcher. Deux parades :

- garder le moins possible de candidats HOME, ce qui est une décision qui vous revient pour Razorbill et SFR TV ;
- faire vérifier par votre launcher qu'il est toujours l'écran d'accueil par défaut, et vous prévenir sinon.

---

## 3. Bluetooth, audio, CEC, réseau (mesurés)

### Bluetooth

- Pile : `ON`, A2DP Source actif. Rien n'est déchargé vers le matériel (`A2dpOffloadEnabled: false`), ce qui est plus simple et plus robuste.
- Appareils associés :

| Appareil | Type | Profils autorisés | État au moment de l'audit |
|---|---|---|---|
| Xiaomi RC (télécommande) | Bluetooth LE | HID (entrée) | **Connectée** |
| JBL Go 3 | Classique | A2DP (audio) | Non connectée |
| JBL TUNE520BT | Classique | A2DP + HEADSET, codec optionnel actif | Non connecté |
| AirPods | Dual | A2DP + HEADSET | Non connectés |

- **Question :** l'« enceinte JBL » des tests est-elle la **JBL Go 3** ? La TUNE520BT est un casque.
- Rappel : `com.android.bluetooth` gère la pile Bluetooth et `com.mi.bluetooth` l'appairage de la télécommande. Les deux sont classés **INDISPENSABLES**.

### Audio

- Sortie active pour la musique et la vidéo : **HDMI**. Chaque sortie (`hdmi`, `bt_a2dp`…) a son propre volume. Android mémorise donc un niveau distinct pour la TV et pour la JBL, ce qui est le comportement attendu pour le test VOL-01/02.

### HDMI-CEC

- CEC activé, contrôle du volume par CEC activé, allumage groupé (« One Touch Play ») activé, extinction automatique activée.
- La TV a répondu avec l'identifiant fabricant `0x0000F0`, c'est-à-dire **Samsung**. Le CEC Samsung (Anynet+) communique donc bien avec la box.
- ⚠️ La box a annoncé une **adresse physique HDMI invalide** (`FF:0E` au lieu de la forme habituelle `X.0.0.0`) à 20 h 24. C'est fréquent quand la TV est en veille ou que le câble HDMI a été rebranché, mais **à revérifier la TV allumée**. Une adresse invalide peut empêcher la TV de basculer automatiquement sur la bonne entrée (test CEC-03).

### Réseau

- Wi-Fi 5 GHz connecté (866 Mbit/s), **un seul réseau enregistré**.
- **Le réseau a été créé par le système** (`cname=android.uid.system`, `networkCreator=CREATOR_USER`), **pas par l'assistant setupwraith**. Le garde-fou de CleanYourTV passerait donc, et le risque de perte du Wi-Fi à l'étape E4 est **réduit, sans être nul**. Il faudra le tester.
- Ethernet : la pile Android le prend en charge (pour un adaptateur USB), mais aucune interface Ethernet n'est présente.

### Accessibilité, écran de veille, clavier

- Aucun service d'accessibilité actif. Deux sont installés (TalkBack, et un second à identifier).
- **Écran de veille actuel : `dreamx` (Backdrop Google TV)**, environ 77 Mo de RAM. Si `dreamx` est désactivé un jour, il faudra d'abord choisir un autre écran de veille (basique, ou celui du launcher).
- Clavier unique : `com.google.android.inputmethod.latin` (Gboard TV). Il est **indispensable** : sans lui, plus aucune saisie de mot de passe n'est possible. Le service de télécommande par téléphone fournit aussi un clavier (`ImeB`).

### DRM

- `dumpsys media.drm` n'existe pas sur ce firmware : la DRM passe par un service matériel (HAL) du fabricant. Le niveau Widevine sera vérifié **dans les applications**, avec la qualité de lecture HD/4K dans Netflix (test ⑦ du plan).

---

## 4. Applications de votre liste prioritaire

| Application | Package | Installée ? | Version | Origine |
|---|---|---|---|---|
| Netflix | `com.netflix.ninja` | ✅ | 13.2.2 | Préinstallée, mise à jour par le Play Store |
| YouTube | `com.google.android.youtube.tv` | ✅ | 5.30.340 | Préinstallée, mise à jour par le Play Store |
| YouTube Music | `com.google.android.youtube.tvmusic` | ✅ | — | Préinstallée |
| Prime Video | `com.amazon.amazonvideo.livingroom` | ✅ | 6.24.13 | Préinstallée, mise à jour par le Play Store |
| IPTV | `com.nst.iptvsmarterstvbox` (IPTV Smarters 3.1.5.1) | ✅ | | APK |
| IPTV | `com.mbm_soft.irontvmax` (6.2) | ✅ | | APK |
| SFR TV | `com.sfr.androidtv.launcher` + `.selfcare` | ✅ | 7.1.0 / 3.1.0 | APK |
| ? | `com.goodtoolapps.zeus` (10.5.11) | ✅ | | Play Store, **à identifier** |
| Disney+, Max, MyCANAL, Molotov, Plex, VLC, Spotify, Kodi | — | ❌ **non installées** | | |

Autres applications installées par l'utilisateur : `com.esaba.downloader` (Downloader), `com.alphainventor.filemanager` (gestionnaire de fichiers), `com.razorbill.launcher` (à identifier).

> Quatre applications viennent d'APK installés hors Play Store (Razorbill, IPTV Smarters, IronTV Max, SFR TV). Le Play Store ne les vérifie pas et ne les met pas à jour. Ce n'est pas un problème pour le projet, mais elles ne sont pas couvertes par les mises à jour automatiques. Les deux applications SFR n'ont pas été mises à jour depuis septembre 2025.

---

## 5. Inventaire classé des 119 packages

**Légende**
- 🔴 **INDISPENSABLE** : ne jamais désactiver.
- 🟠 **OPTIONNEL** : désactivation envisageable après vérification, **ou** simplement sans intérêt à toucher.
- 🟢 **DÉSACTIVABLE** : inutile pour votre usage, **sous réserve de tests**.

### 5.1 🔴 INDISPENSABLES

| Package(s) | Rôle | Pourquoi on n'y touche pas |
|---|---|---|
| `android`, `android.auto_generated_rro_*`, `com.android.systemui`, `com.android.shell` | Cœur d'Android, barre système, ADB | La box ne démarrerait plus, ou ADB serait perdu |
| `com.android.providers.settings`, `.media`, `.media.module`, `.downloads`, `.tv`, `.contacts`, `.calendar`, `.userdictionary` (+ overlays) | Fournisseurs de données système | Utilisés par Play, les applications de streaming (téléchargements), les chaînes TV et les comptes |
| `com.android.tv.settings` (+ overlays), `com.droidlogic.googletv.settings` (+ overlay), `com.xiaomi.gtv.settings` | Paramètres | **Seul moyen de récupération à l'écran** |
| `com.droidlogic`, `com.droidlogic.overlay`, `mitv.service` | Couche Amlogic et service TV Xiaomi : HDMI, CEC, affichage, audio | CEC, résolution et HDR. Risque de cassure des Paramètres (déjà constaté sur d'autres Xiaomi) |
| `com.android.bluetooth`, `com.mi.bluetooth` | Pile Bluetooth, appairage de la télécommande | **Télécommande Xiaomi + JBL** |
| `com.android.inputdevices` | Dispositions clavier et télécommande | Télécommande |
| `com.android.networkstack.*` (5 packages), `com.android.wifi.resources`, `com.android.captiveportallogin`, `com.android.pacprocessor`, `com.android.proxyhandler` | Réseau, Wi-Fi, proxy, portail captif | Réseau |
| `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` | Google Play Services, Google Services Framework, Play Store | Comptes, DRM côté applications, mises à jour, Cast, Netflix et YouTube |
| `com.google.android.webview` | Android System WebView | Écrans de connexion des applications (MyCANAL, Prime…) |
| `com.google.android.packageinstaller`, `com.google.android.permissioncontroller` (+ overlays), `com.google.android.ext.services`, `.ext.shared`, `com.google.android.modulemetadata` (+ overlays) | Installation, permissions, modules système | **Le système de mise à jour de votre launcher en dépend** |
| `com.google.android.inputmethod.latin` | Clavier Gboard TV | Seul clavier : sans lui, aucune saisie n'est possible |
| `com.android.externalstorage`, `com.android.mtp` | Clés USB et stockage | Test SYS-04 |
| `com.android.location.fused`, `com.android.keychain`, `com.android.certinstaller`, `com.android.se`, `com.android.statementservice` | Localisation, certificats, élément sécurisé, vérification des liens | Sécurité TLS, applications |
| `com.google.android.tv.frameworkpackagestubs`, `com.google.android.sss.authbridge`, `com.google.android.onetimeinitializer` | Bouchons système et authentification Google | Comportement mal documenté, donc on n'y touche pas |
| `com.android.tv.settings/.system.FallbackHome` | Écran d'accueil de secours | Filet de sécurité HOME |

### 5.2 🟠 OPTIONNELS (selon votre usage, ou sans intérêt à toucher)

| Package | Rôle | Avis |
|---|---|---|
| `com.google.android.apps.mediashell` | **Chromecast intégré** | À garder si vous castez (question en attente) |
| `com.google.android.katniss` | Google Assistant (bouton micro) | À garder si vous l'utilisez |
| `com.google.android.tv.remote.service` | Télécommande via l'appli Google TV du téléphone, clavier distant | Plutôt à garder : c'est une **voie de secours** si la télécommande Bluetooth est perdue |
| `com.google.android.apps.tv.dreamx` | Écran de veille Google TV (77 Mo) | Ne le désactiver **qu'après** avoir choisi un autre écran de veille |
| `com.android.dreams.basic` | Écran de veille Android de base | À garder (solution de repli) |
| `com.google.android.tts`, `com.google.android.marvin.talkback` | Synthèse vocale, accessibilité | À garder : faible gain, et l'Assistant peut en dépendre |
| `com.xiaomi.mitv.updateservice` | **Mises à jour firmware Xiaomi** | **Votre décision.** Désactivé : plus de correctifs, mais plus de réactivation de Google TV |
| `com.xiaomi.android.tvsetup.partnercustomizer` | Boutons Netflix/YouTube/Prime de la télécommande | À désactiver seulement quand votre launcher gérera ces touches |
| `com.xiaomi.mitv.smartshare`, `com.xiaomi.wfdsinkhelperservice` | Miracast (recopie d'écran) | Selon l'usage |
| `com.xiaomi.mimusic2`, `com.mitv.gallery`, `com.xiaomi.mitv.mediaexplorer`, `com.mitv.videoplayer` | Lecture de fichiers USB Xiaomi | À garder tant que VLC n'est pas installé |
| `com.xiaomi.tvqs.overseas` | Envoi de journaux et de rapports de plantage Xiaomi | Jamais testé désactivé par CleanYourTV : prudence |
| `com.xiaomo.tv.milegal` | Mentions légales Xiaomi | Gain nul, on laisse |
| `com.google.android.syncadapters.calendar` | Synchronisation de l'agenda | Gain négligeable |
| `com.google.android.youtube.tvmusic`, `com.amazon.amazonvideo.livingroom`, `com.netflix.ninja`, `com.google.android.youtube.tv` | Applications de streaming | **Vous les utilisez : on garde** |
| `com.android.printspooler`, `com.android.htmlviewer`, `com.android.soundpicker`, `com.android.bluetoothmidiservice`, `com.android.companiondevicemanager`, `com.android.hotspot2.osulogin`, `com.android.backupconfirm`, `com.android.sharedstoragebackup`, `com.android.wallpaperbackup`, `com.android.localtransport`, `com.android.settings.intelligence`, `com.android.dynsystem`, `com.android.cts.ctsshim`, `com.android.cts.priv.ctsshim` | Composants Android de base inactifs | **Gain nul, on laisse.** Seul `printspooler` est désactivé par CleanYourTV |
| Applications tierces : Razorbill, SFR TV, SFR selfcare, Zeus, Downloader, gestionnaire de fichiers, IPTV ×2 | Vos applications | **Votre décision**, voir les questions |

### 5.3 🟢 DÉSACTIVABLES (sous réserve de tests, une catégorie à la fois)

| Package | Rôle | Risque connu | Étape |
|---|---|---|---|
| `com.google.android.apps.tv.launcherx` | Interface Google TV | Boutons raccourcis inactifs. **Seulement après E2** | E3 |
| `com.google.android.tungsten.setupwraith` | Assistant de configuration (prendrait HOME) | Wi-Fi : risque réduit (réseau créé par le système). Cast : à tester | E4 |
| `com.miui.tv.analytics`, `com.xiaomi.statistic` | Télémétrie Xiaomi | Aucun connu | E5 |
| `com.google.android.feedback`, `com.google.android.partnersetup` | Rapports Google, attribution RLZ | Aucun connu | E5 |
| `com.mitv.tvhome.oemtab`, `.michannel`, `.mitvplus`, `.atv` (PatchWall), `com.xm.webcontent` | Rangées publicitaires et contenus Xiaomi | Aucun connu (PatchWall tourne actuellement : 46 Mo) | E6 |
| `android.autoinstalls.config.xioami.mibox3` | Installation automatique d'applications partenaires | Aucun connu | E6 |
| `com.google.android.videos`, `com.google.android.play.games` | Google TV/Play Films, Play Jeux | Aucun connu | E7 |

Prime Video ne figure pas dans cette liste, puisque vous l'utilisez. CleanYourTV le désactive par défaut.

---

## 6. Risques mis à jour par l'audit

| # | Risque | Évolution |
|---|---|---|
| R1 | Build 773 non validé | **Atténué** : même ensemble de packages que la liste 772 |
| R2 | setupwraith et Wi-Fi | **Atténué** : réseau créé par le système. Le Cast reste à tester |
| R3 | HOME vers RecoveryActivity | **Confirmé** (priorité 1) |
| R15 | **Nouveau** : trois launchers tiers, sélecteur HOME après installation ou mise à jour | À traiter (questions sur Razorbill et SFR TV) |
| R16 | **Nouveau** : 828 Mo libres. Une mise à jour Play ou un APK téléchargé peut échouer | Le launcher devra vérifier l'espace disponible avant de télécharger une mise à jour |
| R17 | **Nouveau** : adresse physique CEC invalide relevée | À revérifier la TV allumée (CEC-01) |
| R18 | **Nouveau** : écran de veille fourni par `dreamx` (Google) | Ne pas désactiver dreamx sans le remplacer |

## 7. Décisions (5 novembre 2026)

### 7.1 Razorbill : abandon et désinstallation — **exécutée le 5/11 à 21 h 16 avec votre accord**

Résultat : `Success`. Le rôle HOME est revenu à `launcherx`. Comparaison complète avec l'audit E0 :

- **Packages** : une seule différence, l'absence de Razorbill. Les 118 autres sont identiques, versions et UID compris, et aucun n'est désactivé.
- **Google TV** : launcherx, setupwraith, dreamx et PatchWall sont activés et inchangés. Le processus launcherx est le même qu'avant (pas de redémarrage).
- **Réglages** : global et system sont identiques. Dans secure, seul `mitv_resume_pkg/activity` a changé, parce que la box était passée en écran de veille dreamx pendant la vérification. C'est sans lien avec la désinstallation.
- **Fonctions** : Wi-Fi connecté avec Internet validé, Bluetooth `ON`, télécommande Xiaomi connectée, 3 appareils audio toujours associés, CEC actif (adresse physique toujours invalide, R17), son non coupé, environ 660 Mo de RAM libre.
- **Profil 10** : Razorbill n'y a jamais été installé, rien à faire.

Préparation initiale :

Analyse des dépendances (lecture seule, refaite automatiquement par le script) :

| Vérification | Résultat |
|---|---|
| Package système ? | Non : application installée depuis un APK, v0.11.0 |
| Accueil affiché actuellement | Non (Google TV) |
| Service d'accessibilité, clavier, notifications, écran de veille | Aucun |
| Administrateur de l'appareil | Non |
| Autres applications qui l'utilisent | Aucune : il ne déclare qu'une permission interne et ne fournit rien aux autres |
| Ce qu'il fait au démarrage | Il se lance au boot (`RECEIVE_BOOT_COMPLETED`), ce qui consomme de la RAM pour rien |
| Préférences Android | **Détient le rôle HOME** (sans effet visible, voir §2.1) |
| Utile à notre projet ? | **Non.** Aucun code ni aucune donnée réutilisés |

**Conclusion : aucune dépendance.** Après désinstallation, Android libère le rôle HOME. L'écran d'accueil reste Google TV (priorité 2), sans changement visible.

Script : [`scripts/maintenance/uninstall-razorbill.sh`](../scripts/maintenance/uninstall-razorbill.sh)
- **Simulation exécutée le 5/11 à 21 h 14** : toutes les vérifications sont passées. L'APK est sauvegardé localement (21,9 Mo, SHA-256 `d50a05c9…ad77720`) pour permettre une réinstallation. Ses réglages ne sont pas conservés.
- Une seule commande modifie la box : `pm uninstall com.razorbill.launcher`. Elle n'est envoyée qu'avec `--execute`, **puis** après avoir tapé `DESINSTALLER`.

### 7.2 SFR TV : conservation impérative

| Constat (lecture seule) | Conséquence |
|---|---|
| Son activité `LauncherActivity` sert **à la fois** d'écran d'accueil et d'application lancée normalement | Votre launcher l'ouvrira comme n'importe quelle app, par l'intent `LEANBACK_LAUNCHER`. Pas besoin du rôle HOME pour regarder la TV |
| Service au premier plan `ScreenStateService` (surveille l'allumage et l'extinction de l'écran) + récepteur `LauncherWakeUpReceiver` | **Hypothèse à tester** : à la sortie de veille, SFR TV pourrait se remettre au premier plan à la place de votre launcher. Tests SFR-05/06 |
| Environ 74 Mo de RAM en permanence | À surveiller avec 2 Go. On n'y touche pas |
| N'est pas une source TV système (`TvInputService`) | Rien à protéger côté entrées |
| Installé depuis un APK (v7.1.0, septembre 2025) | Pas de mise à jour automatique par le Play Store |

**Stratégie retenue :** SFR TV n'est **ni désactivé ni désinstallé**. Votre launcher sera défini comme **détenteur du rôle HOME**. Deux mécanismes assureront qu'il le reste :

1. **Au démarrage et à chaque retour à l'accueil**, le launcher vérifie qu'il est toujours l'écran d'accueil par défaut. Si une mise à jour de SFR TV a fait réapparaître le sélecteur, il vous l'indique et propose de le corriger. Une app ordinaire ne peut pas s'imposer elle-même : c'est Android qui demande la confirmation.
2. **Script de diagnostic** côté Mac, qui contrôle et corrige le rôle HOME par ADB, avec votre accord.

### 7.3 Usages et décisions de l'utilisateur (5 novembre 2026)

| Sujet | Décision | Conséquence technique |
|---|---|---|
| Vérifications à l'écran après la désinstallation de Razorbill et le redémarrage | ✅ Tout se lance (image, télécommande, Home, SFR TV, Netflix, YouTube) | RZ-02, RZ-03 et RZ-04 validés manuellement |
| Chromecast | Pas utilisé | `com.google.android.apps.mediashell` passe en **désactivable**, dans une étape tardive et isolée |
| Assistant / bouton micro | Pas ou très peu utilisé | `com.google.android.katniss` passe en **désactivable**, dans une étape tardive. Le bouton micro ne fera alors plus rien |
| Bouton YouTube de la télécommande | **Conservé** : ouvre YouTube | Le launcher le gère lui-même une fois Google TV désactivé (service de touches) |
| Boutons Netflix et Prime de la télécommande | **Désactivés** | Le launcher les neutralise : ils ne font rien |
| Mises à jour firmware Xiaomi | **Bloquées pour le moment** | `com.xiaomi.mitv.updateservice` est ajouté à une étape dédiée, réversible |
| Profil Android `10:new_user` | Origine inconnue, peut rester ou partir | **On le laisse** : il ne gêne pas, et le supprimer serait irréversible. À réévaluer plus tard |
| Dépôt GitHub | `89hr474hht-rgb/mibox-launcher`, public. L'ancien contenu (launcher Razorbill) peut être remplacé | Aucune donnée personnelle dans le dépôt. L'ancien code est conservé dans une branche `legacy-razorbill` |
| Utilisation familiale de la box | **Aucune action visible à l'écran sans créneau convenu** | Seules les tâches en arrière-plan (lecture ADB, compilation, préparation) sont faites librement. Les étapes qui changent l'écran (installation, changement d'accueil, désactivations, redémarrages) sont planifiées avec vous |

## 8. Tests passés à l'état E0 (ADB uniquement)

| ID | Statut E0 | Constat |
|---|---|---|
| BT-01 | ✅ Test réussi | Pile Bluetooth `ON`, A2DP Source actif |
| BT-03 | ✅ Test réussi | JBL Go 3 et JBL TUNE520BT associées |
| BT-02 | 🟡 Test partiel | Télécommande Xiaomi connectée en Bluetooth LE (HID). Le fonctionnement des touches reste 👁 à valider par vous |
| CEC-01 | 🟡 Test partiel | CEC actif et TV Samsung détectée, mais adresse physique invalide (R17) |
| NET-01 | ✅ Test réussi | Wi-Fi 5 GHz connecté |
| NET-02 | ✅ Test réussi | Réseau créé par le système, pas par setupwraith |
| SYS-01 | ✅ Test réussi | HOME = Google TV (état d'origine attendu) |

Tous les autres tests restent ⬜ **Non testés** ou 👁 **Validation manuelle nécessaire**.
