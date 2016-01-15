package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.objects.Battler;
import com.doylee.worldtraveller.objects.GameObj;
import com.doylee.worldtraveller.objects.Hero;

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

    public static final float MONSTER_SPAWN_TIME = 8.0f;
    public static final float COIN_SPAWN_TIME = 1.0f;

    // NOTE: Units of 'need' to deplete per second
    public static float ENERGY_RATE = 0.1f;
    public static float HUNGER_RATE = 0.3f;
    public static float THIRST_RATE = 0.3f;

    public float globalObjectSpeedModifier = 1.0f;
    public static float globalVolume = 0.0f;

    private IntMap<GameObj> objectList;

    private Hero hero;
    private Scene homeScene;
    private Scene adventureScene;
    private Scene currScene;

    private float worldMoveSpeed;
    private Battle battleState;
    private Battler currBattleMob;

    private float monsterSpawnTimer;
    private float coinSpawnTimer;

    public enum Battle {
        transitionIn, transitionOut, active, inactive
    }

    public GameState() {

        this.objectList = new IntMap<GameObj>();

        // TODO: Get rid of, initialise one copy on start up, instantiate the rest
        objectList.put(GameObj.Type.coin.ordinal(), loadCoinToGame());
        objectList.put(GameObj.Type.hero.ordinal(), loadHeroToGame());
        this.hero = (Hero)objectList.get(GameObj.Type.hero.ordinal());
        objectList.put(GameObj.Type.monster.ordinal(), loadMonsterToGame());

        for (GameObj.Type type : GameObj.Type.values()) {
            // NOTE: If object not initialised yet, then create a skeleton copy of it
            if (!objectList.containsKey(type.ordinal())) {
                objectList.put(type.ordinal(), new GameObj(type));
            }
        }

        IntMap<Texture> homeAssets = new IntMap<Texture>();

        IntMap<Music> homeMusic = new IntMap<Music>();
        Music homeBackground = Gdx.audio.newMusic(Gdx.files.internal("homeBackground.mp3"));
        homeMusic.put(Scene.ScnMusic.background.ordinal(), homeBackground);

        Array<GameObj> homeObjs = new Array<com.doylee.worldtraveller.objects.GameObj>();
        homeObjs.add(hero);

        Texture homeTex = new Texture(Gdx.files.internal("backdrop.png"));
        this.homeScene = new Scene(homeTex, homeAssets, homeObjs, homeMusic, false);

        IntMap<Texture> adventAssets = new IntMap<Texture>();
        Texture coinAsset = new Texture(Gdx.files.internal("coin.png"));
        adventAssets.put(GameObj.Type.coin.ordinal(), coinAsset);

        Array<GameObj> adventObjs = new Array<com.doylee.worldtraveller.objects.GameObj>();
        adventObjs.add(hero);

        Music adventBackground = Gdx.audio.newMusic(Gdx.files.internal("adventBackground.mp3"));
        Music adventBattle = Gdx.audio.newMusic(Gdx.files.internal("adventBattle.mp3"));
        adventBackground.setLooping(true);
        adventBattle.setLooping(true);

        IntMap<Music> adventMusic = new IntMap<Music>();
        adventMusic.put(Scene.ScnMusic.background.ordinal(), adventBackground);
        adventMusic.put(Scene.ScnMusic.battle.ordinal(), adventBattle);

        Texture adventTex = new Texture(Gdx.files.internal("forest_night_1.png"));

        this.adventureScene = new Scene(adventTex, adventAssets, adventObjs, adventMusic, true);

        worldMoveSpeed = 0.0f;
        battleState = Battle.inactive;
        currBattleMob = null;

        monsterSpawnTimer = MONSTER_SPAWN_TIME;
        coinSpawnTimer = COIN_SPAWN_TIME;

        this.currScene = homeScene;
    }

    private GameObj loadCoinToGame() {
        // Load Texture
        Texture coinTex = new Texture(Gdx.files.internal("coin.png"));
        TextureRegion coinTexReg = new TextureRegion(coinTex);

        // Load Animation
        float frameDuration = 0.05f;
        Animation neutral = new Animation(frameDuration, coinTexReg);
        IntMap<Animation> anims = new IntMap<Animation>();
        anims.put(GameObj.States.neutral.ordinal(), neutral);

        // Load Sound
        IntMap<Sound> sfx = new IntMap<Sound>();
        Sound coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin1.wav"));
        sfx.put(GameObj.SoundFX.hit.ordinal(), coinSfx);

        // Set position
        Rectangle coinRect = new Rectangle(0 , 0, SPRITE_SIZE, SPRITE_SIZE);
        GameObj result = new GameObj(coinRect, anims, sfx, GameObj.Type.coin);
        return result;
    }

    private Hero loadHeroToGame() {
        Rectangle baseRect = new Rectangle(0, 0, 16, 16);
        Rectangle rect = new Rectangle((Gdx.graphics.getWidth()/2) - baseRect.getWidth(),
                Gdx.graphics.getHeight()/2, GameState.SPRITE_SIZE, GameState.SPRITE_SIZE);
        Texture base = new Texture(Gdx.files.internal("MyChar.png"));

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

        IntMap<Animation> heroAnim = new IntMap<Animation>();
        heroAnim.put(Hero.States.idle_left.ordinal(), idleLeft);
        heroAnim.put(Hero.States.idle_right.ordinal(), idleRight);
        heroAnim.put(Hero.States.walk_right.ordinal(), walkRight);
        heroAnim.put(Hero.States.walk_left.ordinal(), walkLeft);
        heroAnim.put(Hero.States.battle_right.ordinal(), walkRight);
        heroAnim.put(Hero.States.battle_left.ordinal(), walkLeft);

        // Weapon
        Texture weaponTex = new Texture(Gdx.files.internal("sword.png"));
        Sprite weapon = new Sprite(weaponTex);
        weapon.setBounds(rect.x, rect.y, GameState.SPRITE_SIZE,
                         GameState.SPRITE_SIZE);
        weapon.setOrigin(0, 0);

        // Hero Sound
        IntMap<Sound> sfx = new IntMap<Sound>();
        Sound attackSound = Gdx.audio.newSound(Gdx.files.internal("slice.mp3"));
        sfx.put(GameObj.SoundFX.attack.ordinal(), attackSound);

        Hero result = new Hero(rect, heroAnim, sfx, GameObj.Type.hero,
                               GameObj.States.walk_right, weapon);
        return result;
    }

    private Battler loadMonsterToGame() {
        Rectangle baseRect = new Rectangle(0, 0, 16, 16);
        Rectangle rect = new Rectangle(0, 0, SPRITE_SIZE, SPRITE_SIZE);
        Texture base = new Texture(Gdx.files.internal("MyCharEnemy.png"));

        // Extract animations
        TextureRegion[][] frames = TextureRegion.split(base, (int) baseRect.width, (int) baseRect.height);
        float frameDuration = 0.05f;

        Vector2 idleRightStartSprite = new Vector2(0, 1);
        Animation idleRight = Util.extractAnim(frames, frameDuration,
                idleRightStartSprite, 1);

        Vector2 idleLeftStartSprite = new Vector2(0, 0);
        Animation idleLeft = Util.extractAnim(frames, frameDuration,
                idleLeftStartSprite, 1);

        Vector2 walkRightStartSprite = new Vector2(0, 1);
        Animation walkRight = Util.extractAnim(frames, frameDuration,
                walkRightStartSprite, 4);

        Vector2 walkLeftStartSprite = new Vector2(0, 0);
        Animation walkLeft = Util.extractAnim(frames, frameDuration,
                walkLeftStartSprite, 4);

        IntMap<Animation> monsterAnim = new IntMap<Animation>();
        monsterAnim.put(GameObj.States.idle_left.ordinal(), idleLeft);
        monsterAnim.put(GameObj.States.idle_right.ordinal(), idleRight);
        monsterAnim.put(GameObj.States.walk_right.ordinal(), walkRight);
        monsterAnim.put(GameObj.States.walk_left.ordinal(), walkLeft);

        IntMap<Sound> sfx = new IntMap<Sound>();
        Sound attackSound = Gdx.audio.newSound(Gdx.files.internal("slice.mp3"));
        sfx.put(GameObj.SoundFX.attack.ordinal(), attackSound);
        Battler result = new Battler(rect, monsterAnim, sfx,
                                     GameObj.Type.monster,
                                     GameObj.States.walk_left);
        return result;
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
            if (battleState == Battle.active) {
                // BATTLE is active, stop objects moving
                globalObjectSpeedModifier = 0.0f;

                Battler mob = getCurrBattleMob();
                Battler hero = getHero();
                mob.atbUpdateAndAttack(delta, hero);
                hero.atbUpdateAndAttack(delta, mob);

                // NOTE: Enemy is dead
                if (mob.getHealth() <= 0) {
                    // TODO: Spawn coins equal to the amount that the mob had
                    for (int i = 0; i < mob.getMoney(); i++) {
                        Rectangle rect = new Rectangle(mob.getSprite().getX() + (i*10), mob.getSprite().getY(), SPRITE_SIZE, SPRITE_SIZE);
                        generateCoin(rect);
                    }
                    battleState = Battle.transitionOut;
                    hero.setCurrAnimState(GameObj.States.walk_right);

                    currScene.getSceneObj().removeValue(mob, true);
                    currBattleMob = null;
                }

            } else {
                checkGameObjectsAndProximity();
                if (battleState == Battle.inactive) {
                    globalObjectSpeedModifier = 1.0f;

                    if ((coinSpawnTimer -= delta) <= 0) {
                        Rectangle rect = new Rectangle((int)generateRandOffscreenX(),
                                hero.getSprite().getY(), SPRITE_SIZE, SPRITE_SIZE);
                        generateCoin(rect);
                        coinSpawnTimer = COIN_SPAWN_TIME;
                    }

                    if ((monsterSpawnTimer -= delta) <= 0) {
                        Rectangle rect = new Rectangle((int)generateRandOffscreenX(),
                                hero.getSprite().getY(), SPRITE_SIZE, SPRITE_SIZE);
                        generateMonster(rect);
                        monsterSpawnTimer = MONSTER_SPAWN_TIME;
                    }

                    // TODO: TEMPORARY!!! Generate monster intelligently
                    if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
                        Rectangle rect = new Rectangle((int)generateRandOffscreenX(),
                                hero.getSprite().getY(), SPRITE_SIZE, SPRITE_SIZE);
                        generateMonster(rect);
                    }

                } else if (battleState == Battle.transitionIn || battleState == Battle.transitionOut) {
                    globalObjectSpeedModifier = 0.75f;
                }
            }
        }
        currScene.update(this, delta);

    }

    private void checkGameObjectsAndProximity() {
        Iterator<GameObj> objIterator = currScene.getSceneObj().iterator();
        while (objIterator.hasNext()) {
            GameObj obj = objIterator.next();

            if (obj.getType() == GameObj.Type.coin) {
                if (battleState == Battle.active) {
                    obj.getSprite().setAlpha(0.0f);
                } else {
                    if (obj.getSprite().getX() <= hero.getSprite().getX() + hero.getSprite().getWidth() / 2) {
                        hero.addMoney(1);
                        // TODO: revise how to handle hits/gameobject actions and sound fx
                        obj.playSoundIfExist(GameObj.SoundFX.hit, GameState.globalVolume);
                        objIterator.remove();
                    }
                    obj.getSprite().setAlpha(1.0f);
                }
            } else if (obj.getType() == GameObj.Type.monster) {
                if (battleState == Battle.inactive) {
                    float battleThresholdX = currScene.rect.width + (0.15f * currScene.rect.width);
                    if (obj.getSprite().getX() <= battleThresholdX) {
                        battleState = Battle.transitionIn;
                        currBattleMob = (Battler)obj;
                        break;
                    }
                }
            } else if (obj.getType() == GameObj.Type.hero) {
                if (battleState == Battle.transitionIn) {
                    float battleThresholdX = 0.15f * currScene.rect.width;
                    if (obj.getSprite().getX() <= battleThresholdX) {
                        // Stop world moving, battle transition complete
                        battleState = Battle.active;
                        obj.setCurrAnimState(GameObj.States.battle_right);
                    }
                } else if (battleState == Battle.transitionOut) {
                    float battleThresholdX = currScene.rect.width/2;
                    if (obj.getSprite().getX() >= battleThresholdX) {
                        battleState = Battle.inactive;
                    }

                }
            }
        }
    }

    private void generateCoin(Rectangle rect) {
        GameObj referenceCoin = objectList.get(GameObj.Type.coin.ordinal());
        GameObj newCoin = GameObj.newInstance(referenceCoin);

        newCoin.getSprite().setBounds(rect.x, rect.y, rect.width, rect.height);
        currScene.getSceneObj().add(newCoin);

        System.out.println("DEBUG: Generated coin at x: " + newCoin.getSprite().getX());
    }

    // TODO: PUBLIC IS TEMPORARY!
    public void generateMonster(Rectangle rect) {
        Battler referenceMonster = (Battler)objectList.get(GameObj.Type.monster.ordinal());
        Battler newMonster = Battler.newInstance(referenceMonster);

        newMonster.getSprite().setBounds(rect.x, rect.y, rect.width, rect.height);
        currScene.getSceneObj().add(newMonster);

        System.out.println("DEBUG: Generated monster at x: " + newMonster.getSprite().getX());

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

    // TODO: Get rid of timer access, temporary only
    public float getMonsterSpawnTimer() { return monsterSpawnTimer; }

    public float getWorldMoveSpeed() { return worldMoveSpeed; }
    public Battle getBattleState() { return battleState; }

    public Battler getCurrBattleMob() { return currBattleMob; }

    public void setWorldMoveSpeed(float amount) { this.worldMoveSpeed = amount; }
    public void setCurrScene(Scene scene) {
        // TODO: Don't allow people in battle to change scene?
        if (currScene != scene && (battleState != Battle.active && battleState != Battle.transitionIn && battleState != Battle.transitionOut)) {
            currScene.getCurrSong().stop();
            this.currScene = scene;
        }
    }

}
