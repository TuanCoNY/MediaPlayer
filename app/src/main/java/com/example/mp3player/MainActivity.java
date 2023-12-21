package com.example.mp3player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.splashscreen.SplashScreen;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chibde.visualizer.BarVisualizer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.jgabrielfreitas.core.BlurImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerview;
    SongAdapter songAdapter;
    List<Song> allSongs = new ArrayList<>();
    ActivityResultLauncher<String> storagePermissionLauncher;
    final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
    SimpleExoPlayer player;
    ActivityResultLauncher<String> recordAudioPermissionLauncher; //to be accessed in the song adapter
    final String recordAudioPermission = Manifest.permission.RECORD_AUDIO;
    ConstraintLayout playerView;
    TextView playerCloseBtn;
    //controls
    TextView songNameView, skipPreviousBtn, skipNextBtn, playPauseBtn, repeatModeBtn, playListBtn;
    TextView homeSongNameView, homeSkipPreviousBtn, homePlayPauseBtn, homeSkipNextBtn;
    //wrapper
    ConstraintLayout homeControlWrapper, headWrapper, artworkWrapper, seekbarWrapper, controlWrapper, audioVisualizerWrapper;
    CircleImageView artworkView;
    SeekBar seekbar;
    TextView progressView, durationView;
    BarVisualizer audioVisualizer;
    BlurImageView blurImageView;
    int defaultStatusColor;
    //repeat mode
    int repeatMode = 1; //repeat all = 1,  repeat one = 3, shuffle all = 2
    SearchView searchView;
    //is the act, bound
    boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //install splash screen
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //save the status color
        defaultStatusColor = getWindow().getStatusBarColor();
        //set the navigation color
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199)); // 0 & 255

        //set toolbar and app title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString(R.string.app_name));
        recyclerview = findViewById(R.id.recyclerview);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted->{
            if (granted) {
                //fetch songs
                fetchSongs();
            }
            else {
                userResponses();
            }
        });


        //record audio permission
        recordAudioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted->{
           if (granted && player.isPlaying()) {
               activateAudioVisualizer();
           }
           else {
               userResponsesOnRecordAudioPerm();
           }
        });

        playerView = findViewById(R.id.playerView);
        playerCloseBtn = findViewById(R.id.playerCloseBtn);
        songNameView = findViewById(R.id.songNameView);
        skipPreviousBtn = findViewById(R.id.skipPreviousBtn);
        skipNextBtn = findViewById(R.id.skipNextBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        repeatModeBtn = findViewById(R.id.repeatModeBtn);
        playListBtn = findViewById(R.id.playListBtn);

        homeSongNameView = findViewById(R.id.homeSongNameView);
        homeSkipNextBtn = findViewById(R.id.homeSkipNextBtn);
        homePlayPauseBtn = findViewById(R.id.homePlayPauseBtn);
        homeSkipPreviousBtn = findViewById(R.id.homeSkipPreviousBtn);

        //wrapper
        homeControlWrapper = findViewById(R.id.homeControlWrapper);
        headWrapper = findViewById(R.id.headWrapper);
        artworkWrapper = findViewById(R.id.artWorkWrapper);
        seekbarWrapper = findViewById(R.id.seekbarWrapper);
        controlWrapper = findViewById(R.id.controlWrapper);
        audioVisualizerWrapper = findViewById(R.id.audioVisualizerWrapper);

        //artwork
        artworkView = findViewById(R.id.artworkView);
        //seekbar
        seekbar = findViewById(R.id.seekbar);
        progressView = findViewById(R.id.progressView);
        durationView = findViewById(R.id.durationView);
        //audio visualizer
        audioVisualizer = findViewById(R.id.visualizer);
        //blur image view
        blurImageView = findViewById(R.id.blurImageView);

        //launcher storage permission on create
        //storagePermissionLauncher.launch(permission);


        //bind to the player service, end do every thing after the binding
        doBindService();
    }

    private void doBindService() {
        Intent playerServiceIntent = new Intent(this, PlayerService.class);
        bindService(playerServiceIntent, playerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection playerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            //get the service instance
            PlayerService.ServiceBinder binder = (PlayerService.ServiceBinder) iBinder;
            player = binder.getPlayerService().player;
            isBound = true;
            //ready to show songs
            storagePermissionLauncher.launch(permission);
            //call player control methods
            playerControls();

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    public void onBackPressed() {
        //we say if the player view is visible , close it
        if (playerView.getVisibility() == View.VISIBLE) {
            exitPlayerView();
        }
        else
        super.onBackPressed();
    }

    private void playerControls() {
        //song name marquee
        songNameView.setSelected(true);
        homeSongNameView.setSelected(true);

        //exit the player view
        playerCloseBtn.setOnClickListener(view -> exitPlayerView());
        playListBtn.setOnClickListener(view -> exitPlayerView());
        //open player view on home control wrapper click
        homeControlWrapper.setOnClickListener(view -> showPlayerView());

        //player listener
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                Player.Listener.super.onMediaItemTransition(mediaItem, reason);
                //show the playing song title
                assert mediaItem != null;
                songNameView.setText(mediaItem.mediaMetadata.title);
                homeSongNameView.setText(mediaItem.mediaMetadata.title);

                progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                seekbar.setProgress((int) player.getCurrentPosition());
                seekbar.setMax((int) player.getDuration());
                durationView.setText(getReadableTime((int)player.getDuration()));
                playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
                homePlayPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                //show the current art work
                showCurrentArtwork();
                //update the progress position of a current playing song
                updatePlayerPositionProgress();
                //load the artwork animation
                artworkView.setAnimation(loadRotation());
                //set audio visualizer
                activateAudioVisualizer();
                //update player view colors
                updatePlayerColor();

                if (!player.isPlaying()) {
                    player.play();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                if (playbackState == SimpleExoPlayer.STATE_READY) {
                    //set values to player views
                    songNameView.setText(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.title);
                    homeSongNameView.setText(player.getCurrentMediaItem().mediaMetadata.title);
                    progressView.setText(getReadableTime((int)player.getCurrentPosition()));
                    durationView.setText(getReadableTime((int)player.getDuration()));
                    seekbar.setMax((int) player.getDuration());
                    seekbar.setProgress((int) player.getCurrentPosition());
                    playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                    //show the current art work
                    showCurrentArtwork();
                    //update the progress position of a current playing song
                    updatePlayerPositionProgress();
                    //load the artwork animation
                    artworkView.setAnimation(loadRotation());
                    //set audio visualizer
                    activateAudioVisualizer();
                    //update player view colors
                    updatePlayerColor();

                }
                else {
                    playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play_circle_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play_arrow, 0, 0, 0);

                }
            }
        });
        //skip to next track
        skipNextBtn.setOnClickListener(view -> skipToNextSong());
        homeSkipNextBtn.setOnClickListener(view -> skipToNextSong());
        //skip to previous track
        skipPreviousBtn.setOnClickListener(view -> skipToPreviousSong());
        homeSkipPreviousBtn.setOnClickListener(view -> skipToPreviousSong());
        //play or pause the player
        playPauseBtn.setOnClickListener(view -> playOrPausePlayer());
        homePlayPauseBtn.setOnClickListener(view -> playOrPausePlayer());
        //seek bar listener
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressValue = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                progressValue = seekBar.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player.getPlaybackState() == SimpleExoPlayer.STATE_READY) {
                    seekBar.setProgress(progressValue);
                    progressView.setText(getReadableTime(progressValue));
                    player.seekTo(progressValue);
                }
            }
        });
        //repeat mode
        repeatModeBtn.setOnClickListener(view -> {
            if (repeatMode == 1) {
                //repeat all
                player.setRepeatMode(SimpleExoPlayer.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(false);
                repeatMode = 2;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat, 0, 0, 0);

            } else if (repeatMode == 2) {
                // shuffle all
                player.setShuffleModeEnabled(true);
                player.setRepeatMode(SimpleExoPlayer.REPEAT_MODE_ALL);
                repeatMode = 3;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_shuffle, 0, 0, 0);

            } else if (repeatMode == 3) {
                player.setRepeatMode(SimpleExoPlayer.REPEAT_MODE_ONE);
                repeatMode = 1;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat_one, 0, 0, 0);
            }
            updatePlayerColor();
        });
    }

    private void playOrPausePlayer() {
        if (player.isPlaying()) {
            player.pause();
            playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play_circle_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play_arrow, 0, 0, 0);
            artworkView.clearAnimation();
        }
        else {
            player.play();
            playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
            artworkView.startAnimation(loadRotation());
        }
        //update player colors
        updatePlayerColor();
    }

    private void skipToPreviousSong() {
        if(player.hasPreviousMediaItem()){
            player.seekToPrevious();
        }
    }
    private void skipToNextSong() {
        if(player.hasNextMediaItem()){
            player.seekToNext();
        }
    }

    private void updatePlayerPositionProgress() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (player.isPlaying()) {
                    progressView.setText(getReadableTime((int)player.getCurrentPosition()));
                    seekbar.setProgress((int) player.getCurrentPosition());
                }
                //repeat calling the method
                updatePlayerPositionProgress();
            }
        }, 1000);
    }

    private Animation loadRotation() {
        RotateAnimation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(10000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        return rotateAnimation;
    }

    private void showCurrentArtwork() {
        artworkView.setImageURI(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.artworkUri);

        if (artworkView.getDrawable() == null) {
            artworkView.setImageResource(R.drawable.default_artwork);
        }
    }

    //need fix
    String getReadableTime(int duration) {
        String time;
        int hrs = duration/(1000*60*60);
        int min = duration%(1000*60*60)/(1000*60);
        int secs = (duration % (1000 * 60)) / 1000;
        if (hrs < 1) {
            time = min +":"+ secs;
        }
        else {
            time = hrs + ":" + min + ":" + secs;
        }
        return time;
    }

    private void updatePlayerColor() {

        //only player view is visible
        if (playerView.getVisibility() == View.GONE)
            return;

        BitmapDrawable bitmapDrawable = (BitmapDrawable) artworkView.getDrawable();
        if (bitmapDrawable == null) {
            bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.default_artwork);
        }

        assert bitmapDrawable != null;
        Bitmap bmp = bitmapDrawable.getBitmap();

        //set bitmap to blur image view
        blurImageView.setImageBitmap(bmp);
        blurImageView.setBlur(4);

        //player control colors
        Palette.from(bmp).generate(palette -> {
           if (palette != null) {
               Palette.Swatch swatch = palette.getDarkVibrantSwatch();
               if (swatch == null) {
                   swatch = palette.getMutedSwatch();
                   if (swatch == null) {
                       swatch = palette.getDominantSwatch();
                   }
               }
               //extract text colors
               assert swatch != null;
               int titleTextColor = swatch.getTitleTextColor();
               int bodyTextColor = swatch.getBodyTextColor();
               int rgbColor = swatch.getRgb();

               //set color to player views
               //status & navigation bar colors
               getWindow().setStatusBarColor(rgbColor);
               getWindow().setNavigationBarColor(rgbColor);

               //more view colors
               songNameView.setTextColor(titleTextColor);
               playerCloseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
               progressView.setTextColor(bodyTextColor);
               durationView.setTextColor(bodyTextColor);

               repeatModeBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
               skipPreviousBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
               skipNextBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
               playPauseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
               playListBtn.getCompoundDrawables()[0].setTint(bodyTextColor);

           }
        });
    }
    private void showPlayerView() {
        playerView.setVisibility(View.VISIBLE);
        updatePlayerColor();
    }
    private void exitPlayerView() {
        playerView.setVisibility(View.GONE);
        getWindow().setStatusBarColor(defaultStatusColor);
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199));
    }

    private void userResponsesOnRecordAudioPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (shouldShowRequestPermissionRationale(recordAudioPermission)){
                //show an educational UI explaining why we need this permission
                // use alert dialog
                new AlertDialog.Builder(this)
                        .setTitle("Requesting to show Audio Visualizer")
                        .setMessage("Allow this app to display audio visualizer when music is playing")
                        .setPositiveButton("allow", (dialogInterface, i) -> {
                            // request the perm
                            recordAudioPermissionLauncher.launch(recordAudioPermission);
                        })
                        .setNegativeButton("No", (dialogInterface, i) -> {
                            Toast.makeText(getApplicationContext(), "you denied to show the audio visualizer", Toast.LENGTH_SHORT).show();
                            dialogInterface.dismiss();
                        })
                        .show();
            }
            else {
                Toast.makeText(getApplicationContext(), "you denied to show the audio visualizer", Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void activateAudioVisualizer() {
        //check if we have record audio permission to show an audio visualizer
        if (ContextCompat.checkSelfPermission(this, recordAudioPermission) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //set color to the audio visualizer
        audioVisualizer.setColor(ContextCompat.getColor(this, R.color.secondary_color));
        //set number of visualizer btn 10 & 256
        audioVisualizer.setDensity(10);
        //set the audio session id from the player
        audioVisualizer.setPlayer(player.getAudioSessionId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    private void doUnbindService() {
        if (isBound) {
            unbindService(playerServiceConnection);
            isBound = false;
        }
    }

    private void userResponses() {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            fetchSongs();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(permission)) {
                new AlertDialog.Builder(this)
                        .setTitle("Requesting permission")
                        .setMessage("Allow us to fetch songs on your device")
                        .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // Request permission
                                storagePermissionLauncher.launch(permission);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(getApplicationContext(), "You denied us to show songs", Toast.LENGTH_SHORT).show();
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
            }
        }
        else {
            Toast.makeText(this, "You canceled to show songs", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchSongs() {
        //define a list to cary songs
        List<Song> songs = new ArrayList<>();
        Uri mediaStoreUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }
        else {
            mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        //define projection
        String[] projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
        };
        //order
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        //get the songs
        try (Cursor cursor = getContentResolver().query(mediaStoreUri, projection, null, null, sortOrder)){
            assert cursor != null;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

            //clear the previous loaded before adding loading again
            while (cursor.moveToNext()) {
                //get the values of a column for a given audio file
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                int duration = cursor.getInt(durationColumn);
                int size = cursor.getInt(sizeColumn);
                long albumId = cursor.getLong(albumIdColumn);

                // Check if the file name contains a dot before using lastIndexOf
                int dotIndex = name.lastIndexOf(".");
                if (dotIndex != -1) {
                    // File has an extension, remove it
                    name = name.substring(0, dotIndex);
                }

                // Song uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                // Album artwork uri
                Uri albumArtworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);

                // Song item
                Song song = new Song(name, uri, albumArtworkUri, size, duration);

                // Add song item to song list
                songs.add(song);
            }
            //display song
            showSongs(songs);
        }

    }

    private void showSongs(List<Song> songs) {
        if (songs.size() == 0) {
            Toast.makeText(this, "no song", Toast.LENGTH_SHORT).show();
            return;
        }

        //save song
        allSongs.clear();
        allSongs.addAll(songs);

        //update the toolbar title
        String title = getResources().getString(R.string.app_name) + " - " + songs.size();
        Objects.requireNonNull(getSupportActionBar()).setTitle(title);

        //layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerview.setLayoutManager(layoutManager);

        //song adapter
        songAdapter = new SongAdapter( this, songs, player, playerView);
        //set the adapter to recyclerview
        //recyclerview.setAdapter(songAdapter);

        //recyclerview animators optional
        ScaleInAnimationAdapter scaleInAnimationAdapter = new ScaleInAnimationAdapter(songAdapter);
        scaleInAnimationAdapter.setDuration(1000);
        scaleInAnimationAdapter.setInterpolator(new OvershootInterpolator());
        scaleInAnimationAdapter.setFirstOnly(false);
        recyclerview.setAdapter(scaleInAnimationAdapter);
    }

    //setting the menu search btn
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_btn, menu);
        MenuItem menuItem = menu.findItem(R.id.searchBtn);
        SearchView searchView = (SearchView) menuItem.getActionView();
        assert searchView != null;
        SearchSong(searchView);
        return super.onCreateOptionsMenu(menu);
    }

    private void SearchSong(SearchView searchView) {

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSongs(newText.toLowerCase());
                return true;
            }
        });
    }

    private void filterSongs(String query) {
        List<Song> filteredList = new ArrayList<>();

        if (allSongs.size() > 0) {
            for (Song song : allSongs) {
                if (song.getTitle().toLowerCase().contains(query)) {
                    filteredList.add(song);
                }
            }
            if (songAdapter != null) {
                songAdapter.filterSongs(filteredList);
            }
        }
    }
}