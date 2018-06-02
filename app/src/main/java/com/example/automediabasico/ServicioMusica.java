package com.example.automediabasico;


import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServicioMusica extends MediaBrowserService {
    private MediaSession mSession;
    private List<MediaMetadata> mMusic;
    private MediaPlayer mPlayer;
    private MediaMetadata mCurrentTrack;
    private final String TAG = ServicioMusica.this.getClass().getSimpleName();
    private final String URL = "http://storage.googleapis.com/automotive-media/music.json";
    private RequestQueue requestQueue;
    private Gson gson;
    private Musica musica;
    private final int maxWidth = 1000, maxHeight = 1000;
    private int num = 0, mCurTrack;

    @Override
    public void onCreate() {
        super.onCreate();
        mMusic = new ArrayList<>();
        GsonBuilder gsonBuilder = new GsonBuilder();
        gson = gsonBuilder.create();
        requestQueue = Volley.newRequestQueue(this);
        getRepositorioMusical();
        mPlayer = new MediaPlayer();
        mSession = new MediaSession(this, "MiServicioMusical");
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                for (MediaMetadata item : mMusic) {
                    if (item.getDescription().getMediaId().equals(mediaId)) {
                        mCurrentTrack = item;
                        break;
                    }
                }
                handlePlay();
            }

            @Override
            public void onPlay() {
                if (mCurrentTrack == null) {
                    mCurrentTrack = mMusic.get(0);
                    mCurTrack = 0;
                    handlePlay();
                } else {
                    mCurTrack = mMusic.indexOf(mCurrentTrack);
                    mPlayer.start();
                    mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
                }
            }

            @Override
            public void onPause() {
                mPlayer.pause();
                mSession.setPlaybackState(buildState(PlaybackState.STATE_PAUSED));
            }

            @Override
            public void onSkipToNext() {
                if (mCurrentTrack != null) {
                    int index = mMusic.indexOf(mCurrentTrack);
                    if (index < mMusic.size() - 1) {
                        index++;
                    } else {
                        index = 0;
                    }
                    mCurrentTrack = mMusic.get(index);
                    handlePlay();
                }
            }

            @Override
            public void onSkipToPrevious() {
                if (mCurrentTrack != null) {
                    int index = mMusic.indexOf(mCurrentTrack);
                    if (index > 0) {
                        index--;
                    } else {
                        index = mCurrentTrack.size() - 2;
                    }
                    mCurrentTrack = mMusic.get(index);
                    handlePlay();
                }
            }
        });
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setActive(true);
        setSessionToken(mSession.getSessionToken());
    }

    private PlaybackState buildState(int state) {
        return new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_PAUSE)
                .setState(state, mPlayer.getCurrentPosition(), 1,
                        SystemClock.elapsedRealtime()).build();
    }

    private void handlePlay() {
        mPlayer.seekTo(0);
        mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
        mSession.setMetadata(mCurrentTrack);
        try {
            mPlayer.reset();
            mPlayer.setDataSource(ServicioMusica.this,
                    Uri.parse(mCurrentTrack.getDescription().getMediaId()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mCurTrack = (mCurTrack < mMusic.size() - 1 ? mCurTrack + 1 : 0);
                mCurrentTrack = mMusic.get(mCurTrack);
                mPlayer.seekTo(0);
                mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
                mSession.setMetadata(mCurrentTrack);
                try {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(ServicioMusica.this,
                            Uri.parse(mCurrentTrack.getDescription().getMediaId()));
                    mediaPlayer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayer.prepareAsync();
    }

    @Override
    public MediaBrowserService.BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new MediaBrowserService.BrowserRoot("ROOT", null);
    }

    @Override
    public void onLoadChildren(String s, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> list = new ArrayList<>();
        for (MediaMetadata m : mMusic) {
            list.add(new MediaBrowser.MediaItem(m.getDescription(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(list);
    }

    @Override
    public void onDestroy() {
        mSession.release();
    }

    private void getRepositorioMusical() {
        StringRequest request = new StringRequest(Request.Method.GET, URL, onPostsLoaded, onPostsError);
        requestQueue.add(request);
    }

    private final Response.Listener<String> onPostsLoaded = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            musica = gson.fromJson(response, Musica.class);
            Log.d(TAG, "Número de pistas de audio: " + musica.getMusica().size());
            int slashPos = URL.lastIndexOf('/');
            String path = URL.substring(0, slashPos + 1);
            for (int i = 0; i < musica.getMusica().size(); i++) {
                PistaAudio pista = musica.getMusica().get(i);
                if (!pista.getSource().startsWith("http"))
                    pista.setSource(path + pista.getSource());
                if (!pista.getImage().startsWith("http"))
                    pista.setImage(path + pista.getImage());
                musica.getMusica().set(i, pista);
                ImageRequest imageRequest = new ImageRequest(pista.getImage(), listener, maxWidth, maxHeight, null, null);
                requestQueue.add(imageRequest);
                Log.e("ALBUM IMAGE", pista.getImage());
            }
        }
    };

    private final Response.ErrorListener onPostsError = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e(TAG, error.toString());
        }
    };

    private final Response.Listener<Bitmap> listener = new Response.Listener<Bitmap>() {
        @Override
        public void onResponse(Bitmap bitmap) {
            PistaAudio pista = musica.getMusica().get(num);
            mMusic.add(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, pista.getSource())
                    .putString(MediaMetadata.METADATA_KEY_TITLE, pista.getTitle())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, pista.getArtist())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, pista.getDuration())
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                    .build());
            num++;
        }
    };
}