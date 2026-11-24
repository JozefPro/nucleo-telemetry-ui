package lt.ktu.ld2.ui;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

final class OsmTiles implements AutoCloseable {
    static final int MAX_ZOOM = 19;
    private static final String USER_AGENT = "KTU-komp-komunikacijos-ld2/1.0";
    private final Cache cache;
    private final OkHttpClient client;

    OsmTiles() throws IOException {
        Path directory = Path.of(System.getProperty("user.home"), ".komp-komunikacijos-ld2", "osm-tiles");
        Files.createDirectories(directory);
        cache = new Cache(directory.toFile(), 64L * 1024 * 1024);
        cache.initialize();
        client = new OkHttpClient.Builder()
                .cache(cache)
                .callTimeout(Duration.ofSeconds(12))
                .followRedirects(false)
                .build();
    }

    Call request(int zoom, int x, int y, Consumer<String> finished) {
        Request request = new Request.Builder()
                .url("https://tile.openstreetmap.org/" + zoom + "/" + x + "/" + y + ".png")
                .header("User-Agent", USER_AGENT)
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                finished.accept(null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                String source = null;
                try (response) {
                    var body = response.body();
                    if (response.isSuccessful() && body != null
                            && "image/png".equals(response.header("Content-Type"))) {
                        source = "data:image/png;base64," + Base64.getEncoder().encodeToString(body.bytes());
                    }
                } catch (IOException ignored) {
                    // Report an unavailable tile to the map.
                }
                finished.accept(source);
            }
        });
        return call;
    }

    @Override
    public void close() {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        try {
            cache.close();
        } catch (IOException ignored) {
            // Closing the application must still release the serial port.
        }
    }
}
