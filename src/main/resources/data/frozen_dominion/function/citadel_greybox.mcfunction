# FROZEN CITADEL - grey box. Run on a superflat with deep stone (see export.py).
gamerule doMobSpawning false
effect give @s minecraft:night_vision infinite 0 true
place template frozen_dominion:citadel/gb_0_3 ~-120 ~-116 ~24
place template frozen_dominion:citadel/gb_0_4 ~-120 ~-116 ~72
place template frozen_dominion:citadel/gb_1_0 ~-72 ~-116 ~-120
place template frozen_dominion:citadel/gb_1_1 ~-72 ~-116 ~-72
place template frozen_dominion:citadel/gb_1_2 ~-72 ~-116 ~-24
place template frozen_dominion:citadel/gb_1_3 ~-72 ~-116 ~24
place template frozen_dominion:citadel/gb_1_4 ~-72 ~-116 ~72
place template frozen_dominion:citadel/gb_2_0 ~-24 ~-116 ~-120
place template frozen_dominion:citadel/gb_2_1 ~-24 ~-116 ~-72
place template frozen_dominion:citadel/gb_2_2 ~-24 ~-116 ~-24
place template frozen_dominion:citadel/gb_2_3 ~-24 ~-116 ~24
place template frozen_dominion:citadel/gb_2_4 ~-24 ~-116 ~72
place template frozen_dominion:citadel/gb_3_0 ~24 ~-116 ~-120
place template frozen_dominion:citadel/gb_3_1 ~24 ~-116 ~-72
place template frozen_dominion:citadel/gb_3_2 ~24 ~-116 ~-24
place template frozen_dominion:citadel/gb_3_3 ~24 ~-116 ~24
place template frozen_dominion:citadel/gb_3_4 ~24 ~-116 ~72
place template frozen_dominion:citadel/gb_4_0 ~72 ~-116 ~-120
place template frozen_dominion:citadel/gb_4_1 ~72 ~-116 ~-72
place template frozen_dominion:citadel/gb_4_2 ~72 ~-116 ~-24
place template frozen_dominion:citadel/gb_4_3 ~72 ~-116 ~24
place template frozen_dominion:citadel/gb_4_4 ~72 ~-116 ~72
time set noon
gamerule doDaylightCycle false
tp @s ~0 ~1 ~-118 0 0
tellraw @s {"text":"Cytadela (szara bryla) postawiona. Stoisz u stop schodow podejscia.","color":"aqua"}
