package com.doylee.worldtraveller;

import com.badlogic.gdx.scenes.scene2d.Stage;

public class GameState {
    // NOTE: The average human height is 1.7m, canonically in our world, ~60pixels
    // i.e. 1 m ~= 35.3px
    // Assume average run speed of 10 miles per hour ~= 16km/hr
    // 16km/hr ~= 4.4m/s.
    public static final float BASE_AVATAR_HEIGHT = 60.0f;
    public static final float PIXELS_PER_METER = BASE_AVATAR_HEIGHT/1.7f;
    public static final float WORLD_MOVE_SPEED = 4.4f;
    public static final float WORLD_SPEED_IN_PIXELS = WORLD_MOVE_SPEED * PIXELS_PER_METER;
    public static final float COIN_SPAWN_TIME = 1.0f;

    public int playerMoney;
    public boolean tentMode;
    public float coinSpawnTimer;
    public int distTravelled;

    public GameState() {
        playerMoney = 0;
        tentMode = false;
        coinSpawnTimer = 1.0f;
    }
}
