package com.doylee.worldtraveller.objects.skills;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.objects.Battler;
import com.doylee.worldtraveller.objects.GameObj;

/**
 * Created by Doyle on 17/01/2016.
 */

// TODO: Add cooldown, reset/initialise methods to implement

public abstract class Attack extends GameObj {

    protected float attackEndPauseTime;
    protected float attackEndPause;
    protected boolean complete;
    private float powerMultiplier;
    private float cooldown;
    private float cooldownTimer;
    private boolean needsCooldown;

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float powerMultiplier, float cooldown) {
        super(rect, anims, sfx, type);
        this.attackEndPause = 0.0f;
        this.complete = false;
        this.powerMultiplier = powerMultiplier;
        this.cooldown = cooldown;
        this.cooldownTimer = cooldown;
        this.needsCooldown = false;
    }

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float endPause, float powerMultiplier, float cooldown) {
        super(rect, anims, sfx, type);
        this.attackEndPause = endPause;
        this.attackEndPauseTime = endPause;
        this.complete = false;
        this.powerMultiplier = powerMultiplier;
        this.cooldown = cooldown;
        this.cooldownTimer = cooldown;
        this.needsCooldown = false;
    }

    public boolean isComplete() { return complete; }
    public void setComplete(boolean flag) { complete = flag; }
    public float getPowerMultiplier() { return powerMultiplier; }

    public boolean needsCooldown() { return needsCooldown; }
    public void setNeedsCooldown(boolean needsCooldown) { this.needsCooldown = needsCooldown; }

    public void updateCooldown(float delta) {
        cooldownTimer -= delta;
        if (cooldownTimer <= 0) {
            cooldownTimer = cooldown;
            needsCooldown = false;
        }
    }

    public float getCooldownTimer() { return this.cooldownTimer; }
    public void update(float delta, Battler battler) {
        super.update(delta);
    }

    public String toString() {
        return "Abstract Class - Attack";
    }
}
