package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;

/**
 * Created by Doyle on 31/12/2015.
 */
public abstract class GameObj extends Group {
    private Texture baseTexture;
    private Animation anim;
    private boolean isAnimated;
    private float stateTime;

    public GameObj(float x, float y, float width, float height, boolean isAnimated) {
        this.isAnimated = isAnimated;
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
        // TODO: Currently draws parent first, then the most child-element and upwards
        if (anim == null || isAnimated == false) {
            if (baseTexture == null) System.err.println("Base texture does not exist");
            batch.draw(baseTexture, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        } else {
            TextureRegion currFrame = anim.getKeyFrame(stateTime, true);
            batch.draw(currFrame, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
        super.draw(batch, parentAlpha);
    }
}

