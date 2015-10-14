#!/usr/bin/env node
var fs = require('fs'),
    path = require('path'),
    rootdir = process.argv[2],
    events = new (require('events').EventEmitter)(),
    shell = require('shelljs');

if (!rootdir)
    return;

module.exports = function (context) {

    var cordova_util = context.requireCordovaModule('cordova-lib/src/cordova/util'),
        ConfigParser = context.requireCordovaModule('cordova-lib/src/configparser/ConfigParser'),
        platforms = context.requireCordovaModule('cordova-lib/src/cordova/platform'),
        projectRoot = cordova_util.isCordova();
    var xml = path.join(projectRoot, 'config.xml');
    var cfg = new ConfigParser(xml);
    var packageName = cfg.packageName();

    /**
     * The absolute path for the file.
     *
     * @param {String} platform
     *      The name of the platform like 'ios'.
     * @param {String} relPath
     *      The relative path from the platform root directory.
     *
     * @return String
     */
    function getAbsPath(platform, relPath) {
        var platform_path = path.join(projectRoot, 'platforms', platform);
        console.log(platform_path);
        return path.join(platform_path, relPath);
    }

    /**
     * Replaces a string with another one in a file.
     *
     * @param {String} path
     *      Absolute or relative file path from cordova root project.
     * @param {String} to_replace
     *      The string to replace.
     * @param {String}
     *      The string to replace with.
     */
    function replace (path, to_replace, replace_with) {
        var data = fs.readFileSync(path, 'utf8'),
            result;
        if (data.indexOf(replace_with) > -1)
            return;
        result = data.replace(new RegExp(to_replace, 'g'), replace_with);
        fs.writeFileSync(path, result, 'utf8');
    }
    
    var wxPayEntryActivitySourcDir = getAbsPath('android', '/src/com/justep/x5/v3/wxapi/');
    var wxPayEntryActivityPath = path.join(wxPayEntryActivitySourcDir ,'WXPayEntryActivity.java');
    var wxPayEntryActivityDestDir = getAbsPath('android', '/src/' + packageName.replace(/\./g,'/') +'/wxapi/');


    if(context.hook == "after_plugin_install"){
        replace(wxPayEntryActivityPath, 'package com.justep.x5.v3.wxapi;', "package " + packageName + ".wxapi;");
        shell.mkdir('-p', wxPayEntryActivityDestDir);
        events.emit('verbose', 'Copying from location "' + wxPayEntryActivityPath + '" to location "' + wxPayEntryActivityDestDir + '"');
        shell.mv(wxPayEntryActivityPath, wxPayEntryActivityDestDir);    
    }else if(context.hook == "before_plugin_rm"){
        shell.rm('-rf', wxPayEntryActivitySourcDir);
        shell.rm('-rf', wxPayEntryActivityDestDir);
    }
};
