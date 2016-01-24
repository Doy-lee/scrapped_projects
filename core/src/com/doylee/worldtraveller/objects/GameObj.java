package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 10/01/2016.
 */
public class GameObj {
    protected IntMap<Animation> anims;
    private Sprite sprite;

    protected States currAnimState;
    private Animation currAnim;
    private TextureRegion currFrame;
    private float stateTime;

    protected IntMap<Sound> sfx;
    private Type type;
    private float objectSpeedModifier;

    private boolean initialised;

    public enum States {
        walk_left, walk_right, idle_left, idle_right, neutral, battle_left,
        battle_right
    }

    public enum Type {
        coin, hero, monster, attack
    }

    public enum SoundFX {
        hit, attack
    }

    public GameObj(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type) {
        this.anims = anims;

        this.currAnimState = States.neutral;
        this.currAnim = anims.get(this.currAnimState.ordinal());
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        this.stateTime = 0.0f;

        this.sprite = new Sprite(currFrame);
        this.sprite.setBounds(rect.x, rect.y, rect.width, rect.height);

        this.type = type;
        this.sfx = sfx;
        this.objectSpeedModifier = 1.0f;
    }

    public GameObj(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState) {
        this.anims = anims;

        this.currAnimState = animState;
        this.currAnim = anims.get(this.currAnimState.ordinal());
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        this.stateTime = 0.0f;

        this.sprite = new Sprite(currFrame);
        this.sprite.setBounds(rect.x, rect.y, rect.width, rect.height);

        this.type = type;
        this.sfx = sfx;
    }

    public GameObj(Type type) {
        this.type = type;
        this.initialised = false;
    }

    public void playSoundIfExist(SoundFX sfx, float volume) {
       if (this.sfx.containsKey(sfx.ordinal())) {
            this.sfx.get(sfx.ordinal()).play(volume);
       } else {
           System.err.println("ERROR: A GameObj tried to play an invalid sound effect");
           System.err.println(this.getType().toString() +  " => " + sfx.toString());

       }
    }

    public void update(float delta) {
        this.stateTime += delta;
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        sprite.setRegion(currFrame);
    }

    public void render(SpriteBatch batch) {
        sprite.draw(batch);
    }

    public Type getType() { return type; }
    public Sprite getSprite() { return sprite; }
    public States getCurrAnimState() { return currAnimState; }
    public TextureRegion getCurrFrame() { return currFrame; }

    public float getObjectSpeedModifier() { return this.objectSpeedModifier; }
    public void setObjectSpeedModifier(float amount) { this.objectSpeedModifier = amount; }

    public void setCurrAnimState(States state) {
        assert(anims.containsKey(state.ordinal()));
        this.currAnimState = state;
        this.currAnim = anims.get(currAnimState.ordinal());
        // TODO: Possible bug if we don't reset state time? But this allows animations in home screen to rapidly change and still look good
        //stateTime = 0.0f;
    }

    public static GameObj newInstance(GameObj object) {
        Rectangle rect = object.getSprite().getBoundingRectangle();
        return new GameObj(rect, object.anims, object.sfx, object.type, object.currAnimState);
    }
}
