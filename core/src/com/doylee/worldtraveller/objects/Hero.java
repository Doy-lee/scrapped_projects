package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
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

    private boolean triggerAttackArc;
    private Sprite weapon;

    public Hero(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Sprite weapon) {
        super(rect, anims, sfx, type, animState);

        this.hunger = 100;
        this.thirst = 100;
        this.energy = 100;

        triggerAttackArc = false;
        this.weapon = weapon;
    }

    @Override
    protected void attack(Battler target) {
        super.attack(target);
        triggerAttackArc = true;
    }

    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);

        if (triggerAttackArc) {
            weapon.setX(this.getSprite().getX() + GameState.SPRITE_SIZE);
            weapon.setY(this.getSprite().getY() + GameState.SPRITE_SIZE);
            weapon.draw(batch);

            float currRotation = weapon.getRotation();
            if (currRotation >= -96.0f) {
                currRotation -= 8.0f;
                weapon.setRotation(currRotation);
            } else {
                triggerAttackArc = false;
                weapon.setRotation(0);
            }
        }
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
