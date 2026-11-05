# Phase 2 — Conception et architecture

Statut : **proposition à valider**. Rien n'est développé ni installé.
Appareil cible : Xiaomi Mi Box S 2e gén. (`MiTV-AFKR0`, Android 11 / API 30, espace utilisateur 32 bits, 2 Go de RAM) — voir [03-audit-mi-box.md](03-audit-mi-box.md).

---

## 1. Principes

1. **Deux mondes séparés.** D'un côté le **launcher** : une application Android ordinaire, mise à jour depuis GitHub. De l'autre les **modifications système** : des scripts ADB exécutés depuis le Mac, une étape à la fois. Une mise à jour du launcher ne désactive jamais rien.
2. **Réversible à chaque étape.** Avant chaque modification, un instantané est pris et un script de restauration est prêt. Rien n'est désinstallé côté système.
3. **Sobre.** 2 Go de RAM, environ 870 Mo de stockage libre et un processeur Cortex-A55 : pas de framework lourd, pas d'images 4K en mémoire.
4. **Rien de personnel dans le dépôt public** : ni IP, ni nom de réseau Wi-Fi, ni adresse MAC, ni numéro de série. Les données d'audit restent dans `audits/`, ignoré par Git.
5. **Aucune action visible à l'écran sans créneau convenu**, puisque la box est utilisée par la famille.

---

## 2. Choix techniques du launcher

| Sujet | Choix | Justification |
|---|---|---|
| Langage | **Kotlin** | Standard Android actuel, concis, null-safety |
| Interface | **Vues Android classiques + RecyclerView**, sans Jetpack Compose | Démarrage à froid plus rapide et environ 20 à 30 Mo de RAM en moins que Compose sur un A55 32 bits. C'est l'approche éprouvée par CleanYourTV sur ce même boîtier |
| Bibliothèque Leanback | **Non** | Dépréciée par Google, lourde, rigide |
| minSdk / targetSdk | **30 / 35** | Un seul appareil cible (Android 11). targetSdk récent pour les bonnes pratiques de sécurité |
| Réseau | `HttpURLConnection` + `org.json` (intégrés à Android) | Aucune dépendance réseau tierce, donc moins de surface d'attaque et un APK plus petit |
| Tâche périodique | **WorkManager** (votre demande) | Vérification toutes les 24 h, avec contrainte « réseau disponible » |
| Bibliothèques autorisées | AndroidX `recyclerview`, `work-runtime`, `core-ktx` | Liste fermée, toute ajout est justifié dans le CHANGELOG |
| Code natif | **Aucun** | Évite toute incompatibilité 32/64 bits |
| Budget | APK de moins de 3 Mo, **moins de 40 Mo de RAM** au premier plan, affichage en moins d'une seconde à froid | Vérifiés en Phase 4 avec `dumpsys meminfo` |
| Identifiant d'application | `io.github.miboxlauncher` | Nom neutre, sans donnée personnelle, différent de Razorbill |

## 3. Architecture du dépôt

```
mibox-launcher/
├── app/                          # Le launcher (application Android)
│   └── src/main/java/io/github/miboxlauncher/
│       ├── home/                 # Écran d'accueil, rangée de favoris, grille, horloge
│       ├── apps/                 # Catalogue des applis, tri, masquage, favoris
│       ├── keys/                 # Service de touches de la télécommande (YouTube oui, Netflix/Prime non)
│       ├── health/               # Vérifie que le launcher détient le rôle HOME, signale une dérive
│       ├── settings/             # Réglages du launcher + page Maintenance
│       └── update/               # Interface des mises à jour (bandeau, page, historique)
├── updater/                      # Module Android séparé, testable seul
│   └── src/main/java/io/github/miboxlauncher/updater/
│       ├── github/               # Client de l'API GitHub Releases (lecture seule, sans jeton)
│       ├── version/              # Comparaison SemVer, versions ignorées
│       ├── download/             # Téléchargement, reprise, contrôle de l'espace libre
│       ├── verify/               # SHA-256 + certificat de signature épinglé
│       └── install/              # PackageInstaller (confirmation par l'utilisateur)
├── scripts/                      # Côté Mac, bash, sans dépendance hors adb
│   ├── lib/common.sh             # Connexion, garde-fous, journal, confirmation
│   ├── audit/                    # Audits en lecture seule (existant)
│   ├── backup/snapshot.sh        # Instantané avant chaque étape
│   ├── install/                  # Installation du launcher, rôle HOME
│   ├── steps/apply-step.sh       # Applique UNE étape de lists/
│   ├── restore/                  # Restauration d'une étape ou complète
│   └── maintenance/              # Diagnostic, vérification de dérive, Razorbill (fait)
├── lists/                        # Une liste de packages par étape (E3, E4, …), commentée
├── compatibility/                # Résultats de tests datés (copies remplies du plan)
├── docs/                         # Rapports et documentation
├── .github/workflows/            # ci.yml (vérifications), release.yml (APK signé)
├── README.md · CHANGELOG.md · LICENSE (MIT) · NOTICE (crédits CleanYourTV)
```

Les **scripts et listes de packages** évoluent indépendamment du launcher : leurs changements ne déclenchent pas de mise à jour sur la box.

## 4. Fonctionnement du launcher

### 4.1 Écran d'accueil (v1.0)

- En haut : une **horloge** et, en haut à droite, la **pastille de mise à jour** quand une nouvelle version est disponible.
- **Rangée de favoris** : épinglés et réorganisables à la télécommande (appui long → Déplacer / Retirer). Par défaut : SFR TV, YouTube, Netflix, Prime Video, IPTV.
- **Toutes les applis** : grille des applications TV (intent `LEANBACK_LAUNCHER`, avec bannières) et des applications Android classiques. Possibilité de masquer une appli.
- **Tuiles fixes** : Paramètres Android, Wi-Fi, Bluetooth (pour la JBL), Maintenance.
- **Personnalisation** : fond (couleur unie ou image locale limitée à 1920 px), taille des tuiles, ordre. Les réglages sont stockés en local, sans compte ni cloud.

### 4.2 Bouton Home, Retour, plantages

| Situation | Comportement |
|---|---|
| Home depuis n'importe quelle appli | Android ouvre le détenteur du rôle HOME : votre launcher, une fois les étapes E3 et E4 faites (voir l'audit §2.1) |
| Retour sur l'accueil | **Sans effet** (ou ferme un menu ouvert). Il n'existe aucun écran « derrière » un launcher, donc pas de retour vers Google TV possible |
| Retour répété dans une appli | Ramène à l'accueil du launcher |
| Le launcher plante | Android le relance au prochain appui sur Home (activité `singleTask`, `stateNotNeeded`) |
| Le launcher plante en boucle | Android propose les autres candidats HOME : **SFR TV** et l'écran de secours des Paramètres. Il reste aussi ADB et le script de restauration |
| Une mise à jour de SFR TV réinitialise le choix d'accueil (R15) | Le module `health/` le détecte au prochain affichage et propose de rétablir le launcher. Android demande lui-même la confirmation |

**Accès technicien / maintenance** : une tuile « Maintenance », protégée par une séquence de touches (par exemple ↑ ↑ ↓ ↓ OK) pour éviter les manipulations accidentelles par la famille. Elle affiche :
- la version, le rôle HOME, l'état d'ADB, l'espace libre et la RAM ;
- des raccourcis vers Paramètres → Applications et vers les options développeur ;
- un lien « Ouvrir Google TV » si launcherx est encore activé ;
- les instructions de restauration.

Rien n'empêche d'accéder aux Paramètres Android, et **aucun mécanisme de sécurité Android n'est contourné**.

### 4.3 Boutons de la télécommande

D'après CleanYourTV, une fois `launcherx` désactivé, **tous** les boutons raccourcis cessent de fonctionner. Le launcher fournit alors un **service d'accessibilité minimal** (`keys/`) :

- **YouTube → ouvre YouTube** ;
- **Netflix, Prime → interceptés, sans effet**, comme vous le souhaitez ;
- les touches de volume, muet et lecture ne sont **jamais** interceptées ;
- aucune frappe n'est enregistrée dans les journaux (pas de keylogger) ;
- le service ne lit pas le contenu des fenêtres (`canRetrieveWindowContent=false`).

Les codes exacts des boutons sur le build 773 restent à relever. C'est rapide (`getevent`), mais il faut appuyer sur les boutons : cela se fera pendant un créneau avec vous. Le paquet `partnercustomizer` (gestion d'origine des raccourcis) ne sera désactivé qu'une fois le service validé, pour éviter un double déclenchement.

### 4.4 Écran de veille

L'écran de veille actuel est celui de Google TV (`dreamx`, environ 77 Mo de RAM). Le launcher fournira un **écran de veille horloge** léger (`DreamService`). Une étape dédiée basculera le réglage vers lui, **avant** de pouvoir désactiver `dreamx`.

## 5. Système de mise à jour

### 5.1 Flux

```
Démarrage du launcher ─┐
WorkManager (24 h) ────┴─▶ GET api.github.com/repos/89hr474hht-rgb/mibox-launcher/releases/latest
                           (HTTPS, sans jeton, ≤ 60 requêtes/h autorisées : on en fait 1 à 2 par jour)
        │
        ▼
  tag vX.Y.Z > version installée ? non ──▶ rien
  version ignorée par l'utilisateur ?  oui ─▶ rien
        │ oui
        ▼
  Pastille en haut à droite : « Nouvelle version X.Y.Z » + résumé + [Mettre à jour] [Plus tard]
        │ Mettre à jour
        ▼
  Espace libre ≥ 2 × taille APK + 100 Mo ? non ──▶ message d'erreur clair
        │
        ▼
  Téléchargement (barre de progression) depuis l'URL de l'asset de CETTE release uniquement
        │
        ▼
  ① SHA-256 du fichier = fichier .sha256 publié = champ « digest » renvoyé par l'API GitHub
  ② nom de package = io.github.miboxlauncher
  ③ versionCode > versionCode installé
  ④ empreinte du certificat de signature = empreinte ÉPINGLÉE dans le launcher
        │ une seule vérification échoue ──▶ fichier supprimé, message « Mise à jour refusée : intégrité non vérifiée »
        ▼
  PackageInstaller (session officielle) ──▶ écran de confirmation Android ──▶ installation
        │
        ▼
  Le launcher redémarre (MY_PACKAGE_REPLACED) ──▶ « Mis à jour en X.Y.Z » + notes de version
```

### 5.2 Points de sécurité

- **Aucun jeton GitHub** dans l'application : l'API publique suffit pour un dépôt public.
- **La vérification ④ est la véritable protection.** Le SHA-256 ne prouve que l'intégrité du téléchargement, car il vient de la même source que l'APK. Le certificat épinglé prouve que **c'est bien vous** qui avez signé l'APK. Android refuse de toute façon une mise à jour signée par une autre clé.
- **Installation jamais silencieuse.** Sur Android 11 c'est techniquement impossible pour une appli ordinaire, et c'est voulu.
- **Permission « Installer des applis inconnues »** pour le launcher : accordée une seule fois, par vous, dans les Paramètres, ou par ADB pendant l'installation initiale, avec votre accord.
- **Clé de signature** : générée sur le Mac, **jamais dans le dépôt**. Elle est copiée, encodée en base64, dans les **secrets GitHub** pour la compilation des releases. **Vous devrez en garder une sauvegarde hors du Mac** (clé USB, gestionnaire de mots de passe). Si la clé est perdue, plus aucune mise à jour n'est possible sans désinstaller le launcher.
- **Risque R9** (la box pendant l'installation de la mise à jour) : le processus HOME est tué pendant quelques secondes. Le test MAJ-05 vérifie qu'Android revient bien sur le launcher mis à jour, et non sur SFR TV ou sur un sélecteur.

### 5.3 Interface

- **Pastille** en haut à droite : version, 2 ou 3 lignes de nouveautés, [Mettre à jour] [Plus tard].
- **Page « Mises à jour »** dans les réglages : version installée, dernière vérification, bouton « Vérifier maintenant », historique des versions et notes, option « Ignorer cette version », progression du téléchargement et message d'échec détaillé.

### 5.4 Versions et releases

- **SemVer** : `MAJEUR.MINEUR.CORRECTIF`. `versionCode = MAJEUR×10000 + MINEUR×100 + CORRECTIF`.
- **Première version : 1.0.0.** Les anciens tags Razorbill (v0.5 à v0.11) restent en place, mais 1.0.0 est supérieur à tous. Ces anciens APK ont un autre nom de package et une autre clé : la vérification ② ou ④ les refuserait de toute façon.
- **Chaque release** contient :
  - `mibox-launcher-X.Y.Z.apk` ;
  - `mibox-launcher-X.Y.Z.apk.sha256` ;
  - des notes tirées du `CHANGELOG.md` (nouveautés, corrections, éventuels changements de permissions) ;
  - la date (automatique) et l'empreinte SHA-256 dans le texte.

## 6. GitHub Actions

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `ci.yml` | Chaque push ou PR | Compilation debug, tests unitaires (comparaison de versions, vérification d'intégrité), lint Android, `shellcheck` des scripts, **recherche de données personnelles** (adresses IP privées, adresses MAC, adresses e-mail, mots-clés interdits) qui bloque le commit fautif |
| `release.yml` | Tag `vX.Y.Z` | Compilation release, signature avec la clé issue des secrets, contrôle que la version du tag = version de l'APK, calcul du SHA-256, création de la release avec les notes du CHANGELOG |

## 7. Scripts côté Mac et stratégie de restauration

Tous les scripts partagent `scripts/lib/common.sh`. Ce fichier assure :
- la connexion à la box et la vérification du modèle (`jaws`) ;
- la protection contre la lecture de l'entrée standard par `adb` (bug corrigé lors de la désinstallation de Razorbill) ;
- le journal horodaté dans `audits/` ;
- la confirmation par mot-clé.

| Script | Rôle | Modifie la box ? |
|---|---|---|
| `audit/audit.sh` | Audit complet | Non |
| `backup/snapshot.sh` | Instantané : état de chaque package, détenteur du rôle HOME, préférence HOME, écran de veille, services d'accessibilité, réglages. Écrit en JSON | Non |
| `install/install-launcher.sh` | `adb install`, puis autorisations nécessaires, puis vérification | Oui (après accord) |
| `steps/apply-step.sh <étape>` | Instantané, puis garde-fous (repris de CleanYourTV), puis désactivation de la liste de l'étape **uniquement**, puis relecture | Oui (après accord) |
| `restore/restore-step.sh <étape>` | Rejoue l'instantané de cette étape | Oui (après accord) |
| `restore/restore-all.sh` | Réactive tout ce que le projet a désactivé et rend HOME à Google TV | Oui (après accord) |
| `maintenance/check.sh` | Compare la box aux étapes appliquées (dérive, rôle HOME) | Non |

**Garde-fous repris de CleanYourTV**, sous licence MIT et crédités dans `NOTICE` :
- refus de toute ligne qui n'est pas un nom de package ;
- refus des packages du cœur d'Android ;
- refus de désactiver l'accueil actuel s'il n'y a pas de remplaçant ;
- vérification du propriétaire du réseau Wi-Fi avant `setupwraith`.

**Ajouts propres à ce projet** :
- **liste rouge** : Bluetooth, réseau, CEC, `droidlogic`, SFR TV, Play Services, WebView, clavier. Ces packages sont refusés même s'ils figurent par erreur dans une liste ;
- une seule étape par exécution.

**Restauration** : la restauration fonctionne **indépendamment du launcher**, par ADB depuis le Mac. En dernier recours, une réinitialisation d'usine remet la box d'origine, mais efface comptes et applis. Il n'est pas possible de sauvegarder le firmware ni les données privées des applis sans root.

## 8. Étapes sur la box (Phase 5), dans l'ordre

| Étape | Contenu | Visible à l'écran ? | Prérequis |
|---|---|---|---|
| **E1** | Installer le launcher (pas encore accueil) + autorisations | Une nouvelle tuile apparaît | Phase 4 réussie sur la box |
| **E2** | Rôle HOME attribué au launcher | Non : Google TV garde la priorité 2 (audit §2.1) | E1 |
| **E3** | Désactiver `launcherx` | **Oui : l'accueil change** | E2, créneau convenu |
| **E4** | Désactiver `setupwraith` | Oui (Wi-Fi à surveiller) | E3 stable pendant 24 h |
| E5 | Télémétrie : `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.partnersetup` | Non | E4 stable |
| E6 | Publicités Xiaomi : `com.mitv.tvhome.oemtab`, `.michannel`, `.mitvplus`, `.atv`, `com.xm.webcontent`, `android.autoinstalls.config.xioami.mibox3` | Non | E5 |
| E7 | Mises à jour Xiaomi : `com.xiaomi.mitv.updateservice` (votre décision) | Non | E6 |
| E8 | Préinstallées inutiles : `com.google.android.videos`, `com.google.android.play.games` | Non | E7 |
| E9 | Touches : service de touches actif, puis `partnercustomizer` désactivé | Boutons de la télécommande | Codes relevés avec vous |
| E10 | Écran de veille du launcher, puis `dreamx` désactivé | Écran de veille | E1 |
| E11 *(option)* | Chromecast (`mediashell`), Assistant (`katniss`) | Le bouton micro ne fera plus rien | À décider après une semaine d'usage |

Après chaque étape : campagne de tests du [plan de tests](02-plan-de-tests.md), puis votre validation, puis l'étape suivante.

## 9. Composants à conserver absolument (liste rouge)

`android`, `com.android.systemui`, `com.android.shell`, `com.android.providers.*`, `com.android.tv.settings` (+ overlays), `com.droidlogic*`, `mitv.service`, `com.xiaomi.gtv.settings`, `com.android.bluetooth`, `com.mi.bluetooth`, `com.android.inputdevices`, `com.android.networkstack.*`, `com.android.wifi.resources`, `com.android.captiveportallogin`, `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending`, `com.google.android.webview`, `com.google.android.packageinstaller`, `com.google.android.permissioncontroller`, `com.google.android.ext.*`, `com.google.android.modulemetadata`, `com.google.android.inputmethod.latin`, `com.google.android.tv.remote.service`, `com.android.externalstorage`, `com.android.mtp`, `com.sfr.androidtv.launcher`, `com.sfr.androidtv.selfcare`, `com.netflix.ninja`, `com.google.android.youtube.tv`, `com.google.android.youtube.tvmusic`, `com.amazon.amazonvideo.livingroom`.

## 10. Tests ajoutés pour le launcher et les mises à jour (Phase 4)

| ID | Test |
|---|---|
| LAU-01 | Démarrage à froid en moins d'une seconde, moins de 40 Mo de RAM (`dumpsys meminfo`) |
| LAU-02 | Navigation complète à la télécommande Xiaomi **et** Samsung (CEC) |
| LAU-03 | Toutes les applis installées listées, bannières correctes |
| LAU-04 | Favoris : ajout, déplacement, retrait, conservés après redémarrage |
| LAU-05 | Bouton YouTube → YouTube. Netflix et Prime → rien. Volume intact |
| MAJ-01 | Pas de nouvelle version → aucune pastille |
| MAJ-02 | Nouvelle version → pastille, notes, Plus tard, Ignorer |
| MAJ-03 | APK altéré (SHA-256 faux) → refusé, fichier supprimé |
| MAJ-04 | APK signé par une autre clé → refusé |
| MAJ-05 | Mise à jour réelle → confirmation Android, installation, retour sur le launcher (pas SFR TV) |
| MAJ-06 | Pas de réseau ou espace insuffisant → message clair, aucune boucle |
| MAJ-07 | Vérification périodique WorkManager exécutée (`dumpsys jobscheduler`) |

## 11. Points à valider avant la Phase 3

1. Interface en **vues classiques** (légère) plutôt qu'en Compose ?
2. Identifiant d'application `io.github.miboxlauncher` ?
3. Licence **MIT** pour le dépôt public ?
4. Favoris par défaut : SFR TV, YouTube, Netflix, Prime Video, IPTV Smarters, IronTV Max ?
5. Séquence de touches pour la page Maintenance (↑ ↑ ↓ ↓ OK) ?
6. Vous acceptez de **conserver une sauvegarde de la clé de signature** hors du Mac ?
