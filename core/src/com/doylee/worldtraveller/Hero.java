package com.doylee.worldtraveller;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 9/01/2016.
 */

public class Hero extends GameObj {
    private float hunger;
    private float thirst;
    private float energy;

    private int money;

    public Hero(Rectangle rect, IntMap<Animation> anims, Sound sfx, GameState.Type type, States animState) {
        super(rect, anims, sfx, type, animState);
        this.rect = rect;

        this.hunger = 100;
        this.thirst = 100;
        this.energy = 100;

        this.money = 0;
    }

    public int getHunger() { return (int)hunger; }
    public int getThirst() { return (int)thirst; }
    public int getEnergy() { return (int)energy; }

    public void setHunger(float amount) { this.hunger = amount; }
    public void setThirst(float amount) { this.thirst = amount; }
    public void setEnergy(float amount) { this.energy = amount; }

    public void addMoney(float amount) { this.money += amount; }
    public int getMoney() { return this.money; }

    public void update(float delta) {
        super.update(delta);
        if (this.energy >= 0) this.energy -= GameState.ENERGY_RATE * delta;
        if (this.hunger >= 0) this.hunger -= GameState.HUNGER_RATE * delta;
        if (this.thirst >= 0) this.thirst -= GameState.THIRST_RATE * delta;
    }
}
