package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

/**
 * Created by Doyle on 10/01/2016.
 */
public class Util {

    // TODO: We assume all animations are consecutively lined up in one row
    static public Animation extractAnim(TextureRegion[][] frames, float duration,
                                  Vector2 startSpriteLoc, int animLength) {

        TextureRegion[] animFrames = new TextureRegion[animLength];

        int index = 0;
        for (int i = (int)startSpriteLoc.x; i < animLength; i++) {
            animFrames[index++] = frames[(int)startSpriteLoc.y][i];
        }
        Animation result = new Animation(duration, animFrames);
        return result;
    }

    static public Animation extractAnim(TextureRegion[] frames, float duration,
                                        Vector2 startSpriteLoc, int animLength) {

        TextureRegion[] animFrames = new TextureRegion[animLength];

        int index = 0;
        for (int i = (int)startSpriteLoc.x; i < animLength; i++) {
            animFrames[index++] = frames[i];
        }
        Animation result = new Animation(duration, animFrames);
        return result;
    }

    static public float abs(float value) {
        float result;
        if (value <= 0) {
           result = -value;
        } else {
            result = value;
        }

        return result;
    }

    static public float min(float a, float b) {
        float result;
        if (a < b) result = a;
        else result = b;
        return result;
    }

    static public float max(float a, float b) {
        float result;
        if (a < b) result = b;
        else result = a;
        return result;
    }
}
