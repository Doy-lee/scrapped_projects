package com.doylee.worldtraveller;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

// TODO: Read this https://github.com/libgdx/libgdx/wiki/Projection,-viewport,-&-camera
public class WorldTraveller extends ApplicationAdapter {
	// Textures
	private Texture uvMap;
	private Texture avatarSheet;

	// Sounds/music
	private Music backgroundMusic;

	// Game sys configuration
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private BitmapFont debugFont;

	// Game obj data
	private float spriteWidth;
	private float spriteHeight;
	private float spriteScale;
	private int walkCurrFrame;
	private float walkUpdateSpeedSeconds;

	// Game objects
	private Rectangle worldGround;
	private Rectangle avatar;
	private Array<Rectangle> avatarWalk;

	// World intrinsics
	private float pixelsPerMeter;
	private float worldMoveSpeed;
	private long distTravelled;
	private float oneSecondCounter;


	@Override
	public void create () {
		// Textures
		uvMap = new Texture(Gdx.files.internal("plain_terrain.png"));
		avatarSheet = new Texture(Gdx.files.internal("MyChar.png"));

		// Game sounds
		backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("ffvi_searching_for_friends.mp3"));
		backgroundMusic.setLooping(true);
		backgroundMusic.play();

		// Game sys config
		camera = new OrthographicCamera();
		// NOTE: Match camera to the device resolution
		// setToOrtho(Y Orientation=Down, viewPortWidth, viewPortHeight)
		camera.setToOrtho(false, Gdx.graphics.getWidth(),
				          Gdx.graphics.getHeight());
		batch = new SpriteBatch();
		debugFont = new BitmapFont();
		debugFont.setColor(Color.RED);

		worldGround = new Rectangle(0.0f, 0.0f, uvMap.getWidth(),
				                    uvMap.getHeight());

		// Game obj data
		spriteWidth = 16.0f;
		spriteHeight = 16.0f;
		spriteScale = 4.0f;
		walkCurrFrame = 0;
		walkUpdateSpeedSeconds = 0.1f;
		float avatarCenterToScreen = Gdx.graphics.getWidth()/2 -
				                   (spriteWidth*spriteScale);
		float avatarSize = spriteWidth * spriteScale;

		// Game objs
		avatar = new Rectangle(avatarCenterToScreen, 280.0f, avatarSize,
				               avatarSize);
		avatarWalk = new Array<Rectangle>();
		avatarWalk.add(new Rectangle(0.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(16.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(32.0f, 16.0f, 16.0f, 16.0f));
		avatarWalk.add(new Rectangle(48.0f, 16.0f, 16.0f, 16.0f));

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
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(0.0f, 0.0f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		camera.update();

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

		batch.setProjectionMatrix(camera.combined);
		batch.begin();
            batch.draw(uvMap, worldGround.x, worldGround.y);
			batch.draw(uvMap, worldGround.x + worldGround.width, worldGround.y);
			batch.draw(avatarSheet, avatar.x, avatar.y, avatar.width,
					avatar.height, (int) avatarWalk.get(walkCurrFrame).x,
					(int) avatarWalk.get(walkCurrFrame).y,
					(int) avatarWalk.get(walkCurrFrame).width,
					(int) avatarWalk.get(walkCurrFrame).height, false, false);
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
		batch.end();

	}
}
