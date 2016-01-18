package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Queue;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.objects.skills.Attack;

import java.util.Iterator;

/**
 * Created by Doyle on 12/01/2016.
 */
public class Battler extends GameObj {
    public static float BASE_ATB = 100.0f;
    private int health;
    private int attack;
    private int money;

    private float atb;
    private int speed;

    private boolean triggerAttack;
    private Attack currAttack;
    private Queue attackList;

    public Battler(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Queue attackList) {
        super(rect, anims, sfx, type, animState);

        this.health = 100;
        this.attack = 20;
        this.money = MathUtils.random(0, 5);


        this.atb = BASE_ATB;
        this.speed = 70;

        triggerAttack = false;
        this.currAttack = null;
        this.attackList = attackList;
    }

    protected void attack(Battler target) {
        triggerAttack = true;
        int damage = (int)(attack * currAttack.getPowerMultiplier());
        target.setHealth(target.getHealth() - damage);
        playSoundIfExist(SoundFX.attack, GameState.globalVolume);

        if (target.getHealth() <= 0) {
            target.setHealth(0);
        }

        System.out.println("DEBUG: " + this.getType().toString() + " has attacked " + target.getType().toString() + " for " + damage + " damage.");
    }

    public void atbUpdateAndAttack(float delta, Battler target) {
        // NOTE: Super updates animation key frames
        atb -= (delta * speed);
        if (atb <= 0) {

            Iterator attackIt = attackList.iterator();
            Attack bestAttack = (Attack)attackList.first();
            while (attackIt.hasNext()) {
                Attack skill = (Attack)attackIt.next();
                if (skill.getPowerMultiplier() >= bestAttack.getPowerMultiplier()) {
                    bestAttack = skill;
                }
            }

            currAttack = bestAttack;
            System.out.println("currAttack: " + currAttack.toString());

            attack(target);
            atb = BASE_ATB;
        }
    }

    public void render(SpriteBatch batch) {
        super.render(batch);
        if (triggerAttack) {
            currAttack.getSprite().draw(batch);
        }
    }

    public void update(float delta) {
        super.update(delta);

        if (triggerAttack) {

            currAttack.update(delta, this);

            if (currAttack.isComplete()) {
                currAttack.setComplete(false);
                triggerAttack = false;
            }
        }
    }

    public int getHealth() { return health; }
    public float getATB() { return atb; }
    public int getMoney() { return this.money; }
    public Queue getAttackList() { return this.attackList; }

    public void addMoney(float amount) { this.money += amount; }
    public void setHealth(int amount) { this.health = amount; }

    public static Battler newInstance(Battler object) {
        Rectangle rect = object.getSprite().getBoundingRectangle();
        return new Battler(rect, object.anims, object.sfx, object.getType(), object.getCurrAnimState(), object.getAttackList());
    }
}
