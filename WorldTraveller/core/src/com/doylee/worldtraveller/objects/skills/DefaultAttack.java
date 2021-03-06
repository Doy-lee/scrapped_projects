package com.doylee.worldtraveller.objects.skills;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.Util;
import com.doylee.worldtraveller.objects.Battler;

/**
 * Created by Doyle on 19/01/2016.
 */
public class DefaultAttack extends Attack {
    private static final float END_PAUSE_TIME = 0.5f;
    private float endPauseTimer;

    public DefaultAttack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float powerMultiplier, float cooldown) {
        super(rect, anims, sfx, type, powerMultiplier, cooldown);
        this.endPauseTimer = END_PAUSE_TIME;
    }

    public void update(float delta, Battler battler) {
        super.update(delta, battler);

        getSprite().setOrigin(0, 0);
        getSprite().setY(battler.getSprite().getY() + GameState.SPRITE_SIZE);
        if (battler.getCurrAnimState() == States.battle_right) {
            this.getSprite().setX(battler.getSprite().getX() + GameState.SPRITE_SIZE);
        } else if (battler.getCurrAnimState() == States.battle_left){
            this.getSprite().setX(battler.getSprite().getX() - GameState.SPRITE_SIZE);
        }

        // NOTE: LibGDX default rotation is counter-clockwise, so to move
        // clockwise we need to use negative degrees
        float currRotation = getSprite().getRotation();
        float targetRotation = -30.0f;
        float rotationStep = Util.abs(targetRotation * 10.0f);

        if (currRotation >= targetRotation) {
            currRotation -= (rotationStep * delta);
            getSprite().setRotation(currRotation);
        } else {
            if ((endPauseTimer -= delta) <= 0) {
                getSprite().setRotation(0);
                endPauseTimer = END_PAUSE_TIME;
                complete = true;
            }
        }
    }

    public String toString() {
        return "DefaultAttack";
    }
}
