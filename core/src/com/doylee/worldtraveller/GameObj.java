package com.doylee.worldtraveller;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 10/01/2016.
 */
public class GameObj {
    // NOTE: Rect is public because rect fields are already public, why hide it further
    public Rectangle rect;
    private IntMap<Animation> anims;

    private States currAnimState;
    private Animation currAnim;
    private TextureRegion currFrame;
    private float stateTime;

    private Sound sfx;
    private Type type;
    private float objectSpeedModifier;

    public enum States {
        walk_left, walk_right, idle_left, idle_right, neutral, battle_left,
        battle_right
    }

    public enum Type {
        coin, hero, monster
    }

    public GameObj(Rectangle rect, IntMap<Animation> anims, Sound sfx, Type type) {
        this.rect = rect;
        this.anims = anims;

        this.currAnimState = States.neutral;
        this.currAnim = anims.get(this.currAnimState.ordinal());
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        this.stateTime = 0.0f;

        this.type = type;
        this.sfx = sfx;
        this.objectSpeedModifier = 1.0f;
    }

    public GameObj(Rectangle rect, IntMap<Animation> anims, Sound sfx, Type type, States animState) {
        this.rect = rect;
        this.anims = anims;

        this.currAnimState = animState;
        this.currAnim = anims.get(this.currAnimState.ordinal());
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        this.stateTime = 0.0f;

        this.type = type;
        this.sfx = sfx;
    }

    public void act() {
        sfx.play();
    }

    public void update(float delta) {
        this.stateTime += delta;
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
    }

    public void render(SpriteBatch batch) {
        batch.draw(getCurrFrame(), rect.x, rect.y, rect.width, rect.height);
    }

    public Type getType() { return type; }
    public States getCurrAnimState() { return currAnimState; }
    public TextureRegion getCurrFrame() { return currFrame; }

    public float getObjectSpeedModifier() { return this.objectSpeedModifier; }
    public void setObjectSpeedModifier(float amount) { this.objectSpeedModifier = amount; }

    public void setCurrAnimState(States state) {
        assert(anims.containsKey(state.ordinal()));
        this.currAnimState = state;
        this.currAnim = anims.get(currAnimState.ordinal());
        stateTime = 0.0f;
    }

}
