import React, { Component } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert, Image, TouchableOpacity, NativeModules, Dimensions } from 'react-native';

// import Video from 'react-native-video';

// var ImagePicker = NativeModules.ImageCropPickerModule;

import * as ImageCropPicker from '../componets/RCTImageCropPicker';

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center'
    },
    button: {
        backgroundColor: 'blue',
        marginBottom: 10
    },
    text: {
        color: 'white',
        fontSize: 20,
        textAlign: 'center'
    }
});

export default class App extends Component {

    constructor() {
        super();
        this.state = {
            image: null,
            images: null
        };
    }

    pickSingleWithCamera(cropping) {
        ImageCropPicker.openCamera({
            cropping: cropping,
            width: 500,
            height: 500,
        }, image => {
            console.log('received image', image);
            this.setState({
                image: { uri: image.path, width: image.width, height: image.height },
                images: null
            });
        }, e => alert(e));
    }

    pickSingleBase64(cropit) {
        ImageCropPicker.openPicker({
            width: 300,
            height: 300,
            cropping: cropit,
            includeBase64: true
        }, image => {
            console.log('received base64 image');
            this.setState({
                image: { uri: `data:${image.mime};base64,` + image.data, width: image.width, height: image.height },
                images: null
            });
        }, e => alert(e));
    }

    cleanupImages() {
        // ImagePicker.clean().then(() => {
        //     console.log('removed tmp images from tmp directory');
        // }).catch(e => {
        //     alert(e);
        // });
        ImageCropPicker.clean(() => console.log('removed tmp images from tmp directory'), e => alert(e));
    }

    cleanupSingleImage() {
        let image = this.state.image || (this.state.images && this.state.images.length ? this.state.images[0] : null);
        console.log('will cleanup image', image);

        // ImagePicker.cleanSingle(image ? image.uri : null).then(() => {
        //     console.log(`removed tmp image ${image.uri} from tmp directory`);
        // }).catch(e => {
        //     alert(e);
        // });
        ImageCropPicker.cleanSingle(image ? image.uri : null,
            () => console.log(`removed tmp image ${image.uri} from tmp directory`), e => alert(e));
    }

    cropLast() {
        if (!this.state.image) {
            return Alert.alert('No image', 'Before open cropping only, please select image');
        }

        ImageCropPicker.openCropper({
            path: this.state.image.uri,
            width: 200,
            height: 200
        }, image => {
            console.log('received cropped image', image);
            this.setState({
                image: { uri: image.path, width: image.width, height: image.height, mime: image.mime },
                images: null
            });
        }, e => {
            console.log(e);
            Alert.alert(e.message ? e.message : e);
        });
    }

    pickSingle(cropit, circular = false) {
        ImageCropPicker.openPicker({
            width: 300,
            height: 300,
            cropping: cropit,
            cropperCircleOverlay: circular,
            compressImageMaxWidth: 640,
            compressImageMaxHeight: 480,
            compressImageQuality: 0.5,
            compressVideoPreset: 'MediumQuality',
        }, image => {
            console.log('received image', image);
            this.setState({
                image: { uri: image.path, width: image.width, height: image.height, mime: image.mime },
                images: null
            });
        }, e => {
            console.log(e);
            Alert.alert(e.message ? e.message : e);
        });
    }

    pickMultiple() {
        ImageCropPicker.openPicker({
            multiple: true
        }, images => {
            this.setState({
                image: null,
                images: images.map(i => {
                    console.log('received image', i);
                    return { uri: i.path, width: i.width, height: i.height, mime: i.mime };
                })
            });
        }, e => alert(e));
    }

    scaledHeight(oldW, oldH, newW) {
        return (oldH / oldW) * newW;
    }

    /*renderVideo(video) {
        return (<View style={{ height: 300, width: 300 }}>
            <Video source={{ uri: video.uri, type: video.mime }}
                style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    bottom: 0,
                    right: 0
                }}
                rate={1}
                paused={false}
                volume={1}
                muted={false}
                resizeMode={'cover'}
                onError={e => console.log(e)}
                onLoad={load => console.log(load)}
                repeat={true} />
        </View>);
    }*/

    renderImage(image) {
        return <Image style={{ width: 300, height: 300, resizeMode: 'contain' }} source={image} />
    }

    renderAsset(image) {
        // if (image.mime && image.mime.toLowerCase().indexOf('video/') !== -1) {
        //     return this.renderVideo(image);
        // }
        return this.renderImage(image);
    }

    render() {
        return (<View style={styles.container}>
            <ScrollView>
                {this.state.image ? this.renderAsset(this.state.image) : null}
                {this.state.images ? this.state.images.map(i => <View key={i.uri}>{this.renderAsset(i)}</View>) : null}
            </ScrollView>

            <TouchableOpacity onPress={() => this.pickSingleWithCamera(false)} style={styles.button}>
                <Text style={styles.text}>Select Single With Camera</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.pickSingleWithCamera(true)} style={styles.button}>
                <Text style={styles.text}>Select Single With Camera With Cropping</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.pickSingle(false)} style={styles.button}>
                <Text style={styles.text}>Select Single</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.cropLast()} style={styles.button}>
                <Text style={styles.text}>Crop Last Selected Image</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.pickSingleBase64(false)} style={styles.button}>
                <Text style={styles.text}>Select Single Returning Base64</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.pickSingle(true)} style={styles.button}>
                <Text style={styles.text}>Select Single With Cropping</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => this.pickSingle(true, true)} style={styles.button}>
                <Text style={styles.text}>Select Single With Circular Cropping</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={this.pickMultiple.bind(this)} style={styles.button}>
                <Text style={styles.text}>Select Multiple</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={this.cleanupImages.bind(this)} style={styles.button}>
                <Text style={styles.text}>Cleanup All Images</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={this.cleanupSingleImage.bind(this)} style={styles.button}>
                <Text style={styles.text}>Cleanup Single Image</Text>
            </TouchableOpacity>
        </View>);
    }
}