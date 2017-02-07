package com.cyphercove.gdx.examples;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.cyphercove.gdx.Example;
import com.cyphercove.gdx.covetools.assets.AssignmentAssetManager;
import com.cyphercove.gdx.covetools.assets.AssignmentAssetManager.*;

public class AssignmentAssetManagerTest extends Example {

    AssignmentAssetManager assetManager;
    SpriteBatch batch;
    Viewport viewport;
    PersistantAssets persistantAssets = new PersistantAssets();
    Stage1Assets stage1Assets = new Stage1Assets();

    private static class PersistantAssets {
        @Asset("uiskin.json") public Skin skin;
    }

    private static class Stage1Assets {
        @Asset("egg.png") public Texture eggTexture;
        @Asset("tree.png") public Texture treeTexture;
        @Asset("pack") public TextureAtlas atlas;
        @Assets({"arial-15.fnt", "arial-32.fnt", "arial-32-pad.fnt"}) public BitmapFont[] fonts;
    }

    public AssignmentAssetManagerTest (){
        batch = new SpriteBatch();
        viewport = new ExtendViewport(640, 480);

        assetManager = new AssignmentAssetManager();
        assetManager.loadAssetFields(persistantAssets);
        assetManager.loadAssetFields(stage1Assets);
        assetManager.finishLoading();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.draw(stage1Assets.eggTexture, 20, 20);
        stage1Assets.fonts[1].draw(batch, "Stage 1 Assets",
                20, 20 + stage1Assets.eggTexture.getHeight() + stage1Assets.fonts[1].getLineHeight());
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {

    }
}
