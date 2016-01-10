package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
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

    public static final int SPRITE_SCALE = 3;
    public static final int SPRITE_SIZE = 16 * SPRITE_SCALE;
    public static final Rectangle BASE_RECT = new Rectangle(0, 0, 16, 16);

    // NOTE: Units of 'need' to deplete per second
    public static float ENERGY_RATE = 0.1f;
    public static float HUNGER_RATE = 0.3f;
    public static float THIRST_RATE = 0.3f;

    public float globalObjectSpeedModifier = 1.0f;

    private Hero hero;
    private Scene homeScene;
    private Scene adventureScene;
    private Scene currScene;

    private float worldMoveSpeed;
    private Battle battleState;

    private float coinSpawnTimer;
    private Texture coinTex;
    private Sound coinSfx;

    public enum Battle {
        transition, active, inactive
    }

    public GameState(Hero hero) {
        this.hero = hero;

        IntMap<Texture> homeAssets = new IntMap<Texture>();
        Texture homeTex = new Texture(Gdx.files.internal("backdrop.png"));
        this.homeScene = new Scene(homeTex, homeAssets, false);

        IntMap<Texture> adventAssets = new IntMap<Texture>();
        Texture coinAsset = new Texture(Gdx.files.internal("coin.png"));
        adventAssets.put(GameObj.Type.coin.ordinal(), coinAsset);

        Texture adventTex = new Texture(Gdx.files.internal("forest_night_1.png"));
        this.adventureScene = new Scene(adventTex, adventAssets, true);
        this.adventureScene.getSceneObj().add(hero);

        worldMoveSpeed = 0.0f;

        battleState = Battle.inactive;

        coinSpawnTimer = 1.0f;
        coinTex = new Texture(Gdx.files.internal("coin.png"));
        coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin1.wav"));

        this.currScene = homeScene;
    }

    public void update(float delta) {
        GameObj.States state = hero.getCurrAnimState();
        switch (state) {
            case neutral:
            case idle_left:
            case idle_right:
                worldMoveSpeed = 0.0f;
                break;

            case walk_right:
                worldMoveSpeed = RUN_SPEED_IN_PIXELS;
            break;

            case walk_left:
                worldMoveSpeed = -RUN_SPEED_IN_PIXELS;
                break;

            case battle_left:
                worldMoveSpeed = 0.0f;
                break;

            case battle_right:
                worldMoveSpeed = 0.0f;
                break;

            default:
                System.err.println("ERROR: Unexpected GameObj.State given");
                break;
        }

        // TODO: Move this out of gamestate? Maybe have a game master

        // Proximity detection
        if (currScene.equals(adventureScene)) {
            Iterator<GameObj> objIterator = currScene.getSceneObj().iterator();

            while (objIterator.hasNext()) {
                GameObj obj = objIterator.next();


                if (obj.getType() == GameObj.Type.coin) {
                    if (obj.rect.x <= hero.rect.x + hero.rect.width / 2) {
                        hero.addMoney(1);
                        obj.act();
                        objIterator.remove();
                    }
                } else if (obj.getType() == GameObj.Type.monster) {
                    float battleThresholdX = currScene.rect.width + (0.15f*currScene.rect.width);
                    if (obj.rect.x <= battleThresholdX) {
                        battleState = Battle.transition;
                    }
                } else if (obj.getType() == GameObj.Type.hero) {
                    if (obj.rect.x <= (0.15f * currScene.rect.width) && battleState == Battle.transition) {
                        // Stop world moving, battle transition complete
                        battleState = Battle.active;
                        obj.setCurrAnimState(GameObj.States.battle_right);
                    }
                }
            }

            if (battleState == Battle.inactive) {
                globalObjectSpeedModifier = 1.0f;

                generateRandCoin(delta);

                // TODO: TEMPORARY!!! Generate monster intelligently
                if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
                    generateMonster();
                }

            } else if (battleState == Battle.transition) {
                globalObjectSpeedModifier = 0.5f;
            } else if (battleState == Battle.active) {
                globalObjectSpeedModifier = 0.0f;
            }

        }

        currScene.update(this, delta);
        hero.update(delta);

    }

    private void generateRandCoin(float delta) {
        // Generate coin texture
        TextureRegion coinTexReg = new TextureRegion(coinTex);
        float frameDuration = 0.05f;
        Animation neutral = new Animation(frameDuration, coinTexReg);
        IntMap<Animation> anims = new IntMap<Animation>();
        anims.put(GameObj.States.neutral.ordinal(), neutral);

        coinSpawnTimer -= delta;
        if (coinSpawnTimer <= 0) {
            Rectangle coinRect = new Rectangle((int)generateRandOffscreenX(), hero.rect.y,
                    SPRITE_SIZE, SPRITE_SIZE);
            GameObj coin = new GameObj(coinRect, anims, coinSfx, GameObj.Type.coin);
            currScene.getSceneObj().add(coin);
            coinSpawnTimer = 1.0f;
            System.out.println("DEBUG: Generated coin at x: " + coin.rect.x);
        }
    }

    private void generateMonster() {
        Rectangle baseRect = new Rectangle(0, 0, 16, 16);
        Rectangle rect = new Rectangle((int)generateRandOffscreenX(), hero.rect.y, SPRITE_SIZE, SPRITE_SIZE);
        Texture base = new Texture(Gdx.files.internal("MyCharEnemy.png"));

        // Extract animations
        TextureRegion[][] frames = TextureRegion.split(base, (int)baseRect.width, (int)baseRect.height);
        float frameDuration = 0.05f;

        Vector2 idleRightStartSprite = new Vector2(0, 1);
        Animation idleRight = Util.extractAnim(frames, frameDuration,
                idleRightStartSprite, 1);

        Vector2 idleLeftStartSprite = new Vector2(0, 0);
        Animation idleLeft = Util.extractAnim(frames, frameDuration,
                idleLeftStartSprite, 1);

        Vector2 walkRightStartSprite  = new Vector2(0, 1);
        Animation walkRight = Util.extractAnim(frames, frameDuration,
                walkRightStartSprite, 4);

        Vector2 walkLeftStartSprite = new Vector2(0, 0);
        Animation walkLeft = Util.extractAnim(frames, frameDuration,
                walkLeftStartSprite, 4);

        IntMap<Animation> monsterAnim = new IntMap<Animation>();
        monsterAnim.put(Hero.States.idle_left.ordinal(), idleLeft);
        monsterAnim.put(Hero.States.idle_right.ordinal(), idleRight);
        monsterAnim.put(Hero.States.walk_right.ordinal(), walkRight);
        monsterAnim.put(Hero.States.walk_left.ordinal(), walkLeft);

        GameObj monster = new GameObj(rect, monsterAnim, null,
                                      GameObj.Type.monster,
                                      GameObj.States.walk_left);
        currScene.getSceneObj().add(monster);
        System.out.println("DEBUG: Generated monster at x: " + monster.rect.x);

    }

    private float generateRandOffscreenX () {
        float result = currScene.rect.width;
        result += MathUtils.random(0.0f, currScene.rect.width);
        return result;
    }

    public Hero getHero() { return hero; }
    public Scene getCurrScene() { return currScene; }
    public Scene getHomeScene() { return homeScene; }
    public Scene getAdventureScene() { return adventureScene; }

    public float getWorldMoveSpeed() { return worldMoveSpeed; }
    public Battle getBattleState() { return battleState; }

    public void setWorldMoveSpeed(float amount) { this.worldMoveSpeed = amount; }
    public void setCurrScene(Scene scene) {
        this.currScene = scene;
    }

}
