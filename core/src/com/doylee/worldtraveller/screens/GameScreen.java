package com.doylee.worldtraveller.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.doylee.worldtraveller.objects.Battler;
import com.doylee.worldtraveller.GameState;
import com.doylee.worldtraveller.objects.Hero;
import com.doylee.worldtraveller.Scene;
import com.doylee.worldtraveller.WorldTraveller;

// TODO: Read this https://github.com/libgdx/libgdx/wiki/Projection,-viewport,-&-cavatarWalkamera
public class GameScreen implements Screen {
	final WorldTraveller game;

	// World intrinsics
	private BitmapFont DEBUGFont;
	private OrthographicCamera camera;
	private Stage uiStage;
	private Table uiTable;

	public GameScreen(final WorldTraveller wtGame) {
		game = wtGame;

		// Assets

		// Display configuration
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

		int buttonPadding = 30;
		float buttonScale = 2;

		TextButton eatBtn = new TextButton("Eat", game.skin);
		eatBtn.pad(buttonPadding);
		eatBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				game.state.getHero().setHunger(100.0f);
			}
		});
		eatBtn.setScale(buttonScale);

		TextButton drinkBtn = new TextButton("Drink", game.skin);
		drinkBtn.pad(buttonPadding);
		drinkBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				game.state.getHero().setThirst(100.0f);
			}
		});
		drinkBtn.setScale(buttonScale);

		TextButton restBtn = new TextButton("Rest", game.skin);
		restBtn.pad(buttonPadding);
		restBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				game.state.getHero().setEnergy(100.0f);
			}
		});
		restBtn.setScale(buttonScale);

		TextButton homeBtn = new TextButton("Home", game.skin);
		homeBtn.pad(buttonPadding);
		homeBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
					game.state.setCurrScene(game.state.getHomeScene());
			}
		});
		homeBtn.setScale(buttonScale);

		TextButton adventureBtn = new TextButton("Adventure", game.skin);
		adventureBtn.pad(buttonPadding);
		adventureBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				game.state.setCurrScene(game.state.getAdventureScene());
			}
		});
		adventureBtn.setScale(buttonScale);

		uiTable = new Table(game.skin);
		uiTable.bottom().left();
		uiTable.setDebug(true);
		uiTable.setFillParent(true);
		uiTable.add(eatBtn);
		uiTable.add(drinkBtn);
		uiTable.add(restBtn);
		uiTable.add(homeBtn);
		uiTable.add(adventureBtn);
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

		game.state.update(delta);
		uiStage.act(delta);

		// NOTE: Draw game first, then overlay UI on top
		game.batch.begin();

		// GAME STATE RENDERING
		Hero hero = game.state.getHero();
		Scene scene = game.state.getCurrScene();
		scene.render(game.batch, GameState.globalVolume);

		GameState.Battle battle = game.state.getBattleState();
		if (battle == GameState.Battle.active) {
			game.font.draw(game.batch, "BATTLE ACTIVE", Gdx.graphics.getWidth()/2 - 50.0f, Gdx.graphics.getHeight()/2 + 100.0f);
		}


		// DEBUG
        DEBUGFont.draw(game.batch, "Gdx DeltaTime():  " + Gdx.graphics.getDeltaTime(),
                20.0f, (Gdx.graphics.getHeight() - 20.0f));
        DEBUGFont.draw(game.batch, "Gdx FramesPerSec: " +
                        Gdx.graphics.getFramesPerSecond(), 20.0f,
                (Gdx.graphics.getHeight() - 40.0f));
        DEBUGFont.draw(game.batch, "Gdx Mouse X,Y: " + Gdx.input.getX() + ", " +
                        (Gdx.graphics.getHeight() - Gdx.input.getY()), 20.0f,
                (Gdx.graphics.getHeight() - 60.0f));

		DEBUGFont.draw(game.batch, "CurrAnim:  " + hero.getCurrAnimState().toString(), 20.0f,
				(Gdx.graphics.getHeight() - 80.0f));

		DEBUGFont.draw(game.batch, "Hunger:  " + hero.getHunger(), 20.0f,
				(Gdx.graphics.getHeight() - 100.0f));
		DEBUGFont.draw(game.batch, "Thirst: " + hero.getThirst(), 20.0f,
				(Gdx.graphics.getHeight() - 120.0f));
		DEBUGFont.draw(game.batch, "Energy: " + hero.getEnergy(), 20.0f,
				(Gdx.graphics.getHeight() - 140.0f));

		DEBUGFont.draw(game.batch, "Money: " + hero.getMoney(), 20.0f,
				(Gdx.graphics.getHeight() - 160.0f));

		DEBUGFont.draw(game.batch, "Battle State:  " + game.state.getBattleState().toString(), 20.0f,
				(Gdx.graphics.getHeight() - 180.0f));
		DEBUGFont.draw(game.batch, "World Move Speed: " + game.state.getWorldMoveSpeed(), 20.0f,
				(Gdx.graphics.getHeight() - 200.0f));
		DEBUGFont.draw(game.batch, "Monster Spawn Timer: " + game.state.getMonsterSpawnTimer(), 20.0f,
				(Gdx.graphics.getHeight() - 220.0f));

		// DEBUG DRAW PLAYER HEALTH & ATB
		DEBUGFont.draw(game.batch, "HP: " + hero.getHealth(), hero.getSprite().getX(), hero.getSprite().getY() + GameState.SPRITE_SIZE + 20);
		DEBUGFont.draw(game.batch, "ATB: " + (int)hero.getATB(), hero.getSprite().getX(), hero.getSprite().getY() + GameState.SPRITE_SIZE + 40);
		if (game.state.getCurrBattleMob() != null) {
			Battler mob = game.state.getCurrBattleMob();
			DEBUGFont.draw(game.batch, "HP: " + mob.getHealth(), mob.getSprite().getX(), mob.getSprite().getY() + GameState.SPRITE_SIZE + 20);
			DEBUGFont.draw(game.batch, "ATB: " + (int)mob.getATB(), mob.getSprite().getX(), mob.getSprite().getY() + GameState.SPRITE_SIZE + 40);
		}

        game.batch.end();

		uiStage.draw();

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
		uiStage.dispose();
	}
}
