package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;

import java.io.BufferedReader;
import java.io.IOException;

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
    public boolean isTentActive;

    public World world;
    public GamePlayer hero;
    public Array<String> worldAdjectives;

    public GameState() {
        this.playerMoney = 0;
        this.tentMode = false;
        this.coinSpawnTimer = 1.0f;
        this.distTravelled = 0;

        BufferedReader reader = Gdx.files.internal("world_adjectives.txt").reader(512);
        worldAdjectives = new Array<String>();
        try {
            String line = reader.readLine();
            while (line != null) {
                worldAdjectives.add(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String adjectives: worldAdjectives) {
            System.out.println("DEBUG Parsed adjective: " + adjectives);
        }

        Array<WorldChunk> chunksArray = generateWorldChunks(6);
        String worldName = generateWorldName();
        this.world = new World(chunksArray, worldName);

        float spriteSize = 16.0f;
        float spriteScale = 4.0f;
        float avatarCenterToScreen = Gdx.graphics.getWidth()/2 -
                (spriteSize* spriteScale);
        float avatarSize = spriteSize * spriteScale;

        Texture avatarSheet = new Texture(Gdx.files.internal("MyChar.png"));
        Texture tentTex = new Texture(Gdx.files.internal("circus_tent.png"));

        TextureRegion[][] tmp = TextureRegion.split(avatarSheet, (int)spriteSize, (int)spriteSize);
        int walkLength = 4;
        TextureRegion[] walkFrames = new TextureRegion[walkLength];
        for (int i = 0; i < walkLength; i++) {
            walkFrames[i] = tmp[1][i];
        }
        Animation walkAnim = new Animation(0.05f, walkFrames);

        hero = new GamePlayer(avatarCenterToScreen, world.horizonInPixels,
                            avatarSize, avatarSize, true, tentTex, walkAnim);
    }

    public void generateWorld() {
        String worldName = worldAdjectives.get(MathUtils.random(0, worldAdjectives.size - 1));
        world = new World(generateWorldChunks(6), worldName);
    }

    public void resetHeroPosition() {
        float spriteSize = 16.0f;
        float spriteScale = 4.0f;
        float avatarCenterToScreen = Gdx.graphics.getWidth()/2 -
                (spriteSize* spriteScale);
        hero.setX(avatarCenterToScreen);
    }

    public Array<WorldChunk> generateWorldChunks(int numChunks) {
        Array<WorldChunk> result = new Array<WorldChunk>(6);

        Texture tex = new Texture(Gdx.files.internal("plain_terrain_cut.png"));
        //int height = tex.getHeight();
        //int width = tex.getWidth();
        int height = Gdx.graphics.getHeight();
        int width = Gdx.graphics.getWidth();

        result.add(new WorldChunk(0f, 0f, width, height, tex));
        result.add(new WorldChunk(Gdx.graphics.getWidth(), 0f, width, height, tex));
        result.add(new WorldChunk(Gdx.graphics.getWidth()*2, 0f, width, height, tex));
        result.add(new WorldChunk(Gdx.graphics.getWidth()*3, 0f, width, height, tex));
        result.add(new WorldChunk(Gdx.graphics.getWidth()*4, 0f, width, height, tex));
        result.add(new WorldChunk(Gdx.graphics.getWidth()*5, 0f, width, height, tex));
        return result;
    }

    public String generateWorldName() {
        String result = worldAdjectives.get(MathUtils.random(0, worldAdjectives.size - 1));
        System.out.println("DEBUG World name set to " + result);
        return result;
    }

    public void setTentMode(boolean tentMode) {
        isTentActive = tentMode;
        if (tentMode) {
            hero.setVisible(false);
            hero.tent.setVisible(true);
            hero.tent.setX(hero.getX());
            hero.tent.setY(hero.getY());
        } else {
            hero.setVisible(true);
            hero.tent.setVisible(false);
        }
    }
}
