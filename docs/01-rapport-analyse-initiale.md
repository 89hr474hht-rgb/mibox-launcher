# Rapport technique initial — Phase 1 (analyse)

Date : 5 novembre 2026
Statut : **analyse uniquement**. Aucune commande n'a été envoyée à l'appareil. ADB n'est pas installé sur ce Mac (`adb not found`).
Sources : dépôt `Gathin23/cleanyourtv` (commit `fc148d5`, launcher 3.4) lu en local, et les deux photos des écrans « À propos ».

---

## 0. Résumé

1. **Votre appareil n'est pas une télévision mais un boîtier de streaming.** `MiTV-AFKR0`, nom de code `jaws`, correspond à la **Xiaomi Mi Box S (2e génération)**. Le dépôt CleanYourTV le dit explicitement (`examples/xiaomi-mi-box-s-2nd-gen/`), et le préfixe Netflix « …-JAWS-XIAOMMITV-AFKR0 » le confirme. Cela change une partie du cahier des charges (voir §3.3) : les réglages d'image de la dalle, les sources HDMI, l'ARC/eARC et le tuner relèvent de **la télévision sur laquelle le boîtier est branché**, pas du boîtier.
2. **Ce modèle est déjà documenté par CleanYourTV**, avec trois firmwares validés : `RTT0.211222.001.683`, `.737` et `.772`. **Votre build est le `.773`**, qui n'a pas été validé. C'est très probablement une petite mise à jour du 772, mais il faudra le vérifier, et non le supposer.
3. **Votre objectif est réalisable sans root**, avec les limites suivantes :
   - votre launcher peut devenir l'écran d'accueil permanent (HOME) ;
   - l'interface Google TV peut être **désactivée** (`pm disable-user`) mais **pas supprimée** : ses APK restent dans la partition système en lecture seule ;
   - une mise à jour firmware Xiaomi peut réactiver ce qui a été désactivé ;
   - sur Android 11, la mise à jour automatique du launcher demandera **toujours une confirmation à l'écran**. Une installation silencieuse est impossible sans privilèges système.
4. **Le root ou le flash sont à exclure.** Déverrouiller le bootloader ferait passer Widevine de L1 à L3, donc plus de HD ni de 4K sur Netflix, Prime, Disney+, etc. Aucun gain fonctionnel du projet ne le justifie.

---

## 1. Ce qu'on sait déjà de l'appareil (d'après vos photos)

| Élément | Valeur | Commentaire |
|---|---|---|
| Modèle | `MiTV-AFKR0` | Xiaomi Mi Box S 2nd Gen, codename `jaws` |
| Système | Android TV 11 (SDK 30), interface Google TV | Google TV « 11 » |
| Correctif de sécurité | 5 novembre 2024 | Deux ans de retard : Xiaomi publie peu de mises à jour |
| Noyau | `5.4.233-android12-9` (build du 18 nov. 2024) | Noyau GKI 5.4 générique, normal sur cette plateforme Amlogic |
| Build | `RTT0.211222.001.773 release-keys` | Build de production signé, **non validé** par CleanYourTV (683/737/772 le sont) |
| SoC | Amlogic (fiches publiques : S905X4-K, 4× Cortex-A55, Mali-G31) | **À confirmer par ADB** |
| RAM | 2 Go (d'après le dépôt) | Contrainte majeure pour le launcher |
| Réseau | Wi-Fi 5, Bluetooth | **Pas de port Ethernet** sur ce boîtier : il faudrait un adaptateur USB, à vérifier |
| Horloge | Pas d'horloge sauvegardée sur pile | Voir le risque R7 |

> ⚠️ Le numéro **Netflix ESN** visible sur votre photo identifie l'appareil auprès de Netflix. Il n'apparaît volontairement pas dans ce rapport et ne devra jamais être publié dans un dépôt GitHub.

### 1.1 Installation réelle (confirmée le 5 novembre 2026)

```
 Mi Box S 2e gén. ──HDMI (CEC)──▶ Samsung OLED 2023 (modèle exact à confirmer)
        │                              └─ son : haut-parleurs intégrés de la TV
        └──Bluetooth (A2DP)──▶ enceinte JBL (usage ponctuel)
        └──Bluetooth (HID)───◀ télécommande Xiaomi « Xiaomi RC »
 Télécommande Samsung ──IR──▶ TV ──CEC (Anynet+)──▶ Mi Box
```

Conséquences directes :

- **Le Bluetooth du boîtier est critique à double titre.** La télécommande Xiaomi est **Bluetooth**, pas infrarouge. Casser la pile Bluetooth, c'est donc perdre **à la fois** la JBL **et** la télécommande. Tout package lié au Bluetooth (`com.android.bluetooth`, `com.mi.bluetooth`, etc.) est classé **INDISPENSABLE** jusqu'à ce que ses dépendances aient été établies. Il ne sera pas désactivé sans votre validation explicite. La même règle s'applique à **tout composant réseau**.
- **HDMI-CEC (Anynet+ chez Samsung)** sert à trois choses : piloter la Mi Box avec la télécommande Samsung, allumer et éteindre les deux appareils ensemble, et régler le volume de la TV depuis la télécommande Xiaomi. Le service `hdmi_control` et les composants Amlogic `com.droidlogic*` sont donc **INDISPENSABLES**.
- **Le volume suit deux chemins différents.** Avec la JBL connectée, la sortie audio passe en Bluetooth : le volume est celui de l'enceinte (volume absolu AVRCP). Sans la JBL, la Mi Box envoie des commandes de volume CEC à la Samsung. Les deux chemins doivent être testés.
- **Formats** (à vérifier pendant les tests, et non à supposer) : à ma connaissance, les TV Samsung ne prennent en charge **ni Dolby Vision ni le décodage DTS**. La Mi Box devra donc sortir en HDR10/HDR10+, et le réglage de sortie audio devra éviter le passthrough DTS.
- **Le système de la Samsung (Tizen) est hors du périmètre** de ce projet.

---

## 2. Analyse du dépôt CleanYourTV

### 2.1 Ce que c'est

Un projet communautaire sous licence **MIT** qui comprend :

- **`launcher/`** : un launcher Android TV en **Java** (74 classes, package `com.gathin.tvhome`, minSdk 26, targetSdk 34, Java 17). Il n'a que deux dépendances (RecyclerView et DynamicAnimation), ce qui le rend très léger ;
- **`tools/`** : des scripts Bash/PowerShell et un client ADB en Python (`tv.py`, basé sur `adb-shell`, qui ne nécessite pas le binaire `adb`) ;
- **`examples/`** : environ 443 dossiers de listes de désactivation, une par modèle ou firmware, avec des notes de terrain ;
- **`themes/`**, **`addons/`** : des thèmes et modules optionnels (horloge, météo, économiseurs d'écran…) ;
- **`docs/`** : la restauration, le dépannage (environ 1 800 lignes) et des notes par appareil ;
- **`tests/`** et **`.github/workflows/`** (`ci.yml`, `addons.yml`) : des tests Python et la CI.

### 2.2 Fonctionnement avec ADB

Tout passe par **ADB réseau (port 5555)** après activation du débogage dans les options développeur. Aucune élévation de privilèges n'est utilisée. Les commandes clés sont :

| Action | Commande Android utilisée |
|---|---|
| Désactiver une app | `pm disable-user --user 0 <package>` |
| Réactiver | `pm enable --user 0 <package>` |
| Savoir qui possède HOME | `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` |
| Lister les candidats HOME | `cmd package query-activities --brief … -c android.intent.category.HOME` |
| Imposer le launcher | `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` |
| Installer | `pm install -r` (APK poussé dans `/data/local/tmp`) |
| Permissions spéciales | `pm grant … WRITE_SECURE_SETTINGS`, `ACCESS_FINE_LOCATION`, `cmd appops …` |

Points importants :

- **Aucune désinstallation.** Le projet interdit `pm uninstall`, le root et le contournement des blocages constructeur. Tout est réversible.
- La désactivation porte sur **l'utilisateur 0** : l'état est stocké dans `/data`, donc **une réinitialisation d'usine remet tout à l'état d'origine**.
- `disable.sh` refuse toute ligne qui n'a pas la forme d'un nom de package (protection contre l'injection de commandes) ;
- il refuse aussi les packages du cœur Android (une regex `CORE_RE` couvre `android`, `systemui`, `settings`, `shell`…).

### 2.3 Mécanismes de sécurité (garde-fous) de `disable.sh`

1. **Garde HOME** : il refuse de désactiver le launcher actif tant qu'aucun autre launcher n'est installé et capable de prendre HOME.
2. **Garde setupwraith** : il refuse de désactiver l'assistant de configuration Google (`com.google.android.tungsten.setupwraith`) si le réseau Wi-Fi enregistré lui « appartient ». Sinon, le Wi-Fi peut tomber.
3. **Garde des entrées TV** : il conserve les packages qui fournissent des sources HDMI/AV (`TvInputService`). Cela ne vous concerne pas, le boîtier n'a pas d'entrée.
4. **Garde des handlers** : il conserve les applications qui ouvrent les Paramètres et les entrées.
5. **Vérification après coup** : il relit la liste des packages encore actifs et signale ceux que la TV a refusé de désactiver.
6. **Avertissement `ro.adb.secure`** : il prévient si l'ADB réseau accepte n'importe quel appareil.

Chaque garde peut être contournée par une variable d'environnement (`FORCE_HOME`, `FORCE_SETUPWRAITH`…). Je ne les utiliserai pas sans votre accord explicite.

### 2.4 Sauvegarde et restauration

- Avant la première modification, `disable.sh` écrit un **enregistrement** dans `~/.cleanyourtv/`. Il contient l'état de chaque package de la liste (activé, désactivé ou absent), le composant qui possédait HOME, et les trois échelles d'animation.
- `restore.sh <liste> [launcher d'origine]` **rejoue cet enregistrement**. Sur Google TV, le launcher d'origine est `com.google.android.apps.tv.launcherx`.
- Un second passage de la même liste **ne remplace pas** l'enregistrement initial.
- `check.py` détecte la **dérive** (ce qu'une mise à jour firmware a réactivé) et `--fix` la corrige en appliquant les mêmes gardes.

**Limites de la restauration :**

- elle ne restaure que ce qui figurait dans la liste. Une désactivation faite à la main n'est pas suivie ;
- elle laisse le nouveau launcher installé ;
- elle ne sauvegarde **ni les données des applications, ni les comptes, ni les réglages**. Seul l'état activé/désactivé des packages est concerné ;
- **aucune sauvegarde du firmware** n'est possible sans root. En dernier recours, il reste la réinitialisation d'usine.

### 2.5 Ce que la liste du Mi Box S 2e gén. désactive (validée sur 683/737/772)

| Catégorie | Packages | Mon avis |
|---|---|---|
| Télémétrie | `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.partnersetup` | Désactivation envisageable, faible risque |
| Publicité / promos Xiaomi | `com.mitv.tvhome.oemtab`, `.michannel`, `.mitvplus`, `.atv` (PatchWall), `com.xm.webcontent`, `android.autoinstalls.config.xioami.mibox3` | Désactivation envisageable |
| Mises à jour Xiaomi | `com.xiaomi.mitv.updateservice` | **Votre décision** (voir la question Q8) : cela bloque aussi les correctifs de sécurité |
| Applications préinstallées | Prime Video, Google TV/Play Films, Play Jeux, Print Spooler | Selon votre usage. **Vous voulez garder Prime Video** : à retirer de la liste |
| Interface Google TV | `com.google.android.apps.tv.launcherx` | **Seulement après** que votre launcher possède HOME |
| Assistant de configuration | `com.google.android.tungsten.setupwraith` | **Le point le plus délicat** (voir R2) |

**Conservés volontairement** : Google Assistant (`katniss`), Chromecast (`mediashell`), télécommande téléphone (`tv.remote.service`), lecteurs USB Xiaomi, Miracast, `mitv.service`, `com.mi.bluetooth`, `com.droidlogic*` (système Amlogic), `com.xiaomi.gtv.settings`.

**Constaté par les contributeurs après plusieurs jours d'usage** : le Wi-Fi, la lecture USB, Google Home, la télécommande téléphone, le Chromecast depuis un Pixel et l'Assistant fonctionnent toujours.

### 2.6 Ce que CleanYourTV ne fait pas, ou refuse de faire

- **Pas de mise à jour automatique ni de signature de release.** C'est une règle explicite du projet (`docs/DOS-AND-DONTS.md`). Le launcher est même signé avec la **clé de debug** en release. C'est acceptable pour un projet compilé par chacun depuis les sources, mais **incompatible avec votre système de mise à jour**, qui exige une clé de signature stable et privée.
- **Pas d'APK publiés** : chacun compile soi-même.
- **Une seule voie d'installation** : `build-and-install.sh`.

→ Votre projet doit donc être **un dépôt indépendant**, pas une contribution à CleanYourTV. La licence MIT le permet, à condition de conserver la mention de copyright pour tout code repris.

### 2.7 Le launcher CleanYourTV : points techniques utiles

- Activité HOME en `singleTask`, `clearTaskOnLaunch`, `excludeFromRecents`, `stateNotNeeded` : c'est la configuration standard d'un launcher robuste.
- Des **services d'accessibilité** optionnels (`InputKeyService`, `HomeKeyService`) interceptent les touches de la télécommande. **C'est indispensable sur ce boîtier**, car les boutons Netflix/YouTube/Prime **cessent de fonctionner** une fois `launcherx` désactivé : c'était lui qui les gérait.
- `ServiceRepair` réactive ces services après un arrêt forcé, grâce à `WRITE_SECURE_SETTINGS` accordé par ADB.
- Le projet est très orienté multi-appareils (Coocaa, CVTE, Yandex, Fire TV…) et contient beaucoup de fonctions décoratives (fêtes, Halloween, calendriers). Pour vous, l'essentiel du code est hors sujet.

---

## 3. Possibilités et limites sur votre matériel

### 3.1 Faisable sans root

| Objectif | Faisable ? | Comment |
|---|---|---|
| Votre launcher au démarrage et sur le bouton Home | ✅ Oui | Filtre `HOME`, puis `set-home-activity`, puis désactivation de `launcherx` et `setupwraith` |
| Plus aucun écran Google TV en usage normal | ✅ Oui, visuellement | Par désactivation, pas par suppression |
| Retirer les recommandations et pubs de l'accueil | ✅ Oui | Elles vivent dans `launcherx` et les packages `mitv.tvhome.*` |
| Réduire la télémétrie | 🟡 Partiellement | Xiaomi et l'attribution Google, oui. Google Play Services reste actif et nécessaire |
| Supprimer réellement Google TV du système | ❌ Non | APK dans `/system` (lecture seule) : il faudrait le root |
| Bouton Retour qui ne quitte pas le launcher | ✅ Oui | Un HOME n'a pas d'écran « derrière » lui : Retour est simplement consommé |
| Mise à jour du launcher depuis GitHub | ✅ Oui, avec confirmation | `PackageInstaller` + permission « Installer des applis inconnues » |
| Mise à jour **silencieuse** | ❌ Non sur Android 11 | L'option « pas d'action utilisateur » n'existe qu'à partir d'Android 12 (API 31). Le boîtier est en API 30 |
| Garder Widevine L1, la 4K et le HDR | ✅ Oui | `pm disable-user` ne touche pas la DRM. Le root, lui, la casserait |
| Boutons Netflix/YouTube/Prime de la télécommande | ✅ Oui | Via un service d'accessibilité dans votre launcher |

### 3.2 Contraintes matérielles

- **2 Go de RAM**, dont une bonne partie est déjà utilisée par Android et Google Play Services. Le launcher doit rester léger, sans images 4K en mémoire (CleanYourTV plafonne à 1 920 px). Il ne faut pas non plus qu'Android tue les apps de streaming pour libérer de la place pour le launcher.
- **Stockage d'environ 8 Go** : il faut surveiller l'espace libre, car un APK téléchargé pour une mise à jour doit tenir dans l'espace de l'application.
- **Interface affichée en 1080p** (la vidéo peut sortir en 4K) : le launcher doit être conçu pour 1920×1080 à densité TV.

### 3.3 Conséquences du fait que c'est un **boîtier**

Une partie de votre cahier des charges (§7) **ne dépend pas du boîtier** :

| Fonction | Où elle se joue |
|---|---|
| Réglages d'image (rétroéclairage, couleurs de la dalle) | **Sur la télévision**. Le boîtier ne règle que la résolution, le HDR et la plage de couleurs de sortie |
| ARC / eARC, barre de son | **Télévision ↔ barre de son**. Le boîtier ne fait qu'envoyer l'audio (passthrough Dolby/DTS) |
| HDMI-CEC | Partagé : le boîtier allume ou éteint la TV et règle le volume via CEC. **À tester** |
| Sources HDMI, tuner TNT | **Sur la télévision** : le boîtier n'a ni entrée ni tuner |
| Ethernet | **Absent** du boîtier, seulement via un adaptateur USB |
| Veille et réveil | Côté boîtier, avec CEC vers la TV |

Si votre télévision est elle-même une Smart TV, elle a **son propre système**, qui n'est pas concerné par ce projet.

---

## 4. Risques identifiés

| # | Risque | Gravité | Prévention |
|---|---|---|---|
| R1 | **Build 773 non validé** : un package a pu être ajouté ou renommé | Moyenne | Audit en lecture seule, puis comparaison avec la liste 772 avant toute modification |
| R2 | **setupwraith** : sa désactivation peut couper le Wi-Fi (mot de passe à ressaisir) s'il « possède » le réseau. Sur certains appareils Google, elle **casse le Chromecast** (credentials Cast) | Haute | Vérifier le propriétaire du réseau Wi-Fi, y compris un éventuel ancien réseau caché enregistré pendant la configuration. Tester le Cast juste après, et après un redémarrage |
| R3 | Désactiver `launcherx` **seul** : HOME bascule sur `RecoveryActivity` de setupwraith, pas sur votre launcher | Haute | Ordre strict : installer le launcher, vérifier qu'il possède HOME, puis désactiver launcherx et setupwraith |
| R4 | Les **boutons raccourcis** de la télécommande deviennent inactifs | Faible | Service d'accessibilité dans le launcher. Les scancodes diffèrent selon le build (683 ≠ 737/772) : à relever sur votre 773 |
| R5 | **Une mise à jour firmware** réactive des packages et rend HOME à Google TV | Moyenne | Vérification de dérive après chaque mise à jour. Votre launcher pourra afficher un avertissement |
| R6 | **Prime Video se réactive tout seul** (constaté sur 772) | Faible | Sans objet si vous le gardez |
| R7 | **Après une coupure de courant**, le boîtier repart avec la date du firmware (2024). Avec un DNS privé strict, le TLS échoue et le boîtier affiche « Pas d'Internet » | Moyenne | Laisser l'heure automatique activée et le DNS privé en mode automatique. Tester le démarrage à froid |
| R8 | **ADB réseau ouvert** : n'importe qui sur le réseau local peut s'y connecter si `ro.adb.secure=0` | Moyenne | Vérifier `ro.adb.secure`, puis refermer le débogage après chaque session |
| R9 | **Mise à jour du launcher** : pendant l'installation, le processus HOME est tué. Si launcherx et setupwraith sont désactivés, Android peut afficher un écran de secours, voire un écran noir quelques secondes | Moyenne | À tester explicitement en Phase 4. Prévoir un retour automatique sur HOME une fois l'installation terminée |
| R10 | **Le système de mise à jour devient une porte d'entrée** : un APK malveillant pourrait remplacer votre launcher | Haute | Clé de signature privée gardée hors du dépôt (secret GitHub), empreinte du certificat **épinglée dans l'app**, SHA-256 vérifié, et Android refuse de toute façon une mise à jour signée par une autre clé |
| R11 | Un **dépôt GitHub privé** empêche l'app d'interroger les Releases sans token, or un token dans l'app est interdit | Bloquant | Dépôt (ou au moins Releases) **public** |
| R13 | **Perte du Bluetooth** = perte de la télécommande Xiaomi **et** de la JBL | Critique | Aucun package Bluetooth ou réseau désactivé sans analyse de ses dépendances. Garder ADB réseau comme accès de secours, et la télécommande Samsung via CEC comme second moyen de navigation |
| R14 | **Perte du CEC** : la télécommande Samsung ne pilote plus la box, ou le volume ne se règle plus | Haute | `com.droidlogic*` et `hdmi_control` intouchables. Test CEC après chaque étape |
| R12 | Une réinitialisation d'usine efface comptes et apps, mais remet le boîtier en état d'origine | Information | C'est le filet de sécurité ultime. Le dépôt recommande de réactiver setupwraith avant d'en faire une |

**Moyens de récupération qui resteront toujours disponibles** (aucun ne dépend de votre launcher) :

1. ADB réseau depuis le Mac (installé le 5 nov. 2026 : platform-tools 37.0.2 via Homebrew), avec `pm enable` et `set-home-activity` ;
2. le script de restauration ;
3. Paramètres → Applications, depuis un bouton « Maintenance » du launcher protégé par une séquence de touches ;
4. la réinitialisation d'usine (Paramètres, ou mode recovery).

---

## 5. Orientation préliminaire (à valider en Phase 2)

- **Je recommande de ne pas forker CleanYourTV tel quel.** Plus de 90 % de son code vise d'autres appareils ou des fonctions décoratives, et sa philosophie (pas de mise à jour automatique, signature de debug) contredit vos exigences. Il vaut mieux **écrire votre propre launcher**, plus petit, et **reprendre les mécanismes éprouvés** (filtre HOME, gardes de désactivation, enregistrement de restauration, service de touches) en citant leur origine MIT.
- **Scripts** : réutiliser la logique des gardes de CleanYourTV, adaptée à un seul appareil (le vôtre), avec des journaux horodatés.
- **Ordre des phases** : comme dans votre cahier des charges. Une seule modification sensible à la fois, avec un test entre chaque.

---

## 6. Prochaine étape proposée : audit en lecture seule

Une fois ADB installé **sur le Mac** et le débogage activé **sur le boîtier**, je lancerai uniquement des commandes de lecture : `getprop`, `pm list packages`, `dumpsys package`, `cmd package resolve-activity`, `dumpsys meminfo`, `df`, `wm size`/`wm density`, `dumpsys wifi` (propriétaire du réseau), `getprop ro.adb.secure`, etc. Le résultat sera un inventaire classé en **INDISPENSABLE / OPTIONNEL / DÉSACTIVABLE** et un tableau de compatibilité « avant ».

Aucune commande `pm disable`, `pm install`, `settings put` ou `cmd … set` ne sera lancée sans votre accord explicite.
