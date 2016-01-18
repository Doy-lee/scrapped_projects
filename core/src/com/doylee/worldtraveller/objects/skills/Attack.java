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

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float powerMultiplier) {
        super(rect, anims, sfx, type);
        this.attackEndPause = 0.0f;
        this.complete = false;
        this.powerMultiplier = powerMultiplier;
    }

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float endPause, float powerMultiplier) {
        super(rect, anims, sfx, type);
        this.attackEndPause = endPause;
        this.attackEndPauseTime = endPause;
        this.complete = false;
        this.powerMultiplier = powerMultiplier;
    }

    public boolean isComplete() { return complete; }
    public void setComplete(boolean flag) { complete = flag; }
    public float getPowerMultiplier() { return powerMultiplier; }

    public void update(float delta, Battler battler) {
        super.update(delta);
    }

    public String toString() {
        return "Abstract Class - Attack";
    }
}
