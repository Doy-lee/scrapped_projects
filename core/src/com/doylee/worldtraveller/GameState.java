package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import java.util.Iterator;

/**
 * Created by Doyle on 9/01/2016.
 */
public class GameState {
    // NOTE: Assume average height of 1.7m ~= 60px, so 1m ~= 35px
    public static final float BASE_AVATAR_HEIGHT = 60.0f;
    public static final float PIXELS_PER_METER = BASE_AVATAR_HEIGHT/1.7f;
    public static final float RUN_SPEED = 4.4f;
    public static final float RUN_SPEED_IN_PIXELS = RUN_SPEED * PIXELS_PER_METER;

    // NOTE: Units of 'need' to deplete per second
    public static float ENERGY_RATE = 0.1f;
    public static float HUNGER_RATE = 0.3f;
    public static float THIRST_RATE = 0.3f;

    public enum Type {
        coin, hero
    }

    private Hero hero;
    private Scene homeScene;
    private Scene adventureScene;
    private Scene currScene;

    private float coinSpawnTimer;
    private Texture coinTex;
    private Sound coinSfx;

    public GameState(Hero hero) {
        this.hero = hero;

        IntMap<Texture> homeAssets = new IntMap<Texture>();
        Texture homeTex = new Texture(Gdx.files.internal("backdrop.png"));
        this.homeScene = new Scene(homeTex, homeAssets, false);

        IntMap<Texture> adventAssets = new IntMap<Texture>();
        Texture coinAsset = new Texture(Gdx.files.internal("coin.png"));
        adventAssets.put(Type.coin.ordinal(), coinAsset);

        Texture adventTex = new Texture(Gdx.files.internal("forest_night_1.png"));
        this.adventureScene = new Scene(adventTex, adventAssets, true);

        coinSpawnTimer = 1.0f;
        coinTex = new Texture(Gdx.files.internal("coin.png"));
        coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin1.wav"));

        this.currScene = homeScene;
    }

    public void update(float delta) {
        currScene.update(delta);
        hero.update(delta);

        // TODO: Move this out of gamestate? Maybe have a game master
        Iterator<GameObj> objIterator = currScene.getSceneObj().iterator();
        while (objIterator.hasNext()) {
            GameObj obj = objIterator.next();
            if (obj.getType() == GameState.Type.coin) {
                if (obj.rect.x <= hero.rect.x + hero.rect.width/2) {
                    hero.addMoney(1);
                    obj.act();
                    objIterator.remove();
                }
            }
        }

        generateRandCoin(delta);

    }

    private void generateRandCoin(float delta) {

        // Generate coin texture
        TextureRegion coinTexReg = new TextureRegion(coinTex);
        float frameDuration = 0.05f;
        Animation neutral = new Animation(frameDuration, coinTexReg);
        IntMap<Animation> anims = new IntMap<Animation>();
        anims.put(GameObj.States.neutral.ordinal(), neutral);

        if (currScene.equals(adventureScene)) {
            coinSpawnTimer -= delta;
            if (coinSpawnTimer <= 0) {
                float randomiseCoinPosX = currScene.rect.width;
                randomiseCoinPosX += MathUtils.random(0.0f, currScene.rect.width);
                Rectangle coinRect = new Rectangle(randomiseCoinPosX, hero.rect.y,
                        hero.rect.width, hero.rect.height);
                GameObj coin = new GameObj(coinRect, anims, coinSfx, GameState.Type.coin);
                currScene.getSceneObj().add(coin);
                coinSpawnTimer = 1.0f;
                System.out.println("DEBUG: Generated coin at x: " + coin.rect.x);
            }
        }
    }

    public Hero getHero() { return hero; }
    public Scene getCurrScene() { return currScene; }
    public Scene getHomeScene() { return homeScene; }
    public Scene getAdventureScene() { return adventureScene; }

    public void setCurrScene(Scene scene) {
        this.currScene = scene;
    }

}
