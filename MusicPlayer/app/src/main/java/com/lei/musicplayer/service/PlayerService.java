package com.lei.musicplayer.service;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import com.lei.musicplayer.AppConstant;
import com.lei.musicplayer.application.AppCache;
import com.lei.musicplayer.bean.Mp3Info;
import com.lei.musicplayer.util.LogTool;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lei on 2017/8/3.
 * play music
 */
public class PlayerService extends Service {
    private static final String TAG = "PlayerService";
    private MediaPlayer mediaPlayer = new MediaPlayer();
    //private String path;
    private int play_progress = 0;//the current progress of one music
    public int play_position = 0;//the current position of music in list
    private List<Mp3Info> mLocalMusicList = new ArrayList<Mp3Info>();
    private int mPlayType = AppConstant.CIRCLE_ALL;
    private OnPlayerServiceListener mPlayerServiceListener;

    private Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == 1) {
                if(mediaPlayer != null) {
                    play_progress = mediaPlayer.getCurrentPosition(); // 获取当前音乐播放的位置
                    mPlayerServiceListener.onMusicCurrentPosition(play_progress);
                    handler.sendEmptyMessageDelayed(1, 1000);
                }

            }
        };
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerBinder() ;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogTool.i(TAG, " onStartCommand ");
        if (intent != null && intent.getAction() != null){
            switch (intent.getAction()){
                case AppConstant.ACTION_PLAY_STOP :
                    playOrStop(intent.getIntExtra(AppConstant.MSG_PLAY_POSITION,-1));
                    break;
                case AppConstant.ACTION__CONTROL_PROGRESS://拖动进度条
                    seekBarPlay(intent.getIntExtra(AppConstant.MSG_PROGRESS,0));
                    break;
                case AppConstant.ACTION__NEXT :
                    playNext();
                    break;
                case AppConstant.ACTION__PREVIOUS :
                    playPrevious();
                    break;
                default:
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void playOrStop(int getPlayPosition) {
        if (getPlayPosition > -1){//listView列表点击歌曲，直接0进度开始播放歌曲
            play_progress = 0;
            play_position = getPlayPosition;
            play(play_progress, play_position);
        }else{//暂停、开始按钮点击歌曲,继续上次进度播放
            if (mediaPlayer.isPlaying()){//暂停
                stop();
            }else {//继续上次位置播放
                play(play_progress,play_position);
            }
        }
    }

    private void seekBarPlay(int shortTimeProgress) {
        play_progress = shortTimeProgress;
        play(play_progress, play_position);
    }

    public void play(final int progress,final int playPosition) {
        play_progress = progress;
        play_position = playPosition;
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(mLocalMusicList.get(playPosition).getUrl());
            mediaPlayer.prepare();
            //注册一个监听器
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.start();
                    if (progress > 0) {
                        mediaPlayer.seekTo(progress);
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    complete();
                }
            });

            AppCache.setPlayingPosition(play_position);
            mPlayerServiceListener.onMusicPlay();
            handler.sendEmptyMessage(1);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playPrevious() {
        play_progress = 0;
        if (play_position == 0){
            play_position = mLocalMusicList.size() - 1;
        }else {
            play_position --;
        }
        play(play_progress, play_position);
    }

    public void playNext() {
        play_progress = 0;
        if (play_position == mLocalMusicList.size() - 1){
            play_position = 0;
        }else {
            play_position ++;
        }
        play(play_progress, play_position);
    }

    private void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }
    }

    private void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()){
            mediaPlayer.stop();
            mPlayerServiceListener.onMusicStop();
            stopHandlerLoop();
        }
    }

    private void stopHandlerLoop() {
        handler.removeMessages(1);
    }

    private void complete(){
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mPlayerServiceListener.onMusicComplete();
            stopHandlerLoop();
        }
    }

    public void onDestroy(){
        if (mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        AppCache.setPlayService(null);
    }


    /*
    * 扫描本地音乐
    * */
    public void scanLocalMusic(ScanCallBack callBack){
        mLocalMusicList = getLocalMusic();
        if (mLocalMusicList != null ){
            AppCache.setLocalMusicList(mLocalMusicList);
            callBack.onSuccess();
        }else {
            callBack.onFail("localMusicList is null");
        }
    }

    /*
   * 获取本地音乐
   * */
    private List<Mp3Info> getLocalMusic(){
        List<Mp3Info> infos = new ArrayList<Mp3Info>();
        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,null,null,null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToNext();
            long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
            //对歌曲长度进行过滤
            if (duration < AppConstant.MUSIC_DURATION){
                continue;
            }
            long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
            String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
            String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
            long size = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE));
            //the path of the music
            String url = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            String albumId = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
            //LogTool.i(TAG,"artist: " + artist);
            //getCoverImage(Integer.parseInt(albumId));
            //String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
            String albumKey = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_KEY));
            int isMusic = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC));
            //LogTool.i(TAG,"albumArt: "+albumArt);

            Mp3Info info = new Mp3Info();
            if (isMusic != 0){
                info.setId(id);
                info.setTitle(title);
                info.setArtist(artist);
                info.setDuration(duration);
                info.setSize(size);
                info.setUrl(url);
                info.setAlbumKey(albumKey);
                infos.add(info);
            }

        }

        return getAlbumArt(infos);
    }

    public List<Mp3Info> getAlbumArt(List<Mp3Info> infos){
        String[] mediaColumns1 = new String[] {
                MediaStore.Audio.Albums.ALBUM_ART,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ALBUM_KEY};
        Cursor cursor1 = getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                mediaColumns1, null, null,
                null);
        if (cursor1 != null) {
            cursor1.moveToFirst();
            do {
                String album_art =  cursor1.getString(0);
                String album =  cursor1.getString(1);
                String albumKey = cursor1.getString(2);
                LogTool.i(TAG, "ALBUM_ART 0 " + album_art
                        + "ALBUM_ART 1 " + album + " ALBUM_ART 2 " + albumKey);
                if (album_art != null && album != null){
                    for (Mp3Info info : infos) {
                        if (info.getAlbumKey().equals(albumKey)){
                            info.setAlbumArt(album_art);
                            break;
                        }
                    }
                }
            } while (cursor1.moveToNext());
            cursor1.close();
        }

        return infos;
    }

    public void setOnPlayerListener(OnPlayerServiceListener listener){
        mPlayerServiceListener = listener;
    }

    public class PlayerBinder extends android.os.Binder{
        public PlayerService getService(){
            return PlayerService.this;
        }
    }
    
}