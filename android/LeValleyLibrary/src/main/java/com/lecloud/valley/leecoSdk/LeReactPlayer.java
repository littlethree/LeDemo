/*************************************************************************
 * Description: 乐视视频播放组件
 * Author: raojia
 * Mail: raojia@le.com
 * Created Time: 2016-12-11
 ************************************************************************/
package com.lecloud.valley.leecoSdk;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.lecloud.sdk.player.live.MobileLivePlayer;
import com.lecloud.valley.common.Events;
import com.lecloud.valley.utils.LogUtils;
import com.lecloud.valley.utils.ScreenBrightnessManager;
import com.lecloud.valley.utils.TimeUtils;
import com.lecloud.sdk.api.linepeople.OnlinePeopleChangeListener;
import com.lecloud.sdk.api.md.entity.action.ActionInfo;
import com.lecloud.sdk.api.md.entity.action.CoverConfig;
import com.lecloud.sdk.api.md.entity.action.LiveInfo;
import com.lecloud.sdk.api.md.entity.action.WaterConfig;
import com.lecloud.sdk.api.md.entity.vod.VideoHolder;
import com.lecloud.sdk.api.status.ActionStatus;
import com.lecloud.sdk.api.status.ActionStatusListener;
import com.lecloud.sdk.api.timeshift.ItimeShiftListener;
import com.lecloud.sdk.constant.PlayerEvent;
import com.lecloud.sdk.constant.PlayerParams;
import com.lecloud.sdk.constant.StatusCode;
import com.lecloud.sdk.listener.AdPlayerListener;
import com.lecloud.sdk.listener.MediaDataPlayerListener;
import com.lecloud.sdk.listener.OnPlayStateListener;
import com.letv.android.client.cp.sdk.player.live.CPActionLivePlayer;
import com.letv.android.client.cp.sdk.player.live.CPLivePlayer;
import com.letv.android.client.cp.sdk.player.vod.CPVodPlayer;
import com.letvcloud.cmf.MediaPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lecloud.valley.common.Constants.*;
import static com.lecloud.valley.utils.LogUtils.TAG;


/**
 * Created by raojia on 2016/11/10.
 */
class LeReactPlayer extends LeTextureView implements LifecycleEventListener, MediaDataPlayerListener,
        OnPlayStateListener, AdPlayerListener, MediaPlayer.OnVideoRotateListener {

    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;
    private int mViewId;
    /// 水印
    private LeWaterMarkView mLeWaterMarkView;

    /*
    * 设备控制
    */
    private final AudioManager mAudioManager;
    private int mCurrentBrightness;

    /// 播放器设置
    private int mPlayMode = -1;

    private boolean mDownload = false; //是否可以下载
    private boolean mPano = false;  //是否全景
    // 播放器可用状态，prepared, started, paused，completed 时为true
    private boolean mLePlayerValid = false;

    /*
    * 视频媒资信息
    */
    private String mVideoTitle; // 点播视频标题
    private LinkedHashMap<String, String> mRateList;  // 当前支持的码率
    private String mDefaultRate;  // 当前视频默认码率
    private int mVideoWidth;  //视频实际宽度
    private int mVideoHeight;  //视频实际高度
    private CoverConfig mCoverConfig;  //LOGO，加载图，水印等信息
    /*
    * 视频状态信息
    */
    private String mCurrentRate;  // 当前视频码率
    private String mSelectRate;   // 用户选择的码率

    private boolean mPaused = false;  // 暂停状态
    private boolean mRepeat = false;  // 重播状态
    protected boolean isCompleted = false;   // 是否播放完毕
    private boolean isSeeking = false;  // 是否在缓冲加载状态

    private boolean mPlayInBackground = false;  //是否后台播放
    private boolean mOriginPauseStatus = false;  //退入后台前状态

    /*
     * == 云点播状态 ==============
    */
    private int mVideoDuration = 0;  //当前视频总长


    private int mLastPosition;  //上次播放位置
    private int mVideoBufferedDuration = 0; //当前已缓冲长度
    // VOD进度更新线程
    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = new Runnable() {

        @Override
        public void run() {
            if (mLePlayerValid && !isCompleted) {
                WritableMap event = Arguments.createMap();
                event.putInt(EVENT_PROP_CURRENT_TIME, (int) Math.ceil(getCurrentPosition() / 1000.0));
                event.putInt(EVENT_PROP_DURATION, mVideoDuration);
                event.putInt(EVENT_PROP_PLAYABLE_DURATION, mVideoBufferedDuration);
                mEventEmitter.receiveEvent(mViewId, Events.EVENT_PROGRESS.toString(), event);
            }
            mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, 250);
        }
    };

    /*
     * == 云直播状态变量 =====================
    */
    private ActionInfo mActionInfo;

    private com.lecloud.sdk.api.md.entity.live.LiveInfo mCurrentLiveInfo;
    private String mCurrentLiveId; //当前机位
    private int mServerTime = 0;
    private int mBeginTime = 0;
    private int mCurrentTime = 0;
    private int mWaterMarkHight = 800;  //高位
    private int mWaterMarkLow = 200; //低位
    private int mMaxDelayTime = 1000; // 最大延时
    private int mCachePreSize = 500; // 起播缓冲值
    private int mCacheMaxSize = 10000; //最大缓冲值
    private ItimeShiftListener mTimeShiftListener;
    private OnlinePeopleChangeListener mOnlinePeopleChangeListener;
    private ActionStatusListener mActionStatusListener;


/*============================= 播放器构造 ===================================*/

    public LeReactPlayer(ThemedReactContext context) {
        super(context);
        mThemedReactContext = context;
        mThemedReactContext.addLifecycleEventListener(this);

        setSurfaceTextureListener(this);

        //设置声音管理器
        mAudioManager = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);

        // 获得当前屏幕亮度，取值0-1
        mCurrentBrightness = ScreenBrightnessManager.getScreenBrightness(context.getBaseContext()) * 100 / 255;

        // 屏幕方向
//        mCurrentOritentation = ScreenUtils.getOrientation(context.getBaseContext());
    }

    public void setEventEmitter(RCTEventEmitter mEventEmitter) {
        this.mEventEmitter = mEventEmitter;
    }

    public void setViewId(int mViewId) {
        this.mViewId = mViewId;
    }

    public void setWaterMarkSurface(LeWaterMarkView mLeWaterMarkView) {
        this.mLeWaterMarkView = mLeWaterMarkView;
    }

    private void initActionLiveListener() {

        if (mTimeShiftListener == null) {
            mTimeShiftListener = new ItimeShiftListener() {
                /**
                 * 直播时移监听 (Live)
                 * 用于更新直播时间和进度条显示
                 *
                 */
                @Override
                public void onChange(long serverTime, long currentTime, long beginTime) {
                    Log.d(TAG, LogUtils.getTraceInfo() + "直播时移事件——— serverTime：" + TimeUtils.timet(serverTime)
                            + "，currentTime：" + TimeUtils.timet(currentTime) + "，beginTime：" + TimeUtils.timet(beginTime));

                    mServerTime = (int) Math.ceil(serverTime / 1000);
                    mBeginTime = (int) Math.ceil(beginTime / 1000);
                    mCurrentTime = (int) Math.ceil(currentTime / 1000);

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_SERVER_TIME, mServerTime);
                    event.putInt(EVENT_PROP_CURRENT_TIME, mCurrentTime);
                    event.putInt(EVENT_PROP_LIVE_BEGIN_TIME, mBeginTime);
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_ACTION_TIME_SHIFT.toString(), event);
                }
            };
            setTimeShiftListener(mTimeShiftListener);
        }

        if (mActionStatusListener == null) {
            mActionStatusListener = new ActionStatusListener() {
                @Override
                public void onChange(ActionStatus actionStatus) {
                    Log.d(TAG, LogUtils.getTraceInfo() + "直播状态变化事件——— actionStatus：" + actionStatus.toString());

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_LIVE_ACTION_STATE, actionStatus.getStatus());
                    event.putString(EVENT_PROP_LIVE_ACTION_ID, actionStatus.getActivityId());
                    event.putInt(EVENT_PROP_LIVE_BEGIN_TIME, (int) Math.ceil(actionStatus.getBeginTime() / 1000));
                    event.putInt(EVENT_PROP_LIVE_END_TIME, (int) Math.ceil(actionStatus.getEndTime() / 1000));
                    event.putString(EVENT_PROP_LIVE_ID, actionStatus.getLiveId());
                    event.putString(EVENT_PROP_STREAM_ID, actionStatus.getStreamId());

                    event.putString(EVENT_PROP_ERROR_CODE, actionStatus.getErrCode());
                    event.putString(EVENT_PROP_ERROR_MSG, actionStatus.getErrMsg());

                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_ACTION_STATUS_CHANGE.toString(), event);
                }
            };

            setActionStatusListener(mActionStatusListener);
        }

        if (mOnlinePeopleChangeListener == null) {
            mOnlinePeopleChangeListener = new OnlinePeopleChangeListener() {

                @Override
                public void onChange(String s) {
                    Log.d(TAG, LogUtils.getTraceInfo() + "在线人数变化事件——— onlineNum：" + s);

                    WritableMap event = Arguments.createMap();
                    event.putString(EVENT_PROP_ONLINE_NUM, s);
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_ONLINE_NUM_CHANGE.toString(), event);
                }
            };

            setOnlinePeopleListener(mOnlinePeopleChangeListener);
        }

    }

    // 创建播放器及监听
    private void initLePlayerIfNeeded() {
        if (mMediaPlayer == null) {
            mLePlayerValid = false;

            Context ctx = mThemedReactContext.getBaseContext();

            ((Activity) mThemedReactContext.getBaseContext()).getWindow().setFormat(PixelFormat.TRANSLUCENT);
            ((Activity) mThemedReactContext.getBaseContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            switch (mPlayMode) {
                case PlayerParams.VALUE_PLAYER_LIVE: //直播机位
                    mMediaPlayer = new CPLivePlayer(ctx);
                    break;

                case PlayerParams.VALUE_PLAYER_VOD: //云点播
                    mMediaPlayer = new CPVodPlayer(ctx);
                    break;

                case PlayerParams.VALUE_PLAYER_ACTION_LIVE: //云直播
                    mMediaPlayer = new CPActionLivePlayer(ctx);

                    setCacheWatermark(mWaterMarkHight, mWaterMarkLow);
                    setMaxDelayTime(mMaxDelayTime);
                    setCachePreSize(mCachePreSize);
                    setCacheMaxSize(mCacheMaxSize);

                    break;

                case PlayerParams.VALUE_PLAYER_MOBILE_LIVE: //移动直播
                    mMediaPlayer = new MobileLivePlayer(ctx);
                    break;

            }

            //设置播放器监听器
            setOnMediaDataPlayerListener(this);

            setOnAdPlayerListener(this);

            setOnPlayStateListener(this);

            setOnVideoRotateListener(this);

            if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE)
                initActionLiveListener();
        }
    }


    /*============================= 播放器外部接口 ===================================*/

    /**
     * 设置数据源，必填（VOD、LIVE）
     *
     * @param bundle 数据源包
     */
    public void setSrc(final Bundle bundle) {
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 传入数据源 bundle:" + bundle);
        if (bundle == null) return;

        // 播放模式切换，重新创建Player
        int newPlayMode = bundle.containsKey(PROP_SRC_PLAY_MODE) ? bundle.getInt(PROP_SRC_PLAY_MODE) : -1;
//        if (mPlayMode != -1 && newPlayMode != mPlayMode) {
//            cleanupMediaPlayerResources();
//        }
        cleanupMediaPlayerResources();

        mPlayMode = newPlayMode;
        mPano = bundle.containsKey(PROP_SRC_IS_PANO) && bundle.getBoolean(PROP_SRC_IS_PANO);
        mRepeat = bundle.containsKey(PROP_SRC_IS_REPEAT) && bundle.getBoolean(PROP_SRC_IS_REPEAT);
        mSelectRate = bundle.containsKey(PROP_RATE)? bundle.getString(PROP_RATE):"";

        //创建播放器
        initLePlayerIfNeeded();

        if (mMediaPlayer != null) {
//            clearDataSource();

            //初始化状态变量
            initFieldParaStates();

            if (bundle.containsKey("path"))
                setDataSource(bundle.getString("path"));
            else
                setDataSource(bundle);

            WritableMap event = Arguments.createMap();
            event.putString(PROP_SRC, bundle.toString());
            mEventEmitter.receiveEvent(mViewId, Events.EVENT_LOAD_SOURCE.toString(), event);
        }
    }

    private void initFieldParaStates() {
        mLePlayerValid = false;
        isCompleted = false;
        isSeeking = false;

        mVideoTitle = "";
        mVideoDuration = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoBufferedDuration = 0;
        mLastPosition = 0;
        mCurrentRate = mDefaultRate = "";
        mCurrentLiveId = "";
        mCurrentTime = 0;
        mServerTime = 0;
        mBeginTime = 0;

        mRateList = null;
        mCoverConfig = null;
        mActionInfo = null;
        mCurrentLiveInfo = null;

    }

    /**
     * 视频Seek到某一位置（VOD）
     * 直播Seek到某一时间（LIVE）
     *
     * @param sec 单位秒
     */
    public void setSeekTo(final int sec) {
        if (sec < 0) return;
        mLastPosition = sec; //保存上次位置
        if (!mLePlayerValid) return;

        if (mPlayMode == PlayerParams.VALUE_PLAYER_VOD) { //点播

            if (sec <= mVideoDuration) {
                mLastPosition = sec;
                seekTo(sec * 1000);
            } else {
                mLastPosition = mVideoDuration * 1000;
                seekTo(mVideoDuration * 1000);
            }

            if (isCompleted && mVideoDuration != 0 && sec < mVideoDuration) {
                isCompleted = false;
                retry();
            }
            Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— SEEK TO：" + sec);

        } else if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE) { //直播

            if (sec > mServerTime) {
                mLastPosition = mServerTime;
                retry();
            } else if (sec < mBeginTime) {
                mLastPosition = mBeginTime;
                seekTimeShift(mBeginTime);
            } else {
                mLastPosition = sec;
                seekTimeShift(sec);
            }

            Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— SEEK TIMESHIFT：" + sec);
        }

        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_CURRENT_TIME, (int) Math.ceil(getCurrentPosition() / 1000));
        event.putInt(EVENT_PROP_SEEK_TIME, sec);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_SEEK.toString(), event);

    }

    /**
     * 视频切换码率（VOD、LIVE）
     *
     * @param rate 码率值
     */
    public void setRate(final String rate) {
        if (TextUtils.isEmpty(rate)) {
            return;
        }

        // 检查码率是否可用
        if (mLePlayerValid && mRateList != null && mRateList.containsKey(rate)) {
            // 保存当前位置
            saveLastPostion();

            //切换码率
            setDataSourceByRate(rate);
            mCurrentRate = rate;

            WritableMap event = Arguments.createMap();
            event.putString(EVENT_PROP_CURRENT_RATE, mCurrentRate);
            event.putString(EVENT_PROP_NEXT_RATE, rate);
            mEventEmitter.receiveEvent(mViewId, Events.EVENT_RATE_CHANG.toString(), event);
        }
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 切换码率 current:" + mCurrentRate + " next:" + rate);
    }


    /**
     * 云直播切换机位（LIVE）
     *
     * @param liveId 机位ID
     */
    public void setLive(final String liveId) {
        if (TextUtils.isEmpty(liveId)) {
            return;
        }

        //检查是否是云直播播放器，相关参数是否正确
        if (!mLePlayerValid || mPlayMode != PlayerParams.VALUE_PLAYER_ACTION_LIVE || mActionInfo == null)
            return;

        // 检查机位是否可用
        if (mActionInfo.getLiveInfos() != null && mActionInfo.getLiveInfos().contains(liveId)) {
            //切换机位
            setDataSourceByLiveId(liveId);
            mCurrentLiveId = liveId;

            WritableMap event = Arguments.createMap();
            event.putString(EVENT_PROP_CURRENT_LIVE, mCurrentLiveId);
            event.putString(EVENT_PROP_NEXT_LIVE, liveId);
            mEventEmitter.receiveEvent(mViewId, Events.EVENT_ACTION_LIVE_CHANGE.toString(), event);
        }
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 切换机位 current:" + mCurrentLiveId + " next:" + liveId);
    }


    /**
     * 设置视频暂停和启动（VOD、LIVE）
     *
     * @param paused paused
     */
    public void setPaused(final boolean paused) {
        if (!mLePlayerValid || mPaused == paused) {
            return;
        }

        mPaused = paused;

        setPausedModifier(paused);
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 设置暂停状态:" + paused);
    }


    public void setPlayInBackground(final boolean playInBackground) {
        mPlayInBackground = playInBackground;

        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 设置是否后台播放:" + playInBackground);
    }

    /**
     * 设置视频暂停和启动（VOD、LIVE）
     *
     * @param clicked 是否点击
     */
    public void setClickAd(final boolean clicked) {
        if (!clicked || !mLePlayerValid) {
            return;
        }

        clickAd();


        WritableMap event = Arguments.createMap();
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_AD_CLICK.toString(), event);
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 点击广告:" + clicked);
    }

    /**
     * 设置左右声道（VOD、LIVE）
     *
     * @param leftVolume  左声道
     * @param rightVolume 右声道
     */
    public void setLeftAndRightTrack(final float leftVolume, final float rightVolume) {
        if (leftVolume < 0 || rightVolume < 0) {
            return;
        }
        setVolume(leftVolume, rightVolume);
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 设置左右声道:" + leftVolume + "," + rightVolume);
    }

    /**
     * 音量控制 0-100（VOD、LIVE）
     *
     * @param percentage 音量百分比
     */
    public void setVolumePercent(final int percentage) {
        if (null == mAudioManager) {
            return;
        }
        if (percentage < 0 || percentage > 100) {
            return;
        }
        int maxValue = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, percentage * maxValue / 100, 0);
        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 调节音量:" + percentage);
    }


    /**
     * 设置亮度百分比（VOD、LIVE）
     *
     * @param brightness 取值0-1
     */
    public void setScreenBrightness(final int brightness) {
        if (brightness < 0 || brightness > 100) {
            return;
        }
        mCurrentBrightness = brightness;
        ScreenBrightnessManager.setScreenBrightness((Activity) (mThemedReactContext.getBaseContext()), ((brightness * 255) / 100));

        Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 调节亮度:" + brightness);
    }

    /**
     * 保存上次播放位置
     */
    private void saveLastPostion() {
        if (!mLePlayerValid || getCurrentPosition() == 0) {
            return;
        }
        mLastPosition = (int) Math.ceil(getCurrentPosition() / 1000);
    }


    /**
     * 设置视频暂停和启动（VOD、LIVE）
     *
     * @param paused paused
     */
    private void setPausedModifier(final boolean paused) {
        mPaused = paused;

        if (!mLePlayerValid) {
            return;
        }

        if (mPaused) {
            if (isPlaying()) {

                pause();

                //暂停更新进度
                if (mPlayMode == PlayerParams.VALUE_PLAYER_VOD) {
                    saveLastPostion();
                    mProgressUpdateHandler.removeCallbacks(mProgressUpdateRunnable);

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_DURATION, mVideoDuration);
                    event.putInt(EVENT_PROP_CURRENT_TIME, (int) Math.ceil(getCurrentPosition() / 1000));
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_PAUSE.toString(), event);

                } else if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE && mTimeShiftListener != null) {
                    //stopTimeShift();

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_LIVE_BEGIN_TIME, mBeginTime);
                    event.putInt(EVENT_PROP_CURRENT_TIME, mCurrentTime);
                    event.putInt(EVENT_PROP_SERVER_TIME, mServerTime);
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_PAUSE.toString(), event);
                }
                Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 暂停播放 pause ");
            }
        } else {
            if (!isPlaying()) {

                start();

                //启动更新进度
                if (mPlayMode == PlayerParams.VALUE_PLAYER_VOD) {
                    mProgressUpdateHandler.post(mProgressUpdateRunnable);

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_DURATION, mVideoDuration);
                    event.putInt(EVENT_PROP_CURRENT_TIME, (int) Math.ceil(getCurrentPosition() / 1000.0));
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_RESUME.toString(), event);

                } else if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE && mTimeShiftListener != null) {
                    startTimeShift();

                    WritableMap event = Arguments.createMap();
                    event.putInt(EVENT_PROP_LIVE_BEGIN_TIME, mBeginTime);
                    event.putInt(EVENT_PROP_CURRENT_TIME, mCurrentTime);
                    event.putInt(EVENT_PROP_SERVER_TIME, mServerTime);
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_RESUME.toString(), event);
                }

                Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 开始播放 start ");

            }
        }
    }

    /**
     * 设置回到上次播放的地址（VOD）
     *
     * @param lastPosition lastPosition
     */
    private void setLastPosModifier(final int lastPosition) {
        mLastPosition = lastPosition;

        if (!mLePlayerValid) {
            return;
        }
        //回到上次播放位置
        if (mMediaPlayer != null && mPlayMode == PlayerParams.VALUE_PLAYER_VOD && mLastPosition != 0) {

            if (mLastPosition < mVideoDuration)
                seekToLastPostion(lastPosition * 1000);
            else
                seekToLastPostion(mVideoDuration * 1000);

            Log.d(TAG, LogUtils.getTraceInfo() + "外部控制——— 恢复位置 seekToLastPostion: " + mLastPosition);
        }

    }


    /**
     * 根据当前状态设置播放器
     */
    private void applyModifiers() {
        setPausedModifier(mPaused);
        setLastPosModifier(mLastPosition);
    }

    /**
     * 销毁播放器，释放资源（VOD、LIVE）
     */
    public void cleanupMediaPlayerResources() {
        Log.d(TAG, LogUtils.getTraceInfo() + "控件清理 cleanupMediaPlayerResources 调起！");
        
        if (mMediaPlayer != null) {
            mLePlayerValid = false;

            if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE && mTimeShiftListener != null)
                stopTimeShift();

            if (isPlaying()) stop();
            release();

            mTimeShiftListener = null;
            mActionStatusListener = null;
            mOnlinePeopleChangeListener = null;

            mMediaPlayer = null;
        }

        ((Activity) mThemedReactContext.getBaseContext()).getWindow().setFormat(PixelFormat.TRANSLUCENT);
        ((Activity) mThemedReactContext.getBaseContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }


/*============================= 事件回调处理 ===================================*/

    /**
     * 处理播放器准备完成事件
     */
    private void processPrepared(int what, Bundle bundle) {
        mLePlayerValid = true;

        //开始封装回调事件参数
        WritableMap event = Arguments.createMap();

        mVideoDuration = (int) Math.ceil(getDuration() / 1000);

        // 当前播放模式
        event.putInt(EVENT_PROP_PLAY_MODE, mPlayMode);

        // 视频基本信息，长/宽/方向
        WritableMap naturalSize = Arguments.createMap();
        naturalSize.putInt(EVENT_PROP_WIDTH, mVideoWidth);
        naturalSize.putInt(EVENT_PROP_HEIGHT, mVideoHeight);
        if (mVideoWidth > mVideoHeight)
            naturalSize.putString(EVENT_PROP_VIDEO_ORIENTATION, "landscape");
        else
            naturalSize.putString(EVENT_PROP_VIDEO_ORIENTATION, "portrait");

        // 视频基本信息
        event.putString(EVENT_PROP_TITLE, mVideoTitle); //视频标题
        event.putMap(EVENT_PROP_NATURALSIZE, naturalSize);  //原始尺寸


        // 媒资信息
        boolean hasRateInfo = (mRateList != null && mRateList.size() > 0);
        boolean hasLogo = (mCoverConfig != null && mCoverConfig.getLogoConfig() != null && mCoverConfig.getLogoConfig().getPicUrl() != null);
        boolean hasLoading = (mCoverConfig != null && mCoverConfig.getLoadingConfig() != null && mCoverConfig.getLoadingConfig().getPicUrl() != null);
        boolean hasWater = (mCoverConfig != null && mCoverConfig.getWaterMarks() != null && mCoverConfig.getWaterMarks().size() > 0);

        // 视频码率信息
        if (hasRateInfo) {
            WritableArray ratesList = Arguments.createArray();
            for (Map.Entry<String, String> rate : mRateList.entrySet()) {
                WritableMap map = Arguments.createMap();
                map.putString(EVENT_PROP_RATE_KEY, rate.getKey());
                map.putString(EVENT_PROP_RATE_VALUE, rate.getValue());
                ratesList.pushMap(map);
            }
            event.putArray(EVENT_PROP_RATELIST, ratesList);  //可用码率
            event.putString(EVENT_PROP_DEFAULT_RATE, mDefaultRate);  //默认码率
            event.putString(EVENT_PROP_CURRENT_RATE, mCurrentRate);  //当前码率
        }

        // 视频封面信息: LOGO
        if (hasLogo) {
            WritableMap logoConfig = Arguments.createMap();
            logoConfig.putString(EVENT_PROP_PIC, mCoverConfig.getLogoConfig().getPicUrl());
            logoConfig.putString(EVENT_PROP_TARGET, mCoverConfig.getLogoConfig().getTargetUrl());
            logoConfig.putString(EVENT_PROP_POS, mCoverConfig.getLogoConfig().getPos());

            event.putMap(EVENT_PROP_LOGO, logoConfig);  // LOGO信息
        }
        // 视频封面信息: 加载
        if (hasLoading) {
            WritableMap LoadingConfig = Arguments.createMap();
            LoadingConfig.putString(EVENT_PROP_PIC, mCoverConfig.getLoadingConfig().getPicUrl());
            LoadingConfig.putString(EVENT_PROP_TARGET, mCoverConfig.getLoadingConfig().getTargetUrl());
            LoadingConfig.putString(EVENT_PROP_POS, mCoverConfig.getLoadingConfig().getPos());

            event.putMap(EVENT_PROP_LOAD, LoadingConfig);  // LOADING信息
        }
        // 视频封面信息: 水印
        if (hasWater) {
            WritableArray waterMarkList = Arguments.createArray();
            for (int i = 0; i < mCoverConfig.getWaterMarks().size(); i++) {
                WritableMap map = Arguments.createMap();
                WaterConfig waterConfig = mCoverConfig.getWaterMarks().get(i);
                map.putString(EVENT_PROP_PIC, waterConfig.getPicUrl());
                map.putString(EVENT_PROP_TARGET, waterConfig.getTargetUrl());
                map.putString(EVENT_PROP_POS, waterConfig.getPos());
                waterMarkList.pushMap(map);
            }
            event.putArray(EVENT_PROP_WMARKS, waterMarkList);  // 水印信息
        }

        if (mPlayMode == PlayerParams.VALUE_PLAYER_VOD) { //VOD模式下参数
            event.putDouble(EVENT_PROP_DURATION, mVideoDuration);  //视频总长度（VOD）
            event.putDouble(EVENT_PROP_CURRENT_TIME, mLastPosition);  //当前播放位置（VOD）
            event.putBoolean(EVENT_PROP_VOD_IS_DOWNLOAD, mDownload); //是否允许下载（VOD）
            event.putBoolean(EVENT_PROP_VOD_IS_PANO, mPano); //是否全景（VOD）

        } else if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE) { //LIVE模式参数

            WritableMap actionLive = Arguments.createMap();
            if (mActionInfo != null) {
                actionLive.putString(EVENT_PROP_LIVE_COVER_IMG, mActionInfo.getCoverImgUrl()); //直播封面图（LIVE）
                actionLive.putString(EVENT_PROP_LIVE_PLAYER_URL, mActionInfo.getPlayerPageUrl()); //直播页面URL（LIVE）
                actionLive.putInt(EVENT_PROP_LIVE_ACTION_STATE, mActionInfo.getActivityState()); //直播状态（LIVE）
                actionLive.putString(EVENT_PROP_CURRENT_LIVE, mCurrentLiveId); //当前机位（LIVE）

                actionLive.putBoolean(EVENT_PROP_LIVE_NEED_FULLVIEW, mActionInfo.getNeedFullView() != 0); //是否全屏播放（LIVE)
                actionLive.putBoolean(EVENT_PROP_LIVE_NEED_TIMESHIFT, mActionInfo.getNeedTimeShift() != 0); //是否支持时移（LIVE）
                actionLive.putBoolean(EVENT_PROP_LIVE_IS_NEED_AD, mActionInfo.getIsNeedAd() != 0); //是否有广告（LIVE）
                actionLive.putString(EVENT_PROP_LIVE_ARK, mActionInfo.getArk()); //ARK（LIVE）

                if (mCurrentLiveInfo != null) {
                    actionLive.putString(EVENT_PROP_LIVE_BEGIN_TIME, mCurrentLiveInfo.getLiveBeginTime()); //开始时间（LIVE）
                    actionLive.putString(EVENT_PROP_LIVE_START_TIME, mCurrentLiveInfo.getLiveStartTime()); //结束时间（LIVE）
                }

                final List<LiveInfo> liveInfos = mActionInfo.getLiveInfos();
                if (liveInfos != null && liveInfos.size() > 0) {
                    WritableArray liveList = Arguments.createArray();
                    for (int i = 0; i < liveInfos.size(); i++) {
                        WritableMap map = Arguments.createMap();
                        LiveInfo liveInfo = liveInfos.get(i);
                        map.putString(EVENT_PROP_LIVE_ID, liveInfo.getLiveId()); // 机位ID （LIVE）
                        map.putInt(EVENT_PROP_LIVE_MACHINE, liveInfo.getMachine()); // 机位名称 （LIVE）
                        map.putString(EVENT_PROP_LIVE_PRV_STEAMID, liveInfo.getPreviewStreamId()); // 流ID （LIVE）
                        map.putString(EVENT_PROP_LIVE_PRV_STEAMURL, liveInfo.getPreviewStreamPlayUrl()); // 流预览信息 （LIVE）
                        map.putInt(EVENT_PROP_LIVE_STATUS, liveInfo.getStatus()); // 机位状态 （LIVE）
                        liveList.pushMap(map);
                    }
                    actionLive.putArray(EVENT_PROP_LIVES, liveList);  // 机位信息 （LIVE）
                }
            }
            event.putMap(EVENT_PROP_ACTIONLIVE, actionLive); //云直播数据
        }

//        event.putInt(EVENT_PROP_MMS_STATCODE, mMediaStatusCode); //媒资状态码
//        event.putInt(EVENT_PROP_MMS_HTTPCODE, mMediaHttpCode); //媒资状态码mMediaStatusCode

        // 设备信息： 声音和亮度
        int volume = 0;
        if (null != mAudioManager) {
            volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxValue = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volume = volume * 100 / maxValue; //获得声音百分比
        }

        // 设备信息
        event.putInt(EVENT_PROP_VOLUME, volume); //声音百分比
        event.putInt(EVENT_PROP_BRIGHTNESS, mCurrentBrightness); //屏幕亮度

        mEventEmitter.receiveEvent(mViewId, Events.EVENT_LOAD.toString(), event);

        // 执行播放器控制
        applyModifiers();

    }


    /**
     * 处理视频信息事件
     *
     * @param what   the what
     * @param bundle the extra
     * @return the boolean
     */
    private boolean processPlayerInfo(int what, Bundle bundle) {
        int statusCode = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_STATUS_CODE)) ?
                bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE) : -1;

        switch (statusCode) {
            case StatusCode.PLAY_INFO_BUFFERING_START://500004
                //缓冲开始
                isSeeking = true;
                mEventEmitter.receiveEvent(mViewId, Events.EVENT_BUFFER_START.toString(), null);
                break;
            case StatusCode.PLAY_INFO_BUFFERING_END://500005
                //缓冲结束
                mEventEmitter.receiveEvent(mViewId, Events.EVENT_BUFFER_END.toString(), null);
                break;
            case StatusCode.PLAY_INFO_VIDEO_RENDERING_START://500006
                //渲染第一帧完成
                mEventEmitter.receiveEvent(mViewId, Events.EVENT_RENDING_START.toString(), null);
                break;
            case StatusCode.PLAY_INFO_VIDEO_BUFFERPERCENT://600006
                //视频缓冲时的进度，开始转圈
                WritableMap event = Arguments.createMap();
                event.putInt(EVENT_PROP_VIDEO_BUFF, bundle.containsKey(EVENT_PROP_VIDEO_BUFF) ? bundle.getInt(EVENT_PROP_VIDEO_BUFF) : 0);
                mEventEmitter.receiveEvent(mViewId, Events.EVENT_BUFFER_PERCENT.toString(), event);
                break;
            default:
                if (mPlayMode != PlayerParams.VALUE_PLAYER_VOD)
                    mEventEmitter.receiveEvent(mViewId, Events.EVENT_BUFFER_START.toString(), null);

        }
        return false;
    }

    /**
     * 处理视频开始缓冲事件.
     *
     * @param what   the what
     * @param bundle the extra
     * @return the boolean
     */
    private boolean processPlayerLoading(int what, Bundle bundle) {
        //视频缓冲时的进度，开始转圈
        isSeeking = true;

        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_VIDEO_BUFF, (bundle != null && bundle.containsKey(PlayerParams.KEY_VIDEO_BUFFER)) ?
                bundle.getInt(PlayerParams.KEY_VIDEO_BUFFER) : 0);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_BUFFER_PERCENT.toString(), event);
        return false;
    }

    /**
     * 处理视频Seek完毕
     *
     * @param what   the what
     * @param bundle the extra
     * @return the boolean
     */
    private boolean processSeekComplete(int what, Bundle bundle) {
        //视频缓冲转圈加载完成
        saveLastPostion(); //TODO 待驗證
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_SEEK_COMPLETE.toString(), null);
        return false;
    }

    /**
     * 处理获得视频真实尺寸的回调
     *
     * @param what   PLAY_VIDEOSIZE_CHANGE
     * @param bundle width,height
     * @return boolean
     */
    private boolean processVideoSizeChanged(int what, Bundle bundle) {

        mVideoWidth = (bundle != null && bundle.containsKey(PlayerParams.KEY_WIDTH)) ? bundle.getInt(PlayerParams.KEY_WIDTH) : -1;
        mVideoHeight = (bundle != null && bundle.containsKey(PlayerParams.KEY_HEIGHT)) ? bundle.getInt(PlayerParams.KEY_HEIGHT) : -1;

        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_WIDTH, mVideoWidth);
        event.putInt(EVENT_PROP_HEIGHT, mVideoHeight);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_CHANGESIZE.toString(), event);
        return true;
    }


    /**
     * 处理播放器缓冲事件
     *
     * @param what   PLAY_BUFFERING
     * @param bundle Bundle[{bufferpercent=xx}]
     * @return boolean
     */
    private boolean processBufferingUpdate(int what, Bundle bundle) {
        // 正常播放状态
        isSeeking = false;

        int percent = (bundle != null && bundle.containsKey(PlayerParams.KEY_PLAY_BUFFERPERCENT)) ? bundle.getInt(PlayerParams.KEY_PLAY_BUFFERPERCENT) : 0;
        mVideoBufferedDuration = (int) Math.round((double) (mVideoDuration * percent) / 100.0);

        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_PLAY_BUFFERPERCENT, percent);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_PLAYABLE_PERCENT.toString(), event);
        return true;
    }


    /**
     * 处理下载完成的事件
     *
     * @param what   PLAY_DOWNLOAD_FINISHED
     * @param bundle null
     * @return boolean
     */
    private boolean processDownloadFinish(int what, Bundle bundle) {
        int percent = 100;
        mVideoBufferedDuration = mVideoDuration;

        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_PLAY_BUFFERPERCENT, percent);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_PLAYABLE_PERCENT.toString(), event);
        return true;
    }


    /**
     * 处理播放完成事件
     *
     * @param what   the what
     * @param bundle the extra
     * @return boolean
     */
    private boolean processCompletion(int what, Bundle bundle) {
        isCompleted = true;
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_END.toString(), null);

        if(mRepeat) setSeekTo(0); //是否重播

        return true;
    }

    /**
     * 处理出错事件
     *
     * @param what   the what
     * @param bundle the extra
     * @return boolean
     */
    private boolean processError(int what, Bundle bundle) {
        int statusCode = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_STATUS_CODE)) ? bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE) : -1;
        String errorCode = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_ERROR_CODE)) ? bundle.getString(PlayerParams.KEY_RESULT_ERROR_CODE) : "";
        String errorMsg = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_ERROR_MSG)) ? bundle.getString(PlayerParams.KEY_RESULT_ERROR_MSG) : "";

        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_WHAT, what);
        error.putInt(EVENT_PROP_MMS_STATCODE, statusCode);
        error.putString(EVENT_PROP_ERROR_CODE, errorCode);
        error.putString(EVENT_PROP_ERROR_MSG, errorMsg);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_ERROR.toString(), error);
        return true;
    }


    /**
     * 处理媒资点播数据获取的的事件
     *
     * @param what   AD_PROGRESS
     * @param bundle null
     * @return boolean
     */
    private boolean processMediaVodLoad(int what, Bundle bundle) {
        int mediaStatusCode = bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE);

        switch (mediaStatusCode) {

            case StatusCode.MEDIADATA_SUCCESS: //OK
                VideoHolder videoHolder = bundle.getParcelable(PlayerParams.KEY_RESULT_DATA);
                if (videoHolder == null) return false;

                //获得视频标题
                String title = videoHolder.getTitle();
                if (!TextUtils.isEmpty(title)) {
                    mVideoTitle = title;
                }
                //获得视频长度
                mVideoDuration = (int) Math.ceil(Long.parseLong(videoHolder.getVideoDuration()) / 1000);

                //获得默认码率和码率列表
                if (mRateList != null) mRateList.clear();
                mRateList = videoHolder.getVtypes();
                mDownload = videoHolder.isDownload();
                mPano = videoHolder.isPano();
                mCurrentRate = mDefaultRate = videoHolder.getDefaultVtype();

                //获得加载和水印图
                mCoverConfig = videoHolder.getCoverConfig();

                if (mLeWaterMarkView != null
                        && mCoverConfig != null
                        && mCoverConfig.getWaterMarks() != null
                        && mCoverConfig.getWaterMarks().size() > 0) {
                    mLeWaterMarkView.setWaterMarks(mCoverConfig.getWaterMarks());
                    mLeWaterMarkView.showWaterMarks();
                }

                //设置当前码率为默认
                setDataSourceByRate(mCurrentRate);
                break;

            default: //处理错误
                processError(what, bundle);
                break;
        }

        Log.d(TAG, LogUtils.getTraceInfo() + "媒资数据事件——— 点播事件 event:" + what + " bundle:" + bundle.toString());
        return true;
    }


    /**
     * 处理云直播返回的活动信息（1个活动最多包含4个机位，在后台配置）
     *
     * @param what   MEDIADATA_ACTION
     * @param bundle null
     * @return boolean
     */
    private boolean processMediaActionLoad(int what, Bundle bundle) {
        int mediaStatusCode = bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE);

        switch (mediaStatusCode) {

            case StatusCode.MEDIADATA_SUCCESS: //OK
                ActionInfo actionInfo = bundle.getParcelable(PlayerParams.KEY_RESULT_DATA);
                if (actionInfo == null) return false;

                //获得视频标题
                String title = actionInfo.getActivityName();
                if (!TextUtils.isEmpty(title)) {
                    mVideoTitle = title;
                }

                // 获得封面图和网页播放地址
                mActionInfo = actionInfo;

                //获得加载和水印图
                mCoverConfig = actionInfo.getCoverConfig();

                if (mLeWaterMarkView != null
                        && mCoverConfig != null
                        && mCoverConfig.getWaterMarks() != null
                        && mCoverConfig.getWaterMarks().size() > 0) {
                    mLeWaterMarkView.setWaterMarks(mCoverConfig.getWaterMarks());
                    mLeWaterMarkView.showWaterMarks();
                }

                // 获得活动状态
                int actionStatus = actionInfo.getActivityState();
                if (actionStatus == ActionStatus.STATUS_LIVE_ING) {
                    LiveInfo liveInfo = null;
                    //获取当前第一个直播节目
                    List<LiveInfo> liveInfoList = actionInfo.getLiveInfos();
                    for (LiveInfo entity : liveInfoList) {
                        liveInfo = entity;
                        if (liveInfo.getStatus() == LiveInfo.STATUS_ON_USE) break;
                    }
                    if (liveInfo != null && liveInfo.getStatus() == LiveInfo.STATUS_ON_USE) { //开始直播
                        setDataSourceByLiveId(liveInfo.getLiveId());
                        mCurrentLiveId = liveInfo.getLiveId();
                    } else {
                        int liveStatus = (liveInfo == null) ? -1 : liveInfo.getStatus();
                        processLiveStatus(liveStatus, bundle);
                    }
                } else {
                    processActionStatus(actionStatus, bundle);
                }

                break;

            default: //处理错误
                processError(what, bundle);
                break;
        }

        Log.d(TAG, LogUtils.getTraceInfo() + "媒资数据事件——— 直播活动数据事件 event:" + what + " bundle:" + bundle.toString());
        return true;
    }


    /**
     * 处理云直播状态反馈
     *
     * @param status
     * @param bundle the extra
     * @return boolean
     */
    private boolean processActionStatus(int status, Bundle bundle) {
        int statusCode = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_STATUS_CODE)) ? bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE) : -1;

        String errorCode = String.valueOf(status);
        String errorMsg = null;
        switch (status) {
            case ActionStatus.SATUS_NOT_START:
                errorMsg = "直播未开始，请稍后……";
                break;
            case ActionStatus.STATUS_END:
                errorMsg = "直播已结束";
                break;
            case ActionStatus.STATUS_LIVE_ING:
                errorMsg = "直播信号已恢复";
                break;
            case ActionStatus.STATUS_INTERRUPTED:
                errorMsg = "直播信号中断，请稍后……";
                break;
            default:
                errorMsg = "暂无直播信号，请稍后……";
        }

        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_MMS_STATCODE, statusCode);
        error.putString(EVENT_PROP_ERROR_CODE, errorCode);
        error.putString(EVENT_PROP_ERROR_MSG, errorMsg);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_ERROR.toString(), error);
        return true;
    }

    /**
     * 云直播返回的机位信息事件
     *
     * @param what
     * @param bundle null
     * @return boolean
     */
    private boolean processMediaLiveLoad(int what, Bundle bundle) {
        int mediaStatusCode = bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE);

        switch (mediaStatusCode) {

            case StatusCode.MEDIADATA_SUCCESS: //OK
                com.lecloud.sdk.api.md.entity.live.LiveInfo liveInfo = bundle.getParcelable(PlayerParams.KEY_RESULT_DATA);
                if (liveInfo == null) return false;

                mCurrentLiveInfo = liveInfo;

                //获得默认码率和码率列表
                if (mRateList != null) mRateList.clear();
                mRateList = liveInfo.getVtypes();

                mCurrentRate = mDefaultRate = liveInfo.getDefaultVtype();

                int liveStatus = liveInfo.getActivityState();
                if (liveStatus == LiveInfo.STATUS_ON_USE) {
                    setDataSourceByRate(mCurrentRate);
                } else {
                    processLiveStatus(liveStatus, bundle);
                }
                break;

            default: //处理错误
                processError(what, bundle);
                break;
        }

        Log.d(TAG, LogUtils.getTraceInfo() + "媒资数据事件——— 直播机位事件 event:" + what + " bundle:" + bundle.toString());
        return true;
    }


    /**
     * 处理云直播机位信息的状态反馈
     *
     * @param status
     * @param bundle the extra
     * @return boolean
     */
    private boolean processLiveStatus(int status, Bundle bundle) {
        int statusCode = (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_STATUS_CODE)) ? bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE) : -1;

        String errorCode = String.valueOf(status);
        String errorMsg;
        switch (status) {
            case LiveInfo.STATUS_NOT_USE:
                errorMsg = "直播未开始，请稍后……";
                break;
            case LiveInfo.STATUS_END:
                errorMsg = "直播已结束";
                break;
            case LiveInfo.STATUS_ON_USE:
                errorMsg = "直播信号已恢复";
                break;
            default:
                errorMsg = "暂无直播信号，请稍后……";
        }

        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_MMS_STATCODE, statusCode);
        error.putString(EVENT_PROP_ERROR_CODE, errorCode);
        error.putString(EVENT_PROP_ERROR_MSG, errorMsg);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_ERROR.toString(), error);
        return true;
    }

    /**
     * 处理媒资调度数据获取的的事件
     *
     * @param what   MEDIADATA_GET_PLAYURL
     * @param bundle null
     * @return boolean
     */
    private boolean processMediaPlayURLLoad(int what, Bundle bundle) {
        // todo 调度信息获取
        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_MMS_STATCODE, (bundle != null && bundle.containsKey(PlayerParams.KEY_RESULT_STATUS_CODE)) ? bundle.getInt(PlayerParams.KEY_RESULT_STATUS_CODE) : -1);
//        event.putString(EVENT_PROP_RET_DATA, (bundle != null && bundle.containsKey(EVENT_PROP_RET_DATA)) ? bundle.getString(EVENT_PROP_RET_DATA) : "");
        event.putInt(EVENT_PROP_MMS_HTTPCODE, (bundle != null && bundle.containsKey(PlayerParams.KEY_HTTP_CODE)) ? bundle.getInt(PlayerParams.KEY_HTTP_CODE) : -1);
//        mEventEmitter.receiveEvent(mViewId, Events.EVENT_MEDIA_PLAYURL.toString(), event);
        return true;
    }

    /**
     * 处理广告开始的事件
     *
     * @param what   AD_START
     * @param bundle null
     * @return boolean
     */
    private boolean processAdvertStart(int what, Bundle bundle) {
        //mLePlayerValid = true; //广告不允许暂停
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_AD_START.toString(), null);
        return true;
    }

    /**
     * 处理广告结束的事件
     *
     * @param what   AD_COMPLETE
     * @param bundle null
     * @return boolean
     */
    private boolean processAdvertComplete(int what, Bundle bundle) {
        mLePlayerValid = true;
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_AD_COMPLETE.toString(), null);
        return true;
    }

    /**
     * 处理正在播放广告的事件
     *
     * @param what   AD_PROGRESS
     * @param bundle null
     * @return boolean
     */
    private boolean processAdvertProgress(int what, Bundle bundle) {
        WritableMap event = Arguments.createMap();
        event.putInt(EVENT_PROP_AD_TIME, (bundle != null && bundle.containsKey(EVENT_PROP_AD_TIME)) ? bundle.getInt(EVENT_PROP_AD_TIME) : 0);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_AD_PROGRESS.toString(), event);
        return true;
    }

    /**
     * 处理广告出错的事件
     *
     * @param what   AD_ERROR
     * @param bundle null
     * @return boolean
     */
    private boolean processAdvertError(int what, Bundle bundle) {
        processError(what, bundle);
        return true;
    }

    /**
     * 处理其他事件
     *
     * @param what   the what
     * @param bundle the extra
     * @return boolean
     */
    private boolean processOtherEvent(int what, Bundle bundle) {
        WritableMap other = Arguments.createMap();
        other.putInt(EVENT_PROP_WHAT, what);
        other.putString(EVENT_PROP_EXTRA, (bundle != null) ? bundle.toString() : "");
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_EVENT, other);
        mEventEmitter.receiveEvent(mViewId, Events.EVENT_OTHER_EVENT.toString(), event);
        return true;
    }

    /*============================= 各类事件回调 ===================================*/

    /**
     * 处理播放器事件，具体事件参见IPlayer类
     */
    @Override
    public void videoState(int state, Bundle bundle) {
        boolean handled = false;
        String event = "";
        switch (state) {

            case PlayerEvent.PLAY_INIT: //200
                // 播放器初始化
                handled = true;
                event = "PLAY_INIT";
//                processOtherEvent(state, bundle);
                break;

            case PlayerEvent.PLAY_BUFFERING: // 获取视频缓冲加载状态 201
                handled = true;
                event = "PLAY_BUFFERING";
                processBufferingUpdate(state, bundle);
                break;

            case PlayerEvent.PLAY_COMPLETION:  // 播放完毕  202
                handled = true;
                event = "PLAY_COMPLETION";
                processCompletion(state, bundle);
                break;


            case PlayerEvent.PLAY_DECODER_CHANGED: // 解码方式切换 203
                handled = true;
                event = "PLAY_DECODER_CHANGED";

                break;

            case PlayerEvent.PLAY_DOWNLOAD_FINISHED: // 视频下载完成 204
                handled = true;
                event = "PLAY_DOWNLOAD_FINISHED";
                processDownloadFinish(state, bundle);
                break;

            case PlayerEvent.PLAY_ERROR: //播放出错 205
                handled = true;
                event = "PLAY_ERROR";
                processError(state, bundle);
                break;

            case PlayerEvent.PLAY_INFO: //206
                // 获取播放器状态
                handled = true;
                event = "PLAY_INFO";
                processPlayerInfo(state, bundle);
                break;

            case PlayerEvent.PLAY_LOADINGSTART: // 207
                // 开始缓冲视频
                handled = true;
                event = "PLAY_LOADINGSTART";
                processPlayerLoading(state, bundle);
                break;

            case PlayerEvent.PLAY_PREPARED: //播放器准备完毕 208
                handled = true;
                event = "PLAY_PREPARED";
                processPrepared(state, bundle);
                break;

            case PlayerEvent.PLAY_SEEK_COMPLETE: // 用户跳转完毕 209
                handled = true;
                event = "PLAY_SEEK_COMPLETE";
                processSeekComplete(state, bundle);
                break;

            /**
             * 获取到视频的宽高的时候，此时可以通过视频的宽高计算出比例，进而设置视频view的显示大小。
             */
            case PlayerEvent.PLAY_VIDEOSIZE_CHANGED: // 视频宽高发生变化时 210
                handled = true;
                event = "PLAY_VIDEOSIZE_CHANGED";
                processVideoSizeChanged(PlayerEvent.PLAY_VIDEOSIZE_CHANGED, bundle);
                break;

            case PlayerEvent.PLAY_OVERLOAD_PROTECTED: // 视频过载保护 211
                handled = true;
                event = "PLAY_OVERLOAD_PROTECTED";
                break;

            case PlayerEvent.VIEW_PREPARE_VIDEO_SURFACE: // 添加了视频播放器SurfaceView 8001
                handled = true;
                event = "VIEW_PREPARE_VIDEO_SURFACE";
                setDisplay();
                break;

            case PlayerEvent.VIEW_PREPARE_AD_SURFACE:  // 添加了广告播放器SurfaceView 8002
                handled = true;
                event = "VIEW_PREPARE_AD_SURFACE";
                setDisplay();
                break;

        }
        if (handled)
            Log.d(TAG, LogUtils.getTraceInfo() + "播放器事件——— event " + event + " state " + state + " bundle " + bundle);
    }


    @Override
    public void onVideoRotate(int i) {

    }


    /**
     * 处理视频信息类事件
     */
    @Override
    public void onMediaDataPlayerEvent(int state, Bundle bundle) {
        boolean handled = false;
        String event = "";
        switch (state) {

            case PlayerEvent.MEDIADATA_VOD:  // 云点播返回媒资信息  6000
                handled = true;
                event = "MEDIADATA_VOD";
                processMediaVodLoad(state, bundle);

                break;

            case PlayerEvent.MEDIADATA_LIVE:  // 云直播返回的机位信息  6001
                handled = true;
                event = "MEDIADATA_LIVE";
                processMediaLiveLoad(state, bundle);
                break;

            case PlayerEvent.MEDIADATA_GET_PLAYURL:  // 云直播请求调度服务器回来的结果（rtmp直播）  6002
                handled = true;
                event = "MEDIADATA_GET_PLAYURL";
                processMediaPlayURLLoad(state, bundle);
                break;

            case PlayerEvent.MEDIADATA_ACTION:  // 云直播返回的活动信息（1个活动最多4个机位，在后台配置）6003
                handled = true;
                event = "MEDIADATA_ACTION";
                processMediaActionLoad(state, bundle);
                break;

        }
        if (handled)
            Log.d(TAG, LogUtils.getTraceInfo() + "视频信息事件——— event " + event + " state " + state + " bundle " + bundle);
    }

    /**
     * 处理广告类事件
     */
    @Override
    public void onAdPlayerEvent(int state, Bundle bundle) {
        boolean handled = false;
        String event = "";
        switch (state) {

            case PlayerEvent.AD_TIME:  // 广告？？？？  7004
                handled = true;
                event = "AD_TIME";
                break;

            case PlayerEvent.AD_START:  // 广告开始播放 7005
                handled = true;
                event = "AD_START";
                processAdvertStart(state, bundle);
                break;

            case PlayerEvent.AD_COMPLETE:  // 广告结束播放  7006
                handled = true;
                event = "AD_COMPLETE";
                processAdvertComplete(state, bundle);
                break;

            case PlayerEvent.AD_PROGRESS: // 广告播放进度  7007
                handled = true;
                event = "AD_PROGRESS";
                processAdvertProgress(state, bundle);
                break;

            case PlayerEvent.AD_ERROR: // 广告播放错误  7008
                handled = true;
                event = "AD_ERROR";
                processAdvertError(state, bundle);
                break;

            default:
                videoState(state, bundle);
                break;

        }
        if (handled)
            Log.d(TAG, LogUtils.getTraceInfo() + "广告播放事件——— event：" + event + "，state：" + state + "，bundle：" + bundle);

    }


    @Override
    protected void onAttachedToWindow() {
        Log.d(TAG, LogUtils.getTraceInfo() + "生命周期事件 onAttachedToWindow 调起！");
//        if (mOrientationSensorUtils == null) {
//            mOrientationSensorUtils = new OrientationSensorUtils((Activity) mThemedReactContext.getBaseContext(), mOrientationChangeHandler);
//        }
//        if(!mUseGravitySensor){
//            return;
//        }
//        mOrientationSensorUtils.onResume();

        super.onAttachedToWindow();
    }



    @Override
    protected void onDetachedFromWindow() {
        Log.d(TAG, LogUtils.getTraceInfo() + "生命周期事件 onDetachedFromWindow 调起！");
        super.onDetachedFromWindow();

//        if (mOrientationSensorUtils != null) {
//            mOrientationSensorUtils.onPause();
//        }
//        mOrientationChangeHandler.removeCallbacksAndMessages(null);

        if (mMediaPlayer != null) {
            cleanupMediaPlayerResources();
        }
    }


/*============================= 容器生命周期方法 ===================================*/


    @Override
    public void onHostResume() {
        Log.d(TAG, LogUtils.getTraceInfo() + "生命周期事件 onHostResume 调起！");

        if (mMediaPlayer != null && !mPlayInBackground) {

            mPaused = false;
//            retry();

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (mLeWaterMarkView != null
                            && mCoverConfig != null
                            && mCoverConfig.getWaterMarks() != null
                            && mCoverConfig.getWaterMarks().size() > 0) {
                        //mLeWaterMarkView.setWaterMarks(mCoverConfig.getWaterMarks());
                        mLeWaterMarkView.showWaterMarks();
                    }

                    if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE && mTimeShiftListener != null)
                        startTimeShift();
                    // Restore original state
                    retry();
//                    setPausedModifier(mOriginPauseStatus);
                }
            });

        }
    }

    @Override
    public void onHostPause() {
        Log.d(TAG, LogUtils.getTraceInfo() + "生命周期事件 onHostPause 调起！");

        if (mMediaPlayer != null && !mPlayInBackground) {

            mOriginPauseStatus = mPaused;
            mPaused = true;

            if (mPlayMode == PlayerParams.VALUE_PLAYER_VOD) saveLastPostion();
            if (mPlayMode == PlayerParams.VALUE_PLAYER_ACTION_LIVE && mTimeShiftListener != null)
                stopTimeShift();

            pause();
//            setPausedModifier(mPaused);
        }
    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, LogUtils.getTraceInfo() + "生命周期事件 onHostDestroy 调起！");

        if (mMediaPlayer != null) {
            cleanupMediaPlayerResources();
        }
    }

}
