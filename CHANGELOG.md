# Changelog

All notable changes to ArcadiaTweaks are documented here.

---

## [0.2.0] - 2026-10-26

### Fixed

- **Simply Swords: no more free Runic Tablets (Arcadia bug #321)** — A unique weapon without the `simplyswords:awakening` component (weapons from before the awakening system, or handed out as a plain item by lootboxes, quests and commands) counts as level 8, and the Runic Forge turned those 8 implicit levels into 8 real Runic Tablets. The implicit level is now stored on the weapon as a lock the first time it enters the forge: the forge gives no tablet for it, the weapon keeps its power, its gems can still be changed, and the tablet slots covered by the lock refuse tablets. Weapons awakened with real tablets are untouched. New `simplyswords` Mixin config, guarded by `SimplySwordsMixinPlugin`, kill switch `[safety] runicForgeImplicitLevelLock`.
- **Waystones: "Visible to Team" follows FTB Teams** — Waystones resolved TEAM visibility from the vanilla scoreboard team, which the Arcadia tab list uses to sort players by rank, so every player of the same rank saw everyone else's team waystones. A TEAM waystone is now offered only to its owner and to the owner's FTB Teams party (allies excluded, fail closed while FTB Teams is not loaded). New `waystones` module and jar variant, kill switches `[modules] waystones` and `[waystones] team_visibility_uses_ftb_teams`.
- **Server freeze from runaway entity movement** — An entity whose velocity grew to absurd values (seen on Aether mobs, up to 1e20 blocks per tick) made `Entity.move` walk millions of blocks in the collision search and the watchdog killed the server. A movement that is NaN, infinite or above the limit on one axis now resets the entity's velocity and skips the move, with one rate-limited warning. Dedicated servers only, kill switch `[safety] entityMoveGuard`, limit `entityMoveGuardLimit` (100 blocks per tick).

### Changed

- **Built against the current Arcadia modpack** — Target mod versions in `gradle.properties` now match the jars the modpack runs (BotanyPots 21.1.44, Refined Storage 2.0.9, Waystones 21.1.46, Balm 21.0.66, FTB Library 2101.1.37, Simply Swords 1.70.2), so a plain `./gradlew build` works without command line overrides.
- **Admin command renamed** — The in-game admin command is now `/arcadia_tweaks gui` (was `/arcadiatweaks gui`); `admin` stays an alias of `gui`.
- **Admin GUI** — The menu has a fourth row for the Waystones module.
- **Tests** — JUnit coverage for the Waystones and Simply Swords Mixin targets and for the content of every jar variant.
- **Documentation** — README lists every module, the `[safety]` section and the test commands; this changelog was added. Curveo added to the authors.

### Correctifs

- **Simply Swords : plus de Runic Tablets gratuites (bug Arcadia #321)** — Une arme unique sans le composant `simplyswords:awakening` (armes d'avant le système d'éveil, ou données comme simple objet par les lootboxes, les quêtes et les commandes) compte comme niveau 8, et la Runic Forge transformait ces 8 niveaux implicites en 8 vraies Runic Tablets. Le niveau implicite est maintenant enregistré sur l'arme comme un verrou la première fois qu'elle entre dans la forge : la forge ne donne aucune tablette pour lui, l'arme garde sa puissance, ses gemmes peuvent toujours être changées, et les emplacements couverts par le verrou refusent les tablettes. Les armes éveillées avec de vraies tablettes ne changent pas. Nouvelle config Mixin `simplyswords`, protégée par `SimplySwordsMixinPlugin`, interrupteur `[safety] runicForgeImplicitLevelLock`.
- **Waystones : « Visible to Team » suit FTB Teams** — Waystones déterminait la visibilité TEAM à partir de l'équipe du scoreboard vanilla, que la tab list d'Arcadia utilise pour trier les joueurs par grade : tous les joueurs d'un même grade voyaient les waystones d'équipe des autres. Une waystone TEAM n'est maintenant proposée qu'à son propriétaire et à la partie FTB Teams du propriétaire (alliés exclus, rien n'est proposé tant que FTB Teams n'est pas chargé). Nouveau module et nouvelle variante de jar `waystones`, interrupteurs `[modules] waystones` et `[waystones] team_visibility_uses_ftb_teams`.
- **Gel du serveur par un mouvement d'entité incontrôlé** — Une entité dont la vitesse grimpait à des valeurs absurdes (vu sur des mobs de l'Aether, jusqu'à 1e20 blocs par tick) faisait parcourir des millions de blocs à `Entity.move` pendant la recherche de collisions, et le watchdog tuait le serveur. Un mouvement NaN, infini ou au-delà de la limite sur un axe remet maintenant la vitesse de l'entité à zéro et saute le déplacement, avec un avertissement limité dans le temps. Serveurs dédiés uniquement, interrupteur `[safety] entityMoveGuard`, limite `entityMoveGuardLimit` (100 blocs par tick).

### Modifications

- **Compilé contre le modpack Arcadia actuel** — Les versions des mods ciblés dans `gradle.properties` correspondent maintenant aux jars du modpack (BotanyPots 21.1.44, Refined Storage 2.0.9, Waystones 21.1.46, Balm 21.0.66, FTB Library 2101.1.37, Simply Swords 1.70.2) : un simple `./gradlew build` fonctionne sans paramètres en ligne de commande.
- **Commande d'administration renommée** — La commande en jeu est maintenant `/arcadia_tweaks gui` (avant `/arcadiatweaks gui`) ; `admin` reste un alias de `gui`.
- **Menu d'administration** — Le menu a une quatrième ligne pour le module Waystones.
- **Tests** — Tests JUnit pour les cibles Mixin de Waystones et de Simply Swords et pour le contenu de chaque variante de jar.
- **Documentation** — Le README liste chaque module, la section `[safety]` et les commandes de test ; ce changelog a été ajouté. Curveo ajouté aux auteurs.

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
