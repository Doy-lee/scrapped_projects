package com.doylee.worldtraveller;

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

    public GameState(Hero hero) {
        this.hero = hero;
    }

    public void update(float delta) {
        hero.update(delta);
    }

    public Hero getHero() {
        return hero;
    }

}
