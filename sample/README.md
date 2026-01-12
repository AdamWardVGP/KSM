# Adventure Sample


```
stateDiagram-v2
    Start --> DarkForest : Begin
    DarkForest --> OldBridge : GoLeft
    DarkForest --> CaveEntrance : GoRight
    OldBridge --> Treasure : CrossBridge
    OldBridge --> GameOver : RunAway
    CaveEntrance --> DarkForest : RunAway
    FightMonster --> Treasure : Fight
    FightMonster --> GameOver : RunAway
    GameOver --> Start : Restart
    FightMonster --> Start : Restart
```
