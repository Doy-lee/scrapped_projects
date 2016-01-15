package com.doylee.worldtraveller;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.doylee.worldtraveller.screens.HomeScreen;

public class WorldTraveller extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public Skin skin;
    public GameState state;

    public void create() {
        batch = new SpriteBatch();
        //Use LibGDX's default Arial font.
        font = new BitmapFont();

        // TODO: Using default atlas change to mine
        TextureAtlas atlas = new TextureAtlas(Gdx.files.internal("template.atlas"));
        skin = new Skin(Gdx.files.internal("ui.json"), atlas);
        skin.getFont("default-font").getData().setScale(0.2f);

        this.state = new GameState();
        this.setScreen(new HomeScreen(this));
    }

    public void render() {
        super.render(); //important!
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
    }

}
