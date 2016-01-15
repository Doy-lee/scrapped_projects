package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.GameState;

/**
 * Created by Doyle on 12/01/2016.
 */
public class Battler extends GameObj {
    private static float BASE_ATB = 100.0f;
    private int health;
    private int attack;

    private float atb;
    private int speed;

    private int money;
    public Battler(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState) {
        super(rect, anims, sfx, type, animState);

        this.health = 100;
        this.attack = 20;

        this.atb = BASE_ATB;
        this.speed = 70;

        this.money = MathUtils.random(0, 5);
    }

    private void attack(Battler target) {
        target.setHealth(target.getHealth() - attack);
        playSoundIfExist(SoundFX.attack, GameState.globalVolume);
        if (target.getHealth() <= 0) {
            target.setHealth(0);
        }
        System.out.println("DEBUG: " + this.getType().toString() + " has attacked " + target.getType().toString() + " for " + attack + " damage.");
    }

    public void atbUpdateAndAttack(float delta, Battler target) {
        super.update(delta);
        atb -= (delta * speed);
        if (atb <= 0) {
            attack(target);
            atb = BASE_ATB;
        }
    }

    public int getHealth() { return health; }
    public float getATB() { return atb; }
    public int getMoney() { return this.money; }

    public void addMoney(float amount) { this.money += amount; }
    public void setHealth(int amount) { this.health = amount; }

    public static Battler newInstance(Battler object) {
        Rectangle rect = object.getSprite().getBoundingRectangle();
        return new Battler(rect, object.anims, object.sfx, object.getType(), object.getCurrAnimState());
    }
}
