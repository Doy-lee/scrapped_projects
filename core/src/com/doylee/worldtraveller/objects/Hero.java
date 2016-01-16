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

    private boolean triggerAttackArc;
    private float attackEndPause;
    private Sprite weapon;

    public Hero(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Sprite weapon) {
        super(rect, anims, sfx, type, animState);

        this.hunger = 100;
        this.thirst = 100;
        this.energy = 100;

        triggerAttackArc = false;
        attackEndPause = 0.5f;
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
            weapon.draw(batch);
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

        if (triggerAttackArc) {
            weapon.setX(this.getSprite().getX() + GameState.SPRITE_SIZE);
            weapon.setY(this.getSprite().getY() + GameState.SPRITE_SIZE);

            float currRotation = weapon.getRotation();
            float endRotation = 30.0f;
            float rotationStep = endRotation * 10.0f;

            if (currAnimState == States.battle_right) {
                endRotation = -endRotation;
                rotationStep = -rotationStep;
            }

            if (currRotation >= endRotation) {
                currRotation += rotationStep * delta;
                weapon.setRotation(currRotation);
            } else {
                attackEndPause -= delta;
                if ((attackEndPause -= delta) <= 0) {
                    triggerAttackArc = false;
                    weapon.setRotation(0);
                    attackEndPause = 0.5f;
                }
            }
        }
    }
}
