import React, { Component } from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from 'react-native';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import Orientation from '../componets/RCTOrientation';

class orient extends Component {
  constructor() {
    super();
    const init = Orientation.getInitialOrientation();
    this.state = {
      init,
      or: init,
      // sor: init,
    };
  }

  componentWillMount() {
    Orientation.addOnOrientationListener((or) => this.setState({ or }));
  }

  componentWillUnmount() {
    Orientation.removeOnOrientationListener((or) => this.setState({ or }));
  }

  render() {
    const { init, or, sor} = this.state;
    return (
      <View style={styles.container}>
        <Text style={styles.welcome}>
          锁定测试页面
        </Text>
        <Text style={styles.instructions}>
          {`初始方向: ${init}`}
        </Text>
        <Text style={styles.instructions}>
          {`当前方向: ${or}`}
        </Text>
        <TouchableOpacity onPress={(para) => Orientation.setOrientation(Orientation.ORIENTATION_UNSPECIFIED)} style={styles.button} >
          <Text style={styles.instructions}>
            解锁
          </Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={(para) => Orientation.setOrientation(Orientation.ORIENTATION_PORTRAIT)} style={styles.button} >
          <Text style={styles.instructions}>
            锁定正竖屏
          </Text>
        </TouchableOpacity>
        <View style={styles.buttonContainer}>
          <TouchableOpacity onPress={(para) => Orientation.setOrientation(Orientation.ORIENTATION_LANDSCAPE)} style={styles.button} >
            <Text style={styles.instructions}>
              锁定正横屏
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={(para) => Orientation.setOrientation(Orientation.ORIENTATION_REVERSE_LANDSCAPE)} style={styles.button} >
            <Text style={styles.instructions}>
              锁定反横屏
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={(para) => Orientation.setOrientation(Orientation.ORIENTATION_REVERSE_PORTRAIT)} style={styles.button} >
            <Text style={styles.instructions}>
              锁定反竖屏
            </Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={(para) => this.props.navigator.pop()} style={styles.button}>
          <Text style={styles.instructions}>
            返 回
            </Text>
        </TouchableOpacity>
      </View>

    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
  buttonContainer: {
    flex: 0,
    flexDirection: 'row',
    justifyContent: 'space-around'
  },
  button: {
    padding: 5,
    margin: 5,
    borderWidth: 1,
    borderColor: 'white',
    borderRadius: 3,
    backgroundColor: 'grey',
  }
});


//配置Map映射表，拿到自己关心的数据
const mapStateToProps = state => ({
  //state.xxx必须与reducer同名
  //datasource: state.play.datasource,
});


const mapDispatchToProps = dispatch => ({
  //actions: bindActionCreators(playActions, dispatch)
});

//连接Redux
export default connect(mapStateToProps, mapDispatchToProps)(orient);
