package com.doylee.worldtraveller.objects;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.Util;

/**
 * Created by Doyle on 17/01/2016.
 */
public class Attack extends GameObj {

    private float attackEndPauseTime;
    private float attackEndPause;
    private boolean complete;

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type) {
        super(rect, anims, sfx, type);
        attackEndPause = 0.0f;
        complete = false;
    }

    public Attack(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float endPause) {
        super(rect, anims, sfx, type);
        attackEndPause = endPause;
        attackEndPauseTime = endPause;
        complete = false;
    }

    public boolean isComplete() { return complete; }
    public void setComplete(boolean flag) { complete = flag; }

    @Override
    public void update(float delta) {
        super.update(delta);

        getSprite().setOrigin(0, 0);

        // NOTE: LibGDX default rotation is counter-clockwise, so to move
        // clockwise we need to use negative degrees
        float currRotation = getSprite().getRotation();
        float targetRotation = -30.0f;
        float rotationStep = Util.abs(targetRotation * 10.0f);

        if (currRotation >= targetRotation) {
            currRotation -= (rotationStep * delta);
            getSprite().setRotation(currRotation);
        } else {
            if ((attackEndPause -= delta) <= 0) {
                getSprite().setRotation(0);
                attackEndPause = attackEndPauseTime;
                complete = true;
            }
        }

    }
}
