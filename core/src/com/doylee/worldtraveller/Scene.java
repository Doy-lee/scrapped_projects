package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

/**
 * Created by Doyle on 10/01/2016.
 */
public class Scene {
    private Texture backdrop;
    public Rectangle rect;
    private IntMap<Texture> assets;
    private Array<GameObj> sceneObjs;
    private boolean isAnimated;

    public Scene(Texture backdrop, IntMap<Texture> assets, boolean isAnimated) {
        this.backdrop = backdrop;
        this.rect = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.assets = assets;
        this.sceneObjs = new Array<GameObj>();
        this.isAnimated = isAnimated;
    }

    public void update(float delta) {
        if (isAnimated) {
            for (GameObj obj : sceneObjs) {
                obj.rect.x -= GameState.RUN_SPEED_IN_PIXELS * delta;
            }
            rect.x -= GameState.RUN_SPEED_IN_PIXELS * delta;
            if (rect.x <= -rect.width) rect.x = 0;
        }
    }

    public void render(SpriteBatch batch) {
        batch.draw(backdrop, rect.x, rect.y, rect.getWidth(), rect.getHeight());
        // Double buffer up for screen scrolling
        if (isAnimated) {
            batch.draw(backdrop, rect.getWidth() + rect.x, rect.y, rect.getWidth(), rect.getHeight());
        }

       for (GameObj obj : sceneObjs) {
           obj.render(batch);
       }
    }

    public Array<GameObj> getSceneObj() { return sceneObjs; }

}
