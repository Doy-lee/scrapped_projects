package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.*;

/**
 * Created by Doyle on 9/01/2016.
 */

public class Hero extends Battler {
    private float hunger;
    private float thirst;
    private float energy;


    public Hero(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Attack weapon) {
        super(rect, anims, sfx, type, animState, weapon);

        this.hunger = 100;
        this.thirst = 100;
        this.energy = 100;
    }

    public int getHunger() { return (int)hunger; }
    public int getThirst() { return (int)thirst; }
    public int getEnergy() { return (int)energy; }

    public void setHunger(float amount) { this.hunger = amount; }
    public void setThirst(float amount) { this.thirst = amount; }
    public void setEnergy(float amount) { this.energy = amount; }

    public void update(float delta) {
        super.update(delta);

        if (this.energy >= 0) this.energy -= GameState.ENERGY_RATE * delta;
        if (this.hunger >= 0) this.hunger -= GameState.HUNGER_RATE * delta;
        if (this.thirst >= 0) this.thirst -= GameState.THIRST_RATE * delta;

    }
}
