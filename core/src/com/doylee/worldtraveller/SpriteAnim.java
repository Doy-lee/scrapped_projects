package com.doylee.worldtraveller;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

// TODO: https://github.com/libgdx/libgdx/wiki/2D-Animation
public class SpriteAnim {
    private Array<Rectangle> anim;
    private int length;
    private int currFrame;
    private float updateSpeedSeconds;
    private float frameAdvanceCounter;

    public SpriteAnim(Array<Rectangle> anim, int length, float updateSpeedSeconds) {
        this.anim = anim;
        this.length = length;
        this.updateSpeedSeconds = updateSpeedSeconds;
        frameAdvanceCounter = updateSpeedSeconds;
    }

    public Rectangle getCurrFrame() {
        return anim.get(currFrame);
    }

    public void update(float delta) {
        frameAdvanceCounter -= delta;
        if (frameAdvanceCounter <= 0) {
            if (currFrame < length-1) {
                currFrame++;
            } else {
                currFrame = 0;
            }
            frameAdvanceCounter = updateSpeedSeconds;
        }

    }

}
