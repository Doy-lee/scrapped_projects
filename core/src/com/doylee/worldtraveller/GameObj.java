package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;

/**
 * Created by Doyle on 31/12/2015.
 */
public class GameObj extends Group {
    private Texture baseTexture;
    private Animation anim;
    private boolean playAnim;
    private float stateTime;

    public GameObj(float x, float y, float width, float height, boolean playAnim) {
        this.playAnim = playAnim;
        this.setBounds(x, y, width, height);
        stateTime = 0f;
    }

    public void addAnimation(Animation anim) {
        this.anim = anim;
    }

    public void setTexture(Texture tex) {
        this.baseTexture = tex;
    }

    public void act(float delta) {
        // NOTE: Update state time for animations
        stateTime += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (anim == null || playAnim == false) {
            if (baseTexture == null) System.err.println("Base texture does not exist");
            batch.draw(baseTexture, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        } else {
            TextureRegion currFrame = anim.getKeyFrame(stateTime, true);
            batch.draw(currFrame, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }
}

