package com.doylee.worldtraveller;

/**
 * Created by Doyle on 2/01/2016.
 */
public class GamePlayer extends GameObj {

    public GamePlayer(float x, float y, float width, float height, boolean isAnimated) {
        super(x, y, width, height, isAnimated);
    }

    public void act(float delta) {
        super.act(delta);
        if (this.isVisible()) {
            this.setX(this.getX() + (GameState.WORLD_SPEED_IN_PIXELS * delta));
        }
    }
}
