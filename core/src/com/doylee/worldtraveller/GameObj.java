package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

/**
 * Created by Doyle on 10/01/2016.
 */
public class GameObj {
    public Rectangle rect;
    private Texture base;
    private GameState.Type type;

    public GameObj(Rectangle rect, Texture base, GameState.Type type) {
        this.rect = rect;
        this.base = base;
        this.type = type;
    }

    public void render(SpriteBatch batch) {
        batch.draw(base, rect.x, rect.y, rect.width, rect.height);
    }

    public GameState.Type getType() { return type; }
}
