package com.doylee.worldtraveller;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

/**
 * Created by Doyle on 10/01/2016.
 */
public class GameObj {
    public Rectangle rect;
    private Texture base;
    private Sound sfx;
    private GameState.Type type;

    public GameObj(Rectangle rect, Texture base, Sound sfx, GameState.Type type) {
        this.rect = rect;
        this.base = base;
        this.type = type;
        this.sfx = sfx;
    }

    public void act() {
        sfx.play();
    }

    public void render(SpriteBatch batch) {
        batch.draw(base, rect.x, rect.y, rect.width, rect.height);
    }

    public GameState.Type getType() { return type; }
}
