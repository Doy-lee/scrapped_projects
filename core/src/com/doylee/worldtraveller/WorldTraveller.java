package com.doylee.worldtraveller;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;


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

    public Hero generateHero() {
        Rectangle rect = new Rectangle(Gdx.graphics.getWidth()/2,
                Gdx.graphics.getHeight()/2, 16*3, 16*3);
        Texture base = new Texture(Gdx.files.internal("MyChar.png"));
        Rectangle baseRect = new Rectangle(0, 0, 16, 16);

        // Extract animations
        TextureRegion[][] frames = TextureRegion.split(base, (int)baseRect.width, (int)baseRect.height);
        float frameDuration = 0.05f;

        Vector2 idleRightStartSprite = new Vector2(0, 1);
        Animation idleRight = extractAnim(frames, frameDuration,
                                          idleRightStartSprite, 1);

        Vector2 idleLeftStartSprite = new Vector2(0, 0);
        Animation idleLeft = extractAnim(frames, frameDuration,
                                         idleLeftStartSprite, 1);

        Vector2 walkRightStartSprite  = new Vector2(0, 1);
        Animation walkRight = extractAnim(frames, frameDuration,
                                          walkRightStartSprite, 4);

        Vector2 walkLeftStartSprite = new Vector2(0, 0);
        Animation walkLeft = extractAnim(frames, frameDuration,
                                         walkLeftStartSprite, 4);

        Array<Animation> heroAnims = new Array<Animation>();
        heroAnims.add(walkRight);
        heroAnims.add(walkLeft);
        heroAnims.add(idleLeft);
        heroAnims.add(idleRight);

        Hero result = new Hero(rect, baseRect, heroAnims);
        return result;
    }

    // TODO: We assume all animations are consecutively lined up in one row
    private Animation extractAnim(TextureRegion[][] frames, float duration,
                                  Vector2 startSpriteLoc, int animLength) {

        TextureRegion[] animFrames = new TextureRegion[animLength];

        int index = 0;
        for (int i = (int)startSpriteLoc.x; i < animLength; i++) {
            animFrames[index++] = frames[(int)startSpriteLoc.y][i];
        }
        Animation result = new Animation(duration, animFrames);
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
