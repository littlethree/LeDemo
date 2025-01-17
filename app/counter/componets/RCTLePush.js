/*************************************************************************
 * Description: 乐视推流组件
 * Author: raojia
 * Mail: raojia@le.com
 * Created Time: 2017-02-05
 * Modified Time: 2017-02-05
 ************************************************************************/
'use strict';

import React, { Component, PropTypes } from 'react';
import {
    StyleSheet,
    requireNativeComponent,
    NativeModules,
    View
} from 'react-native';
import resolveAssetSource from 'react-native/Libraries/Image/resolveAssetSource';

const styles = StyleSheet.create({
    base: {
        overflow: 'hidden',
    },
});

/**
 * 封装LeSDK推流组件
 * @export
 * @class Push
 * @extends {Component}
 */
export default class Push extends Component {
    /**
     * 设置组件别名
     * @param {any} component 组件名
     * @memberOf Push
     */
    _assignRoot = (component) => {
        this._root = component;
    };

    /**
     * 设置封装属性映射为Native属性
     * @param {any} nativeProps 原生属性
     * @memberOf Push
     */
    setNativeProps(nativeProps) {
        this._root.setNativeProps(nativeProps);
    };

    /**
     * 推流类型
     */
    static PUSH_TYPE_MOBILE_URI = NativeModules.UIManager.RCTLePush.Constants.PUSH_TYPE_MOBILE_URI;
    static PUSH_TYPE_MOBILE = NativeModules.UIManager.RCTLePush.Constants.PUSH_TYPE_MOBILE;
    static PUSH_TYPE_LECLOUD = NativeModules.UIManager.RCTLePush.Constants.PUSH_TYPE_LECLOUD;
    static PUSH_TYPE_NONE = NativeModules.UIManager.RCTLePush.Constants.PUSH_TYPE_NONE;

    /**
     * 滤镜类型
     */
    static FILTER_VIDEO_NONE = 0;
    static FILTER_VIDEO_DEFAULT = 1;
    static FILTER_VIDEO_WARM = 2;
    static FILTER_VIDEO_CALM = 3;
    static FILTER_VIDEO_ROMANCE = 4;

    /**
     * 推流状态
     */
    static PUSH_STATE_CLOSED = 0;
    static PUSH_STATE_CONNECTING = 1;
    static PUSH_STATE_CONNECTED = 2;
    static PUSH_STATE_OPENED = 3;
    static PUSH_STATE_DISCONNECTING = 4;
    static PUSH_STATE_ERROR = 5;
    static PUSH_STATE_WARNING = 6;

    static propTypes = {
        /* 原生属性 */
        //para: PropTypes.object,

        /* 组件属性 */
        /* 推流参数：支持移动直播（有地址）、移动直播（无地址）、乐视直播 */
        target: PropTypes.oneOfType([
            //移动直播（有地址）
            PropTypes.shape({
                type: PropTypes.number.isRequired,
                url: PropTypes.string,
                landscape: PropTypes.bool,
                frontCamera: PropTypes.bool,
                focus: PropTypes.bool,
            }),
            //移动直播（无地址）
            PropTypes.shape({
                type: PropTypes.number.isRequired,
                streamName: PropTypes.string,
                domainName: PropTypes.string,
                appkey: PropTypes.string,
                landscape: PropTypes.bool,
                frontCamera: PropTypes.bool,
                focus: PropTypes.bool,
            }),
            //乐视云直播
            PropTypes.shape({
                type: PropTypes.number.isRequired,
                activityId: PropTypes.string,
                userId: PropTypes.string,
                secretKey: PropTypes.string,
                landscape: PropTypes.bool,
                frontCamera: PropTypes.bool,
                focus: PropTypes.bool,
            }),
        ]).isRequired,

        /* 开始/停止推流 */
        push: PropTypes.bool,
        /* 切换摄像头 */
        camera: PropTypes.number,
        /* 开始/停止闪光灯 */
        flash: PropTypes.bool,
        /* 选取滤镜 */
        filter: PropTypes.number,
        /* 设置音量 */
        volume: PropTypes.number,

        /* 推流端事件相关 */
        onPushTargetLoad: PropTypes.func,
        onPushStateUpdate: PropTypes.func,
        onPushTimeUpdate: PropTypes.func,
        onPushCameraUpdate: PropTypes.func,
        onPushFlashUpdate: PropTypes.func,
        onPushFilterUpdate: PropTypes.func,
        onPushVolumeUpdate: PropTypes.func,

        ...View.propTypes,
    };

    render() {
        const target = resolveAssetSource(this.props.target) || {};
        /* 组件属性赋值 */
        const nativeProps = Object.assign({}, this.props);
        Object.assign(nativeProps, {
            style: [styles.base, nativeProps.style],
            para: {
                type: target.type,
                url: target.url || null,
                streamName: target.streamName || null,
                domainName: target.domainName || null,
                appkey: target.appkey || null,
                activityId: target.activityId || null,
                userId: target.userId || null,
                secretKey: target.secretKey || null,
                landscape: target.landscape || false,
                frontCamera: target.frontCamera || false,
                focus: target.focus || true,
            },
            /*回调函数属性赋值*/
            onPushTargetLoad: (event) => { this.props.onPushTargetLoad && this.props.onPushTargetLoad(event.nativeEvent); },
            /* 推流操作 */
            onPushStateUpdate: (event) => { this.props.onPushStateUpdate && this.props.onPushStateUpdate(event.nativeEvent); },
            onPushTimeUpdate: (event) => { this.props.onPushTimeUpdate && this.props.onPushTimeUpdate(event.nativeEvent); },
            onPushCameraUpdate: (event) => { this.props.onPushCameraUpdate && this.props.onPushCameraUpdate(event.nativeEvent); },
            onPushFlashUpdate: (event) => { this.props.onPushFlashUpdate && this.props.onPushFlashUpdate(event.nativeEvent); },
            onPushFilterUpdate: (event) => { this.props.onPushFilterUpdate && this.props.onPushFilterUpdate(event.nativeEvent); },
            onPushVolumeUpdate: (event) => { this.props.onPushVolumeUpdate && this.props.onPushVolumeUpdate(event.nativeEvent); },
        });

        // alert(NativeModules.UIManager.RCTLePush.Constants.PUSH_TYPE_MOBILE);

        // console.log(nativeProps);
        return (
            <RCTLePush
                ref={this._assignRoot}
                {...nativeProps}
            />
        );
    }
}

const RCTLePush = requireNativeComponent('RCTLePush', Push, {
    nativeOnly: { para: true },
});
