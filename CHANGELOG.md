# Changelog

All notable changes to ArcadiaTweaks are documented here.

---

## [0.2.0] - 2026-10-26

### Fixed

- **Refined Storage: activeness changes applied up to 19 s late** — The S1 activeness coalescing only called `updateActiveness` every 20 ticks, but Refined Storage 2.0.9 debounces changes with a counter that only moves on calls, so every change after the first one (power back, redstone mode switched) waited up to about 380 ticks instead of 19 before importers and exporters reacted. The counter now advances by the ticks that were skipped, so a change lands at most `interval - 1` ticks away from plain Refined Storage. Measured in a GameTest: delays of 6 to 20 ticks with the fix, 386 ticks before it.
- **Simply Swords: no more free Runic Tablets (Arcadia bug #321)** — A unique weapon without the `simplyswords:awakening` component (weapons from before the awakening system, or handed out as a plain item by lootboxes, quests and commands) counts as level 8, and the Runic Forge turned those 8 implicit levels into 8 real Runic Tablets. The implicit level is now stored on the weapon as a lock the first time it enters the forge: the forge gives no tablet for it, the weapon keeps its power, its gems can still be changed, and the tablet slots covered by the lock refuse tablets. Weapons awakened with real tablets are untouched. Kill switch `[safety] runicForgeImplicitLevelLock`.
- **Waystones: "Visible to Team" follows FTB Teams** — Waystones resolved TEAM visibility from the vanilla scoreboard team, which the Arcadia tab list uses to sort players by rank, so every player of the same rank saw everyone else's team waystones. A TEAM waystone is now offered only to its owner and to the owner's FTB Teams party (allies excluded, fail closed while FTB Teams is not loaded). Kill switches `[modules] waystones` and `[waystones] team_visibility_uses_ftb_teams`.
- **Server freeze from runaway entity movement** — An entity whose velocity grew to absurd values (seen on Aether mobs, up to 1e20 blocks per tick) made `Entity.move` walk millions of blocks in the collision search and the watchdog killed the server. A movement that is NaN, infinite or above the limit on one axis now resets the entity's velocity and skips the move, with one rate-limited warning. Dedicated servers only, kill switch `[safety] entityMoveGuard`, limit `entityMoveGuardLimit` (100 blocks per tick).
- **The BotanyPots module switch did nothing** — `[modules] botany = false` left every BotanyPots strategy running. Each strategy now also requires the module switch, like the other modules.

### Performance

- **CosmeticWeapons and CosmeticArmours: item presence gate** — The 15 advancement procedures that test several items every player tick called `Inventory.contains` once per item, and every call scanned the inventory, Curios and Accessories. After the first lookup of a call, items that are not in the inventory are now answered without a new scan. Same answers and same advancements (822 placements, near misses and 500 random inventories compared with the gate on and off). Bench: 434 to 100 microseconds per player tick for the 26 procedures. A startup audit turns the gate off if an unknown Mixin hooks `Inventory.contains` or the ItemStack comparison. New module `cosmetic`, switch `[cosmetic] contains_presence_gate`.
- **Accessories: `contains` fast path and lighter entity tick** — Accessories' extension of `Inventory.contains(ItemStack)` can never match (its predicate tests `isEmpty()`), yet it scanned every accessory slot on each call; the scan is now skipped while every state-changing call is kept in order (1.6 to 0.5-0.7 microseconds per call). The per-entity Accessories tick allocates about 45% less (3976 to 2360 bytes per player tick). Both are pinned to Accessories 1.1.0-beta.54 by bytecode fingerprints and switch themselves off on any other build. New module `accessories`, switches `[accessories] contains_fast_path` and `tick_allocation_trimmed`.
- **Refined Storage: faster autosave** — Every autosave re-encoded every storage disk of the world as soon as one disk changed. Disks unchanged since the last save now reuse their encoded form (200 disks with 5% changed: 95 to 8 ms per save in the bench). Saved data is byte-identical with the cache on and off; every 20th save re-encodes and compares, and any difference turns the cache off for the session. Switches `storage_save_cache_enabled`, `storage_save_cache_verify_interval`.
- **Refined Storage: single component lookup and quieter debug log** — One hash lookup instead of two per network component access, and the "External storage has changed" and transmitter retry lines no longer flood `debug.log` from the tick loop. Switches `single_component_lookup_enabled`, `quiet_hot_debug_logs_enabled`.

### Changed

- **One published jar** — `arcadiatweaks-0.2.0.jar` carries every module; the single-module jars stay available as Gradle tasks for internal testing only.
- **Admin menu** — `/arcadia_tweaks gui` (renamed from `/arcadiatweaks gui`, `admin` stays an alias) is now a paged 9x6 menu: one row per module with its switch first, a safety guards row, the default value in every tooltip, and a two-click "Recommended defaults" reset.
- **Defaults** — Every optimization proven identical by the tests is on by default; BotanyPots A2 (light flag downgrade) stays off because it changes neighbour updates.
- **Built against the current Arcadia modpack** — Target mod versions in `gradle.properties` match the jars the modpack runs, so a plain `./gradlew build` works.
- **Cleaner logs** — The unused `refmap` entry is gone from every Mixin config (six warnings less on each start).
- **Tests** — 83 JUnit tests and 39 GameTests, including the real Runic Forge, FTB Teams parties, a Refined Storage network and its save, and Cosmetic and Accessories differential tests (patch on against patch off). A GameTest drives the admin menu with a FakePlayer.
- **Documentation** — README (English and French) lists every module, the `[safety]` section and the test commands; this changelog was added. Curveo added to the authors.

### Correctifs

- **Refined Storage : changements d'activité appliqués jusqu'à 19 s en retard** — La fusion S1 des vérifications d'activité n'appelait `updateActiveness` que toutes les 20 ticks, alors que Refined Storage 2.0.9 temporise les changements avec un compteur qui n'avance qu'à chaque appel : chaque changement après le premier (retour du courant, mode redstone modifié) attendait jusqu'à environ 380 ticks au lieu de 19 avant que les importeurs et exportateurs réagissent. Le compteur avance maintenant des ticks sautés, donc un changement arrive au plus `interval - 1` ticks de Refined Storage normal. Mesuré en GameTest : 6 à 20 ticks avec le correctif, 386 ticks avant.
- **Simply Swords : plus de Runic Tablets gratuites (bug Arcadia #321)** — Une arme unique sans le composant `simplyswords:awakening` (armes d'avant le système d'éveil, ou données comme simple objet par les lootboxes, les quêtes et les commandes) compte comme niveau 8, et la Runic Forge transformait ces 8 niveaux implicites en 8 vraies Runic Tablets. Le niveau implicite est maintenant enregistré sur l'arme comme un verrou la première fois qu'elle entre dans la forge : la forge ne donne aucune tablette pour lui, l'arme garde sa puissance, ses gemmes peuvent toujours être changées, et les emplacements couverts par le verrou refusent les tablettes. Les armes éveillées avec de vraies tablettes ne changent pas. Interrupteur `[safety] runicForgeImplicitLevelLock`.
- **Waystones : « Visible to Team » suit FTB Teams** — Waystones déterminait la visibilité TEAM à partir de l'équipe du scoreboard vanilla, que la tab list d'Arcadia utilise pour trier les joueurs par grade : tous les joueurs d'un même grade voyaient les waystones d'équipe des autres. Une waystone TEAM n'est maintenant proposée qu'à son propriétaire et à la partie FTB Teams du propriétaire (alliés exclus, rien n'est proposé tant que FTB Teams n'est pas chargé). Interrupteurs `[modules] waystones` et `[waystones] team_visibility_uses_ftb_teams`.
- **Gel du serveur par un mouvement d'entité incontrôlé** — Une entité dont la vitesse grimpait à des valeurs absurdes (vu sur des mobs de l'Aether, jusqu'à 1e20 blocs par tick) faisait parcourir des millions de blocs à `Entity.move` pendant la recherche de collisions, et le watchdog tuait le serveur. Un mouvement NaN, infini ou au-delà de la limite sur un axe remet maintenant la vitesse de l'entité à zéro et saute le déplacement, avec un avertissement limité dans le temps. Serveurs dédiés uniquement, interrupteur `[safety] entityMoveGuard`, limite `entityMoveGuardLimit` (100 blocs par tick).
- **L'interrupteur du module BotanyPots ne faisait rien** — `[modules] botany = false` laissait tourner toutes les stratégies BotanyPots. Chaque stratégie exige maintenant aussi l'interrupteur du module, comme les autres modules.

### Performance

- **CosmeticWeapons et CosmeticArmours : filtre de présence des objets** — Les 15 procédures de progrès qui testent plusieurs objets à chaque tick joueur appelaient `Inventory.contains` une fois par objet, et chaque appel parcourait l'inventaire, Curios et Accessories. Après la première recherche d'un appel, les objets absents de l'inventaire obtiennent maintenant leur réponse sans nouveau parcours. Mêmes réponses et mêmes progrès (822 placements, quasi-correspondances et 500 inventaires aléatoires comparés filtre activé et désactivé). Banc : 434 à 100 microsecondes par tick joueur pour les 26 procédures. Un audit au démarrage coupe le filtre si un Mixin inconnu modifie `Inventory.contains` ou la comparaison d'ItemStack. Nouveau module `cosmetic`, interrupteur `[cosmetic] contains_presence_gate`.
- **Accessories : raccourci de `contains` et tick d'entité allégé** — L'extension d'Accessories pour `Inventory.contains(ItemStack)` ne peut jamais correspondre (son prédicat teste `isEmpty()`), mais elle parcourait chaque emplacement d'accessoire à chaque appel ; ce parcours est maintenant sauté en gardant, dans l'ordre, chaque appel qui modifie un état (1,6 à 0,5-0,7 microseconde par appel). Le tick Accessories de chaque entité alloue environ 45 % de moins (3976 à 2360 octets par tick joueur). Les deux sont liés à Accessories 1.1.0-beta.54 par des empreintes de bytecode et se coupent seuls sur toute autre version. Nouveau module `accessories`, interrupteurs `[accessories] contains_fast_path` et `tick_allocation_trimmed`.
- **Refined Storage : sauvegarde automatique plus rapide** — Chaque sauvegarde automatique réencodait tous les disques de stockage du monde dès qu'un seul disque avait changé. Les disques inchangés depuis la dernière sauvegarde réutilisent maintenant leur forme encodée (200 disques dont 5 % modifiés : 95 à 8 ms par sauvegarde au banc). Les données sauvegardées sont identiques octet pour octet avec et sans le cache ; une sauvegarde sur 20 réencode et compare, et toute différence coupe le cache pour la session. Interrupteurs `storage_save_cache_enabled`, `storage_save_cache_verify_interval`.
- **Refined Storage : une seule recherche de composant et log debug allégé** — Une seule recherche dans la table au lieu de deux pour chaque accès à un composant du réseau, et les lignes « External storage has changed » et de nouvel essai des transmetteurs n'inondent plus `debug.log` depuis la boucle de tick. Interrupteurs `single_component_lookup_enabled`, `quiet_hot_debug_logs_enabled`.

### Modifications

- **Un seul jar publié** — `arcadiatweaks-0.2.0.jar` contient tous les modules ; les jars à module unique restent disponibles comme tâches Gradle pour les tests internes uniquement.
- **Menu d'administration** — `/arcadia_tweaks gui` (renommé depuis `/arcadiatweaks gui`, `admin` reste un alias) est maintenant un menu 9x6 en pages : une ligne par module avec son interrupteur en premier, une ligne de gardes-fous, la valeur par défaut dans chaque info-bulle, et un bouton « Réglages recommandés » à deux clics.
- **Valeurs par défaut** — Toute optimisation prouvée identique par les tests est activée par défaut ; BotanyPots A2 (rétrogradation des drapeaux de lumière) reste désactivée car elle change les mises à jour des blocs voisins.
- **Compilé contre le modpack Arcadia actuel** — Les versions des mods ciblés dans `gradle.properties` correspondent aux jars du modpack : un simple `./gradlew build` fonctionne.
- **Logs plus propres** — L'entrée `refmap` inutile a disparu de chaque config Mixin (six avertissements de moins à chaque démarrage).
- **Tests** — 83 tests JUnit et 39 GameTests, dont la vraie Runic Forge, les parties FTB Teams, un réseau Refined Storage et sa sauvegarde, et des tests comparatifs Cosmetic et Accessories (patch activé contre patch désactivé). Un GameTest manipule le menu d'administration avec un FakePlayer.
- **Documentation** — Le README (anglais et français) liste chaque module, la section `[safety]` et les commandes de test ; ce changelog a été ajouté. Curveo ajouté aux auteurs.

---

## [0.1.1] - 2026-05-18

### Added

- **Refined Storage module** — Coalesced network node and transmitter activeness checks.
- **Mekanism module** — Backoff for idle transmitter pull scans, network emit scans and Logistical Transporter idle path recalculations, plus an optional client-side safe rendering path for colored Logistical Transporters with a model cache guard.
- **Jar variants** — `all`, `botany`, `refinedstorage` and `mekanism`; the admin GUI command follows the selected variant.
- **Structural Mixin checks** — A changed target skips only the affected Mixin instead of crashing the whole jar.

### Ajouts

- **Module Refined Storage** — Regroupement des vérifications d'activité des nœuds et transmetteurs du réseau.
- **Module Mekanism** — Temporisation des scans d'aspiration des câbles inactifs, des scans d'émission des réseaux et des recalculs de chemin à vide des Logistical Transporters, plus un rendu client sûr et optionnel des Logistical Transporters colorés avec une garde sur le cache de modèles.
- **Variantes de jar** — `all`, `botany`, `refinedstorage` et `mekanism` ; la commande du menu d'administration suit la variante choisie.
- **Vérifications structurelles des Mixins** — Une cible modifiée ne saute que le Mixin concerné au lieu de faire planter tout le jar.

---

## [0.1.0] - 2026-04-25

### Added

- **BotanyPots module** — Server-side optimizations for BotanyPots, each behind its own kill switch: crop and soil match memoization (S1), tick coalescing with growth compensation (S2), hopper export backoff (S3), required growth ticks cache (A1) and an optional block update flag downgrade (A2).

### Ajouts

- **Module BotanyPots** — Optimisations côté serveur pour BotanyPots, chacune avec son propre interrupteur : mémorisation des correspondances culture et sol (S1), regroupement des ticks avec compensation de croissance (S2), temporisation des exports par entonnoir (S3), cache des ticks de croissance requis (A1) et rétrogradation optionnelle des drapeaux de mise à jour de bloc (A2).

---
