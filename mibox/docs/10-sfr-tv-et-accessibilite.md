# SFR TV et les services d'accessibilité de LaunchOS

## Le problème

Quand un service d'accessibilité de LaunchOS est actif, SFR TV (`com.sfr.androidtv.launcher`, 7.1.0) affiche au
lancement « L'interface n'est pas encore compatible avec le mode Accessibilité », et la touche OK de la télécommande
ne fait plus rien dedans. LaunchOS en a trois sur la box : `HomeKeyService`, `InputKeyService` (CleanYourTV) et
`RemoteKeysService` (boutons Netflix, YouTube, Prime Video et mise en veille du minuteur).

## La cause (lue dans le code de SFR TV 7.1.0)

L'activité dont héritent tous les écrans de SFR TV appelle `AccessibilityManager.isEnabled()` à chaque `onResume`, et
écoute les changements d'état. Si c'est vrai, elle affiche son message (canal `ACCESSIBILITY_ALERT`, bouton vers les
réglages d'accessibilité) ; si c'est faux, elle le retire. Elle ne regarde rien d'autre : ni quel service, ni son type,
ni `isAccessibilityTool`.

Sur Android 11, `isEnabled()` est vrai dès qu'un service est lié, même un service qui ne lit que des touches. Donc
`packageNames`, `isAccessibilityTool` (Android 12 et plus seulement) ou couper le filtrage des touches ne changent rien.
L'appli `com.sfr.androidtv.selfcare` 3.1.0 n'a pas ce contrôle.

Essai sur l'émulateur (Android TV 12) avec une appli sonde qui fait le même contrôle, pilotée par une télécommande HID
virtuelle : la touche OK arrive identique avec ou sans les trois services (mêmes indicateurs, même source, 1 à 2 ms de
plus). Ce n'est donc pas LaunchOS qui abîme la touche OK : c'est SFR TV en « mode accessibilité ».

Modifier l'APK de SFR TV a été écarté : il faudrait le re-signer, ce qui casse en général la lecture protégée (DRM),
et chaque mise à jour de SFR le remplacerait.

## La solution (LaunchOS 1.5.0, `A11yPause`)

- Liste `mibox_a11y_pause_packages` (`launcher/mibox/src/main/res/values/mibox_config.xml`) : `com.sfr.androidtv.launcher`.
  Vide, la fonction est coupée.
- `RemoteKeysService` reçoit les changements de fenêtre **de ces applis seulement** (`packageNames`). Quand SFR TV
  arrive devant, LaunchOS retire ses trois services de `enabled_accessibility_services` et les note. SFR TV reçoit
  « accessibilité coupée » et retire son message.
- Dès qu'un écran de LaunchOS revient devant (Accueil, Retour jusqu'à LaunchOS, réglages), les services notés sont
  remis.
- Minuteur de veille : l'écran de la dernière minute est un écran de LaunchOS, il remet donc les services (il en a
  besoin pour mettre la box en veille). Quand il se ferme, SFR TV revient devant et les services repartent ; sur
  l'émulateur, c'est le retour de la fenêtre de l'appli sonde qui les a retirés, aussitôt. En secours, l'écran les
  retire lui-même en se fermant (« Ajouter 30 min », Retour), ou 1,5 s après la mise en veille.
- Il faut la permission `WRITE_SECURE_SETTINGS`, accordée une fois par ADB :
  `pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS`. Sans elle, rien ne change (comme en 1.4.0).
  Depuis la 1.5.1, la permission est relue à chaque écran de LaunchOS : accordée après l'installation, elle compte dès
  le retour sur l'accueil (en 1.5.0, il fallait redémarrer le service des boutons ; c'est ce qui a été fait sur la box,
  en le retirant puis en le remettant dans `enabled_accessibility_services`).

## Ce que ça coûte

- Pendant que SFR TV est devant, les boutons Netflix, YouTube et Prime Video ne font rien.
- Si on passe de SFR TV à une autre appli sans repasser par LaunchOS, les services restent coupés jusqu'au prochain
  écran de LaunchOS.
- Le message de SFR TV peut apparaître une fraction de seconde : sur l'émulateur, l'appli sonde a vu « activée » puis
  « coupée » 30 ms plus tard quand elle tournait déjà, et directement « coupée » quand elle démarrait.

## Revenir en arrière

`pm revoke com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS` coupe la fonction sans réinstaller. Si les
services étaient retirés à ce moment-là, les remettre dans Accessibilité, ou :
`settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService:com.gathin.tvhome/com.gathin.tvhome.mibox.RemoteKeysService:com.gathin.tvhome/com.gathin.tvhome.InputKeyService`
puis `settings put secure accessibility_enabled 1`.

Tests sur la box : section « C terdecies » de [02-plan-de-tests.md](02-plan-de-tests.md).
