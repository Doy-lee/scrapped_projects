package com.doylee.worldtraveller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

/**
 * Created by Doyle on 10/01/2016.
 */
public class Scene {
    private Texture backdrop;
    private Array<Texture> assets;

    public Scene(Texture backdrop, Array<Texture> assets) {
        this.backdrop = backdrop;
        this.assets = assets;
    }
    public Texture getBackdrop() { return backdrop; }
}
