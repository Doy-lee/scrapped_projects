package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;

/**
 * Created by Doyle on 2/01/2016.
 */
public class GamePlayer extends GameObj {
    public GameObj tent;

    public GamePlayer(float x, float y, float width, float height, boolean playAnim,
                      Texture tentTex, Animation playerAnim) {
        super(x, y, width, height, playAnim);
        this.addAnimation(playerAnim);
        // TODO: Fix up tent size, placeholder at the moment
        this.tent = new GameObj(this.getX(), this.getY(), 94.0f*1.5f, 77.0f*1.5f, false);
        this.tent.setTexture(tentTex);
        this.tent.setVisible(false);
    }

    public void act(float delta) {
        super.act(delta);
        if (this.isVisible()) {
            this.setX(this.getX() + (GameState.WORLD_SPEED_IN_PIXELS * delta));
        }
    }
}
