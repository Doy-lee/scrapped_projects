package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.objects.GameObj;

/**
 * Created by Doyle on 10/01/2016.
 */
public class Scene {
    private Texture backdrop;
    private Music currSong;

    public Rectangle rect;
    private IntMap<Texture> assets;
    private IntMap<Music> music;
    private Array<GameObj> sceneObjs;
    private Array<GameObj> battleObjs;
    private boolean isAnimated;

    public enum ScnMusic {
        background, battle
    }

    public Scene(Texture backdrop, IntMap<Texture> assets, Array<GameObj> sceneObjs, IntMap<Music> music, boolean isAnimated) {
        this.backdrop = backdrop;
        this.rect = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.assets = assets;
        this.music = music;
        this.sceneObjs = sceneObjs;
        this.isAnimated = isAnimated;

        this.currSong = music.get(ScnMusic.background.ordinal());
    }

    public void update(GameState state, float delta) {
        GameState.Battle battleState = state.getBattleState();
        if (battleState != GameState.Battle.active) {

            float worldMoveSpeed = state.getWorldMoveSpeed();
            float totalMoveDelta = worldMoveSpeed * state.globalObjectSpeedModifier * delta;

            for (GameObj obj : sceneObjs) {
                if (obj.getType() != GameObj.Type.hero) {
                    obj.getSprite().setX(obj.getSprite().getX() - totalMoveDelta);
                } else {
                    // TODO: Should we tie the hero movement to stage and then counteract stage moving?
                    // TODO: Otherwise the hero is in actuality stationary until we need to shift him to battle mode
                    switch (battleState) {
                        case transitionIn:
                            obj.getSprite().setX(obj.getSprite().getX() - totalMoveDelta);
                            break;
                        case transitionOut:
                            obj.getSprite().setX(obj.getSprite().getX() + totalMoveDelta);
                            break;
                    }
                }
            }

            if (isAnimated) {
                rect.x -= worldMoveSpeed * delta;
                if (rect.x <= -rect.width) rect.x = 0;
            }

            if (currSong != music.get(ScnMusic.background.ordinal())) {
                currSong.stop();
                currSong = music.get(ScnMusic.background.ordinal());
            }
        } else {
            // battle active
            if (currSong != music.get(ScnMusic.battle.ordinal())) {
                currSong.stop();
                currSong = music.get(ScnMusic.battle.ordinal());
            }
        }

        for (GameObj obj : sceneObjs) {
            obj.update(delta);
        }
    }

    public void render(SpriteBatch batch, float volume) {
        batch.draw(backdrop, rect.x, rect.y, rect.getWidth(), rect.getHeight());
        // Double buffer up for screen scrolling
        if (isAnimated) {
            batch.draw(backdrop, rect.getWidth() + rect.x, rect.y, rect.getWidth(), rect.getHeight());
        }

        if (!currSong.isPlaying()) {
            currSong.setVolume(volume);
            currSong.play();
        }

       for (GameObj obj : sceneObjs) {
           obj.render(batch);
       }
    }

    public void dispose() {
    }

    public Music getCurrSong() { return currSong; }

    public Array<GameObj> getSceneObj() { return sceneObjs; }

}
