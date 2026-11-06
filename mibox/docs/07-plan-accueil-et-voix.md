# Plan : accueil sans Google TV (E4, E3) et sans commande vocale (E11)

Préparé le 6 novembre 2026, puis **appliqué le même jour** : E4 et E3 vers 11 h 55, E11 vers 12 h 30, suivis
d'un redémarrage réussi. Les résultats sont dans [02-plan-de-tests.md](02-plan-de-tests.md), sections C septies
et C octies.

## 1. Ce qu'on veut

| Aujourd'hui | Après le plan |
|---|---|
| Le bouton rond (Home) ramène à l'accueil Google TV | Il ramène à votre launcher |
| Le bouton micro lance l'Assistant Google | Il ne fait plus rien |

Votre launcher détient déjà le rôle HOME, mais deux applis Google passent devant lui : l'accueil
Google TV (`launcherx`, priorité 2) et l'assistant de configuration (`setupwraith`, priorité 1, son écran
`RecoveryActivity`). Tant qu'elles sont actives, Home va chez elles. Il suffit de désactiver ces deux-là ;
**rien d'autre n'est nécessaire pour le bouton rond**.

Tout est **désactivé pour l'utilisateur 0**, jamais supprimé : `tools/restore.sh` remet chaque étape en
place.

## 2. Ce qui est déjà connu

D'après l'audit du 5 novembre (build 773), à reconfirmer le jour J par le contrôle de la section 4 :

- applis qui répondent à Home : `launcherx` (2), `setupwraith` (1), SFR TV (0), `FallbackHome` des
  réglages (−1000). Razorbill a été désinstallé depuis ;
- les 4 réseaux Wi-Fi enregistrés appartiennent au système (`android.uid.system`), aucun à
  `setupwraith` : désactiver l'assistant ne supprimera pas votre Wi-Fi ;
- depuis le 6 novembre la box est **en Ethernet**, ce qui écarte le principal risque de E4 (la perte du
  Wi-Fi, donc d'ADB) ;
- la voix passe par `com.google.android.katniss` : c'est l'Assistant, la recherche vocale et le service
  de reconnaissance vocale d'Android.

Deux Mi Box S 2e gén. identiques (builds 737 et 772) ont fait exactement ces changements dans CleanYourTV
(`examples/xiaomi-mi-box-s-2nd-gen/debloat.txt`) : Home et le réseau reviennent seuls après un
redémarrage, Bluetooth et télécommande fonctionnent.

## 3. Ce qui change pour la famille

| Fonction | Après E4 + E3 | Après E11 |
|---|---|---|
| Bouton rond (Home) | Votre launcher | — |
| Applis, Play Store, SFR TV, Netflix, YouTube | Inchangés | Inchangés |
| Télécommande (navigation, OK, retour, volume), JBL, CEC | Inchangés | Inchangés |
| Boutons **Netflix, YouTube, Prime, Apps** de la télécommande | **Ne font plus rien** : c'est l'accueil Google TV qui les gérait. Le bouton YouTube revient à l'étape E9 | — |
| Bouton **micro** | L'Assistant peut refuser ou échouer sans l'accueil Google TV | **Ne fait plus rien** |
| Micro **dans les applis** (loupe vocale de YouTube) | Inchangé | Ne marche plus ; la saisie au clavier reste |
| Recommandations, liste « À regarder », notifications de Google TV | Disparaissent avec l'accueil Google TV | — |
| Paramètres → Comptes | Peut apparaître vide (la page est dans l'accueil Google TV). Pour changer de compte : réactiver `launcherx`, changer, désactiver | — |
| Chromecast | Toujours là (vous ne vous en servez pas ; option E12) | — |

Ce qui reste protégé quoi qu'il arrive : la liste rouge (`mibox/AGENTS.md`), dont
`com.google.android.tv.remote.service`, qui transporte le micro de la télécommande et l'appli
télécommande du téléphone. Le script vérifie aussi qu'elle est active.

## 4. Le jour J (environ 20 minutes, télé libre)

Toutes les commandes partent de `/Volumes/SSD-H/Launcher`. `<box>` est l'adresse IP de la box (Paramètres →
Réseau, ou le menu réseau du launcher 1.2.0).

**0. Connexion**, depuis Terminal.app (le Mac bloque ADB lancé par Claude) :

```sh
adb connect <box>:5555
export TV_HOST=<box> TV_ADB_BINARY=/opt/homebrew/bin/adb
```

**1. Contrôle en lecture seule.** Il ne change rien, ne regarde pas ce qui passe à l'écran et
n'affiche ni nom Wi-Fi ni adresse :

```sh
mibox/scripts/audit/precheck-accueil.sh <box>
```

Attendu : build 773, rôle HOME = `com.gathin.tvhome`, aucun réseau Wi-Fi à `setupwraith`, liste rouge
active, « 0 point à regarder ». Sinon, on s'arrête et on regarde.

**2. E4, l'assistant de configuration.** Rien ne change à l'écran : l'accueil Google TV garde Home.

```sh
tools/disable.sh mibox/lists/E4-setup-wizard.txt
```

Vérifier : `pm list packages -d` contient `setupwraith`, la box répond toujours (Ethernet).

Si vous avez supprimé les réseaux Wi-Fi en passant à l'Ethernet, le contrôle affiche « aucun réseau Wi-Fi
enregistré lisible » et `disable.sh` saute `setupwraith` par prudence (« could not check which app owns
the saved Wi-Fi networks »). C'est le cas prévu par le script pour une box en Ethernet : relancer alors,
avec votre accord, `FORCE_SETUPWRAITH=1 tools/disable.sh mibox/lists/E4-setup-wizard.txt`, puis
`unset FORCE_SETUPWRAITH`.

**3. E3, l'accueil Google TV**, juste après :

```sh
tools/disable.sh mibox/lists/E3-google-tv.txt
tools/venv/bin/python tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

Attendu : la dernière ligne est `com.gathin.tvhome/.MainActivity`.

L'ordre est imposé : tant que `setupwraith` est actif, `disable.sh` refuse de désactiver l'accueil Google
TV, sinon Home tomberait sur l'écran noir de l'assistant, qui peut faire boucler Android
(`docs/TROUBLESHOOTING.md`).

**4. Vos tests, avec la vraie télécommande** (identifiants de `02-plan-de-tests.md`) :

| Test | Quoi |
|---|---|
| RC-01, RC-02 | Home depuis une appli, puis 10 × Retour : toujours votre launcher, jamais Google TV |
| SFR-02, SFR-05, SFR-09 | SFR TV s'ouvre, Home et Retour depuis SFR TV reviennent à votre launcher |
| BT-02, BT-04, VOL-01 | Télécommande, son sur la JBL, volume |
| CEC-02, CEC-03 | Télécommande Samsung, allumage groupé |
| NET-01, SYS-05 | Internet, Paramètres d'Android accessibles depuis le launcher |

**5. Redémarrage**, si les tests sont bons et avec votre accord :

```sh
tools/venv/bin/python tools/tv.py --reboot
```

Après : Home = votre launcher (SYS-01), Ethernet revenu, télécommande et JBL reconnectées, SFR TV ne prend
pas la main (SFR-06, SFR-07), et `mibox/scripts/audit/precheck-accueil.sh` montre toujours les mêmes
paquets désactivés. Sur une autre Mi Box (772), le firmware avait réactivé une appli au redémarrage.

**6. E11, la commande vocale**, une fois E3 et E4 validés :

```sh
tools/disable.sh mibox/lists/E11-assistant.txt
```

Test : un appui sur le micro ne fait plus rien, et le reste de la télécommande répond normalement.

## 5. Revenir en arrière

Une commande par étape, dans l'ordre inverse. Chaque `restore.sh` relit l'état enregistré par
`disable.sh`.

```sh
tools/restore.sh mibox/lists/E11-assistant.txt
tools/restore.sh mibox/lists/E3-google-tv.txt com.google.android.apps.tv.launcherx
tools/restore.sh mibox/lists/E4-setup-wizard.txt
```

Si l'accueil Google TV doit revenir en urgence (écran noir après Home, par exemple) : la deuxième commande
suffit, Google TV reprend Home tout de suite. Avant une réinitialisation d'usine, toujours réactiver
`setupwraith`.

## 6. Ensuite

- **E9** : remettre le bouton YouTube via le launcher (code 190 sur les builds 737/772, à confirmer sur le
  773 avec la vraie télécommande), Netflix et Prime laissés sans action comme vous l'avez choisi.
- **E10** : écran de veille horloge, puis désactivation de `dreamx`.
- **E5 à E8** : télémétrie, publicités Xiaomi, mises à jour Xiaomi, applis préinstallées. Invisibles à
  l'écran.
- À la fin : fermer ADB sur la box. Les mises à jour du launcher passent par GitHub, pas par ADB.

## Sources

- CleanYourTV, listes validées sur Mi Box S 2e gén. : `examples/xiaomi-mi-box-s-2nd-gen/` (737, 772, 683).
- CleanYourTV, `docs/TROUBLESHOOTING.md` : Assistant sans `launcherx`, page Comptes vide, rôle de
  `tv.remote.service` pour le micro.
- Retours d'utilisateurs Mi Box 2e gén. avec un autre launcher :
  [r/MiBox, Projectivy](https://reddit.birdcat.cafe/r/MiBox/comments/1ivxcns/mi_box_with_projectivy_launcher/mece3s1/?context=3) ;
  le bouton YouTube cassé après désactivation de l'accueil d'origine est aussi signalé sur
  [r/AndroidTV](https://pol1.lr.ggtyler.dev/r/AndroidTV/comments/jnxlmw/guidenatively_use_custom_launcher_on_chromecast/gle5toc/?context=3).
- Micro de la télécommande inactif quand l'appli Google est désactivée :
  [Android Central](https://forums.androidcentral.com/threads/tv-microphone-issue-when-disabling-google-app.1062689).
