package com.doylee.worldtraveller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

/**
 * Created by Doyle on 2/01/2016.
 */
public class World {
    public Array<WorldChunk> chunks;
    public Array<GameItemSpawn> coins;
    public String worldName;
    public float horizonInPixels = 0.25f * Gdx.graphics.getHeight();

    public World(WorldChunk chunk) {
        this.chunks.add(chunk);
    }

    public World(Array<WorldChunk> chunkArray, String worldName) {
        this.chunks = chunkArray;
        this.coins = new Array<GameItemSpawn>();
        this.worldName = worldName;

        // NOTE: Check that all chunk widths are the same
        float DEBUG_RESULT = chunkArray.get(0).getWidth();
        for (WorldChunk chunk: this.chunks) {
            assert(DEBUG_RESULT == chunk.getWidth());
        }
    }

    public void addChunk(WorldChunk chunk) {
        chunks.add(chunk);
    }

    public float getWorldSizeInPixels() {
        float result = 0;
        result = chunks.size * chunks.get(0).getWidth();
        return result;
    }
}
