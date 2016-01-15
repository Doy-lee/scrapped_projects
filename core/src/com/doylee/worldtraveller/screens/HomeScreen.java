package com.doylee.worldtraveller.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.doylee.worldtraveller.SpriteAccessor;
import com.doylee.worldtraveller.Util;
import com.doylee.worldtraveller.WorldTraveller;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;

public class HomeScreen implements Screen {
    final WorldTraveller game;
    private OrthographicCamera camera;

    private Sprite splashScreen;
    private TweenManager tweenManager;

    private Stage stage;
    private Table table;

    public HomeScreen(final WorldTraveller wtGame) {
        game = wtGame;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(),
                          Gdx.graphics.getHeight());

        stage = new Stage(new ScreenViewport(camera), game.batch);
        Gdx.input.setInputProcessor(stage);

        Texture splashTex = new Texture(Gdx.files.internal("splash.png"));
        splashScreen = new Sprite(splashTex);
        splashScreen.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Tween.registerAccessor(Sprite.class, new SpriteAccessor());
        tweenManager = new TweenManager();
        int tweenDuration = 1;
        Tween.set(splashScreen, SpriteAccessor.ALPHA).target(0).start(tweenManager);
        Tween.to(splashScreen, SpriteAccessor.ALPHA, tweenDuration).target(1).start(tweenManager);
        Tween.to(splashScreen, SpriteAccessor.ALPHA, tweenDuration).delay(tweenDuration + 2).target(0).start(tweenManager);

        TextButton debugButton = new TextButton("Start", game.skin);
        debugButton.pad(20);

        table = new Table(game.skin);
        table.setFillParent(true);
        table.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        //table.add(debugButton);
        //table.row();
        stage.addActor(table);
    }

    @Override
    public void show() {
    }

    private float deltaSum = 0.0f;
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0.0f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        tweenManager.update(delta);

        stage.act(delta);
        stage.draw();

        game.batch.begin();
        if (tweenManager.getRunningTweensCount() == 0) {
            game.setScreen(new GameScreen(game));
            dispose();
        } else {
            splashScreen.draw(game.batch);
        }
        game.batch.end();

    }

    @Override
    public void resize(int width, int height) {
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

    }
}
