package com.doylee.worldtraveller.objects.skills;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.objects.Battler;

/**
 * Created by Doyle on 19/01/2016.
 */
public class Fireball extends Attack {
    public Fireball(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float powerMultiplier) {
        super(rect, anims, sfx, type, powerMultiplier);
    }

    public Fireball(Rectangle rect, IntMap<Animation> anims, IntMap<Sound> sfx, Type type, float endPause, float powerMultiplier) {
        super(rect, anims, sfx, type, endPause, powerMultiplier);
    }

    public void update(float delta, Battler battler) {
        super.update(delta, battler);

        getSprite().setY(battler.getSprite().getY() + GameState.SPRITE_SIZE/2);
        float movSpeed = 1500.0f;
        float newPosX = getSprite().getX() + (movSpeed * delta);
        getSprite().setX(newPosX);

        if (getSprite().getX() >= Gdx.graphics.getWidth()) {
            this.getSprite().setX(battler.getSprite().getX());
            this.getSprite().setY(battler.getSprite().getY());
            complete = true;
        }
    }

    public String toString() {
        return "Fireball";
    }
}
