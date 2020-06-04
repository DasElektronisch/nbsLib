package dev.elektronisch.nbslib.api;

import dev.elektronisch.nbslib.util.StreamUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class Song {

    private static final int TICKS_PER_SECOND = 20;

    private final String title;
    private final String author;
    private final String originalAuthor;
    private final String description;

    private final float speed, delay;
    private final short height, length;

    private final Int2ObjectMap<Layer> layerMap;

    Song(final String title, final String author,
         final String originalAuthor, final String description,
         final float speed, final short height,
         final short length, final Int2ObjectMap<Layer> layerMap) {
        this.title = title;
        this.author = author;
        this.originalAuthor = originalAuthor;
        this.description = description;
        this.speed = speed;
        this.delay = TICKS_PER_SECOND / speed;
        this.height = height;
        this.length = length;
        this.layerMap = layerMap;
    }

    @Nullable
    public static Song createFromFile(final File nbsFile) {
        final Int2ObjectMap<Layer> layerMap = new Int2ObjectOpenHashMap<>();
        try (final DataInputStream dataInputStream = new DataInputStream(new FileInputStream(nbsFile))) {
            short length = StreamUtil.readShort(dataInputStream);
            int version = 0;
            if (length == 0) {
                version = dataInputStream.readByte();
                dataInputStream.readByte(); // We don't support custom instruments
                if (version >= 3) {
                    length = StreamUtil.readShort(dataInputStream);
                }
            }
            final short height = StreamUtil.readShort(dataInputStream);
            final String title = StreamUtil.readString(dataInputStream);
            final String author = StreamUtil.readString(dataInputStream);
            final String originalAuthor = StreamUtil.readString(dataInputStream);
            final String description = StreamUtil.readString(dataInputStream);
            final float speed = StreamUtil.readShort(dataInputStream) / 100f;

            // The following information is unnecessary; we're skipping it
            dataInputStream.readBoolean(); // Auto-save enabled
            dataInputStream.readByte(); // Auto-save period
            dataInputStream.readByte(); // Time signature
            StreamUtil.readInt(dataInputStream); // Minutes spent for project
            StreamUtil.readInt(dataInputStream); // Left clicks
            StreamUtil.readInt(dataInputStream); // Right clicks
            StreamUtil.readInt(dataInputStream); // Blocks added
            StreamUtil.readInt(dataInputStream); // Blocks removed
            StreamUtil.readString(dataInputStream); // .midi / .schem name

            // Only read this information in NBS 4 or higher
            if (version >= 4) {
                dataInputStream.readByte(); // Loop on/off
                dataInputStream.readByte(); // Max Loops
                StreamUtil.readShort(dataInputStream); // Loop start
            }

            short currentTick = -1;
            while (true) {
                final short jumpTicks = StreamUtil.readShort(dataInputStream);
                if (jumpTicks == 0) break;

                currentTick += jumpTicks;

                short currentLayer = -1;
                while (true) {
                    final short jumpLayers = StreamUtil.readShort(dataInputStream);
                    if (jumpLayers == 0) break;

                    currentLayer += jumpLayers;

                    byte instrumentIndex = dataInputStream.readByte();

                    final byte key = dataInputStream.readByte();
                    byte velocity = 100;
                    short pitch = 0;

                    // Only read this information in NBS 4 or higher
                    if (version >= 4) {
                        velocity = dataInputStream.readByte();
                        dataInputStream.readByte(); // Panning isn't implemented yet
                        pitch = StreamUtil.readShort(dataInputStream);
                    }

                    final Layer layer = layerMap.computeIfAbsent(currentLayer, index -> new Layer());
                    layer.getNoteMap().put(currentTick, new Note(instrumentIndex, key, velocity, pitch));
                }
            }

            if (version > 0 && version < 3) {
                length = currentTick;
            }

            for (int i = 0; i < height; i++) {
                final Layer layer = layerMap.get(i);
                final String name = StreamUtil.readString(dataInputStream);

                // Only read this information in NBS 4 or higher
                if (version >= 4) {
                    dataInputStream.readByte(); // Whether layer is locked
                }

                final byte volume = dataInputStream.readByte();

                // Only read this information in NBS 2 or higher
                if (version >= 2) {
                    dataInputStream.readByte(); // Whether layer is stereo
                }

                if (layer != null) {
                    layer.setName(name);
                    layer.setVolume(volume);
                }
            }
            return new Song(title, author, originalAuthor, description, speed, height, length, layerMap);
        } catch (final IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while creating song from '{}'", nbsFile.getName());
        }
        return null;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getOriginalAuthor() {
        return originalAuthor;
    }

    public String getDescription() {
        return description;
    }

    public float getSpeed() {
        return speed;
    }

    public float getDelay() {
        return delay;
    }

    public short getHeight() {
        return height;
    }

    public short getLength() {
        return length;
    }

    public Int2ObjectMap<Layer> getLayerMap() {
        return layerMap;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return title.equals(song.title) &&
                author.equals(song.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, author);
    }

    @Override
    public String toString() {
        return "Song{" +
                "title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", originalAuthor='" + originalAuthor + '\'' +
                ", description='" + description + '\'' +
                ", speed=" + speed +
                ", delay=" + delay +
                ", height=" + height +
                ", length=" + length +
                ", layerMap=" + layerMap +
                '}';
    }
}