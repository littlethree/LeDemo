/*************************************************************************
 * Description: SDK组件示例
 * Author: raojia
 * Mail: raojia@le.com
 * Created Time: 2016-10-30
 ************************************************************************/
'use strict';

import React, {Component} from 'react';
import {
    AppRegistry,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
    Dimensions,
} from 'react-native';

//取得屏幕宽高
const {height: SCREEN_HEIGHT, width: SCREEN_WIDTH} = Dimensions.get('window');

import RCTLeVideoView from '../componets/RCTLeVideoView';

class VideoPlayer extends Component {
    constructor(props) {
        super(props);
        this.state = {
            /* 视频真实尺寸 */
            width: -1,
            height: -1,
            /* 暂停/播放状态 */
            paused: false,
            /* 跳转 */
            seek: 0,
            /* 播放码率 */
            rate: '',
            /* 播放音量 */
            volume: 1,
            /* 视频总长度 */
            duration: 0.0,
            /* 视频当前时间点 */
            currentTime: 0.0,
            /* 视频已缓冲百分比 */
            buffPercent: 0,

            /* 播放源信息 */
            sourceInfo: '',
            /* 视频信息 */
            videoInfo: '',
            /* 码率列表信息 */
            ratesInfo: [],
            /* 媒资信息 */
            mediaInfo: '',
            /* 事件信息 */
            eventInfo: '',
            /* 广告信息 */
            advertInfo: '',
        };

        this.onRateListLoad = this.onRateListLoad.bind(this);
    }


    /**
     * 获得视频可选的码率
     * @param {any} data
     * @memberOf VideoPlayer
     */
    onRateListLoad(data) {
        var arr = data.rateList;
        this.setState({ ratesInfo: arr });
        //alert(this.state.ratesInfo);
        for (var i = 0; i < arr.length; i++) {
            console.log(`key:${arr[i].key},value:${arr[i].value}`);
        }
    }

    /**
     * 渲染码率控件，设置码率
     * @param {any} volume 码率
     * @returns
     * @memberOf VideoPlayer
     */
    renderRateControl(rate) {
        const isSelected = (this.state.rate == rate);
        let rateName = '';
        switch (rate) {
            case '21':
                rateName = ' 标清 ';
                break;
            case '13':
                rateName = ' 高清 ';
                break;
            case '22':
                rateName = ' 超清 ';
                break;
            default:
                break;
        }
        // if( this.state.ratesInfo.length > 0 ) alert(this.state.ratesInfo );
        return (
            <TouchableOpacity onPress={() => { this.setState({ rate: rate }); } }>
                <Text style={[styles.controlOption, { fontWeight: isSelected ? "bold" : "normal" }]}>
                    {rateName}
                </Text>
            </TouchableOpacity>
        );
    }

    /**
     * 获得视频当前播放百分比
     * @returns
     * @memberOf VideoPlayer
     */
    getCurrentTimePercentage() {
        if (this.state.currentTime > 0) {
            return parseFloat(this.state.currentTime) / parseFloat(this.state.duration);
        } else {
            return 0;
        }
    }

    render() {
        const flexCompleted = this.getCurrentTimePercentage() * 100;
        const flexRemaining = (1 - this.getCurrentTimePercentage()) * 100;

        //网络地址
        const uri = { uri: "http://cache.utovr.com/201601131107187320.mp4", pano: false, hasSkin: false };
        //标准点播
        //const vod = { playMode: 10000, uuid: "838389", vuid: "200271100", businessline: "102", saas: true, pano: false, hasSkin: false }; //Demo示例，有广告
        const vod = { playMode: 10000, uuid: "847695", vuid: "200323369", businessline: "102", saas: true, pano: false, hasSkin: false }; //乐视云测试数据
        //const vod = { playMode: 10000, uuid: "841215", vuid: "300184109", businessline: "102", saas: true, pano: false, hasSkin: false };  //川台数据
        // const vod = { playMode: 10000, uuid: "819108", vuid: "200644549", businessline: "102", saas: true, pano: false, hasSkin: false };  //川台数据,艳秋提供带广告
        //活动直播
        const live = { playMode: 10002, actionId: "A2016062700000gx", usehls: false, customerId: "838389", businessline: "102", cuid: "", utoken: "", pano: false, hasSkin: false };

        return (
            <View style={styles.container}>
                <TouchableOpacity style={styles.fullScreen} onPress={() => { this.setState({ paused: !this.state.paused }); } }>
                    <RCTLeVideoView style={styles.fullScreen}
                        source={vod}
                        paused={this.state.paused}
                        seek={this.state.seek}
                        rate={this.state.rate}
                        onLoadSource={(data) => { this.setState({ sourceInfo: `视频源: ${data.src}` }); } }
                        onSizeChange={(data) => { this.setState({ width: data.width, height: data.height, videoInfo: `实际宽高:${data.width}，${data.height}` }); } }
                        onRateLoad={this.onRateListLoad}
                        onLoad={(data) => { this.setState({ duration: data.duration, eventInfo: 'Player准备完毕' }); } }
                        onProgress={(data) => { this.setState({ currentTime: data.currentTime, eventInfo: `播放中…… ${data.currentTime}/${data.duration}` }); } }
                        onPlayablePercent = {(data) => { this.setState({ buffPercent: data.bufferpercent }); } }
                        onStartBuffer={() => { this.setState({ eventInfo: '缓冲开始！' }); } }
                        onBufferPercent={(data) => { this.setState({ eventInfo: `缓冲中…… ${data.videobuff}%` }); } }
                        onEndBuffer={() => { this.setState({ eventInfo: '缓冲完毕！' }); } }
                        onStartRending={() => { this.setState({ eventInfo: '渲染第一帧……' }); } }
                        onSeek={(data) => { this.setState({ eventInfo: `跳转到……${data.currentTime}+${data.seekTime}` }); } }
                        onSeekComplete={(data) => { this.setState({ eventInfo: `跳转完毕！` }); } }
                        onPause={(data) => { this.setState({ eventInfo: `暂停…… ${data.currentTime}/${data.duration}` }); } }
                        onResume={(data) => { this.setState({ eventInfo: `恢复播放…… ${data.currentTime}/${data.duration}` }); } }
                        onEnd={() => { this.setState({ eventInfo: '播放完毕！' }); } }
                        onAdStart={() => { this.setState({ advertInfo: '广告开始！' }); } }
                        onAdProgress={(data) => { this.setState({ advertInfo: `广告播放中……倒计时${data.AdTime}` }); } }
                        onAdComplete={() => { this.setState({ advertInfo: `广告结束！` }); } }
                        onMMSVodLoad={(data) => { this.setState({ mediaInfo: `点播媒资：status_code=${data.status_code},data=${data.data},http_code=${data.http_code}` }); } }
                        onMMSLiveLoad={(data) => { this.setState({ mediaInfo: `直播媒资：${data}` }); } }
                        onMMSActionLoad={(data) => { this.setState({ mediaInfo: `活动直播：${data}` }); } }
                        onMMSPlayURLLoad={(data) => { this.setState({ mediaInfo: `媒资调度：${data}` }); } }
                        />
                </TouchableOpacity>

                <View style={styles.displays}>
                    <View style={styles.infoDisplays}>
                        <View style={styles.bufferDisplay}>
                            <Text style={styles.DisplayOption}>
                                {this.state.sourceInfo}
                            </Text>
                        </View>
                        <View style={styles.bufferDisplay}>
                            <Text style={[styles.DisplayOption, { color: 'blue' }]}>
                                {this.state.videoInfo} -
                                {this.state.rateListInfo}
                            </Text>
                        </View>
                        <View style={styles.bufferDisplay}>
                            <Text style={[styles.DisplayOption, { color: 'red' }]}>
                                {this.state.eventInfo} 已缓冲{this.state.buffPercent}%
                            </Text>
                        </View>
                        <View style={styles.bufferDisplay}>
                            <Text style={[styles.DisplayOption, { color: 'green' }]}>
                                {this.state.advertInfo}
                            </Text>
                        </View>
                        <View style={styles.bufferDisplay}>
                            <Text style={[styles.DisplayOption, { color: 'yellow' }]}>
                                {this.state.mediaInfo}
                            </Text>
                        </View>
                    </View>
                </View>

                <View style={styles.controls}>
                    <View style={styles.generalControls}>
                        <View style={styles.volumeControl}>
                            {this.renderRateControl('21') }
                            {this.renderRateControl('13') }
                            {this.renderRateControl('22') }
                        </View>
                    </View>

                    <View style={styles.trackingControls}>
                        <TouchableOpacity onPress={() => { this.setState({ seek: this.state.currentTime + 50 }); } }>
                            <View style={styles.progress}>
                                <View style={[styles.innerProgressCompleted, { flex: flexCompleted }]} />
                                <View style={[styles.innerProgressRemaining, { flex: flexRemaining }]} />
                            </View>
                        </TouchableOpacity>
                    </View>
                </View>
            </View >
        );
    }
}


const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'black',
    },
    fullScreen: {
        position: 'absolute',
        top: 0,
        left: 0,
        bottom: 0,
        right: 0,
    },
    controls: {
        backgroundColor: "transparent",
        borderRadius: 5,
        position: 'absolute',
        bottom: 20,
        left: 20,
        right: 20,
    },
    displays: {
        backgroundColor: "transparent",
        borderRadius: 5,
        position: 'absolute',
        bottom: 100,
        left: 20,
        right: 20,
    },
    progress: {
        flex: 1,
        flexDirection: 'row',
        borderRadius: 3,
        overflow: 'hidden',
    },
    innerProgressCompleted: {
        height: 20,
        backgroundColor: '#cccccc',
    },
    innerProgressBuffered: {
        height: 20,
        backgroundColor: '#cfe2f3',
    },
    innerProgressRemaining: {
        height: 20,
        backgroundColor: '#2C2C2C',
    },
    infoDisplays: {
        flex: 1,
        flexDirection: 'column',
        borderRadius: 4,
        overflow: 'hidden',
        paddingBottom: 20,
    },
    bufferDisplay: {
        flex: 1,
        flexDirection: 'row',
        justifyContent: 'center',
    },
    DisplayOption: {
        alignSelf: 'center',
        fontSize: 11,
        color: "white",
        paddingLeft: 2,
        paddingRight: 2,
        lineHeight: 12,
    },
    generalControls: {
        flex: 1,
        flexDirection: 'row',
        borderRadius: 4,
        overflow: 'hidden',
        paddingBottom: 20,
    },
    rateControl: {
        flex: 1,
        flexDirection: 'row',
        justifyContent: 'center',
    },
    volumeControl: {
        flex: 1,
        flexDirection: 'row',
        justifyContent: 'center',
    },
    resizeModeControl: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
    },
    controlOption: {
        alignSelf: 'center',
        fontSize: 12,
        color: "white",
        paddingLeft: 2,
        paddingRight: 2,
        lineHeight: 12,
    },
});


export default VideoPlayer;