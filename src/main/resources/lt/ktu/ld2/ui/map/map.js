(() => {
    "use strict";

    const pending = new Map();
    let nextTileId = 0;
    let map;
    let marker;

    window.ld2Map = {
        initialize(bridge, dark, maxZoom) {
            map = L.map("map", {
                attributionControl: false,
                maxZoom,
                fadeAnimation: false,
                zoomAnimation: false,
                zoomControl: false
            });
            L.control.zoom({zoomInTitle: "Priartinti", zoomOutTitle: "Atitolinti"}).addTo(map);

            const VisibleTiles = L.GridLayer.extend({
                createTile(coords, done) {
                    const tile = document.createElement("img");
                    const id = ++nextTileId;
                    tile.alt = "";
                    tile.dataset.tileId = id;
                    pending.set(id, {tile, done});
                    bridge.requestTile(id, coords.z, coords.x, coords.y);
                    return tile;
                }
            });

            let succeeded = 0;
            let failed = 0;
            new VisibleTiles({maxZoom, keepBuffer: 1, updateWhenIdle: true})
                .on("tileunload", ({tile}) => {
                    const id = Number(tile.dataset.tileId);
                    if (pending.delete(id)) {
                        bridge.cancelTile(id);
                    }
                })
                .on("loading", () => {
                    succeeded = 0;
                    failed = 0;
                    bridge.tilesLoading();
                })
                .on("tileload", () => succeeded++)
                .on("tileerror", () => failed++)
                .on("load", () => bridge.tilesLoaded(succeeded, failed))
                .addTo(map);

            this.setDark(dark);
        },

        setDark(dark) {
            document.documentElement.classList.toggle("dark", dark);
        },

        setPosition(latitude, longitude) {
            const position = [latitude, longitude];
            if (!marker) {
                map.invalidateSize({pan: false});
                map.setView(position, 14, {animate: false});
                marker = L.circleMarker(position, {
                    radius: 8,
                    weight: 2,
                    color: "#ffffff",
                    fillColor: "#1465cd",
                    fillOpacity: 1
                }).addTo(map);
            } else {
                marker.setLatLng(position);
            }
        },

        completeTile(id, source) {
            const entry = pending.get(id);
            if (!entry) {
                return;
            }
            const {tile, done} = entry;
            const finish = error => {
                if (pending.delete(id)) {
                    done(error, tile);
                }
            };
            if (source) {
                tile.onload = () => finish(null);
                tile.onerror = () => finish(new Error("Netinkama plytelė"));
                tile.src = source;
            } else {
                finish(new Error("Plytelė nepasiekiama"));
            }
        }
    };
})();
