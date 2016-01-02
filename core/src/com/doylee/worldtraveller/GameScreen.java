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
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
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

	// Textures
	private Texture uvMap;
	private Texture avatarSheet;
	private Texture coinTex;
	private Texture tentTex;

	// Sounds/music
	private Music backgroundMusic;
	private Sound coinSfx;

	// Game sys configuration
	private BitmapFont DEBUGFont;
	private OrthographicCamera camera;
	private Stage uiStage;
	private Stage gameStage;
	private String stageName;

	// Game obj data
	private float worldHorizonInPixels;
	private float spriteWidth;
	private float spriteHeight;
	private float spriteScale;

	// Game objects
	private GamePlayer hero;
	private World world;
	private Array<GameItemSpawn> coins;
	private GameObj tent;

	// World intrinsics
	private float oneSecondCounter;
	private Array<String> worldAdjectives;

	private Table table;

	public GameScreen(final WorldTraveller wtGame) {
		game = wtGame;

		// Textures
		uvMap = new Texture(Gdx.files.internal("plain_terrain_cut.png"));
		avatarSheet = new Texture(Gdx.files.internal("MyChar.png"));
		coinTex = new Texture(Gdx.files.internal("coin.png"));
		tentTex = new Texture(Gdx.files.internal("circus_tent.png"));

		// Game sounds
		backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("ffvi_searching_for_friends.mp3"));
		backgroundMusic.setLooping(true);
		coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin1.wav"));

		// Game sys config
		camera = new OrthographicCamera();
		// NOTE: Match camera to the device resolution
		// setToOrtho(Y Orientation=Down, viewPortWidth, viewPortHeight)
		camera.setToOrtho(false, Gdx.graphics.getWidth(),
				          Gdx.graphics.getHeight());
		DEBUGFont = new BitmapFont();
		DEBUGFont.setColor(Color.GREEN);
		uiStage = new Stage(new ScreenViewport(), game.batch);
		Gdx.input.setInputProcessor(uiStage);

		// Game obj data
		worldHorizonInPixels = 280.0f;
		spriteWidth = 16.0f;
		spriteHeight = 16.0f;
		spriteScale = 4.0f;
		float avatarCenterToScreen = Gdx.graphics.getWidth()/2 -
				                   (spriteWidth*spriteScale);
		float avatarSize = spriteWidth * spriteScale;

		// Game objs
		Rectangle avatar = new Rectangle(avatarCenterToScreen, worldHorizonInPixels,
				               avatarSize, avatarSize);

		//coins = new Array<Rectangle>();
		coins = new Array<GameItemSpawn>();
		tent = new GameObj(avatar.x, avatar.y, 94.0f*1.5f, 77.0f*1.5f, false);
		tent.setTexture(tentTex);
		tent.setVisible(false);

		// World intrinsics
		oneSecondCounter = 0;

		gameState = new GameState();

		Array<WorldChunk> chunksArray = new Array<WorldChunk>(6);
		chunksArray.add(new WorldChunk(0f, 0f, Gdx.graphics.getWidth(), uvMap.getHeight(), uvMap));
		chunksArray.add(new WorldChunk(Gdx.graphics.getWidth(), 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		chunksArray.add(new WorldChunk(Gdx.graphics.getWidth()*2, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		chunksArray.add(new WorldChunk(Gdx.graphics.getWidth() * 3, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		chunksArray.add(new WorldChunk(Gdx.graphics.getWidth()*4, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		chunksArray.add(new WorldChunk(Gdx.graphics.getWidth()*5, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		world = new World(chunksArray);

		// split (tex, tile width, tile height)
		TextureRegion[][] tmp = TextureRegion.split(avatarSheet, 16, 16);
		int walkLength = 4;
		TextureRegion[] walkFrames = new TextureRegion[walkLength];
		for (int i = 0; i < walkLength; i++) {
			walkFrames[i] = tmp[1][i];
		}
		Animation walkAnim = new Animation(0.05f, walkFrames);

		hero = new GamePlayer(avatarCenterToScreen, worldHorizonInPixels, avatarSize, avatarSize, true);
		hero.addAnimation(walkAnim);

		gameStage = new Stage(new ScreenViewport(camera), game.batch);
		for (WorldChunk chunk : world.chunks) {
			gameStage.addActor(chunk);
		}
		gameStage.addActor(hero);
		gameStage.addActor(tent);

		TextButton camp = new TextButton("Camp", game.skin);
		camp.pad(20);
		camp.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				toggleTentMode();
			}
		});

		table = new Table(game.skin);
		table.setFillParent(true);
		table.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		table.add(camp);
		uiStage.addActor(table);


		// TODO: Check what happens with a low buffer read value
		BufferedReader reader = Gdx.files.internal("world_adjectives.txt").reader(1024);
		worldAdjectives = new Array<String>();
		try {
			String line = reader.readLine();
			while (line != null) {
				worldAdjectives.add(line);
				line = reader.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (String adjectives: worldAdjectives) {
			System.out.println("DEBUG Parsed adjective: " + adjectives);
		}

		stageName = worldAdjectives.get(MathUtils.random(0, worldAdjectives.size-1));
		System.out.println("DEBUG Stage name set to " + stageName);
	}

	public void toggleTentMode() {
		gameState.tentMode = !gameState.tentMode;
		hero.setVisible(!hero.isVisible());
		tent.setVisible(!tent.isVisible());
		tent.setX(hero.getX());
		tent.setY(hero.getY());
	}

	@Override
	public void show() {
		//backgroundMusic.play();
	}

	public void render(float delta) {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		game.batch.setProjectionMatrix(camera.combined);
		// NOTE: Camera positions sets the center point of the camera view port
		float camOriginX = camera.position.x - (Gdx.graphics.getWidth()/2) + hero.getWidth()/2;
        if (camOriginX  <= Gdx.graphics.getWidth() * (world.chunks.size-1)) {
			camera.position.set(hero.getX() + hero.getWidth(), Gdx.graphics.getHeight()/2, 0);
		}
		camera.update();

		// Toggle camp
		if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
			toggleTentMode();
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
				float randomiseCoinX = hero.getX() + Gdx.graphics.getWidth();
				randomiseCoinX += MathUtils.random(0.0f, Gdx.graphics.getWidth());
				if (randomiseCoinX <= world.getWorldSizeInPixels()) {
					GameItemSpawn coinObj = new GameItemSpawn(randomiseCoinX,
							worldHorizonInPixels, 16.0f*2,
							16.0f*2, false);
					coinObj.setTexture(coinTex);
					coins.add(coinObj);
					gameStage.addActor(coinObj);
					System.out.println("DEBUG Adding coin " + coins.size + " to stage at " + coinObj.getX());
				}
				gameState.coinSpawnTimer = gameState.COIN_SPAWN_TIME;
			}

			Iterator<GameItemSpawn> coinIter = coins.iterator();
			while (coinIter.hasNext()) {
				GameObj coin = coinIter.next();
				if (coin.getX() <= hero.getX() + hero.getWidth()/2) {
					gameState.playerMoney++;
					coinSfx.play();
					// NOTE: Remove coin from array, remove coin from stage (stop drawing)
					coinIter.remove();
					coin.remove();
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
            game.font.draw(game.batch, "The " + stageName + " Plains", Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight() - 400.0f);
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
            DEBUGFont.draw(game.batch, "Player X: " + hero.getX(), 20.0f,
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
		avatarSheet.dispose();
		coinTex.dispose();

		uiStage.dispose();
		gameStage.dispose();

		backgroundMusic.dispose();
	}
}
