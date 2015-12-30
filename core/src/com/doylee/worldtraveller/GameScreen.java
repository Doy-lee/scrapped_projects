package com.doylee.worldtraveller;

import com.badlogic.gdx.ApplicationAdapter;
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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;

// TODO: Read this https://github.com/libgdx/libgdx/wiki/Projection,-viewport,-&-camera
public class GameScreen implements Screen {
	final WorldTraveller game;

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
	private SpriteBatch batch;
	private BitmapFont debugFont;
	private Stage stage;

	// Game obj data
	private float worldHorizonInPixels;
	private float spriteWidth;
	private float spriteHeight;
	private float spriteScale;
	private int walkCurrFrame;
	private float walkUpdateSpeedSeconds;
	private float coinSpawnTimeSeconds;

	// Game objects
	private Rectangle worldGround;
	private Rectangle avatar;
	private Array<Rectangle> avatarWalk;
	private Array<Rectangle> coins;
	private Rectangle tent;

	// World intrinsics
	private float pixelsPerMeter;
	private float worldMoveSpeed;
	private long distTravelled;
	private float oneSecondCounter;

	private int playerMoney;
	private boolean tentMode;

	public GameScreen(final WorldTraveller wtGame) {
		game = wtGame;

		// Textures
		uvMap = new Texture(Gdx.files.internal("plain_terrain.png"));
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
		batch = new SpriteBatch();
		debugFont = new BitmapFont();
		debugFont.setColor(Color.GREEN);
		stage = new Stage();

		worldGround = new Rectangle(0.0f, 0.0f, uvMap.getWidth(),
				                    uvMap.getHeight());

		// Game obj data
		worldHorizonInPixels = 280.0f;
		spriteWidth = 16.0f;
		spriteHeight = 16.0f;
		spriteScale = 4.0f;
		walkCurrFrame = 0;
		walkUpdateSpeedSeconds = 0.1f;
		coinSpawnTimeSeconds = 1.0f;
		float avatarCenterToScreen = Gdx.graphics.getWidth()/2 -
				                   (spriteWidth*spriteScale);
		float avatarSize = spriteWidth * spriteScale;

		// Game objs
		avatar = new Rectangle(avatarCenterToScreen, worldHorizonInPixels,
				               avatarSize, avatarSize);
		avatarWalk = new Array<Rectangle>();
		avatarWalk.add(new Rectangle(0.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(16.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(32.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(48.0f, 16.0f, 16.0f, 16.0f));
		coins = new Array<Rectangle>();
		tent = new Rectangle(avatar.x, avatar.y, 94.0f*1.5f, 77.0f*1.5f);

		// World intrinsics
		// NOTE: The average human height is 1.7m, canonically in our world, ~60pixels
		// i.e. 1 m ~= 35.3px
		float baseAvatarHeight = 60.0f;
		pixelsPerMeter = baseAvatarHeight/1.7f;
		// Assume average run speed of 10 miles per hour ~= 16km/hr
		// 16km/hr ~= 4.4m/s.
		worldMoveSpeed = 4.4f;
		distTravelled = 0;
		oneSecondCounter = 0;

		playerMoney = 0;
	}

	@Override
	public void show() {
		//backgroundMusic.play();
	}

	public void render(float delta) {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		camera.update();

		if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
			// toggle tentMode
			tentMode = !tentMode;
		}

		if (tentMode == true) {
		} else {
			worldGround.x -= (worldMoveSpeed * pixelsPerMeter) * Gdx.graphics.getDeltaTime();

			if (worldGround.x <= -worldGround.width) {
				worldGround.x = 0;
			}

			walkUpdateSpeedSeconds -= Gdx.graphics.getDeltaTime();
			if (walkUpdateSpeedSeconds <= 0) {
				if (walkCurrFrame < 3) {
					walkCurrFrame++;
				} else {
					walkCurrFrame = 0;
				}
				walkUpdateSpeedSeconds = 0.09f;
			}

			oneSecondCounter += Gdx.graphics.getDeltaTime();
			if (oneSecondCounter >= 1.0f) {
				distTravelled += worldMoveSpeed;
				oneSecondCounter = 0.0f;
			}

			coinSpawnTimeSeconds -= Gdx.graphics.getDeltaTime();
			if (coinSpawnTimeSeconds <= 0) {
				float randomiseCoinX = Gdx.graphics.getWidth();
				randomiseCoinX += MathUtils.random(0.0f, Gdx.graphics.getWidth());
				Rectangle coinObj = new Rectangle(randomiseCoinX,
						worldHorizonInPixels, 16.0f*2,
						16.0f*2);
				coins.add(coinObj);
				coinSpawnTimeSeconds = 1.0f;
			}

			Iterator<Rectangle> coinIter = coins.iterator();
			while (coinIter.hasNext()) {
				Rectangle coin = coinIter.next();
				coin.x -= (worldMoveSpeed * pixelsPerMeter) * Gdx.graphics.getDeltaTime();
				if (coin.overlaps(avatar)) {
					playerMoney++;
					coinSfx.play();
					coinIter.remove();
				}
			}
		}

		// TODO: Use proper 2D physics
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
			// RENDER WORLD
            batch.draw(uvMap, worldGround.x, worldGround.y);
			batch.draw(uvMap, worldGround.x + worldGround.width, worldGround.y);

			for (Rectangle coin: coins) {
				batch.draw(coinTex, coin.x, coin.y, coin.width, coin.height);
			}

			if (tentMode) {
				batch.draw(tentTex, tent.x, tent.y, tent.width, tent.height);
			} else {
				batch.draw(avatarSheet, avatar.x, avatar.y, avatar.width,
						avatar.height, (int) avatarWalk.get(walkCurrFrame).x,
						(int) avatarWalk.get(walkCurrFrame).y,
						(int) avatarWalk.get(walkCurrFrame).width,
						(int) avatarWalk.get(walkCurrFrame).height, false, false);
			}


			// RENDER DEBUG FONT
            debugFont.draw(batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
					       20.0f, (Gdx.graphics.getHeight() - 20.0f));
			debugFont.draw(batch, "Gdx FramesPerSec: " +
					       Gdx.graphics.getFramesPerSecond(), 20.0f,
					       (Gdx.graphics.getHeight() - 40.0f));
            debugFont.draw(batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
					       (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
					       (Gdx.graphics.getHeight() - 60.0f));
            debugFont.draw(batch, "Distance Travelled: " + distTravelled, 20.0f,
				           (Gdx.graphics.getHeight() - 80.0f));
            debugFont.draw(batch, "Player Money: " + playerMoney, 20.0f,
					       (Gdx.graphics.getHeight() - 100.0f));
            debugFont.draw(batch, "Tent State: " + tentMode, 20.0f,
                    (Gdx.graphics.getHeight() - 120.0f));
		batch.end();

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

		batch.dispose();
		debugFont.dispose();
		stage.dispose();

		backgroundMusic.dispose();
	}
}
