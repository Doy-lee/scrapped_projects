package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;

// TODO: Read this https://github.com/libgdx/libgdx/wiki/Projection,-viewport,-&-cavatarWalkamera
public class GameScreen implements Screen {
	final WorldTraveller game;

	// Assets
	private Texture coinTex;
	private Sound coinSfx;
	private Music backgroundMusic;

	// World intrinsics
	private BitmapFont DEBUGFont;
	private OrthographicCamera camera;
	private Stage uiStage;
	private Table uiTable;

	public GameScreen(final WorldTraveller wtGame) {
		game = wtGame;

		// Assets
		coinTex = new Texture(Gdx.files.internal("coin.png"));
		backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("ffvi_searching_for_friends.mp3"));
		backgroundMusic.setLooping(true);
		coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin1.wav"));

		// World intrinsics
		DEBUGFont = new BitmapFont();
		DEBUGFont.setColor(Color.GREEN);
		camera = new OrthographicCamera();
		// NOTE: Match camera to the device resolution
		// setToOrtho(Y Orientation=Down, viewPortWidth, viewPortHeight)
		camera.setToOrtho(false, Gdx.graphics.getWidth(),
				Gdx.graphics.getHeight());

		setupGUI();
	}

	public void setupGUI() {
		uiStage = new Stage(new ScreenViewport(), game.batch);
		Gdx.input.setInputProcessor(uiStage);

		TextButton campBtn = new TextButton("Camp", game.skin);
		campBtn.pad(10);
		campBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
			}
		});

		TextButton menuBtn = new TextButton("Menu", game.skin);
		menuBtn.pad(10);
		menuBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
			}
		});

		TextButton buyBtn = new TextButton("Buy", game.skin);
		buyBtn.pad(10);
		buyBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
			}
		});

		uiTable = new Table(game.skin);
		uiTable.bottom().left();
		uiTable.setDebug(true);
		uiTable.setFillParent(true);
		uiTable.add(campBtn);
		uiTable.add(menuBtn);
		uiTable.add(buyBtn);

		uiStage.addActor(uiTable);
	}

	@Override
	public void show() {
		//backgroundMusic.play();
	}

	public void render(float delta) {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		game.batch.setProjectionMatrix(camera.combined);

        // RENDER DEBUG FONT
        DEBUGFont.draw(game.batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
                20.0f, (Gdx.graphics.getHeight() - 20.0f));
        DEBUGFont.draw(game.batch, "Gdx FramesPerSec: " +
                        Gdx.graphics.getFramesPerSecond(), 20.0f,
                (Gdx.graphics.getHeight() - 40.0f));
        DEBUGFont.draw(game.batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
                        (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
                (Gdx.graphics.getHeight() - 60.0f));
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
		coinTex.dispose();
		uiStage.dispose();
		backgroundMusic.dispose();
	}
}
