package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Queue;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.objects.skills.Attack;

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
    private Array<Attack> attackList;
    private Queue queuedAttacks;

    public Battler(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Array<Attack> attackList) {
        super(rect, anims, sfx, type, animState);

        this.health = 100;
        this.attack = 20;
        this.money = MathUtils.random(0, 5);


        this.atb = BASE_ATB;
        this.speed = 70;

        triggerAttack = false;
        this.currAttack = null;
        this.attackList = attackList;
        this.queuedAttacks = new Queue();
    }

    protected void attack(Battler target) {
        triggerAttack = true;
        int damage = (int)(attack * currAttack.getPowerMultiplier());
        target.setHealth(target.getHealth() - damage);
        playSoundIfExist(SoundFX.attack, GameState.globalVolume);

        if (target.getHealth() <= 0) {
            target.setHealth(0);
        }

        System.out.println("DEBUG: " + this.getType().toString() + " has attacked " + target.getType().toString() + " with " + currAttack + " for " + damage + " damage.");
    }

    public void atbUpdateAndAttack(float delta, Battler target) {
        // NOTE: Super updates animation key frames
        atb -= (delta * speed);
        if (atb <= 0) {

            Attack bestAttack = null;
            for (int i = 0; i < attackList.size; ++i) {
                Attack atk = attackList.get(i);
                if (bestAttack == null) {
                    bestAttack = atk;
                } else {
                    if (!(atk.needsCooldown()) && (atk.getPowerMultiplier() >=
                            bestAttack.getPowerMultiplier())) {
                        bestAttack = atk;
                    }
                }
            }

            queuedAttacks.addLast(bestAttack);
            currAttack = (Attack)queuedAttacks.first();

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
                currAttack.setNeedsCooldown(true);

                if (queuedAttacks.size > 0) {
                    queuedAttacks.removeFirst();
                }
            }
        }

        for (int i = 0; i < attackList.size; ++i) {
            if (attackList.get(i).needsCooldown()) {
                attackList.get(i).updateCooldown(delta);
            }
        }
    }

    public int getHealth() { return health; }
    public float getATB() { return atb; }
    public int getMoney() { return this.money; }
    public Array<Attack> getAttackList() { return this.attackList; }
    public Queue getQueuedAttacks() { return this.queuedAttacks; }

    public void addMoney(float amount) { this.money += amount; }
    public void setHealth(int amount) { this.health = amount; }

    public static Battler newInstance(Battler object) {
        Rectangle rect = object.getSprite().getBoundingRectangle();
        return new Battler(rect, object.anims, object.sfx, object.getType(), object.getCurrAnimState(), object.getAttackList());
    }
}
