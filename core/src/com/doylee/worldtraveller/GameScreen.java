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
	private GameState state;

	// Assets
	private Texture coinTex;
	private Music backgroundMusic;
	private Sound coinSfx;

	// World intrinsics
	private BitmapFont DEBUGFont;
	private OrthographicCamera camera;
	private Stage uiStage;
	private Stage gameStage;
	private float oneSecondCounter;
	private Table table;

	public GameScreen(final WorldTraveller wtGame) {
		game = wtGame;
		state = new GameState();
		state.generateWorld();

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
		updateGameStage();
		oneSecondCounter = 0;
	}

	public void updateGameStage() {
		gameStage = new Stage(new ScreenViewport(camera), game.batch);
		for (WorldChunk chunk : state.world.chunks) {
			gameStage.addActor(chunk);
		}
		gameStage.addActor(state.hero);
		gameStage.addActor(state.hero.tent);
	}

	public void setupGUI() {
		uiStage = new Stage(new ScreenViewport(), game.batch);
		Gdx.input.setInputProcessor(uiStage);

		TextButton campBtn = new TextButton("Camp", game.skin);
		campBtn.pad(10);
		campBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (state.isTentActive) state.setTentMode(false);
				else state.setTentMode(true);
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

		table = new Table(game.skin);
		table.bottom().left();
		table.setDebug(true);
		table.setFillParent(true);
		table.add(campBtn);
		table.add(menuBtn);
		table.add(buyBtn);

		uiStage.addActor(table);
	}

	@Override
	public void show() {
		//backgroundMusic.play();
	}

	public void render(float delta) {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		game.batch.setProjectionMatrix(camera.combined);

		if (state.hero.getX() >= state.world.getWorldSizeInPixels()) {
			System.out.println("DEBUG Cleared stage");
			state.generateWorld();
			state.resetHeroPosition();
			this.updateGameStage();
		} else {
			// NOTE: Camera positions sets the center point of the camera view port
			float camOriginX = camera.position.x - (Gdx.graphics.getWidth()/2) + state.hero.getWidth()/2;
			if (camOriginX  <= Gdx.graphics.getWidth() * (state.world.chunks.size-1)) {
				camera.position.set(state.hero.getX() + state.hero.getWidth(), Gdx.graphics.getHeight()/2, 0);
			}
			camera.update();

			// Toggle camp
			if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
				if (state.isTentActive) state.setTentMode(false);
				else state.setTentMode(true);
			}

			if (state.isTentActive == false) {
				oneSecondCounter += Gdx.graphics.getDeltaTime();
				if (oneSecondCounter >= 1.0f) {
					state.distTravelled += GameState.WORLD_MOVE_SPEED;
					oneSecondCounter = 0.0f;
				}

				state.coinSpawnTimer -= Gdx.graphics.getDeltaTime();
				if (state.coinSpawnTimer <= 0) {
					// NOTE: Always generate a coin off-screen away from the user
					float randomiseCoinX = state.hero.getX() + Gdx.graphics.getWidth();
					randomiseCoinX += MathUtils.random(0.0f, Gdx.graphics.getWidth());
					if (randomiseCoinX <= state.world.getWorldSizeInPixels()) {
						GameItemSpawn coinObj = new GameItemSpawn(randomiseCoinX,
								World.HORIZON_IN_PIXELS, 16.0f * 2,
								16.0f * 2, false);
						coinObj.setTexture(coinTex);
						state.world.coins.add(coinObj);
						gameStage.addActor(coinObj);
						System.out.println("DEBUG Adding coin " + state.world.coins.size + " to stage at " + coinObj.getX());
					}
					state.coinSpawnTimer = state.COIN_SPAWN_TIME;
				}

				Iterator<GameItemSpawn> coinIter = state.world.coins.iterator();
				while (coinIter.hasNext()) {
					GameObj coin = coinIter.next();
					if (coin.getX() <= state.hero.getX() + state.hero.getWidth() / 2) {
						state.playerMoney++;
						coinSfx.play();
						// NOTE: Remove coin from array, remove coin from stage (stop drawing)
						coinIter.remove();
						coin.remove();
					}
				}
			}
		}

        // TODO: Use proper 2D physics
        gameStage.act(delta);
        uiStage.act(delta);

        gameStage.draw();
        uiStage.draw();

        game.batch.begin();
        // NOTE: Render title, probably put as actor into ui
        game.font.draw(game.batch, "The " + state.world.worldName + " Plains", Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() - 400.0f);
        // RENDER DEBUG FONT
        DEBUGFont.draw(game.batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
                20.0f, (Gdx.graphics.getHeight() - 20.0f));
        DEBUGFont.draw(game.batch, "Gdx FramesPerSec: " +
                        Gdx.graphics.getFramesPerSecond(), 20.0f,
                (Gdx.graphics.getHeight() - 40.0f));
        DEBUGFont.draw(game.batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
                        (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
                (Gdx.graphics.getHeight() - 60.0f));
        DEBUGFont.draw(game.batch, "Distance Travelled: " + state.distTravelled, 20.0f,
                (Gdx.graphics.getHeight() - 80.0f));
        DEBUGFont.draw(game.batch, "Player Money: " + state.playerMoney, 20.0f,
                (Gdx.graphics.getHeight() - 100.0f));
        DEBUGFont.draw(game.batch, "Tent State: " + state.isTentActive, 20.0f,
                (Gdx.graphics.getHeight() - 120.0f));
        DEBUGFont.draw(game.batch, "Player X: " + state.hero.getX(), 20.0f,
                (Gdx.graphics.getHeight() - 140.0f));
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
		gameStage.dispose();

		backgroundMusic.dispose();
	}
}
