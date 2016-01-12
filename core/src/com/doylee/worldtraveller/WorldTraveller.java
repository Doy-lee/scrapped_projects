package com.doylee.worldtraveller;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.IntMap;

public class WorldTraveller extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public Skin skin;
    public GameState state;

    public void create() {
        batch = new SpriteBatch();
        //Use LibGDX's default Arial font.
        font = new BitmapFont();
        TextureAtlas atlas = new TextureAtlas(Gdx.files.internal("uiskin.atlas"));
        skin = new Skin(Gdx.files.internal("uiskin.json"), atlas);

        Hero hero = generateHero();
        this.state = new GameState(hero);

        this.setScreen(new HomeScreen(this));
    }

    private Hero generateHero() {
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

        IntMap<Sound> sfx = new IntMap<Sound>();
        Sound attackSound = Gdx.audio.newSound(Gdx.files.internal("slice.mp3"));
        sfx.put(GameObj.SoundFX.attack.ordinal(), attackSound);
        Hero result = new Hero(rect, heroAnim, sfx, GameObj.Type.hero, GameObj.States.walk_right);
        return result;
    }

    public void render() {
        super.render(); //important!
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
    }

}
