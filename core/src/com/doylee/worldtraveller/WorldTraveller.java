package com.doylee.worldtraveller;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.doylee.worldtraveller.screens.GameScreen;
import com.doylee.worldtraveller.screens.HomeScreen;

public class WorldTraveller extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public Skin skin;
    public GameState state;

    public void create() {
        batch = new SpriteBatch();
        //Use LibGDX's default Arial font.

        // TODO: Using default atlas change to mine
        TextureAtlas atlas = new TextureAtlas(Gdx.files.internal("template.atlas"));
        skin = new Skin(Gdx.files.internal("ui.json"), atlas);
        skin.getFont("default-font").getData().setScale(0.35f);

        font = skin.getFont("default-font");

        // TODO: Temporary to allow for faster debugging, jump straight to game
        this.state = new GameState();
        this.setScreen(new HomeScreen(this));
        //this.setScreen(new GameScreen(this));
    }

    public void render() {
        super.render(); //important!
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
    }

}
