/**
 * Android原生组件：俯瞰图
 */
'use strict';

import React, {Component, PropTypes} from 'react';
import {
    requireNativeComponent,
    View,
} from 'react-native';

var face = {
    name: 'KenBurnsView',
    propTypes: {
        picName: PropTypes.string.isRequired,
        ...View.propTypes,
    }
};

export default requireNativeComponent('KenBurnsView', face);