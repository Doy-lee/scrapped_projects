package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.g2d.Sprite;

import aurelienribon.tweenengine.TweenAccessor;

/**
 * Created by Doyle on 15/01/2016.
 */
public class SpriteAccessor implements TweenAccessor<Sprite> {

    public static final int ALPHA = 0;
    @Override
    public int getValues(Sprite sprite, int i, float[] floats) {
        int numFloats = 0;
        switch (i) {
            case ALPHA:
                floats[0] = sprite.getColor().a;
                numFloats = 1;
                break;

            default:
                System.err.println("Tween get values has invalid switch type");
                assert(false);
        }
        return numFloats;
    }

    @Override
    public void setValues(Sprite sprite, int i, float[] floats) {
        switch (i) {
            case ALPHA:
                sprite.setColor(sprite.getColor().r, sprite.getColor().g, sprite.getColor().b, floats[0]);
                break;

            default:
                System.err.println("Tween get values has invalid switch type");
                assert(false);
        }
    }
}
