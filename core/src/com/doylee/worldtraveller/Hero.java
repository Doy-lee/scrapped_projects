package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 9/01/2016.
 */

public class Hero {
    // NOTE: Rect is public because rect fields are already public, why hide it further
    public Rectangle rect;
    private IntMap<Animation> anims;

    private States currAnimState;
    private Animation currAnim;
    private TextureRegion currFrame;
    private float stateTime;

    private float hunger;
    private float thirst;
    private float energy;

    private int money;

    public enum States {
        walk_left, walk_right, idle_left, idle_right
    }

    public Hero(Rectangle rect, IntMap<Animation> anims) {
        this.rect = rect;
        this.anims = anims;

        this.currAnimState = States.walk_right;
        this.currAnim = anims.get(this.currAnimState.ordinal());
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
        this.stateTime = 0.0f;

        this.hunger = 100;
        this.thirst = 100;
        this.energy = 100;

        this.money = 0;
    }

    public void render(SpriteBatch batch) {
        batch.draw(getCurrFrame(), rect.x, rect.y, rect.width, rect.height);
    }

    public States getCurrAnimState() { return currAnimState; }
    public TextureRegion getCurrFrame() { return currFrame; }

    public int getHunger() { return (int)hunger; }
    public int getThirst() { return (int)thirst; }
    public int getEnergy() { return (int)energy; }

    public void setHunger(float amount) { this.hunger = amount; }
    public void setThirst(float amount) { this.thirst = amount; }
    public void setEnergy(float amount) { this.energy = amount; }

    public void addMoney(float amount) { this.money += amount; }
    public int getMoney() { return this.money; }

    public void update(float delta) {
        if (this.energy >= 0) this.energy -= GameState.ENERGY_RATE * delta;
        if (this.hunger >= 0) this.hunger -= GameState.HUNGER_RATE * delta;
        if (this.thirst >= 0) this.thirst -= GameState.THIRST_RATE * delta;

        this.stateTime += delta;
        this.currFrame = currAnim.getKeyFrame(stateTime, true);
    }
}
