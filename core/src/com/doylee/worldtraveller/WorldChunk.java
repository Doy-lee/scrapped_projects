package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * Created by Doyle on 31/12/2015.
 */
public class WorldChunk extends Actor {
    private Texture background;

    public WorldChunk(float x, float y, float width, float height, Texture background) {
        this.background = background;
        this.setBounds(x, y, width, height);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.draw(background, this.getX(), this.getY(), Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
