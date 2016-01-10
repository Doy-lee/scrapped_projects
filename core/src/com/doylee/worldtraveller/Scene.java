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

    public Scene(Texture backdrop, IntMap<Texture> assets, Array<GameObj> sceneObjs, boolean isAnimated) {
        this.backdrop = backdrop;
        this.rect = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.assets = assets;
        this.sceneObjs = sceneObjs;
        this.isAnimated = isAnimated;
    }

    public void update(GameState state, float delta) {
        if (isAnimated) {
            float worldMoveSpeed = state.getWorldMoveSpeed();
            GameState.Battle battleState = state.getBattleState();

            float totalMoveDelta = worldMoveSpeed * state.globalObjectSpeedModifier * delta;

            for (GameObj obj : sceneObjs) {
                if (obj.getType() != GameObj.Type.hero) {
                    obj.getSprite().setX(obj.getSprite().getX() - totalMoveDelta);
                } else {
                    // TODO: Should we tie the hero movement to stage and then counteract stage moving?
                    // TODO: Otherwise the hero is in actuality stationary until we need to shift him to battle mode
                    if (battleState == GameState.Battle.transition) {
                        obj.getSprite().setX(obj.getSprite().getX() - totalMoveDelta);
                    }
                }
            }

            rect.x -= worldMoveSpeed * delta;
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
