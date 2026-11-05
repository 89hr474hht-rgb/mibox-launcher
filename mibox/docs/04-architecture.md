# Phase 2 — Architecture : un fork de CleanYourTV

Statut : **validé dans son principe le 5 novembre 2026**. Vous avez choisi d'utiliser CleanYourTV tel quel (launcher, outils, listes) et de le modifier avec vos idées.
Cette version remplace celle du même jour qui proposait d'écrire un launcher neuf : c'était une erreur d'interprétation de ma part.

Appareil : Xiaomi Mi Box S 2e gén. (`MiTV-AFKR0`, Android 11, espace utilisateur 32 bits, 2 Go de RAM). Voir [03-audit-mi-box.md](03-audit-mi-box.md).

---

## 1. Principe

```
Gathin23/cleanyourtv (upstream, MIT)  ──git fetch/merge──▶  89hr474hht-rgb/mibox-launcher (votre fork)
                                                             ├── tout CleanYourTV, inchangé autant que possible
                                                             ├── mibox/            ← tout ce qui vous est propre
                                                             ├── .github/README.md ← page d'accueil GitHub du fork
                                                             └── vos modifications du launcher (petites, isolées)
```

- **CleanYourTV est importé avec tout son historique.** Une commande suffit pour récupérer ses améliorations futures (§6).
- **Vos ajouts vivent dans `mibox/`**, et vos modifications du launcher sont **petites et isolées**. Les futures fusions avec CleanYourTV restent ainsi simples.
- **La licence MIT est respectée** : `LICENSE` d'origine conservé (copyright Gathin), crédits dans la page d'accueil du fork.
- **Aucune donnée personnelle** dans le dépôt public. Les audits restent dans `audits/`, ignoré par Git.

## 2. Organisation du dépôt

| Chemin | Origine | Rôle |
|---|---|---|
| `launcher/` | CleanYourTV, **modifié par vous** | Le launcher (Java, `com.gathin.tvhome`) |
| `launcher/mibox/` | **Vous** | Le module de mise à jour (F2 + F3), ajouté au launcher seulement avec `-Pmibox` (§5) |
| `.github/workflows/release.yml` | **Vous** | Publie une release signée à chaque tag `vX.Y.Z` |
| `tools/` | CleanYourTV | `disable.sh`, `restore.sh`, `check.py`, `tv.py`… : désactivation, restauration, dérive |
| `examples/xiaomi-mi-box-s-2nd-gen/` | CleanYourTV | Liste validée pour votre box (builds 683/737/772) : **référence, non modifiée** |
| `themes/`, `addons/`, `docs/` | CleanYourTV | Thèmes, modules, documentation d'origine |
| `README.md`, `AGENTS.md`, `PROMPT.md` | CleanYourTV, **non modifiés** | Restent identiques pour les fusions et les tests de CleanYourTV |
| `.github/README.md` | **Vous** | Page affichée par GitHub (prioritaire sur le README racine) |
| `CHANGELOG.md` | **Vous** | Versions de votre fork |
| `CLAUDE.md` | CleanYourTV, **adapté** | Fait lire les règles du fork en premier |
| `mibox/docs/` | **Vous** | Rapports, plan de tests, audit, cette architecture |
| `mibox/lists/` | **Vous** | Vos listes de désactivation, **une par étape** (E3, E4…), au format de `tools/disable.sh` |
| `mibox/config/` | **Vous** | Réglages du launcher pour votre box (favoris, boutons, applis masquées) |
| `mibox/scripts/` | **Vous** | Audit en lecture seule, maintenance (Razorbill), installation de l'édition Mi Box, et plus tard la vérification du rôle HOME |
| `mibox/AGENTS.md` | **Vous** | Règles du fork pour les agents (priment sur `AGENTS.md`) |

## 3. Le launcher CleanYourTV sur votre box

Ce que le launcher d'origine sait **déjà** faire, et qui couvre vos besoins sans écrire de code :

| Votre besoin | Dans CleanYourTV | Réglage |
|---|---|---|
| Interface minimaliste, rapide, télécommande | Oui, en Java, avec seulement 2 dépendances. Validé sur ce boîtier | — |
| Français | Oui (`values-fr`) | automatique |
| Favoris (dock) | Oui | `dock_apps` : SFR TV, YouTube, Netflix, Prime Video, IPTV ×2 |
| Organiser et masquer des applis | Oui (réorganisation, bibliothèque d'applis) | `hidden_apps` |
| Bouton YouTube → YouTube | Oui (service de touches) | `app_keys` : code du bouton YouTube, **à relever sur votre build 773** |
| Boutons Netflix et Prime neutralisés | Oui | On ne les associe à rien : ils sont inactifs une fois Google TV désactivé |
| Pas d'entrée TV / tuner | Oui, la pastille Entrées se masque | `live_tv_package` vide |
| Paramètres accessibles | Oui (pastilles Paramètres, Wi-Fi, réglages rapides) | — |
| Écran de veille horloge | Oui (`ClockDream`) | Remplace l'écran de veille Google (`dreamx`) avant sa désactivation |
| Personnalisation | Thèmes, fonds d'écran, nombreux réglages | `themes/` + `mibox/config/` |

**L'identifiant reste `com.gathin.tvhome`**, car il est utilisé 14 fois par le script d'installation. Votre version est signée avec **votre** clé : elle ne peut donc pas être confondue avec une version compilée par quelqu'un d'autre.

## 4. Vos modifications (fork)

Chaque modification est faite dans un **commit séparé**, avec un réglage `config.xml` quand c'est possible. Le comportement d'origine reste intact quand le réglage est désactivé : c'est la règle de CleanYourTV, et elle facilite les fusions.

| # | Modification | Pourquoi CleanYourTV ne l'a pas |
|---|---|---|
| **F1** | **Configuration Mi Box** (`mibox/config/`) : favoris, applis masquées, bouton YouTube, pas d'entrée TV | Propre à votre usage |
| **F2** | **Signature release avec votre clé** (lue depuis des variables d'environnement ou des secrets GitHub) + numéros de version SemVer | CleanYourTV signe avec la clé de debug : chacun compile pour soi |
| **F3** | **Système de mise à jour depuis GitHub Releases** (détail §5) | Interdit dans CleanYourTV (« no self-updaters ») ; possible dans votre fork |
| **F4** | **Vérification du rôle HOME** : le launcher contrôle qu'il est toujours l'écran d'accueil par défaut et le signale sinon (risque R15 lié à SFR TV) | Absent de CleanYourTV |
| **F5** | **Workflow `release.yml`** : APK signé, SHA-256, release GitHub automatique sur tag | Interdit dans CleanYourTV (« no prebuilt APKs ») |
| F6 | Vos idées suivantes | — |

## 5. Système de mise à jour (F3)

Le flux et les règles de sécurité sont ceux déjà définis. Les adaptations au code Java de CleanYourTV sont les suivantes :

1. **Vérification** au démarrage du launcher (au plus une fois par heure) et **toutes les 24 h**, via l'API publique `releases`, en HTTPS et **sans jeton**.
2. **Comparaison SemVer**. Les versions ignorées par l'utilisateur ne sont plus proposées.
3. **Pastille en haut à droite** de l'accueil : version, résumé, [Mettre à jour] [Plus tard]. « Plus tard » la masque 24 h. La page « Mises à jour du launcher » (dernière ligne des réglages du launcher) regroupe l'historique, les notes, la progression, les erreurs et l'option « Ignorer cette version ».
4. **Contrôle de l'espace libre** avant le téléchargement : il ne reste qu'environ 870 Mo libres sur la box.
5. **Vérifications** : ① SHA-256 = fichier `.sha256` = `digest` de l'API ; ② package `com.gathin.tvhome` ; ③ `versionCode` supérieur ; ④ `versionName` = version de la release ; ⑤ **certificat de signature = celui du launcher installé**. L'empreinte de la clé est aussi épinglée dans `release.yml` et dans le script d'installation, qui refusent tout APK signé autrement. Si une seule échoue, le fichier est supprimé et rien n'est installé.
6. **Installation via `PackageInstaller`**, avec l'écran de confirmation d'Android (obligatoire sur Android 11).
7. **Une mise à jour du launcher ne désactive jamais rien** sur la box.

**Réalisation (Phase 3).** Tout le code du système de mise à jour est dans `launcher/mibox/` : un module Gradle à part, en Java, sans aucune bibliothèque. Il n'est ajouté au launcher que si l'on compile avec `-Pmibox`. Il s'accroche au launcher **sans modifier le code de CleanYourTV** : un `ContentProvider` démarre avec l'application, surveille l'écran d'accueil pour y afficher la pastille et ajoute la ligne « Mises à jour du launcher » aux réglages.

Conséquence : la compilation CleanYourTV normale ne change pas, à l'octet près (588 185 octets, sans permission `INTERNET`, sous la limite de 576 Ko). Les 2935 tests de CleanYourTV passent. L'édition Mi Box pèse 619 870 octets, avec les permissions `INTERNET`, `REQUEST_INSTALL_PACKAGES` et `RECEIVE_BOOT_COMPLETED`. Publication et sécurité : [05-mises-a-jour.md](05-mises-a-jour.md).

**Ajouts de la 1.1.0 (même méthode, sans toucher au code de CleanYourTV).**
- **Bouton Paramètres :** il ouvre les réglages du launcher, grâce à la clé `launcher_settings_button` de CleanYourTV, activée dans `launcher/mibox/app-res/`. Ce dossier contient les valeurs propres à l'édition Mi Box. La ligne « Paramètres du téléviseur » y devient « Paramètres de l'appareil » et reste la dernière. « Mises à jour du launcher » est ajoutée en tête, « Bluetooth » juste avant.
- **Pastille Bluetooth :** elle se place à droite de la pastille réseau. Elle affiche « Bluetooth », ou l'enceinte ou le casque connecté ; la télécommande, toujours connectée, n'est pas nommée. Un nom trop long défile 3 fois, puis s'arrête, pour ne pas redessiner l'écran sans fin.
- **Page Bluetooth :** elle se contente de **lire** l'état : appareils associés, type, connecté ou non. Associer, connecter et oublier un appareil passent par les écrans d'Android : « Associer un appareil » ouvre l'écran d'appairage de Google TV.
- **Wi-Fi :** CleanYourTV affiche déjà le nom du réseau en Wi-Fi, et « Ethernet » en filaire. Android 11 ne donne ce nom qu'avec la localisation activée ; elle est coupée sur la box, d'où « Wi-Fi ». Se connecter à un réseau depuis une appli ordinaire n'est plus possible depuis Android 10 : le bouton ouvre donc l'écran Wi-Fi d'Android.

**Écart assumé par rapport à votre cahier des charges : JobScheduler au lieu de WorkManager.**
- WorkManager est une bibliothèque écrite en Kotlin, qui ajouterait environ 1 Mo et la bibliothèque standard Kotlin à un launcher qui a aujourd'hui 2 dépendances.
- CleanYourTV impose une limite de taille d'APK, et la mémoire de la box est comptée.
- `JobScheduler` est l'API système **sur laquelle WorkManager s'appuie** sur Android 11. Le résultat est identique : tâche périodique de 24 h, contrainte réseau, survie aux redémarrages. Il n'y a aucune dépendance en plus.
- Si vous tenez à WorkManager, c'est faisable : dites-le-moi.

**Clé de signature** : générée sur le Mac (`keytool` du JDK 17), jamais dans le dépôt, copiée dans les secrets GitHub. **À sauvegarder hors du Mac** : sans elle, plus aucune mise à jour n'est possible sans désinstaller.

**Versions** : la première version publiée sera **1.0.0**, supérieure aux anciens tags Razorbill (v0.5 à v0.11). Ces anciens APK ont un autre package et une autre clé, et sont donc refusés par les vérifications ② et ④.

## 6. Récupérer les nouveautés de CleanYourTV

```bash
git fetch upstream
git merge upstream/main          # conflits possibles seulement sur vos fichiers modifiés (launcher/, CLAUDE.md, .gitignore)
```

On ne récupère **pas** automatiquement : chaque fusion est relue, car CleanYourTV évolue vite. Après une fusion, le launcher est recompilé et testé avant toute release.

## 7. Outils côté Mac

On utilise **les outils de CleanYourTV**, et non des scripts réécrits :

| Outil | Usage |
|---|---|
| `launcher/build-and-install.sh` | Compile et installe le launcher, puis le définit comme accueil. **Lancé avec `STOCK_LAUNCHER=none`** pour E1/E2, pour qu'il ne désactive pas Google TV lui-même |
| `mibox/scripts/install/install-launcher.sh` | Compile l'édition Mi Box signée, vérifie l'APK (version, clé, module présent), puis appelle `build-and-install.sh` (`SKIP_BUILD=1 STOCK_LAUNCHER=none`) et autorise le launcher à installer ses mises à jour. Sans `--execute`, ne touche pas la box |
| `tools/disable.sh mibox/lists/E3-…txt` | Applique **une** étape, avec tous ses garde-fous : enregistrement de restauration, protection de HOME, du Wi-Fi/setupwraith et du cœur Android |
| `tools/restore.sh` | Annule une étape à partir de son enregistrement |
| `tools/check.py` | Détecte une dérive (après une mise à jour firmware, par exemple) |
| `tools/tv.py` | Client ADB. On le fait passer par l'`adb` du Mac (`TV_ADB_BINARY=/opt/homebrew/bin/adb`), qui a déjà l'accès au réseau local |
| `mibox/scripts/audit/audit.sh` | Notre audit complet en lecture seule (conservé) |

Prérequis : un environnement Python pour `tools/` (`python3 -m venv tools/venv`), le JDK 17 (installé) et le SDK Android (Phase 3).

## 8. Étapes sur la box

| Étape | Contenu | Outil | Visible à l'écran ? |
|---|---|---|---|
| **E1+E2** | Installer le launcher (édition Mi Box 1.0.0, avec mises à jour), lui donner le rôle HOME, **sans** désactiver Google TV | `install-launcher.sh --execute` | Une tuile apparaît. L'accueil ne change pas : Google TV garde la priorité 2 |
| **E3** | `com.google.android.apps.tv.launcherx` | `disable.sh mibox/lists/E3-google-tv.txt` | **Oui : l'accueil change.** Créneau convenu |
| **E4** | `com.google.android.tungsten.setupwraith` | `disable.sh mibox/lists/E4-setup-wizard.txt` | Oui. Le Wi-Fi est à surveiller (risque réduit d'après l'audit) |
| E5 | Télémétrie (4 packages) | `disable.sh` | Non |
| E6 | Publicités et promotions Xiaomi (6 packages) | `disable.sh` | Non |
| E7 | Mises à jour Xiaomi (`com.xiaomi.mitv.updateservice`) | `disable.sh` | Non |
| E8 | Google TV Films, Play Jeux | `disable.sh` | Non |
| E9 | Bouton YouTube via le launcher, puis `partnercustomizer` | `build-and-install.sh` (`INPUT_KEY_SERVICE=1`) + `disable.sh` | Boutons de la télécommande |
| E10 | Écran de veille horloge, puis `dreamx` | réglage + `disable.sh` | Écran de veille |
| E11 *(option)* | Chromecast et Assistant | `disable.sh` | Bouton micro inactif |

**Différences avec la liste CleanYourTV d'origine** (`examples/xiaomi-mi-box-s-2nd-gen/debloat.txt`) :
- **Prime Video n'est pas désactivé**, puisque vous l'utilisez. Le reste de la liste est repris tel quel, découpé en étapes ;
- **Chromecast et Assistant sont ajoutés** en option (E11), puisque vous ne vous en servez pas.

Après chaque étape : tests du [plan de tests](02-plan-de-tests.md), votre validation, puis l'étape suivante. **Aucune étape visible n'est lancée sans créneau convenu**, la box étant utilisée par la famille.

## 9. Ce qui ne change pas

La liste rouge (Bluetooth, réseau, CEC/`droidlogic`, SFR TV, Play Services, WebView, clavier…), le plan de tests, les risques R1 à R18 et la stratégie de restauration de l'[audit](03-audit-mi-box.md) restent valables.
