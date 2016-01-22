package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.doylee.worldtraveller.objects.Battler;
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
    private boolean isAnimated;

    public enum ScnMusic {
        background, battle
    }

    public Scene(Texture backdrop, IntMap<Texture> assets, Array<GameObj> sceneObjs, IntMap<Music> music, boolean isAnimated) {
        this.backdrop = backdrop;
        this.currSong = music.get(ScnMusic.background.ordinal());

        this.rect = new Rectangle(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.assets = assets;
        this.music = music;
        this.sceneObjs = sceneObjs;
        this.isAnimated = isAnimated;
    }

    public void update(GameState state, float delta) {
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
    public void setCurrSong(Music song) { currSong = song; }
    public IntMap<Music> getMusic() { return music; }
    public boolean isAnimated() { return isAnimated; }

    public Array<GameObj> getSceneObj() { return sceneObjs; }

}
