package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.GameState;

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

    private boolean triggerAttackArc;
    private Attack weapon;

    public Battler(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, States animState, Attack weapon) {
        super(rect, anims, sfx, type, animState);

        this.health = 100;
        this.attack = 20;
        this.money = MathUtils.random(0, 5);


        this.atb = BASE_ATB;
        this.speed = 70;

        triggerAttackArc = false;
        this.weapon = weapon;
    }

    protected void attack(Battler target) {
        triggerAttackArc = true;
        target.setHealth(target.getHealth() - attack);
        playSoundIfExist(SoundFX.attack, GameState.globalVolume);

        if (target.getHealth() <= 0) {
            target.setHealth(0);
        }

        System.out.println("DEBUG: " + this.getType().toString() + " has attacked " + target.getType().toString() + " for " + attack + " damage.");
    }

    public void atbUpdateAndAttack(float delta, Battler target) {
        // NOTE: Super updates animation key frames
        atb -= (delta * speed);
        if (atb <= 0) {

            weapon.getSprite().setY(this.getSprite().getY() + GameState.SPRITE_SIZE);
            if (currAnimState == States.battle_right) {
                weapon.getSprite().setX(this.getSprite().getX() + GameState.SPRITE_SIZE);
            } else {
                weapon.getSprite().setX(this.getSprite().getX() - GameState.SPRITE_SIZE);
            }

            attack(target);
            atb = BASE_ATB;
        }
    }

    public void render(SpriteBatch batch) {
        super.render(batch);
        if (triggerAttackArc) {
            weapon.getSprite().draw(batch);
        }
    }

    public void update(float delta) {
        super.update(delta);

        if (triggerAttackArc) {
            weapon.update(delta);
            if (weapon.isComplete()) {
                weapon.setComplete(false);
                triggerAttackArc = false;
            }
        }
    }

    public int getHealth() { return health; }
    public float getATB() { return atb; }
    public int getMoney() { return this.money; }
    public Attack getWeapon() { return this.weapon; }

    public void addMoney(float amount) { this.money += amount; }
    public void setHealth(int amount) { this.health = amount; }

    public static Battler newInstance(Battler object) {
        Rectangle rect = object.getSprite().getBoundingRectangle();
        return new Battler(rect, object.anims, object.sfx, object.getType(), object.getCurrAnimState(), object.getWeapon());
    }
}
