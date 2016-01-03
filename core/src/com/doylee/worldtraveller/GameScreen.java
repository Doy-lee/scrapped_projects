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
	private GameState gameState;

	// Assets
	private Texture uvMap;
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
		gameState = new GameState();

		// Assets
		uvMap = new Texture(Gdx.files.internal("plain_terrain_cut.png"));
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
		uiStage = new Stage(new ScreenViewport(), game.batch);
		Gdx.input.setInputProcessor(uiStage);

		TextButton camp = new TextButton("Camp", game.skin);
		camp.pad(20);
		camp.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (gameState.isTentActive) gameState.setTentMode(false);
				else gameState.setTentMode(true);
			}
		});

		table = new Table(game.skin);
		table.setFillParent(true);
		table.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		table.add(camp);
		uiStage.addActor(table);

		//  NOTE;
		updateGameStage();
		oneSecondCounter = 0;
	}

	public void updateGameStage() {
		gameStage = new Stage(new ScreenViewport(camera), game.batch);
		for (WorldChunk chunk : gameState.world.chunks) {
			gameStage.addActor(chunk);
		}
		gameStage.addActor(gameState.hero);
		gameStage.addActor(gameState.hero.tent);
	}

	@Override
	public void show() {
		//backgroundMusic.play();
	}

	public void render(float delta) {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		game.batch.setProjectionMatrix(camera.combined);

		if (gameState.hero.getX() >= gameState.world.getWorldSizeInPixels()) {
			System.out.println("DEBUG Cleared stage");
			gameState.generateWorld();
			gameState.resetHeroPosition();
			this.updateGameStage();
		} else {
			// NOTE: Camera positions sets the center point of the camera view port
			float camOriginX = camera.position.x - (Gdx.graphics.getWidth()/2) + gameState.hero.getWidth()/2;
			if (camOriginX  <= Gdx.graphics.getWidth() * (gameState.world.chunks.size-1)) {
				camera.position.set(gameState.hero.getX() + gameState.hero.getWidth(), Gdx.graphics.getHeight()/2, 0);
			}
			camera.update();

			// Toggle camp
			if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
				if (gameState.isTentActive) gameState.setTentMode(false);
				else gameState.setTentMode(true);
			}

			if (gameState.tentMode == false) {
				oneSecondCounter += Gdx.graphics.getDeltaTime();
				if (oneSecondCounter >= 1.0f) {
					gameState.distTravelled += GameState.WORLD_MOVE_SPEED;
					oneSecondCounter = 0.0f;
				}

				gameState.coinSpawnTimer -= Gdx.graphics.getDeltaTime();
				if (gameState.coinSpawnTimer <= 0) {
					// NOTE: Always generate a coin off-screen away from the user
					float randomiseCoinX = gameState.hero.getX() + Gdx.graphics.getWidth();
					randomiseCoinX += MathUtils.random(0.0f, Gdx.graphics.getWidth());
					if (randomiseCoinX <= gameState.world.getWorldSizeInPixels()) {
						GameItemSpawn coinObj = new GameItemSpawn(randomiseCoinX,
								gameState.world.horizonInPixels, 16.0f * 2,
								16.0f * 2, false);
						coinObj.setTexture(coinTex);
						gameState.world.coins.add(coinObj);
						gameStage.addActor(coinObj);
						System.out.println("DEBUG Adding coin " + gameState.world.coins.size + " to stage at " + coinObj.getX());
					}
					gameState.coinSpawnTimer = gameState.COIN_SPAWN_TIME;
				}

				Iterator<GameItemSpawn> coinIter = gameState.world.coins.iterator();
				while (coinIter.hasNext()) {
					GameObj coin = coinIter.next();
					if (coin.getX() <= gameState.hero.getX() + gameState.hero.getWidth() / 2) {
						gameState.playerMoney++;
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
        game.font.draw(game.batch, "The " + gameState.world.worldName + " Plains", Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() - 400.0f);
        // RENDER DEBUG FONT
        DEBUGFont.draw(game.batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
                20.0f, (Gdx.graphics.getHeight() - 20.0f));
        DEBUGFont.draw(game.batch, "Gdx FramesPerSec: " +
                        Gdx.graphics.getFramesPerSecond(), 20.0f,
                (Gdx.graphics.getHeight() - 40.0f));
        DEBUGFont.draw(game.batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
                        (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
                (Gdx.graphics.getHeight() - 60.0f));
        DEBUGFont.draw(game.batch, "Distance Travelled: " + gameState.distTravelled, 20.0f,
                (Gdx.graphics.getHeight() - 80.0f));
        DEBUGFont.draw(game.batch, "Player Money: " + gameState.playerMoney, 20.0f,
                (Gdx.graphics.getHeight() - 100.0f));
        DEBUGFont.draw(game.batch, "Tent State: " + gameState.tentMode, 20.0f,
                (Gdx.graphics.getHeight() - 120.0f));
        DEBUGFont.draw(game.batch, "Player X: " + gameState.hero.getX(), 20.0f,
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
		uvMap.dispose();
		coinTex.dispose();

		uiStage.dispose();
		gameStage.dispose();

		backgroundMusic.dispose();
	}
}
