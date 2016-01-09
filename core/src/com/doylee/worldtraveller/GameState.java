package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

/**
 * Created by Doyle on 9/01/2016.
 */
public class GameState {
    private static float ONE_SECOND = 1.0f;

    // NOTE: Units of 'need' to deplete per second
    public static float ENERGY_RATE = 0.1f;
    public static float HUNGER_RATE = 0.3f;
    public static float THIRST_RATE = 0.3f;

    private Hero hero;
    private Scene homeScene;
    private Scene adventureScene;
    private Scene currScene;

    public GameState(Hero hero) {
        this.hero = hero;

        Array<Texture> homeAssets = new Array<Texture>();
        Texture homeTex = new Texture(Gdx.files.internal("backdrop.png"));
        this.homeScene = new Scene(homeTex, homeAssets);

        Array<Texture> adventAssets = new Array<Texture>();
        Texture adventTex = new Texture(Gdx.files.internal("forest_night_1.png"));
        this.adventureScene = new Scene(adventTex, adventAssets);

        this.currScene = homeScene;
    }

    public void update(float delta) {
        hero.update(delta);
    }

    public Hero getHero() { return hero; }
    public Scene getCurrScene() { return currScene; }
    public Scene getHomeScene() { return homeScene; }
    public Scene getAdventureScene() { return adventureScene; }
    public void setCurrScene(Scene scene) { this.currScene = scene; }

}
