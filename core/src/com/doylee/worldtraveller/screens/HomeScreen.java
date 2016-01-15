package com.doylee.worldtraveller.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.doylee.worldtraveller.SpriteAccessor;
import com.doylee.worldtraveller.WorldTraveller;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;

public class HomeScreen implements Screen {
    final WorldTraveller game;
    private OrthographicCamera camera;

    private Sprite logoSplash;
    private TweenManager tweenManager;

    public HomeScreen(final WorldTraveller wtGame) {
        game = wtGame;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(),
                          Gdx.graphics.getHeight());

        // TODO: Use just our inbuilt font?
        Texture logoTex = new Texture(Gdx.files.internal("logo.png"));
        logoSplash = new Sprite(logoTex);
        logoSplash.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Tween.registerAccessor(Sprite.class, new SpriteAccessor());
        tweenManager = new TweenManager();
        int tweenDuration = 1;
        Tween.set(logoSplash, SpriteAccessor.ALPHA).target(0).start(tweenManager);
        Tween.to(logoSplash, SpriteAccessor.ALPHA, tweenDuration).target(1)
                .repeatYoyo(1, 2).start(tweenManager);

    }

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0.0f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        tweenManager.update(delta);

        game.batch.begin();
        if (tweenManager.getRunningTweensCount() == 0 || Gdx.input.isTouched()) {
            game.setScreen(new GameScreen(game));
            dispose();
        } else {
            logoSplash.draw(game.batch);
        }
        game.batch.end();

    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        logoSplash.getTexture().dispose();
    }
}
