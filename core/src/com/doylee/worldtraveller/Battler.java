package com.doylee.worldtraveller;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 12/01/2016.
 */
public class Battler extends GameObj {
    private int health;
    private int attack;

    private int money;
    public Battler(Rectangle rect, IntMap<Animation> anims, Sound sfx, Type type, States animState) {
        super(rect, anims, sfx, type, animState);

        this.health = 100;
        this.attack = 20;

        this.money = 0;
    }

    public void attack(Battler target) {
        target.setHealth(target.getHealth() - attack);
        if (target.getHealth() <= 0) {
            target.setHealth(0);
        }
    }

    public int getHealth() { return health; }
    public int getMoney() { return this.money; }

    public void addMoney(float amount) { this.money += amount; }
    public void setHealth(int amount) { this.health = amount; }

}
