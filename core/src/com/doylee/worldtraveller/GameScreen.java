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
	private OrthographicCamera camera;
	private BitmapFont debugFont;
	private Stage uiStage;
	private Stage gameStage;

	// Game obj data
	private float worldHorizonInPixels;
	private float spriteWidth;
	private float spriteHeight;
	private float spriteScale;

	// Game objects
	private GameObj hero;
	private Array<WorldChunk> world;
	private int currWorldChunk;
	private Rectangle worldGround;
	private Array<GameObj> coins;
	private Rectangle tent;

	// World intrinsics
	private long distTravelled;
	private float oneSecondCounter;

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
		debugFont = new BitmapFont();
		debugFont.setColor(Color.GREEN);
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
		coins = new Array<GameObj>();
		tent = new Rectangle(avatar.x, avatar.y, 94.0f*1.5f, 77.0f*1.5f);

		// World intrinsics
		distTravelled = 0;
		oneSecondCounter = 0;

		gameState = new GameState();

		world = new Array<WorldChunk>(6);
		world.add(new WorldChunk(0f, 0f, Gdx.graphics.getWidth(), uvMap.getHeight(), uvMap));
		world.add(new WorldChunk(Gdx.graphics.getWidth(), 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		world.add(new WorldChunk(Gdx.graphics.getWidth()*2, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		world.add(new WorldChunk(Gdx.graphics.getWidth()*3, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		world.add(new WorldChunk(Gdx.graphics.getWidth()*4, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));
		world.add(new WorldChunk(Gdx.graphics.getWidth()*5, 0f, uvMap.getWidth(), uvMap.getHeight(), uvMap));

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

		Actor temp = new Actor();
		hero.addActor(temp);
		temp.setX(16);
		temp.setY(16);

		gameStage = new Stage(new ScreenViewport(camera), game.batch);
		for (WorldChunk wChunk : world) {
			gameStage.addActor(wChunk);
		}
		gameStage.addActor(hero);

		TextButton camp = new TextButton("Camp", game.skin);
		camp.pad(20);
		camp.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				gameState.tentMode = !gameState.tentMode;
				hero.setVisible(!hero.isVisible());
			}
		});

		table = new Table(game.skin);
		table.setFillParent(true);
		table.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		table.add(camp);
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
		gameStage.act(delta);
		uiStage.act(delta);

		// NOTE: Camera positions sets the center point of the camera view port
		float camOriginX = camera.position.x - (Gdx.graphics.getWidth()/2);
        if (camOriginX  <= Gdx.graphics.getWidth() * (world.size-1)) {
			camera.position.set(hero.getX() + hero.getWidth(), Gdx.graphics.getHeight()/2, 0);
		}
		camera.update();

		// Toggle camp
		if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
			gameState.tentMode = !gameState.tentMode;
			hero.setVisible(!hero.isVisible());
		}

		if (gameState.tentMode == false) {
			oneSecondCounter += Gdx.graphics.getDeltaTime();
			if (oneSecondCounter >= 1.0f) {
				distTravelled += GameState.WORLD_MOVE_SPEED;
				oneSecondCounter = 0.0f;
			}

			gameState.coinSpawnTimer -= Gdx.graphics.getDeltaTime();
			if (gameState.coinSpawnTimer <= 0) {
				// NOTE: Always generate a coin off-screen away from the user
				int worldLengthInPixels = world.size * Gdx.graphics.getWidth();
				float randomiseCoinX = hero.getX() + Gdx.graphics.getWidth();
				if (!(randomiseCoinX >= worldLengthInPixels)) {
					randomiseCoinX += MathUtils.random(0.0f, Gdx.graphics.getWidth());
					GameItemSpawn coinObj = new GameItemSpawn(randomiseCoinX,
							worldHorizonInPixels, 16.0f*2,
							16.0f*2, false);
					coinObj.setTexture(coinTex);
					coins.add(coinObj);
					gameStage.addActor(coinObj);
					System.out.println("coin not found, adding coin " + coins.size + " to stage at " + coinObj.getX());
				}
				gameState.coinSpawnTimer = gameState.COIN_SPAWN_TIME;
			}

			Iterator<GameObj> coinIter = coins.iterator();
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
		gameStage.draw();
		uiStage.draw();

		game.batch.begin();
			// RENDER DEBUG FONT
            debugFont.draw(game.batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
					       20.0f, (Gdx.graphics.getHeight() - 20.0f));
			debugFont.draw(game.batch, "Gdx FramesPerSec: " +
					       Gdx.graphics.getFramesPerSecond(), 20.0f,
					       (Gdx.graphics.getHeight() - 40.0f));
            debugFont.draw(game.batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
					       (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
					       (Gdx.graphics.getHeight() - 60.0f));
            debugFont.draw(game.batch, "Distance Travelled: " + distTravelled, 20.0f,
				           (Gdx.graphics.getHeight() - 80.0f));
            debugFont.draw(game.batch, "Player Money: " + gameState.playerMoney, 20.0f,
					       (Gdx.graphics.getHeight() - 100.0f));
            debugFont.draw(game.batch, "Tent State: " + gameState.tentMode, 20.0f,
                    (Gdx.graphics.getHeight() - 120.0f));
            debugFont.draw(game.batch, "Player X: " + hero.getX(), 20.0f,
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

		debugFont.dispose();
		uiStage.dispose();
		gameStage.dispose();

		backgroundMusic.dispose();
	}
}
