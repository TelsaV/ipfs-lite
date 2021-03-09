package threads.server;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.ContentDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;

import io.ipfs.LogUtils;
import threads.server.core.threads.THREADS;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.provider.ProviderDataSource;
import threads.server.work.VideoImageWorker;

import static com.google.android.exoplayer2.util.RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE;


public class ExoPlayerActivity extends Activity {

    private static final String TAG = ExoPlayerActivity.class.getSimpleName();
    private PlayerView mPlayerView;
    private SimpleExoPlayer mPlayer;
    private long playbackPosition;
    private int currentWindow;
    private boolean playWhenReady = true;
    private Uri uri;
    private long idx = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exoplayer);


        mPlayerView = findViewById(R.id.video_preview);

        if (savedInstanceState == null) {
            playWhenReady = true;
            currentWindow = 0;
            playbackPosition = 0;
        } else {
            playWhenReady = savedInstanceState.getBoolean("playWhenReady");
            currentWindow = savedInstanceState.getInt("currentWindow");
            playbackPosition = savedInstanceState.getLong("playBackPosition");
        }

        Intent intent = getIntent();
        handleIntents(intent);
    }

    private void handleIntents(Intent intent) {

        final String action = intent.getAction();
        try {
            if (Intent.ACTION_VIEW.equals(action)) {
                uri = intent.getData();
                if (uri != null) {
                    idx = FileDocumentsProvider.getDocument(uri);
                }
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntents(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("playWhenReady", playWhenReady);
        outState.putInt("currentWindow", currentWindow);
        outState.putLong("playBackPosition", playbackPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPlayer == null) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        releasePlayer();
    }

    private void initializePlayer() {
        THREADS threads = THREADS.getInstance(getApplicationContext());

        if (idx > 0) {
            playbackPosition = threads.getThreadPosition(idx);
        }

        if (mPlayer == null) {
            mPlayer = new SimpleExoPlayer.Builder(getApplicationContext()).build();
            mPlayerView.setRepeatToggleModes(REPEAT_TOGGLE_MODE_NONE);
            mPlayerView.setPlayer(mPlayer);
            mPlayer.setPlayWhenReady(playWhenReady);
            mPlayer.seekTo(currentWindow, playbackPosition);
            mPlayerView.setUseController(true);
        }

        try {
            MediaSource mediaSource = buildMediaSource();
            mPlayer.setMediaSource(mediaSource, false);
            mPlayer.prepare();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void releasePlayer() {
        try {
            if (mPlayer != null) {
                long duration = mPlayer.getDuration();
                playbackPosition = mPlayer.getCurrentPosition();
                currentWindow = mPlayer.getCurrentWindowIndex();
                playWhenReady = mPlayer.getPlayWhenReady();

                mPlayer.release();


                if (idx > 0) {
                    if (duration > 30 * 1000) {
                        THREADS threads = THREADS.getInstance(getApplicationContext());

                        boolean thumbnail = false;
                        int progress = getProgress(playbackPosition, duration);
                        if (progress > 95) {
                            threads.setThreadPosition(idx, 0);
                        } else {
                            thumbnail = Settings.SUPPORT_VIDEO_UPDATE_THUMBNAIL;
                            threads.setThreadPosition(idx, playbackPosition);
                        }
                        threads.setThreadProgress(idx, progress);

                        if (thumbnail) {
                            VideoImageWorker.load(getApplicationContext(), idx, playbackPosition);
                        }
                    }
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            mPlayer = null;
        }
    }

    private int getProgress(long current, long duration) {
        try {
            return (int) ((current * 100.0f) / duration);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return 0;
    }

    private MediaSource buildMediaSource() {

        THREADS threads = THREADS.getInstance(getApplicationContext());
        IPFS ipfs = IPFS.getInstance(getApplicationContext());


        if (idx > 0) {
            String content = threads.getThreadContent(idx);
            if (content != null) {
                DataSpec dataSpec = new DataSpec(uri);
                final ProviderDataSource fileDataSource = new ProviderDataSource(ipfs, content);
                try {
                    fileDataSource.open(dataSpec);
                } catch (IOException e) {
                    LogUtils.error(TAG, e);
                }

                DataSource.Factory factory = () -> fileDataSource;

                MediaItem mediaItem = MediaItem.fromUri(uri);
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem);
            }
        }

        DataSpec dataSpec = new DataSpec(uri);

        final ContentDataSource fileDataSource = new ContentDataSource(getApplicationContext());
        try {
            fileDataSource.open(dataSpec);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        DataSource.Factory factory = () -> fileDataSource;
        MediaItem mediaItem = MediaItem.fromUri(uri);
        return new ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem);


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }


}
